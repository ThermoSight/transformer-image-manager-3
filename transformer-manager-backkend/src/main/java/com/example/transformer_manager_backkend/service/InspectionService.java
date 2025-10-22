package com.example.transformer_manager_backkend.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.transformer_manager_backkend.entity.Admin;
import com.example.transformer_manager_backkend.entity.Image;
import com.example.transformer_manager_backkend.entity.Inspection;
import com.example.transformer_manager_backkend.entity.TransformerRecord;
import com.example.transformer_manager_backkend.entity.User;
import com.example.transformer_manager_backkend.repository.ImageRepository;
import com.example.transformer_manager_backkend.repository.InspectionRepository;
import com.example.transformer_manager_backkend.repository.TransformerRecordRepository;

@Service
public class InspectionService {

    private static final Logger logger = LoggerFactory.getLogger(InspectionService.class);

    private final InspectionRepository inspectionRepository;
    private final TransformerRecordRepository transformerRecordRepository;
    private final AnomalyAnalysisService anomalyAnalysisService;
    private final ImageRepository imageRepository;

    @Value("${upload.directory}")
    private String uploadDirectory;

    public InspectionService(InspectionRepository inspectionRepository,
            TransformerRecordRepository transformerRecordRepository,
            AnomalyAnalysisService anomalyAnalysisService,
            ImageRepository imageRepository) {
        this.inspectionRepository = inspectionRepository;
        this.transformerRecordRepository = transformerRecordRepository;
        this.anomalyAnalysisService = anomalyAnalysisService;
        this.imageRepository = imageRepository;
    }

    public Inspection createInspection(
            Long transformerRecordId,
            String notes,
            List<MultipartFile> maintenanceImages,
            Admin conductedBy,
            java.time.LocalDateTime inspectionDate) throws IOException {

        TransformerRecord transformerRecord = transformerRecordRepository.findById(transformerRecordId)
                .orElseThrow(() -> new RuntimeException("Transformer record not found"));

        Inspection inspection = new Inspection();
        inspection.setTransformerRecord(transformerRecord);
        inspection.setConductedByAdmin(conductedBy);
        inspection.setNotes(notes);
        inspection.setInspectionDate(inspectionDate);

        List<Image> imageEntities = createImageEntities(maintenanceImages, inspection);
        inspection.setImages(imageEntities);

        Inspection savedInspection = inspectionRepository.saveAndFlush(inspection);

        // Queue maintenance images for anomaly analysis after saving
        for (Image image : savedInspection.getImages()) {
            if ("Maintenance".equalsIgnoreCase(image.getType())) {
                anomalyAnalysisService.queueImageForAnalysis(image);
            }
        }

        // Ensure images are initialized for serialization
        savedInspection.getImages().size();

        return savedInspection;
    }

    public Inspection createInspectionByAdmin(
            Long transformerRecordId,
            String notes,
            List<MultipartFile> maintenanceImages,
            Admin conductedBy,
            java.time.LocalDateTime inspectionDate) throws IOException {
        return createInspection(transformerRecordId, notes, maintenanceImages, conductedBy, inspectionDate);
    }

    public Inspection createInspectionByUser(
            Long transformerRecordId,
            String notes,
            List<MultipartFile> maintenanceImages,
            User conductedBy,
            java.time.LocalDateTime inspectionDate) throws IOException {

        TransformerRecord transformerRecord = transformerRecordRepository.findById(transformerRecordId)
                .orElseThrow(() -> new RuntimeException("Transformer record not found"));

        Inspection inspection = new Inspection();
        inspection.setTransformerRecord(transformerRecord);
        inspection.setConductedByUser(conductedBy);
        inspection.setNotes(notes);
        inspection.setInspectionDate(inspectionDate);

        List<Image> imageEntities = createImageEntities(maintenanceImages, inspection);
        inspection.setImages(imageEntities);

        Inspection savedInspection = inspectionRepository.saveAndFlush(inspection);

        // Queue maintenance images for anomaly analysis after saving
        for (Image image : savedInspection.getImages()) {
            if ("Maintenance".equalsIgnoreCase(image.getType())) {
                anomalyAnalysisService.queueImageForAnalysis(image);
            }
        }

        savedInspection.getImages().size();

        return savedInspection;
    }

    private List<Image> createImageEntities(List<MultipartFile> maintenanceImages, Inspection inspection)
            throws IOException {
        List<Image> imageEntities = new ArrayList<>();
        if (maintenanceImages != null && !maintenanceImages.isEmpty()) {
            for (MultipartFile imageFile : maintenanceImages) {
                String fileName = System.currentTimeMillis() + "_" + imageFile.getOriginalFilename();
                Path uploadPath = Paths.get(uploadDirectory);
                if (!Files.exists(uploadPath))
                    Files.createDirectories(uploadPath);
                Path filePath = uploadPath.resolve(fileName);
                Files.copy(imageFile.getInputStream(), filePath);

                Image image = new Image();
                image.setFilePath("/uploads/" + fileName);
                image.setType("Maintenance");
                image.setInspection(inspection);

                imageEntities.add(image);
            }
        }
        return imageEntities;
    }

    public List<Inspection> getInspectionsByTransformerRecordId(Long transformerRecordId) {
        return inspectionRepository.findByTransformerRecordId(transformerRecordId);
    }

    public Inspection getInspectionById(Long id) {
        return inspectionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Inspection not found"));
    }

    public void deleteInspection(Long id) throws IOException {
        Inspection inspection = inspectionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Inspection not found"));

        deleteInspectionImages(inspection);
        inspectionRepository.deleteById(id);
    }

