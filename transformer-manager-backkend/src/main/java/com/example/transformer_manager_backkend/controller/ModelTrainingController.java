package com.example.transformer_manager_backkend.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.transformer_manager_backkend.entity.ModelTrainingRun;
import com.example.transformer_manager_backkend.service.ModelTrainingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/model-training")
@CrossOrigin(origins = "*")
public class ModelTrainingController {

    private final ModelTrainingService modelTrainingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ModelTrainingController(ModelTrainingService modelTrainingService) {
        this.modelTrainingService = modelTrainingService;
    }

    @GetMapping("/runs")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public List<ModelTrainingRunResponse> listRuns() {
        return modelTrainingService.listRuns().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/runs/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<ModelTrainingRunResponse> getRun(@PathVariable Long id) {
        Optional<ModelTrainingRun> run = modelTrainingService.getRun(id);
        return run.map(r -> ResponseEntity.ok(toResponse(r)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/runs/trigger")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ModelTrainingRunResponse> triggerManualRun(@RequestBody TriggerRunRequest request,
            Authentication authentication) {
        String requestedBy = request != null ? request.requestedBy : null;
        if ((requestedBy == null || requestedBy.isBlank()) && authentication != null) {
            requestedBy = authentication.getName();
        }
        if (requestedBy == null || requestedBy.isBlank()) {
            requestedBy = "system";
        }
        String notes = request != null ? request.notes : null;
        ModelTrainingRun run = modelTrainingService.triggerManualRun(requestedBy, notes);
        return ResponseEntity.ok(toResponse(run));
    }

    private ModelTrainingRunResponse toResponse(ModelTrainingRun run) {
        JsonNode metrics = null;
        if (run.getMetricsJson() != null && !run.getMetricsJson().isBlank()) {
            try {
                metrics = objectMapper.readTree(run.getMetricsJson());
            } catch (Exception ignore) {
                metrics = null;
            }
        }
        return new ModelTrainingRunResponse(
                run.getId(),
                run.getRunId(),
                run.getStatus() != null ? run.getStatus().name() : null,
                run.getTriggerType() != null ? run.getTriggerType().name() : null,
                run.getVersionTag(),
                run.getRequestedBy(),
                run.getFeedbackSummary(),
                run.getDatasetPath(),
                run.getModelOutputPath(),
                run.getErrorMessage(),
                run.getAppendedAnnotations(),
                run.getAppendedBoxes(),
                run.getAnalysisJobId(),
                run.getTransformerId(),
                run.getInspectionId(),
                run.getImageId(),
                run.getCreatedAt(),
                run.getStartedAt(),
                run.getCompletedAt(),
                metrics);
    }

    public static class TriggerRunRequest {
        public String requestedBy;
        public String notes;
    }

    public record ModelTrainingRunResponse(
            Long id,
            String runId,
            String status,
            String triggerType,
            String versionTag,
            String requestedBy,
            String feedbackSummary,
            String datasetPath,
            String modelOutputPath,
            String errorMessage,
            Integer appendedAnnotations,
            Integer appendedBoxes,
            Long analysisJobId,
            Long transformerId,
            Long inspectionId,
            Long imageId,
            LocalDateTime createdAt,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            JsonNode metrics) {
    }
}
