package com.example.transformer_manager_backkend.controller;

import com.example.transformer_manager_backkend.entity.MLSettings;
import com.example.transformer_manager_backkend.service.MLSettingsService;
import com.example.transformer_manager_backkend.service.ModelFeedbackService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/ml-settings")
@CrossOrigin(origins = "http://localhost:3000")
public class MLSettingsController {

    private final MLSettingsService mlSettingsService;
    private final ModelFeedbackService modelFeedbackService;

    public MLSettingsController(MLSettingsService mlSettingsService, ModelFeedbackService modelFeedbackService) {
        this.mlSettingsService = mlSettingsService;
        this.modelFeedbackService = modelFeedbackService;
    }

    /**
     * Get detection sensitivity
     */
    @GetMapping("/sensitivity")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<SensitivityResponse> getDetectionSensitivity() {
        double sensitivity = mlSettingsService.getDetectionSensitivity();
        return ResponseEntity.ok(new SensitivityResponse(sensitivity));
    }

    /**
     * Set detection sensitivity
     */
    @PutMapping("/sensitivity")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<SensitivityResponse> setDetectionSensitivity(@RequestBody SensitivityRequest request) {
        if (request.getSensitivity() < 0.1 || request.getSensitivity() > 2.0) {
            return ResponseEntity.badRequest().build();
        }

        mlSettingsService.setDetectionSensitivity(request.getSensitivity());
        double updatedSensitivity = mlSettingsService.getDetectionSensitivity();
        return ResponseEntity.ok(new SensitivityResponse(updatedSensitivity));
    }

    /**
     * Get feedback learning rate
     */
    @GetMapping("/feedback-rate")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<FeedbackRateResponse> getFeedbackLearningRate() {
        double rate = mlSettingsService.getFeedbackLearningRate();
        return ResponseEntity.ok(new FeedbackRateResponse(rate));
    }

    /**
     * Update feedback learning rate
     */
    @PutMapping("/feedback-rate")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<FeedbackRateResponse> setFeedbackLearningRate(@RequestBody FeedbackRateRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }

        double rate = request.getLearningRate();
        if (rate < 0.00001 || rate > 0.05) {
            return ResponseEntity.badRequest()
                    .body(new FeedbackRateResponse(mlSettingsService.getFeedbackLearningRate(), "Learning rate must be between 0.00001 and 0.05"));
        }

