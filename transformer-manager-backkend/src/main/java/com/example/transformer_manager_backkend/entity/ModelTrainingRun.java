package com.example.transformer_manager_backkend.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "model_training_runs")
public class ModelTrainingRun {

    public enum Status {
        QUEUED,
        RUNNING,
        SUCCEEDED,
        FAILED,
        SKIPPED
    }

    public enum TriggerType {
        AUTO_FEEDBACK,
        MANUAL,
        SCHEDULED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String runId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.QUEUED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TriggerType triggerType = TriggerType.AUTO_FEEDBACK;

    @Column
    private String versionTag;

    @Column
    private String requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_annotation_id")
    @JsonIgnoreProperties({
            "analysisJob",
            "annotationBoxes",
            "originalResultJson",
            "modifiedResultJson"
    })
    private Annotation sourceAnnotation;

    @Column
    private Long analysisJobId;

    @Column
    private Long transformerId;

    @Column
    private Long inspectionId;

    @Column
    private Long imageId;

    @Column(columnDefinition = "TEXT")
    private String datasetPath;

    @Column(columnDefinition = "TEXT")
    private String modelOutputPath;

    @Column(columnDefinition = "TEXT")
    private String metricsJson;

    @Column(columnDefinition = "TEXT")
    private String feedbackSummary;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column
    private Integer appendedAnnotations;

    @Column
    private Integer appendedBoxes;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime completedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public TriggerType getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(TriggerType triggerType) {
        this.triggerType = triggerType;
    }

    public String getVersionTag() {
        return versionTag;
    }

    public void setVersionTag(String versionTag) {
        this.versionTag = versionTag;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public Annotation getSourceAnnotation() {
        return sourceAnnotation;
    }

    public void setSourceAnnotation(Annotation sourceAnnotation) {
        this.sourceAnnotation = sourceAnnotation;
    }

    public Long getAnalysisJobId() {
        return analysisJobId;
    }

    public void setAnalysisJobId(Long analysisJobId) {
        this.analysisJobId = analysisJobId;
    }

    public Long getTransformerId() {
        return transformerId;
    }

    public void setTransformerId(Long transformerId) {
        this.transformerId = transformerId;
    }

    public Long getInspectionId() {
        return inspectionId;
    }

    public void setInspectionId(Long inspectionId) {
        this.inspectionId = inspectionId;
    }

    public Long getImageId() {
        return imageId;
    }

    public void setImageId(Long imageId) {
        this.imageId = imageId;
    }

    public String getDatasetPath() {
        return datasetPath;
    }

    public void setDatasetPath(String datasetPath) {
        this.datasetPath = datasetPath;
    }

    public String getModelOutputPath() {
        return modelOutputPath;
    }

    public void setModelOutputPath(String modelOutputPath) {
        this.modelOutputPath = modelOutputPath;
    }

    public String getMetricsJson() {
        return metricsJson;
    }

    public void setMetricsJson(String metricsJson) {
        this.metricsJson = metricsJson;
    }

    public String getFeedbackSummary() {
        return feedbackSummary;
    }

    public void setFeedbackSummary(String feedbackSummary) {
        this.feedbackSummary = feedbackSummary;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getAppendedAnnotations() {
        return appendedAnnotations;
    }

    public void setAppendedAnnotations(Integer appendedAnnotations) {
        this.appendedAnnotations = appendedAnnotations;
    }

    public Integer getAppendedBoxes() {
        return appendedBoxes;
    }

    public void setAppendedBoxes(Integer appendedBoxes) {
        this.appendedBoxes = appendedBoxes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
