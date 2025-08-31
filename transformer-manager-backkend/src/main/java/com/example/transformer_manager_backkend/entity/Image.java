package com.example.transformer_manager_backkend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "images")
public class Image {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String filePath;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime uploadTime;

    @Column(nullable = false)
    private String type; // "Baseline" or "Maintenance"

    @Column
    private String weatherCondition; // Only for Baseline

    // Prevent recursion by ignoring image lists in parent objects
    @ManyToOne
    @JoinColumn(name = "transformer_record_id")
    @JsonIgnoreProperties({ "images", "inspections" })
    private TransformerRecord transformerRecord;

    @ManyToOne
    @JoinColumn(name = "inspection_id")
    @JsonIgnoreProperties({ "images", "transformerRecord" })
    private Inspection inspection;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public LocalDateTime getUploadTime() {
        return uploadTime;
    }

    public void setUploadTime(LocalDateTime uploadTime) {
        this.uploadTime = uploadTime;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getWeatherCondition() {
        return weatherCondition;
    }

    public void setWeatherCondition(String weatherCondition) {
        this.weatherCondition = weatherCondition;
    }

    public TransformerRecord getTransformerRecord() {
        return transformerRecord;
    }

    public void setTransformerRecord(TransformerRecord transformerRecord) {
        this.transformerRecord = transformerRecord;
    }

    public Inspection getInspection() {
        return inspection;
    }

    public void setInspection(Inspection inspection) {
        this.inspection = inspection;
    }
}