        mlSettingsService.setFeedbackLearningRate(rate);
        double updatedRate = mlSettingsService.getFeedbackLearningRate();
        return ResponseEntity.ok(new FeedbackRateResponse(updatedRate));
    }

    /**
     * Summarize feedback adjustments currently being applied
     */
    @GetMapping("/feedback-summary")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<FeedbackSummaryResponse> getFeedbackSummary() {
        double learningRate = mlSettingsService.getFeedbackLearningRate();
        ModelFeedbackService.FeedbackSummary summary = modelFeedbackService.generateFeedbackSummary(learningRate);
        return ResponseEntity.ok(FeedbackSummaryResponse.from(summary));
    }

    /**
     * Get historical feedback snapshots
     */
    @GetMapping("/feedback-history")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<List<FeedbackSnapshotResponse>> getFeedbackHistory(
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "since", required = false) String since) {

        List<ModelFeedbackService.FeedbackSnapshotDTO> snapshots;
        if (since != null && !since.isBlank()) {
            try {
                LocalDateTime parsed = LocalDateTime.parse(since);
                snapshots = modelFeedbackService.getSnapshotHistorySince(parsed);
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest()
                        .body(List.of());
            }
        } else {
            int effectiveLimit = (limit != null && limit > 0) ? limit : 50;
            snapshots = modelFeedbackService.getSnapshotHistory(effectiveLimit);
        }

        List<FeedbackSnapshotResponse> response = snapshots.stream()
                .map(FeedbackSnapshotResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * Get all ML settings
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<MLSettings>> getAllSettings() {
        List<MLSettings> settings = mlSettingsService.getAllSettings();
        return ResponseEntity.ok(settings);
    }

    /**
     * Get setting by key
     */
    @GetMapping("/{key}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<MLSettings> getSettingByKey(@PathVariable String key) {
        Optional<MLSettings> setting = mlSettingsService.getSettingByKey(key);
        return setting.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Set generic setting (admin only)
     */
    @PutMapping("/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MLSettings> setSetting(@PathVariable String key, @RequestBody SettingRequest request) {
        mlSettingsService.setSetting(key, request.getValue(), request.getDescription());
        Optional<MLSettings> updatedSetting = mlSettingsService.getSettingByKey(key);
        return updatedSetting.map(ResponseEntity::ok)
                .orElse(ResponseEntity.internalServerError().build());
    }

    /**
     * Delete setting (admin only)
     */
    @DeleteMapping("/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteSetting(@PathVariable String key) {
        boolean deleted = mlSettingsService.deleteSetting(key);
        return deleted ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    // DTOs
    public static class SensitivityRequest {
        private double sensitivity;

        public double getSensitivity() {
            return sensitivity;
        }

        public void setSensitivity(double sensitivity) {
            this.sensitivity = sensitivity;
        }
    }

    public static class SensitivityResponse {
        private double sensitivity;
        private String description;

        public SensitivityResponse(double sensitivity) {
            this.sensitivity = sensitivity;
            this.description = "Detection sensitivity (0.1-2.0). Higher values = more sensitive detection = more boxes detected.";
        }

        public double getSensitivity() {
            return sensitivity;
        }

        public void setSensitivity(double sensitivity) {
            this.sensitivity = sensitivity;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    public static class FeedbackRateRequest {
        private double learningRate;

        public double getLearningRate() {
            return learningRate;
        }

        public void setLearningRate(double learningRate) {
            this.learningRate = learningRate;
        }
    }

    public static class FeedbackRateResponse {
        private double learningRate;
        private String description;
        private String message;

        public FeedbackRateResponse(double learningRate) {
            this(learningRate, null);
        }

        public FeedbackRateResponse(double learningRate, String message) {
            this.learningRate = learningRate;
            this.message = message;
            this.description = "Fraction applied to human annotation feedback when adjusting model confidence (0.00001-0.05).";
        }

        public double getLearningRate() {
            return learningRate;
        }

        public void setLearningRate(double learningRate) {
            this.learningRate = learningRate;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    public static class FeedbackSummaryResponse {
        private double learningRate;
        private double globalAdjustment;
        private int annotationSamples;
        private String generatedAt;
        private List<LabelFeedbackDTO> labels;

        public static FeedbackSummaryResponse from(ModelFeedbackService.FeedbackSummary summary) {
            FeedbackSummaryResponse response = new FeedbackSummaryResponse();
            response.learningRate = summary.getLearningRate();
            response.globalAdjustment = summary.getGlobalAdjustment();
            response.annotationSamples = summary.getAnnotationSamples();
            response.generatedAt = summary.getGeneratedAt().toString();
            response.labels = summary.getLabelFeedback().stream()
                    .map(LabelFeedbackDTO::from)
                    .toList();
            return response;
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

        public String getGeneratedAt() {
            return generatedAt;
        }

        public List<LabelFeedbackDTO> getLabels() {
            return labels;
        }
    }

    public static class LabelFeedbackDTO {
        private String label;
        private double avgCountDelta;
        private double avgAreaRatio;
        private double avgConfidenceDelta;
        private double adjustment;
        private int samples;

        public static LabelFeedbackDTO from(ModelFeedbackService.LabelFeedback feedback) {
            LabelFeedbackDTO dto = new LabelFeedbackDTO();
            dto.label = feedback.getLabel();
            dto.avgCountDelta = feedback.getAvgCountDelta();
            dto.avgAreaRatio = feedback.getAvgAreaRatio();
            dto.avgConfidenceDelta = feedback.getAvgConfidenceDelta();
            dto.adjustment = feedback.getAdjustment();
            dto.samples = feedback.getSamples();
            return dto;
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

    public static class FeedbackSnapshotResponse {
        private Long id;
        private String createdAt;
        private double learningRate;
        private double globalAdjustment;
        private int annotationSamples;
        private com.fasterxml.jackson.databind.node.ObjectNode labelAdjustments;
        private List<LabelFeedbackDTO> labels;

        public static FeedbackSnapshotResponse from(ModelFeedbackService.FeedbackSnapshotDTO dto) {
            FeedbackSnapshotResponse response = new FeedbackSnapshotResponse();
            response.id = dto.getId();
            response.createdAt = dto.getCreatedAt() != null ? dto.getCreatedAt().toString() : null;
            response.learningRate = dto.getLearningRate();
            response.globalAdjustment = dto.getGlobalAdjustment();
            response.annotationSamples = dto.getAnnotationSamples();
            response.labelAdjustments = dto.getLabelAdjustments();
            response.labels = dto.getLabels().stream()
                    .map(LabelFeedbackDTO::from)
                    .toList();
            return response;
        }

        public Long getId() {
            return id;
        }

        public String getCreatedAt() {
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

        public com.fasterxml.jackson.databind.node.ObjectNode getLabelAdjustments() {
            return labelAdjustments;
        }

        public List<LabelFeedbackDTO> getLabels() {
            return labels;
        }
    }
    public static class SettingRequest {
        private String value;
        private String description;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
