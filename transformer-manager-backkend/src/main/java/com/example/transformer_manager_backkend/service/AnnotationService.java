package com.example.transformer_manager_backkend.service;

import com.example.transformer_manager_backkend.entity.*;
import com.example.transformer_manager_backkend.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AnnotationService {

    private static final Logger logger = LoggerFactory.getLogger(AnnotationService.class);

    private final AnnotationRepository annotationRepository;
    private final AnnotationBoxRepository annotationBoxRepository;
    private final AnalysisJobRepository analysisJobRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.annotation.python.executable:python}")
    private String pythonExecutable;

    @Value("${app.annotation.refresh.script.path:./automatic-anamoly-detection/Model_Inference/refresh_boxes.py}")
    private String refreshScriptPath;

    public AnnotationService(AnnotationRepository annotationRepository,
            AnnotationBoxRepository annotationBoxRepository,
            AnalysisJobRepository analysisJobRepository) {
        this.annotationRepository = annotationRepository;
        this.annotationBoxRepository = annotationBoxRepository;
        this.analysisJobRepository = analysisJobRepository;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Get or create annotation for an analysis job
     */
    @Transactional
    public Annotation getOrCreateAnnotation(Long analysisJobId, Object annotator) {
        Optional<AnalysisJob> analysisJobOpt = analysisJobRepository.findById(analysisJobId);
        if (analysisJobOpt.isEmpty()) {
            throw new RuntimeException("Analysis job not found: " + analysisJobId);
        }

        AnalysisJob analysisJob = analysisJobOpt.get();

        if (analysisJob.getStatus() != AnalysisJob.AnalysisStatus.COMPLETED) {
            throw new RuntimeException("Cannot annotate incomplete analysis job");
        }

        Optional<Annotation> existingAnnotation = annotationRepository.findByAnalysisJob(analysisJob);
        if (existingAnnotation.isPresent()) {
            return existingAnnotation.get();
        }

        // Create new annotation
        Annotation annotation = new Annotation(analysisJob, analysisJob.getResultJson(), analysisJob.getResultJson());

        // Set annotator
        if (annotator instanceof User) {
            annotation.setAnnotatedByUser((User) annotator);
        } else if (annotator instanceof Admin) {
            annotation.setAnnotatedByAdmin((Admin) annotator);
        }

        // Parse original JSON and create annotation boxes
        List<AnnotationBox> boxes = parseJsonToAnnotationBoxes(analysisJob.getResultJson());
        annotation = annotationRepository.save(annotation);

        for (AnnotationBox box : boxes) {
            box.setAnnotation(annotation);
            box.setAction(AnnotationBox.BoxAction.UNCHANGED);
        }
        annotationBoxRepository.saveAll(boxes);
        annotation.setAnnotationBoxes(boxes);

        logger.info("Created annotation for analysis job {}", analysisJobId);
        return annotation;
    }

    /**
     * Get annotation by its ID (ensures boxes are initialized for serialization)
     */
    @Transactional(readOnly = true)
    public Optional<Annotation> getAnnotationById(Long annotationId) {
        Optional<Annotation> opt = annotationRepository.findById(annotationId);
        opt.ifPresent(a -> {
            // touch boxes to initialize lazy collection
            if (a.getAnnotationBoxes() != null) {
                a.getAnnotationBoxes().size();
            }
        });
        return opt;
    }

    /**
     * Update annotation with new box data
     */
    @Transactional
    public Annotation updateAnnotation(Long annotationId, List<AnnotationBoxDTO> boxDTOs,
            String comments, Object annotator) {
        Optional<Annotation> annotationOpt = annotationRepository.findById(annotationId);
        if (annotationOpt.isEmpty()) {
            throw new RuntimeException("Annotation not found: " + annotationId);
        }

        Annotation annotation = annotationOpt.get();

        // Allow any user to edit for now (open access per request)

        // Update comments
        annotation.setComments(comments != null ? comments : "");
        annotation.setAnnotationType(Annotation.AnnotationType.EDITED);

        // Clear existing boxes by modifying the managed collection
        List<AnnotationBox> managedBoxes = annotation.getAnnotationBoxes();
        if (managedBoxes != null && !managedBoxes.isEmpty()) {
            for (AnnotationBox b : new ArrayList<>(managedBoxes)) {
                b.setAnnotation(null);
            }
            managedBoxes.clear();
        }

        // Create new boxes from DTOs and add to managed collection
        if (boxDTOs == null) {
            boxDTOs = new ArrayList<>();
        }
        for (AnnotationBoxDTO dto : boxDTOs) {
            int x = dto.getX() != null ? dto.getX() : 0;
            int y = dto.getY() != null ? dto.getY() : 0;
            int w = dto.getWidth() != null ? Math.max(1, dto.getWidth()) : 1;
            int h = dto.getHeight() != null ? Math.max(1, dto.getHeight()) : 1;
            String type = (dto.getType() != null && !dto.getType().isBlank()) ? dto.getType() : "Custom Anomaly";

            AnnotationBox box = new AnnotationBox(x, y, w, h, type, dto.getConfidence());
            box.setAnnotation(annotation);
            box.setAction(dto.getAction() != null ? dto.getAction() : AnnotationBox.BoxAction.UNCHANGED);
            box.setComments(dto.getComments());
            managedBoxes.add(box);
        }

        // Update modified JSON based on current managed boxes
        String modifiedJson = createModifiedJson(annotation, managedBoxes);
        annotation.setModifiedResultJson(modifiedJson);

        annotation = annotationRepository.save(annotation);

        // Update the JSON file and refresh the boxed image
        try {
            updateJsonFileAndRefreshImage(annotation, modifiedJson);
        } catch (Exception e) {
            logger.error("Failed to update JSON file and refresh image for annotation {}", annotationId, e);
            // Don't throw exception here to avoid transaction rollback
        }

        logger.info("Updated annotation {} with {} boxes", annotationId, managedBoxes.size());
        return annotation;
    }

    /**
     * Get annotation by analysis job ID
     */
    public Optional<Annotation> getAnnotationByAnalysisJobId(Long analysisJobId) {
        return annotationRepository.findByAnalysisJobId(analysisJobId);
    }

    /**
     * Get all annotations for an inspection
     */
    public List<Annotation> getAnnotationsByInspectionId(Long inspectionId) {
        return annotationRepository.findByInspectionId(inspectionId);
    }

    /**
     * Export feedback log as JSON
     */
    public String exportFeedbackLogAsJson() {
        List<Annotation> annotations = annotationRepository.findAllWithFeedbackData();
        ObjectNode rootNode = objectMapper.createObjectNode();
        ArrayNode feedbackArray = objectMapper.createArrayNode();

        for (Annotation annotation : annotations) {
            ObjectNode feedbackEntry = objectMapper.createObjectNode();
            AnalysisJob job = annotation.getAnalysisJob();
            Image image = job.getImage();

            feedbackEntry.put("imageId", image.getId());
            feedbackEntry.put("imagePath", image.getFilePath());
            feedbackEntry.put("analysisJobId", job.getId());
            feedbackEntry.put("transformerId",
                    image.getTransformerRecord() != null ? image.getTransformerRecord().getId() : null);
            feedbackEntry.put("inspectionId", image.getInspection() != null ? image.getInspection().getId() : null);

            try {
                feedbackEntry.set("originalAIDetections", objectMapper.readTree(annotation.getOriginalResultJson()));
                feedbackEntry.set("finalUserAnnotations", objectMapper.readTree(annotation.getModifiedResultJson()));
            } catch (Exception e) {
                logger.error("Error parsing JSON for annotation {}", annotation.getId(), e);
            }

            feedbackEntry.put("annotatorType", annotation.getAnnotatedByUser() != null ? "USER" : "ADMIN");
            feedbackEntry.put("annotatorName", annotation.getAnnotatorDisplayName());
            feedbackEntry.put("annotationType", annotation.getAnnotationType().toString());
            feedbackEntry.put("comments", annotation.getComments());
            feedbackEntry.put("createdAt", annotation.getCreatedAt().toString());
            feedbackEntry.put("updatedAt", annotation.getUpdatedAt().toString());

            feedbackArray.add(feedbackEntry);
        }

        rootNode.set("feedbackLog", feedbackArray);
        rootNode.put("exportTimestamp", java.time.LocalDateTime.now().toString());
        rootNode.put("totalEntries", feedbackArray.size());

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
        } catch (Exception e) {
            logger.error("Error serializing feedback log", e);
            return "{}";
        }
    }

    /**
     * Parse JSON result to annotation boxes
     */
    private List<AnnotationBox> parseJsonToAnnotationBoxes(String resultJson) {
        List<AnnotationBox> boxes = new ArrayList<>();
        try {
            JsonNode rootNode = objectMapper.readTree(resultJson);
            JsonNode boxesNode = rootNode.get("boxes");

            if (boxesNode != null && boxesNode.isArray()) {
                for (JsonNode boxNode : boxesNode) {
                    JsonNode boxArray = boxNode.get("box");
                    if (boxArray != null && boxArray.isArray() && boxArray.size() >= 4) {
                        int x = boxArray.get(0).asInt();
                        int y = boxArray.get(1).asInt();
                        int width = boxArray.get(2).asInt();
                        int height = boxArray.get(3).asInt();
                        String type = boxNode.get("type").asText("Unknown");
                        Double confidence = boxNode.has("confidence") ? boxNode.get("confidence").asDouble() : null;

                        AnnotationBox box = new AnnotationBox(x, y, width, height, type, confidence);
                        boxes.add(box);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing JSON to annotation boxes", e);
        }
        return boxes;
    }

    /**
     * Create modified JSON from annotation and boxes
     */
    private String createModifiedJson(Annotation annotation, List<AnnotationBox> boxes) {
        try {
            JsonNode originalNode = objectMapper.readTree(annotation.getOriginalResultJson());
            ObjectNode modifiedNode = originalNode.deepCopy();

            // Update the boxes array
            ArrayNode boxesArray = objectMapper.createArrayNode();
            for (AnnotationBox box : boxes) {
                ObjectNode boxNode = objectMapper.createObjectNode();
                ArrayNode coordsArray = objectMapper.createArrayNode();
                coordsArray.add(box.getX());
                coordsArray.add(box.getY());
                coordsArray.add(box.getWidth());
                coordsArray.add(box.getHeight());

                boxNode.set("box", coordsArray);
                boxNode.put("type", box.getType());
                if (box.getConfidence() != null) {
                    boxNode.put("confidence", box.getConfidence());
                }
                boxesArray.add(boxNode);
            }

            modifiedNode.set("boxes", boxesArray);

            return objectMapper.writeValueAsString(modifiedNode);
        } catch (Exception e) {
            logger.error("Error creating modified JSON", e);
            return annotation.getOriginalResultJson();
        }
    }

    /**
     * Update JSON file and refresh the boxed image using Python script
     */
    private void updateJsonFileAndRefreshImage(Annotation annotation, String modifiedJson)
            throws IOException, InterruptedException {
        AnalysisJob job = annotation.getAnalysisJob();
        Image image = job.getImage();

        // Find the corresponding JSON file in uploads/analysis
        String originalPath = image.getFilePath();
        String fileName = originalPath.substring(originalPath.lastIndexOf("/") + 1);
        String baseName = fileName.substring(0, fileName.lastIndexOf("."));
        String jsonFileName = baseName + ".json";

        Path jsonFilePath = Paths.get("uploads", "analysis", jsonFileName);

        if (!Files.exists(jsonFilePath)) {
            logger.warn("JSON file not found: {}", jsonFilePath);
            return;
        }

        // Update the JSON file with modified data
        Files.writeString(jsonFilePath, modifiedJson);
        logger.info("Updated JSON file: {}", jsonFilePath);

        // Run the refresh_boxes.py script to update the image
        String command = String.format("%s \"%s\" --json \"%s\"",
                pythonExecutable,
                refreshScriptPath,
                jsonFilePath.toAbsolutePath().toString());

        logger.info("Running refresh command: {}", command);

        ProcessBuilder processBuilder = new ProcessBuilder();
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            processBuilder.command("cmd", "/c", command);
        } else {
            processBuilder.command("bash", "-c", command);
        }

        Process process = processBuilder.start();

        // Capture output
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                logger.info("Refresh script output: {}", line);
            }

            while ((line = errorReader.readLine()) != null) {
                logger.warn("Refresh script error: {}", line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode == 0) {
            logger.info("Successfully refreshed boxed image for annotation {}", annotation.getId());
        } else {
            logger.error("Refresh script failed with exit code: {}", exitCode);
        }
    }

    /**
     * DTO for annotation box data transfer
     */
    public static class AnnotationBoxDTO {
        private Integer x;
        private Integer y;
        private Integer width;
        private Integer height;
        private String type;
        private Double confidence;
        private AnnotationBox.BoxAction action;
        private String comments;

        // Constructors
        public AnnotationBoxDTO() {
        }

        public AnnotationBoxDTO(Integer x, Integer y, Integer width, Integer height, String type, Double confidence) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.type = type;
            this.confidence = confidence;
        }

        // Getters and Setters
        public Integer getX() {
            return x;
        }

        public void setX(Integer x) {
            this.x = x;
        }

        public Integer getY() {
            return y;
        }

        public void setY(Integer y) {
            this.y = y;
        }

        public Integer getWidth() {
            return width;
        }

        public void setWidth(Integer width) {
            this.width = width;
        }

        public Integer getHeight() {
            return height;
        }

        public void setHeight(Integer height) {
            this.height = height;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Double getConfidence() {
            return confidence;
        }

        public void setConfidence(Double confidence) {
            this.confidence = confidence;
        }

        public AnnotationBox.BoxAction getAction() {
            return action;
        }

        public void setAction(AnnotationBox.BoxAction action) {
            this.action = action;
        }

        public String getComments() {
            return comments;
        }

        public void setComments(String comments) {
            this.comments = comments;
        }
    }
}