    public void deleteInspectionByUser(Long id, User user) throws IOException {
        Inspection inspection = inspectionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Inspection not found"));

        // Verify the user owns this inspection
        if (inspection.getConductedByUser() == null ||
                !inspection.getConductedByUser().getId().equals(user.getId())) {
            throw new RuntimeException("You can only delete your own inspections");
        }

        deleteInspectionImages(inspection);
        inspectionRepository.deleteById(id);
    }

    @Transactional
    public Inspection addImagesToInspection(Long inspectionId, List<MultipartFile> newImages, Admin admin)
            throws IOException {
        Inspection inspection = inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new RuntimeException("Inspection not found"));

        // Create new image entities for the additional images
        List<Image> additionalImageEntities = createImageEntities(newImages, inspection);

        // Add new images to existing list
        List<Image> existingImages = inspection.getImages();
        if (existingImages == null) {
            existingImages = new ArrayList<>();
        }
        existingImages.addAll(additionalImageEntities);
        inspection.setImages(existingImages);

        Inspection savedInspection = inspectionRepository.saveAndFlush(inspection);

        // Queue new maintenance images for anomaly analysis without breaking the upload flow
        for (Image image : additionalImageEntities) {
            if ("Maintenance".equalsIgnoreCase(image.getType())) {
                try {
                    Image imageToQueue = image;
                    if (imageToQueue.getId() == null) {
                        Optional<Image> persisted = imageRepository.findByFilePath(image.getFilePath());
                        if (persisted.isPresent()) {
                            imageToQueue = persisted.get();
                        }
                    }
                    if (imageToQueue.getId() != null) {
                        anomalyAnalysisService.queueImageForAnalysis(imageToQueue);
                    } else {
                        logger.warn("Skipping anomaly queue for image with file {} because it has no ID", image.getFilePath());
                    }
                } catch (Exception queueError) {
                    logger.error("Failed to queue image {} for anomaly analysis", image.getId(), queueError);
                }
            }
        }

        savedInspection.getImages().size();

        return savedInspection;
    }

    @Transactional
    public Inspection addImagesToInspection(Long inspectionId, List<MultipartFile> newImages, User user)
            throws IOException {
        Inspection inspection = inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new RuntimeException("Inspection not found"));

        // Verify the user can add images to this inspection (either they created it or
        // they're adding to any if they're admin)
        // For simplicity, allow any authenticated user to add images to any inspection
        // You can add more restrictive logic here if needed

        // Create new image entities for the additional images
        List<Image> additionalImageEntities = createImageEntities(newImages, inspection);

        // Add new images to existing list
        List<Image> existingImages = inspection.getImages();
        if (existingImages == null) {
            existingImages = new ArrayList<>();
        }
        existingImages.addAll(additionalImageEntities);
        inspection.setImages(existingImages);

        Inspection savedInspection = inspectionRepository.saveAndFlush(inspection);

        // Queue new maintenance images for anomaly analysis without failing the request
        for (Image image : additionalImageEntities) {
            if ("Maintenance".equalsIgnoreCase(image.getType())) {
                try {
                    Image imageToQueue = image;
                    if (imageToQueue.getId() == null) {
                        Optional<Image> persisted = imageRepository.findByFilePath(image.getFilePath());
                        if (persisted.isPresent()) {
                            imageToQueue = persisted.get();
                        }
                    }
                    if (imageToQueue.getId() != null) {
                        anomalyAnalysisService.queueImageForAnalysis(imageToQueue);
                    } else {
                        logger.warn("Skipping anomaly queue for image with file {} because it has no ID", image.getFilePath());
                    }
                } catch (Exception queueError) {
                    logger.error("Failed to queue image {} for anomaly analysis", image.getId(), queueError);
                }
            }
        }

        savedInspection.getImages().size();

        return savedInspection;
    }

    private void deleteInspectionImages(Inspection inspection) throws IOException {
        // Delete associated images
        for (Image image : inspection.getImages()) {
            try {
                Path filePath = Paths.get(uploadDirectory, image.getFilePath().replace("/uploads/", ""));
                Files.deleteIfExists(filePath);
            } catch (Exception ignore) {
            }
        }
    }

    public void deleteInspectionImage(Long imageId, User user) throws IOException {
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new RuntimeException("Image not found"));
        
        // Verify the user can delete this image
        Inspection inspection = image.getInspection();
        if (inspection == null) {
            throw new RuntimeException("Image is not associated with any inspection");
        }
        
        // Check if user owns this inspection (if conducted by user)
        if (inspection.getConductedByUser() != null && 
            !inspection.getConductedByUser().getId().equals(user.getId())) {
            throw new RuntimeException("You can only delete images from your own inspections");
        }
        
        // For admin-conducted inspections, only admins can delete
        if (inspection.getConductedByAdmin() != null && user instanceof User) {
            throw new RuntimeException("Only admins can delete images from admin-conducted inspections");
        }
        
        deleteImageFile(image);
        imageRepository.deleteById(imageId);
    }

    public void deleteInspectionImage(Long imageId, Admin admin) throws IOException {
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new RuntimeException("Image not found"));
        
        // Admins can delete any image
        deleteImageFile(image);
        imageRepository.deleteById(imageId);
    }

    private void deleteImageFile(Image image) throws IOException {
        try {
            Path filePath = Paths.get(uploadDirectory, image.getFilePath().replace("/uploads/", ""));
            Files.deleteIfExists(filePath);
        } catch (Exception e) {
            // Log the error but don't throw to allow DB record deletion
            System.err.println("Failed to delete image file: " + e.getMessage());
        }
    }    
}