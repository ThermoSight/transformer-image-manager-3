package com.example.transformer_manager_backkend.service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.transformer_manager_backkend.entity.Annotation;
import com.example.transformer_manager_backkend.entity.AnnotationBox;
import com.example.transformer_manager_backkend.entity.ModelTrainingRun;
import com.example.transformer_manager_backkend.entity.ModelTrainingRun.Status;
import com.example.transformer_manager_backkend.entity.ModelTrainingRun.TriggerType;
import com.example.transformer_manager_backkend.repository.ModelTrainingRunRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class ModelTrainingService {

    private static final Logger logger = LoggerFactory.getLogger(ModelTrainingService.class);

    private final ModelTrainingRunRepository runRepository;
    private final ObjectMapper objectMapper;

    @Value("${upload.directory:./uploads}")
    private String uploadDirectory;

    @Value("${app.model.training.enabled:true}")
    private boolean trainingEnabled;

    @Value("${app.model.training.auto-trigger:true}")
    private boolean autoTrigger;

    @Value("${app.model.training.auto-promote:true}")
    private boolean autoPromote;

    @Value("${app.model.training.python:python}")
    private String pythonExecutable;

    @Value("${app.model.training.script:../automatic-anamoly-detection/Model_Inference/update_model_from_feedback.py}")
    private String trainingScriptPath;

    @Value("${app.model.training.dataset-dir:../automatic-anamoly-detection/Model_Inference/feedback_dataset}")
    private String datasetRootPath;

    @Value("${app.model.training.versions-dir:../automatic-anamoly-detection/Model_Inference/model_versions}")
    private String versionsRootPath;

    @Value("${app.model.training.base-model:../automatic-anamoly-detection/Model_Inference/model_weights/model.ckpt}")
    private String baseModelPathValue;

    private final ExecutorService executorService;
    private final BlockingQueue<Long> trainingQueue;
    private final Object datasetWriteLock = new Object();

    private Path datasetRoot;
    private Path datasetJsonl;
    private Path datasetImagesDir;
    private Path datasetAnnotationsDir;
    private Path versionsRoot;
    private Path trainingScript;
    private Path baseModelPath;

    public ModelTrainingService(ModelTrainingRunRepository runRepository) {
        this.runRepository = runRepository;
        this.objectMapper = new ObjectMapper();
        this.executorService = Executors.newSingleThreadExecutor();
        this.trainingQueue = new LinkedBlockingQueue<>();
    }

    @PostConstruct
    public void initialize() {
        if (!trainingEnabled) {
            logger.warn("Model training pipeline disabled via configuration");
            return;
        }

        try {
            datasetRoot = resolvePath(datasetRootPath);
            Files.createDirectories(datasetRoot);

            datasetImagesDir = datasetRoot.resolve("images");
            Files.createDirectories(datasetImagesDir);

            datasetAnnotationsDir = datasetRoot.resolve("annotations");
            Files.createDirectories(datasetAnnotationsDir);

            datasetJsonl = datasetRoot.resolve("records.jsonl");

            versionsRoot = resolvePath(versionsRootPath);
            Files.createDirectories(versionsRoot);

            trainingScript = resolvePath(trainingScriptPath);
            if (!Files.exists(trainingScript)) {
                logger.warn("Model training script not found at {}", trainingScript);
            }

            baseModelPath = resolvePath(baseModelPathValue);
            if (!Files.exists(baseModelPath)) {
                logger.warn("Base model path {} does not exist. Training runs may fail until provided.", baseModelPath);
            }

            // Start worker thread
            executorService.submit(this::processQueueLoop);
            logger.info("Model training service initialized. Dataset root: {}", datasetRoot);
        } catch (Exception e) {
            logger.error("Failed to initialize model training service", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }

    /**
     * Handle updated annotation feedback and optionally queue a training run.
     */
    public void handleAnnotationFeedback(Annotation annotation) {
        if (!trainingEnabled || annotation == null) {
            return;
        }

        try {
            FeedbackAppendResult feedbackResult = appendFeedbackRecord(annotation);

            ModelTrainingRun run = new ModelTrainingRun();
            run.setRunId(UUID.randomUUID().toString());
            run.setTriggerType(TriggerType.AUTO_FEEDBACK);
            run.setRequestedBy(feedbackResult.annotatorName);
            run.setSourceAnnotation(annotation);
            run.setAnalysisJobId(feedbackResult.analysisJobId);
            run.setTransformerId(feedbackResult.transformerId);
            run.setInspectionId(feedbackResult.inspectionId);
            run.setImageId(feedbackResult.imageId);
            run.setDatasetPath(datasetRoot.toString());
            run.setFeedbackSummary(feedbackResult.summary);
            run.setAppendedAnnotations(1);
            run.setAppendedBoxes(feedbackResult.boxCount);

            run = runRepository.save(run);
            logger.info("Recorded feedback annotation {} for training run {}", annotation.getId(), run.getRunId());

            if (autoTrigger) {
                trainingQueue.offer(run.getId());
            }
        } catch (Exception e) {
            logger.error("Failed to append annotation {} to feedback dataset", annotation.getId(), e);
        }
    }

    /**
     * Manually trigger a model training run.
     */
    public ModelTrainingRun triggerManualRun(String requestedBy, String notes) {
        if (!trainingEnabled) {
            throw new IllegalStateException("Model training service disabled");
        }

        ModelTrainingRun run = new ModelTrainingRun();
        run.setRunId(UUID.randomUUID().toString());
        run.setTriggerType(TriggerType.MANUAL);
        run.setRequestedBy(requestedBy);
        run.setDatasetPath(datasetRoot != null ? datasetRoot.toString() : null);
        run.setFeedbackSummary(notes);
        run.setAppendedAnnotations(0);
        run.setAppendedBoxes(0);

        run = runRepository.save(run);
        trainingQueue.offer(run.getId());
        logger.info("Manual model training run queued with id {}", run.getRunId());
        return run;
    }

    public List<ModelTrainingRun> listRuns() {
        return runRepository.findAllNewestFirst();
    }

    public Optional<ModelTrainingRun> getRun(Long id) {
        return runRepository.findById(id);
    }

    public Optional<ModelTrainingRun> getLatestRun() {
        return runRepository.findTopByOrderByCreatedAtDesc();
    }

    private void processQueueLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Long runId = trainingQueue.take();
                processRun(runId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("Unexpected failure in training queue loop", e);
            }
        }
    }

    private void processRun(Long runId) {
        Optional<ModelTrainingRun> maybeRun = runRepository.findById(runId);
        if (maybeRun.isEmpty()) {
            logger.warn("Requested training run {} not found", runId);
            return;
        }

        ModelTrainingRun run = maybeRun.get();
        run.setStatus(Status.RUNNING);
        run.setStartedAt(LocalDateTime.now());
        runRepository.save(run);

        if (datasetJsonl == null || versionsRoot == null || trainingScript == null) {
            run.setStatus(Status.FAILED);
            run.setErrorMessage("Model training service not initialized correctly");
            run.setCompletedAt(LocalDateTime.now());
            runRepository.save(run);
            return;
        }

        Path runWorkspace = versionsRoot.resolve("run-" + run.getRunId());
        Path resultFile = runWorkspace.resolve("training_result.json");
        Path stdoutFile = runWorkspace.resolve("stdout.log");

        try {
            Files.createDirectories(runWorkspace);
        } catch (IOException io) {
            logger.error("Failed to create workspace for run {}", run.getRunId(), io);
            run.setStatus(Status.FAILED);
            run.setErrorMessage("Unable to create run workspace: " + io.getMessage());
            run.setCompletedAt(LocalDateTime.now());
            runRepository.save(run);
            return;
        }

        List<String> command = buildTrainingCommand(run, resultFile);
        logger.info("Starting model update run {} using command {}", run.getRunId(), command);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        int exitCode;
        StringBuilder stdout = new StringBuilder();
        try {
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stdout.append(line).append(System.lineSeparator());
                }
            }
            exitCode = process.waitFor();
        } catch (IOException | InterruptedException ex) {
            logger.error("Failed to execute training run {}", run.getRunId(), ex);
            run.setStatus(Status.FAILED);
            run.setErrorMessage("Execution failure: " + ex.getMessage());
            run.setCompletedAt(LocalDateTime.now());
            runRepository.save(run);
            return;
        }

        try {
            Files.writeString(stdoutFile, stdout.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException io) {
            logger.warn("Failed to persist stdout log for run {}", run.getRunId(), io);
        }

        if (exitCode != 0) {
            logger.error("Model training script exited with code {} for run {}", exitCode, run.getRunId());
            run.setStatus(Status.FAILED);
            run.setErrorMessage("Model update script exited with code " + exitCode);
            run.setCompletedAt(LocalDateTime.now());
            runRepository.save(run);
            return;
        }

        if (!Files.exists(resultFile)) {
            logger.error("Training run {} completed but result file {} missing", run.getRunId(), resultFile);
            run.setStatus(Status.FAILED);
            run.setErrorMessage("Training result not produced");
            run.setCompletedAt(LocalDateTime.now());
            runRepository.save(run);
            return;
        }

        try {
            String resultJson = Files.readString(resultFile, StandardCharsets.UTF_8);
            JsonNode resultNode = objectMapper.readTree(resultJson);

            String statusText = resultNode.path("status").asText("ok");
            boolean skipped = "skipped".equalsIgnoreCase(statusText);
            boolean failed = "failed".equalsIgnoreCase(statusText);

            run.setMetricsJson(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultNode));
            run.setVersionTag(resultNode.path("version_tag").asText(null));
            run.setModelOutputPath(resultNode.path("relative_model_path").asText(null));
            run.setErrorMessage(resultNode.path("message").asText(null));

            if (resultNode.has("appended_annotations")) {
                run.setAppendedAnnotations(resultNode.get("appended_annotations").asInt());
            }
            if (resultNode.has("appended_boxes")) {
                run.setAppendedBoxes(resultNode.get("appended_boxes").asInt());
            }

            if (failed) {
                run.setStatus(Status.FAILED);
            } else if (skipped) {
                run.setStatus(Status.SKIPPED);
            } else {
                run.setStatus(Status.SUCCEEDED);
            }

            run.setCompletedAt(LocalDateTime.now());
            runRepository.save(run);

            if (run.getStatus() == Status.SUCCEEDED && autoPromote) {
                promoteNewModel(resultNode);
            }
        } catch (Exception e) {
            logger.error("Failed to parse training result for run {}", run.getRunId(), e);
            run.setStatus(Status.FAILED);
            run.setErrorMessage("Unable to parse training result: " + e.getMessage());
            run.setCompletedAt(LocalDateTime.now());
            runRepository.save(run);
        }
    }

    private List<String> buildTrainingCommand(ModelTrainingRun run, Path resultFile) {
        List<String> command = new ArrayList<>();
        command.add(pythonExecutable);
        command.add(trainingScript.toString());
        command.add("--dataset-json");
        command.add(datasetJsonl.toString());
        command.add("--images-dir");
        command.add(datasetImagesDir.toString());
        command.add("--base-model");
        command.add(baseModelPath.toString());
        command.add("--versions-root");
        command.add(versionsRoot.toString());
        command.add("--result-file");
        command.add(resultFile.toString());
        command.add("--run-id");
        command.add(run.getRunId());
        if (run.getFeedbackSummary() != null && !run.getFeedbackSummary().isBlank()) {
            command.add("--notes");
            command.add(run.getFeedbackSummary());
        }
        return command;
    }

    private void promoteNewModel(JsonNode resultNode) {
        if (!resultNode.has("model_path")) {
            logger.warn("Training result missing model_path; skip promotion");
            return;
        }

        Path newModel = Paths.get(resultNode.get("model_path").asText());
        if (!Files.exists(newModel)) {
            logger.warn("New model file {} does not exist; promotion skipped", newModel);
            return;
        }

        try {
            Files.createDirectories(baseModelPath.getParent());
            Files.copy(newModel, baseModelPath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Promoted model {} to {}", newModel, baseModelPath);
        } catch (IOException e) {
            logger.error("Failed to promote new model {}", newModel, e);
        }
    }

    private FeedbackAppendResult appendFeedbackRecord(Annotation annotation) throws IOException {
        if (datasetRoot == null) {
            throw new IllegalStateException("Dataset root not initialized");
        }

        synchronized (datasetWriteLock) {
            AnnotationBoxActionCounts counts = new AnnotationBoxActionCounts();

            ArrayNode boxesArray = objectMapper.createArrayNode();
            if (annotation.getAnnotationBoxes() != null) {
                for (AnnotationBox box : annotation.getAnnotationBoxes()) {
                    ObjectNode boxNode = objectMapper.createObjectNode();
                    boxNode.put("x", box.getX());
                    boxNode.put("y", box.getY());
                    boxNode.put("width", box.getWidth());
                    boxNode.put("height", box.getHeight());
                    if (box.getType() != null) {
                        boxNode.put("type", box.getType());
                    }
                    if (box.getConfidence() != null) {
                        boxNode.put("confidence", box.getConfidence());
                    }
                    if (box.getComments() != null) {
                        boxNode.put("comments", box.getComments());
                    }
                    if (box.getAction() != null) {
                        boxNode.put("action", box.getAction().name());
                        counts.increment(box.getAction());
                    }
                    boxesArray.add(boxNode);
                }
            }

            Path imagePath = resolveImagePath(annotation);
            String imageFileName = buildImageFileName(annotation, imagePath);
            Path copiedImage = null;
            if (imagePath != null && Files.exists(imagePath)) {
                copiedImage = datasetImagesDir.resolve(imageFileName);
                Files.copy(imagePath, copiedImage, StandardCopyOption.REPLACE_EXISTING);
            }

            Path annotationDump = datasetAnnotationsDir
                    .resolve("annotation-" + annotation.getId() + "-v" + safeVersion(annotation.getVersion()) + ".json");
            Files.writeString(annotationDump, annotation.getModifiedResultJson(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            ObjectNode record = objectMapper.createObjectNode();
            record.put("annotationId", annotation.getId());
            record.put("annotationVersion", safeVersion(annotation.getVersion()));
            if (annotation.getAnalysisJob() != null) {
                record.put("analysisJobId", annotation.getAnalysisJob().getId());
                if (annotation.getAnalysisJob().getImage() != null) {
                    record.put("imageId", annotation.getAnalysisJob().getImage().getId());
                    record.put("imageType", annotation.getAnalysisJob().getImage().getType());
                    if (annotation.getAnalysisJob().getImage().getTransformerRecord() != null) {
                        record.put("transformerId", annotation.getAnalysisJob().getImage().getTransformerRecord().getId());
                    }
                    if (annotation.getAnalysisJob().getImage().getInspection() != null) {
                        record.put("inspectionId", annotation.getAnalysisJob().getImage().getInspection().getId());
                    }
                }
            }
            record.put("recordedAt", LocalDateTime.now().toString());
            record.put("annotator", annotation.getAnnotatorDisplayName());
            if (annotation.getComments() != null) {
                record.put("comments", annotation.getComments());
            }
            if (copiedImage != null) {
                record.put("imageFile", datasetRoot.relativize(copiedImage).toString().replace('\\', '/'));
            }
            record.put("annotationFile", datasetRoot.relativize(annotationDump).toString().replace('\\', '/'));
            record.set("boxes", boxesArray);
            record.put("addedBoxes", counts.added);
            record.put("modifiedBoxes", counts.modified);
            record.put("deletedBoxes", counts.deleted);
            record.put("unchangedBoxes", counts.unchanged);
            record.put("boxCount", boxesArray.size());

            try (BufferedWriter writer = Files.newBufferedWriter(datasetJsonl,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND)) {
                writer.write(objectMapper.writeValueAsString(record));
                writer.newLine();
            }

            String summary = String.format(
                    "Annotation %d -> boxes:%d (added:%d modified:%d deleted:%d)",
                    annotation.getId(), boxesArray.size(), counts.added, counts.modified, counts.deleted);

            Long analysisJobId = annotation.getAnalysisJob() != null ? annotation.getAnalysisJob().getId() : null;
            Long transformerId = null;
            Long inspectionId = null;
            Long imageId = null;
            if (annotation.getAnalysisJob() != null && annotation.getAnalysisJob().getImage() != null) {
                imageId = annotation.getAnalysisJob().getImage().getId();
                if (annotation.getAnalysisJob().getImage().getTransformerRecord() != null) {
                    transformerId = annotation.getAnalysisJob().getImage().getTransformerRecord().getId();
                }
                if (annotation.getAnalysisJob().getImage().getInspection() != null) {
                    inspectionId = annotation.getAnalysisJob().getImage().getInspection().getId();
                }
            }

            return new FeedbackAppendResult(summary,
                    boxesArray.size(),
                    annotation.getAnnotatorDisplayName(),
                    analysisJobId,
                    transformerId,
                    inspectionId,
                    imageId);
        }
    }

    private Path resolveImagePath(Annotation annotation) {
        if (annotation.getAnalysisJob() == null || annotation.getAnalysisJob().getImage() == null) {
            return null;
        }
        String imagePath = annotation.getAnalysisJob().getImage().getFilePath();
        if (imagePath == null || imagePath.isBlank()) {
            return null;
        }

        String normalized = imagePath.replace("\\", "/");
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        Path uploadsRoot = resolvePath(uploadDirectory);
        Path primary = uploadsRoot.resolve(normalized).normalize();
        if (Files.exists(primary)) {
            return primary;
        }

        // Many images are stored under uploads/analysis
        Path secondary = uploadsRoot.resolve("analysis").resolve(normalized).normalize();
        if (Files.exists(secondary)) {
            return secondary;
        }

        logger.warn("Could not resolve image {} relative to uploads {}", normalized, uploadsRoot);
        return primary;
    }

    private String buildImageFileName(Annotation annotation, Path imagePath) {
        String baseName = "annotation-" + annotation.getId() + "-v" + safeVersion(annotation.getVersion());
        String extension = ".png";
        if (imagePath != null && imagePath.getFileName() != null) {
            String name = imagePath.getFileName().toString();
            int idx = name.lastIndexOf('.');
            if (idx > 0) {
                extension = name.substring(idx);
            }
        }
        return baseName + extension;
    }

    private int safeVersion(Long version) {
        return version != null ? version.intValue() : 0;
    }

    private Path resolvePath(String pathValue) {
        return Paths.get(pathValue).toAbsolutePath().normalize();
    }

    private static class AnnotationBoxActionCounts {
        int added;
        int modified;
        int deleted;
        int unchanged;

        void increment(AnnotationBox.BoxAction action) {
            if (action == null) {
                unchanged++;
                return;
            }
            switch (action) {
                case ADDED:
                    added++;
                    break;
                case MODIFIED:
                    modified++;
                    break;
                case DELETED:
                    deleted++;
                    break;
                default:
                    unchanged++;
                    break;
            }
        }
    }

    private static class FeedbackAppendResult {
        final String summary;
        final int boxCount;
        final String annotatorName;
        final Long analysisJobId;
        final Long transformerId;
        final Long inspectionId;
        final Long imageId;

        FeedbackAppendResult(String summary,
                int boxCount,
                String annotatorName,
                Long analysisJobId,
                Long transformerId,
                Long inspectionId,
                Long imageId) {
            this.summary = summary;
            this.boxCount = boxCount;
            this.annotatorName = annotatorName;
            this.analysisJobId = analysisJobId;
            this.transformerId = transformerId;
            this.inspectionId = inspectionId;
            this.imageId = imageId;
        }
    }
}
