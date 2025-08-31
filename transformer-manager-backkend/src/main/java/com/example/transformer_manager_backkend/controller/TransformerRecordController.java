package com.example.transformer_manager_backkend.controller;

import com.example.transformer_manager_backkend.entity.Admin;
import com.example.transformer_manager_backkend.entity.TransformerRecord;
import com.example.transformer_manager_backkend.repository.AdminRepository;
import com.example.transformer_manager_backkend.service.TransformerRecordService;
import com.example.transformer_manager_backkend.service.TransformerRecordService.ImageDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/transformer-records")
@CrossOrigin(origins = "http://localhost:3000")
public class TransformerRecordController {

    private final TransformerRecordService transformerRecordService;
    private final AdminRepository adminRepository;

    public TransformerRecordController(TransformerRecordService transformerRecordService,
            AdminRepository adminRepository) {
        this.transformerRecordService = transformerRecordService;
        this.adminRepository = adminRepository;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TransformerRecord> uploadTransformerRecord(
            @RequestParam("name") String name,
            @RequestParam("locationName") String locationName,
            @RequestParam("locationLat") Double locationLat,
            @RequestParam("locationLng") Double locationLng,
            @RequestParam("capacity") Double capacity,
            @RequestParam(value = "transformerType", required = false) String transformerType,
            @RequestParam(value = "poleNo", required = false) String poleNo,
            @RequestParam("images") List<MultipartFile> images,
            @RequestParam("types") List<String> types,
            @RequestParam(value = "weatherConditions", required = false) List<String> weatherConditions,
            Principal principal) throws IOException {

        Admin admin = adminRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        List<ImageDTO> imageDTOs = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            ImageDTO dto = new ImageDTO();
            dto.file = images.get(i);
            dto.type = types.get(i);
            dto.weatherCondition = (dto.type.equals("Baseline") && weatherConditions != null)
                    ? weatherConditions.get(i)
                    : null;
            imageDTOs.add(dto);
        }

        TransformerRecord savedRecord = transformerRecordService.saveTransformerRecord(
                name, locationName, locationLat, locationLng, capacity,
                transformerType, poleNo, imageDTOs, admin);

        return ResponseEntity.ok(savedRecord);
    }

    @GetMapping
    public ResponseEntity<List<TransformerRecord>> getAllTransformerRecords() {
        return ResponseEntity.ok(transformerRecordService.getAllTransformerRecords());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransformerRecord> getTransformerRecordById(@PathVariable Long id) {
        return ResponseEntity.ok(transformerRecordService.getTransformerRecordById(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteTransformerRecord(@PathVariable Long id) throws IOException {
        transformerRecordService.deleteTransformerRecord(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/images/{imageId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteImage(@PathVariable Long imageId) throws IOException {
        transformerRecordService.deleteImage(imageId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TransformerRecord> updateTransformerRecord(
            @PathVariable Long id,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "locationName", required = false) String locationName,
            @RequestParam(value = "locationLat", required = false) Double locationLat,
            @RequestParam(value = "locationLng", required = false) Double locationLng,
            @RequestParam(value = "capacity", required = false) Double capacity,
            @RequestParam(value = "transformerType", required = false) String transformerType,
            @RequestParam(value = "poleNo", required = false) String poleNo,
            @RequestParam(value = "images", required = false) MultipartFile[] images,
            @RequestParam(value = "types", required = false) String[] types,
            @RequestParam(value = "weatherConditions", required = false) String[] weatherConditions,
            Principal principal) throws IOException {

        Admin admin = adminRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        List<ImageDTO> imageDTOs = null;
        if (images != null && types != null && images.length > 0) {
            imageDTOs = new ArrayList<>();
            for (int i = 0; i < images.length; i++) {
                ImageDTO dto = new ImageDTO();
                dto.file = images[i];
                dto.type = types[i];
                dto.weatherCondition = (dto.type.equals("Baseline") && weatherConditions != null
                        && i < weatherConditions.length)
                                ? weatherConditions[i]
                                : null;
                imageDTOs.add(dto);
            }
        }

        TransformerRecord updatedRecord = transformerRecordService.updateTransformerRecord(
                id, name, locationName, locationLat, locationLng, capacity,
                transformerType, poleNo, imageDTOs, admin);

        return ResponseEntity.ok(updatedRecord);
    }
}