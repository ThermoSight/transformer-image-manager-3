package com.example.transformer_manager_backkend.service;

import com.example.transformer_manager_backkend.entity.Admin;
import com.example.transformer_manager_backkend.entity.Image;
import com.example.transformer_manager_backkend.entity.TransformerRecord;
import com.example.transformer_manager_backkend.repository.TransformerRecordRepository;
import com.example.transformer_manager_backkend.repository.ImageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class TransformerRecordService {

    private final TransformerRecordRepository transformerRecordRepository;
    private final ImageRepository imageRepository;

    @Value("${upload.directory}")
    private String uploadDirectory;

    public TransformerRecordService(TransformerRecordRepository transformerRecordRepository,
            ImageRepository imageRepository) {
        this.transformerRecordRepository = transformerRecordRepository;
        this.imageRepository = imageRepository;
    }

    public TransformerRecord saveTransformerRecord(
            String name,
            String locationName,
            Double locationLat,
            Double locationLng,
            Double capacity,
            String transformerType,
            String poleNo,
            List<ImageDTO> images,
            Admin uploadedBy) throws IOException {

        TransformerRecord transformerRecord = new TransformerRecord();
        transformerRecord.setName(name);
        transformerRecord.setLocationName(locationName);
        transformerRecord.setLocationLat(locationLat);
        transformerRecord.setLocationLng(locationLng);
        transformerRecord.setCapacity(capacity);
        transformerRecord.setTransformerType(transformerType);
        transformerRecord.setPoleNo(poleNo);
        transformerRecord.setUploadedBy(uploadedBy);

        List<Image> imageEntities = new ArrayList<>();
        for (ImageDTO imgDto : images) {
            // Only allow baseline images for transformer creation
            if (!"Baseline".equals(imgDto.type)) {
                throw new IllegalArgumentException("Only baseline images are allowed for transformer creation");
            }

            String fileName = System.currentTimeMillis() + "_" + imgDto.file.getOriginalFilename();
            Path uploadPath = Paths.get(uploadDirectory);
            if (!Files.exists(uploadPath))
                Files.createDirectories(uploadPath);
            Path filePath = uploadPath.resolve(fileName);
            Files.copy(imgDto.file.getInputStream(), filePath);

            Image image = new Image();
            image.setFilePath("/uploads/" + fileName);
            image.setType(imgDto.type);
            image.setWeatherCondition(imgDto.weatherCondition);
            image.setTransformerRecord(transformerRecord);

            imageEntities.add(image);
        }

        transformerRecord.setImages(imageEntities);
        return transformerRecordRepository.save(transformerRecord);
    }

    public List<TransformerRecord> getAllTransformerRecords() {
        return transformerRecordRepository.findAll();
    }

    public TransformerRecord getTransformerRecordById(Long id) {
        return transformerRecordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transformer record not found"));
    }

    public void deleteTransformerRecord(Long id) throws IOException {
        TransformerRecord transformerRecord = transformerRecordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transformer record not found"));

        // Delete associated images
        for (Image image : transformerRecord.getImages()) {
            try {
                Path filePath = Paths.get(uploadDirectory, image.getFilePath().replace("/uploads/", ""));
                Files.deleteIfExists(filePath);
            } catch (Exception ignore) {
            }
        }

        transformerRecordRepository.deleteById(id);
    }

    public void deleteImage(Long imageId) throws IOException {
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new RuntimeException("Image not found"));

        try {
            Path filePath = Paths.get(uploadDirectory, image.getFilePath().replace("/uploads/", ""));
            Files.deleteIfExists(filePath);
        } catch (Exception ignore) {
        }

        imageRepository.deleteById(imageId);
    }

    public TransformerRecord updateTransformerRecord(
            Long id,
            String name,
            String locationName,
            Double locationLat,
            Double locationLng,
            Double capacity,
            String transformerType,
            String poleNo,
            List<ImageDTO> newImages,
            Admin updatedBy) throws IOException {

        TransformerRecord transformerRecord = transformerRecordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transformer record not found"));

        if (name != null)
            transformerRecord.setName(name);
        if (locationName != null)
            transformerRecord.setLocationName(locationName);
        if (locationLat != null)
            transformerRecord.setLocationLat(locationLat);
        if (locationLng != null)
            transformerRecord.setLocationLng(locationLng);
        if (capacity != null)
            transformerRecord.setCapacity(capacity);
        if (transformerType != null)
            transformerRecord.setTransformerType(transformerType);
        if (poleNo != null)
            transformerRecord.setPoleNo(poleNo);

        if (newImages != null && !newImages.isEmpty()) {
            List<Image> imageEntities = transformerRecord.getImages();

            for (ImageDTO imgDto : newImages) {
                // Only allow baseline images for transformer updates
                if (!"Baseline".equals(imgDto.type)) {
                    throw new IllegalArgumentException("Only baseline images are allowed for transformer updates");
                }

                String fileName = System.currentTimeMillis() + "_" + imgDto.file.getOriginalFilename();
                Path uploadPath = Paths.get(uploadDirectory);
                if (!Files.exists(uploadPath))
                    Files.createDirectories(uploadPath);
                Path filePath = uploadPath.resolve(fileName);
                Files.copy(imgDto.file.getInputStream(), filePath);

                Image image = new Image();
                image.setFilePath("/uploads/" + fileName);
                image.setType(imgDto.type);
                image.setWeatherCondition(imgDto.weatherCondition);
                image.setTransformerRecord(transformerRecord);

                imageEntities.add(image);
            }
        }

        return transformerRecordRepository.save(transformerRecord);
    }

    public static class ImageDTO {
        public MultipartFile file;
        public String type;
        public String weatherCondition;
    }
}