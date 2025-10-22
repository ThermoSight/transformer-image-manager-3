package com.example.transformer_manager_backkend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "feedback_snapshots")
public class FeedbackSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "learning_rate", nullable = false)
    private double learningRate;

    @Column(name = "global_adjustment", nullable = false)
    private double globalAdjustment;

    @Column(name = "annotation_samples", nullable = false)
    private int annotationSamples;

    @Column(name = "label_adjustments_json", columnDefinition = "TEXT", nullable = false)
    private String labelAdjustmentsJson;

    @Column(name = "label_feedback_json", columnDefinition = "TEXT", nullable = false)
    private String labelFeedbackJson;

    public FeedbackSnapshot() {
    }

    public FeedbackSnapshot(double learningRate, double globalAdjustment, int annotationSamples,
            String labelAdjustmentsJson, String labelFeedbackJson) {
        this.learningRate = learningRate;
        this.globalAdjustment = globalAdjustment;
        this.annotationSamples = annotationSamples;
        this.labelAdjustmentsJson = labelAdjustmentsJson;
        this.labelFeedbackJson = labelFeedbackJson;
    }

    @PrePersist
    public void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public double getLearningRate() {
        return learningRate;
    }

    public void setLearningRate(double learningRate) {
        this.learningRate = learningRate;
    }

    public double getGlobalAdjustment() {
        return globalAdjustment;
    }

    public void setGlobalAdjustment(double globalAdjustment) {
        this.globalAdjustment = globalAdjustment;
    }

    public int getAnnotationSamples() {
        return annotationSamples;
    }

    public void setAnnotationSamples(int annotationSamples) {
        this.annotationSamples = annotationSamples;
    }

    public String getLabelAdjustmentsJson() {
        return labelAdjustmentsJson;
    }

    public void setLabelAdjustmentsJson(String labelAdjustmentsJson) {
        this.labelAdjustmentsJson = labelAdjustmentsJson;
    }

    public String getLabelFeedbackJson() {
        return labelFeedbackJson;
    }

    public void setLabelFeedbackJson(String labelFeedbackJson) {
        this.labelFeedbackJson = labelFeedbackJson;
    }
}
