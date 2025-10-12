package com.example.transformer_manager_backkend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "annotations")
public class Annotation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "analysis_job_id", nullable = false)
    @JsonIgnoreProperties({ "resultJson", "image" })
    private AnalysisJob analysisJob;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String originalResultJson; // Original AI-generated annotations

    @Column(columnDefinition = "TEXT", nullable = false)
    private String modifiedResultJson; // User-modified annotations

    @OneToMany(mappedBy = "annotation", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<AnnotationBox> annotationBoxes = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnnotationType annotationType = AnnotationType.EDITED;

    @Column(columnDefinition = "TEXT")
    private String comments;

    // User who made the annotation
    @ManyToOne
    @JoinColumn(name = "annotated_by_user_id")
    @JsonIgnoreProperties({ "password", "email" })
    private User annotatedByUser;

    @ManyToOne
    @JoinColumn(name = "annotated_by_admin_id")
    @JsonIgnoreProperties({ "password", "email" })
    private Admin annotatedByAdmin;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // Version control for optimistic locking
    @Version
    private Long version;

    // Constructors
    public Annotation() {
    }

    public Annotation(AnalysisJob analysisJob, String originalResultJson, String modifiedResultJson) {
        this.analysisJob = analysisJob;
        this.originalResultJson = originalResultJson;
        this.modifiedResultJson = modifiedResultJson;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AnalysisJob getAnalysisJob() {
        return analysisJob;
    }

    public void setAnalysisJob(AnalysisJob analysisJob) {
        this.analysisJob = analysisJob;
    }

    public String getOriginalResultJson() {
        return originalResultJson;
    }

    public void setOriginalResultJson(String originalResultJson) {
        this.originalResultJson = originalResultJson;
    }

    public String getModifiedResultJson() {
        return modifiedResultJson;
    }

    public void setModifiedResultJson(String modifiedResultJson) {
        this.modifiedResultJson = modifiedResultJson;
    }

    public List<AnnotationBox> getAnnotationBoxes() {
        return annotationBoxes;
    }

    public void setAnnotationBoxes(List<AnnotationBox> annotationBoxes) {
        this.annotationBoxes = annotationBoxes;
    }

    public AnnotationType getAnnotationType() {
        return annotationType;
    }

    public void setAnnotationType(AnnotationType annotationType) {
        this.annotationType = annotationType;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public User getAnnotatedByUser() {
        return annotatedByUser;
    }

    public void setAnnotatedByUser(User annotatedByUser) {
        this.annotatedByUser = annotatedByUser;
    }

    public Admin getAnnotatedByAdmin() {
        return annotatedByAdmin;
    }

    public void setAnnotatedByAdmin(Admin annotatedByAdmin) {
        this.annotatedByAdmin = annotatedByAdmin;
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    // Helper method to get the annotator's display name
    public String getAnnotatorDisplayName() {
        if (annotatedByUser != null) {
            return annotatedByUser.getUsername();
        } else if (annotatedByAdmin != null) {
            return annotatedByAdmin.getUsername();
        }
        return "Unknown";
    }

    public enum AnnotationType {
        ADDED, // User added new annotation
        EDITED, // User modified existing annotation
        DELETED, // User deleted annotation
        VALIDATED // User validated existing annotation without changes
    }
}