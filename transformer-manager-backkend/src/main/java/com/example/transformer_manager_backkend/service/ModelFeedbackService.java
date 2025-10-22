package com.example.transformer_manager_backkend.service;

import com.example.transformer_manager_backkend.entity.Annotation;
import com.example.transformer_manager_backkend.entity.FeedbackSnapshot;
import com.example.transformer_manager_backkend.repository.AnnotationRepository;
import com.example.transformer_manager_backkend.repository.FeedbackSnapshotRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Aggregates human annotation feedback and produces lightweight adjustment signals
 * that can be consumed by the local ML inference pipeline.
 */
@Service
public class ModelFeedbackService {

    private static final Logger logger = LoggerFactory.getLogger(ModelFeedbackService.class);

    private final AnnotationRepository annotationRepository;
    private final FeedbackSnapshotRepository feedbackSnapshotRepository;
    private final ObjectMapper objectMapper;

    public ModelFeedbackService(AnnotationRepository annotationRepository,
            FeedbackSnapshotRepository feedbackSnapshotRepository) {
        this.annotationRepository = annotationRepository;
        this.feedbackSnapshotRepository = feedbackSnapshotRepository;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Generate summary statistics describing the feedback impact using the provided learning rate.
     */
    public FeedbackSummary generateFeedbackSummary(double learningRate) {
        List<Annotation> annotations = annotationRepository.findAll();
        Map<String, LabelAggregate> aggregates = new HashMap<>();
        int annotationSamples = 0;

        for (Annotation annotation : annotations) {
            if (annotation == null) {
                continue;
            }

            try {
                Map<String, LabelStats> original = extractLabelStats(annotation.getOriginalResultJson());
                Map<String, LabelStats> updated = extractLabelStats(annotation.getModifiedResultJson());

                if (original.isEmpty() && updated.isEmpty()) {
                    continue;
                }

                Set<String> labels = new HashSet<>();
                labels.addAll(original.keySet());
                labels.addAll(updated.keySet());

                for (String label : labels) {
                    LabelStats origStats = Optional.ofNullable(original.get(label)).orElse(LabelStats.empty());
                    LabelStats newStats = Optional.ofNullable(updated.get(label)).orElse(LabelStats.empty());

                    LabelAggregate aggregate = aggregates.computeIfAbsent(label, key -> new LabelAggregate());
                    aggregate.totalCountDelta += (newStats.count - origStats.count);
                    aggregate.totalAreaDelta += (newStats.areaSum - origStats.areaSum);
                    aggregate.totalOrigArea += origStats.areaSum;
                    aggregate.totalUserArea += newStats.areaSum;
                    aggregate.totalConfidenceDelta += (newStats.confidenceSum - origStats.confidenceSum);
                    aggregate.sampleCount += 1;
                }

                annotationSamples++;
            } catch (Exception e) {
                logger.warn("Failed to parse annotation feedback for annotation {}: {}", annotation.getId(), e.getMessage());
            }
        }

        List<LabelFeedback> labelFeedback = new ArrayList<>();
        double totalAdjustment = 0.0;

        for (Map.Entry<String, LabelAggregate> entry : aggregates.entrySet()) {
            String label = entry.getKey();
            LabelAggregate aggregate = entry.getValue();
            if (aggregate.sampleCount == 0) {
                continue;
            }

            double avgCountDelta = aggregate.totalCountDelta / aggregate.sampleCount;
            double avgAreaDelta = aggregate.totalAreaDelta / aggregate.sampleCount;
            double avgOrigArea = aggregate.totalOrigArea / aggregate.sampleCount;
            double avgConfidenceDelta = aggregate.totalConfidenceDelta / aggregate.sampleCount;

            double areaRatio;
            if (avgOrigArea > 1e-6) {
                areaRatio = avgAreaDelta / (avgOrigArea + 1e-6);
            } else if (aggregate.totalAreaDelta > 0) {
                areaRatio = 1.0;
            } else if (aggregate.totalAreaDelta < 0) {
                areaRatio = -1.0;
            } else {
                areaRatio = 0.0;
            }

            areaRatio = clamp(areaRatio, -3.0, 3.0);
            double combinedSignal = (avgCountDelta * 0.5) + (areaRatio * 0.3) + (avgConfidenceDelta * 0.2);
            combinedSignal = clamp(combinedSignal, -5.0, 5.0);
            double adjustment = clamp(learningRate * combinedSignal, -0.2, 0.2);

            LabelFeedback feedback = new LabelFeedback(
                    label,
                    avgCountDelta,
                    areaRatio,
                    avgConfidenceDelta,
                    adjustment,
                    aggregate.sampleCount);
            labelFeedback.add(feedback);
            totalAdjustment += adjustment;
        }

        double globalAdjustment = labelFeedback.isEmpty() ? 0.0 : totalAdjustment / labelFeedback.size();

        return new FeedbackSummary(
                learningRate,
                globalAdjustment,
                annotationSamples,
                LocalDateTime.now(),
                labelFeedback);
    }

    /**
     * Build payload for inference consumption.
     */
    public FeedbackPayload buildFeedbackPayload(double learningRate) {
        FeedbackSummary summary = generateFeedbackSummary(learningRate);
        ObjectNode root = objectMapper.createObjectNode();

        root.put("generated_at", summary.getGeneratedAt().toString());
        root.put("learning_rate", summary.getLearningRate());
        root.put("global_adjustment", summary.getGlobalAdjustment());
        root.put("total_annotations_considered", summary.getAnnotationSamples());

        ObjectNode labelNode = root.putObject("label_adjustments");
        ArrayNode detailArray = root.putArray("label_feedback");

        for (LabelFeedback feedback : summary.getLabelFeedback()) {
            ObjectNode labelObject = labelNode.putObject(feedback.getLabel());
            labelObject.put("adjustment", feedback.getAdjustment());
            labelObject.put("avg_count_delta", feedback.getAvgCountDelta());
            labelObject.put("avg_area_ratio", feedback.getAvgAreaRatio());
            labelObject.put("avg_confidence_delta", feedback.getAvgConfidenceDelta());
            labelObject.put("samples", feedback.getSamples());

            ObjectNode detailObject = detailArray.addObject();
            detailObject.put("label", feedback.getLabel());
            detailObject.put("avg_count_delta", feedback.getAvgCountDelta());
            detailObject.put("avg_area_ratio", feedback.getAvgAreaRatio());
            detailObject.put("avg_confidence_delta", feedback.getAvgConfidenceDelta());
            detailObject.put("adjustment", feedback.getAdjustment());
            detailObject.put("samples", feedback.getSamples());
        }

        FeedbackPayload payload = new FeedbackPayload(summary, root);
        persistSnapshot(payload);
        return payload;
    }

    private Map<String, LabelStats> extractLabelStats(String json) throws Exception {
        Map<String, LabelStats> statsMap = new HashMap<>();
        if (json == null || json.isBlank()) {
            return statsMap;
        }

        JsonNode root = objectMapper.readTree(json);
        JsonNode boxesNode = root.get("boxes");
        if (boxesNode == null || !boxesNode.isArray()) {
            return statsMap;
        }

        for (JsonNode boxNode : boxesNode) {
            JsonNode typeNode = boxNode.get("type");
            JsonNode coordsNode = boxNode.get("box");
            if (typeNode == null || coordsNode == null || !coordsNode.isArray() || coordsNode.size() < 4) {
                continue;
            }
            String label = typeNode.asText("Unknown");
            int width = Math.max(0, coordsNode.get(2).asInt());
            int height = Math.max(0, coordsNode.get(3).asInt());
            double area = (double) width * (double) height;
            double confidence = boxNode.has("confidence") ? boxNode.get("confidence").asDouble(0.0) : 0.0;

            LabelStats stats = statsMap.computeIfAbsent(label, key -> new LabelStats());
            stats.count += 1;
            stats.areaSum += area;
            stats.confidenceSum += confidence;
        }

        return statsMap;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public List<FeedbackSnapshotDTO> getSnapshotHistory(int limit) {
        int pageSize = limit > 0 ? Math.min(limit, 500) : 50;
        List<FeedbackSnapshot> snapshots = feedbackSnapshotRepository
                .findAll(PageRequest.of(0, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
        return snapshots.stream()
                .map(this::toDto)
                .sorted(Comparator.comparing(FeedbackSnapshotDTO::getCreatedAt))
                .collect(Collectors.toList());
    }

    public List<FeedbackSnapshotDTO> getSnapshotHistorySince(LocalDateTime since) {
        List<FeedbackSnapshot> snapshots = feedbackSnapshotRepository
                .findByCreatedAtAfterOrderByCreatedAtDesc(since);
        return snapshots.stream()
                .map(this::toDto)
                .sorted(Comparator.comparing(FeedbackSnapshotDTO::getCreatedAt))
                .collect(Collectors.toList());
    }

    private void persistSnapshot(FeedbackPayload payload) {
        try {
            JsonNode adjustmentsNode = payload.getPayload().get("label_adjustments");
            if (adjustmentsNode == null || adjustmentsNode.isNull()) {
                adjustmentsNode = objectMapper.createObjectNode();
            }
            JsonNode labelFeedbackNode = payload.getPayload().get("label_feedback");
            if (labelFeedbackNode == null || labelFeedbackNode.isNull()) {
                labelFeedbackNode = objectMapper.createArrayNode();
            }
            String adjustmentsJson = objectMapper.writeValueAsString(adjustmentsNode);
            String labelFeedbackJson = objectMapper.writeValueAsString(labelFeedbackNode);
            FeedbackSnapshot snapshot = new FeedbackSnapshot(
                    payload.getSummary().getLearningRate(),
                    payload.getSummary().getGlobalAdjustment(),
                    payload.getSummary().getAnnotationSamples(),
                    adjustmentsJson,
                    labelFeedbackJson);
            feedbackSnapshotRepository.save(snapshot);
        } catch (Exception e) {
            logger.warn("Failed to persist feedback snapshot", e);
        }
    }

    private FeedbackSnapshotDTO toDto(FeedbackSnapshot snapshot) {
        try {
            ObjectNode adjustments = (ObjectNode) objectMapper.readTree(snapshot.getLabelAdjustmentsJson());
            ArrayNode labelDetails = (ArrayNode) objectMapper.readTree(snapshot.getLabelFeedbackJson());
            List<ModelFeedbackService.LabelFeedback> labels = new ArrayList<>();
            labelDetails.forEach(node -> labels.add(new LabelFeedback(
                    node.path("label").asText(),
                    node.path("avg_count_delta").asDouble(0.0),
                    node.path("avg_area_ratio").asDouble(0.0),
                    node.path("avg_confidence_delta").asDouble(0.0),
                    node.path("adjustment").asDouble(0.0),
                    node.path("samples").asInt(0)
            )));

            return new FeedbackSnapshotDTO(
                    snapshot.getId(),
                    snapshot.getCreatedAt(),
                    snapshot.getLearningRate(),
                    snapshot.getGlobalAdjustment(),
                    snapshot.getAnnotationSamples(),
                    adjustments,
                    labels);
        } catch (Exception e) {
            logger.warn("Failed to convert feedback snapshot {}", snapshot.getId(), e);
            return new FeedbackSnapshotDTO(
                    snapshot.getId(),
                    snapshot.getCreatedAt(),
                    snapshot.getLearningRate(),
                    snapshot.getGlobalAdjustment(),
                    snapshot.getAnnotationSamples(),
                    objectMapper.createObjectNode(),
                    List.of());
        }
    }

    public static class FeedbackSnapshotDTO {
        private final Long id;
        private final LocalDateTime createdAt;
        private final double learningRate;
        private final double globalAdjustment;
        private final int annotationSamples;
        private final ObjectNode labelAdjustments;
        private final List<ModelFeedbackService.LabelFeedback> labels;

        public FeedbackSnapshotDTO(Long id, LocalDateTime createdAt, double learningRate, double globalAdjustment,
                int annotationSamples, ObjectNode labelAdjustments, List<ModelFeedbackService.LabelFeedback> labels) {
            this.id = id;
            this.createdAt = createdAt;
            this.learningRate = learningRate;
            this.globalAdjustment = globalAdjustment;
            this.annotationSamples = annotationSamples;
            this.labelAdjustments = labelAdjustments;
            this.labels = labels;
        }

        public Long getId() {
            return id;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public double getLearningRate() {
            return learningRate;
        }

        public double getGlobalAdjustment() {
            return globalAdjustment;
        }

        public int getAnnotationSamples() {
            return annotationSamples;
        }

        public ObjectNode getLabelAdjustments() {
            return labelAdjustments;
        }

        public List<ModelFeedbackService.LabelFeedback> getLabels() {
            return labels;
        }
    }

    /**
     * Container for aggregated statistics per label.
     */
    private static class LabelAggregate {
        double totalCountDelta = 0.0;
        double totalAreaDelta = 0.0;
        double totalOrigArea = 0.0;
        double totalUserArea = 0.0;
        double totalConfidenceDelta = 0.0;
        int sampleCount = 0;
    }

    private static class LabelStats {
        int count = 0;
        double areaSum = 0.0;
        double confidenceSum = 0.0;

        static LabelStats empty() {
            return new LabelStats();
        }
    }

    public static class LabelFeedback {
        private final String label;
        private final double avgCountDelta;
        private final double avgAreaRatio;
        private final double avgConfidenceDelta;
        private final double adjustment;
        private final int samples;

        public LabelFeedback(String label, double avgCountDelta, double avgAreaRatio,
                double avgConfidenceDelta, double adjustment, int samples) {
            this.label = label;
            this.avgCountDelta = avgCountDelta;
            this.avgAreaRatio = avgAreaRatio;
            this.avgConfidenceDelta = avgConfidenceDelta;
            this.adjustment = adjustment;
            this.samples = samples;
        }

        public String getLabel() {
            return label;
        }

        public double getAvgCountDelta() {
            return avgCountDelta;
        }

        public double getAvgAreaRatio() {
            return avgAreaRatio;
        }

        public double getAvgConfidenceDelta() {
            return avgConfidenceDelta;
        }

        public double getAdjustment() {
            return adjustment;
        }

        public int getSamples() {
            return samples;
        }
    }

    public static class FeedbackSummary {
        private final double learningRate;
        private final double globalAdjustment;
        private final int annotationSamples;
        private final LocalDateTime generatedAt;
        private final List<LabelFeedback> labelFeedback;

        public FeedbackSummary(double learningRate, double globalAdjustment, int annotationSamples,
                LocalDateTime generatedAt, List<LabelFeedback> labelFeedback) {
            this.learningRate = learningRate;
            this.globalAdjustment = globalAdjustment;
            this.annotationSamples = annotationSamples;
            this.generatedAt = generatedAt;
            this.labelFeedback = labelFeedback;
        }

        public double getLearningRate() {
            return learningRate;
        }

        public double getGlobalAdjustment() {
            return globalAdjustment;
        }

        public int getAnnotationSamples() {
            return annotationSamples;
        }

        public LocalDateTime getGeneratedAt() {
            return generatedAt;
        }

        public List<LabelFeedback> getLabelFeedback() {
            return labelFeedback;
        }
    }

    public static class FeedbackPayload {
        private final FeedbackSummary summary;
        private final ObjectNode payload;

        FeedbackPayload(FeedbackSummary summary, ObjectNode payload) {
            this.summary = summary;
            this.payload = payload;
        }

        public FeedbackSummary getSummary() {
            return summary;
        }

        public ObjectNode getPayload() {
            return payload;
        }

        public String toJsonString() {
            return payload.toPrettyString();
        }

        public boolean hasAdjustments() {
            return payload.has("label_feedback") && payload.get("label_feedback").size() > 0;
        }
    }
}
