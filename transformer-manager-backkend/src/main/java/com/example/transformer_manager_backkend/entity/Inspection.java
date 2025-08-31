package com.example.transformer_manager_backkend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "inspections")
public class Inspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "transformer_record_id", nullable = false)
    @JsonIgnoreProperties({ "inspections", "images" })
    private TransformerRecord transformerRecord;

    @OneToMany(mappedBy = "inspection", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties({ "inspection", "transformerRecord" })
    private List<Image> images;

    @ManyToOne
    @JoinColumn(name = "conducted_by_admin")
    private Admin conductedByAdmin;

    @ManyToOne
    @JoinColumn(name = "conducted_by_user")
    private User conductedByUser;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private String notes;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TransformerRecord getTransformerRecord() {
        return transformerRecord;
    }

    public void setTransformerRecord(TransformerRecord transformerRecord) {
        this.transformerRecord = transformerRecord;
    }

    public List<Image> getImages() {
        return images;
    }

    public void setImages(List<Image> images) {
        this.images = images;
    }

    public Admin getConductedByAdmin() {
        return conductedByAdmin;
    }

    public void setConductedByAdmin(Admin conductedByAdmin) {
        this.conductedByAdmin = conductedByAdmin;
    }

    public User getConductedByUser() {
        return conductedByUser;
    }

    public void setConductedByUser(User conductedByUser) {
        this.conductedByUser = conductedByUser;
    }

    // Helper method to get the conductor name regardless of type
    public String getConductorName() {
        if (conductedByAdmin != null) {
            return conductedByAdmin.getDisplayName();
        }
        if (conductedByUser != null) {
            return conductedByUser.getDisplayName();
        }
        return "Unknown";
    }

    // Helper method to get conductor role
    public String getConductorRole() {
        if (conductedByAdmin != null) {
            return "ADMIN";
        }
        if (conductedByUser != null) {
            return "USER";
        }
        return "UNKNOWN";
    }

    // Legacy getter for backward compatibility - now deprecated
    @Deprecated
    public Admin getConductedBy() {
        return conductedByAdmin;
    }

    // Legacy setter for backward compatibility - now deprecated
    @Deprecated
    public void setConductedBy(Admin conductedBy) {
        this.conductedByAdmin = conductedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}