package com.example.transformer_manager_backkend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "annotation_boxes")
public class AnnotationBox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "annotation_id", nullable = false)
    @JsonIgnoreProperties({ "annotationBoxes" })
    private Annotation annotation;

    // Bounding box coordinates [x, y, width, height]
    @Column(nullable = false)
    private Integer x;

    @Column(nullable = false)
    private Integer y;

    @Column(nullable = false)
    private Integer width;

    @Column(nullable = false)
    private Integer height;

    @Column(nullable = false)
    private String type; // e.g., "Loose Joint (Faulty)", "Point Overload (Faulty)"

    @Column
    private Double confidence; // AI confidence score (if from AI) or null if user-added

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BoxAction action = BoxAction.UNCHANGED;

    @Column(columnDefinition = "TEXT")
    private String comments; // User comments for this specific box

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // Constructors
    public AnnotationBox() {
    }

    public AnnotationBox(Integer x, Integer y, Integer width, Integer height, String type, Double confidence) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.type = type;
        this.confidence = confidence;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Annotation getAnnotation() {
        return annotation;
    }

    public void setAnnotation(Annotation annotation) {
        this.annotation = annotation;
    }

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

    public BoxAction getAction() {
        return action;
    }

    public void setAction(BoxAction action) {
        this.action = action;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
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

    // Helper methods
    public int[] getBoxArray() {
        return new int[] { x, y, width, height };
    }

    public void setBoxArray(int[] box) {
        if (box.length == 4) {
            this.x = box[0];
            this.y = box[1];
            this.width = box[2];
            this.height = box[3];
        }
    }

    public boolean isUserAdded() {
        return confidence == null;
    }

    public enum BoxAction {
        UNCHANGED, // AI-generated box kept as-is
        MODIFIED, // AI-generated box that was resized/moved
        ADDED, // User-added new box
        DELETED // AI-generated box that was deleted
    }
}