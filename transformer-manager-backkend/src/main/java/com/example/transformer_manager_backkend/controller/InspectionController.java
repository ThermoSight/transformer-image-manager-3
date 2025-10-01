package com.example.transformer_manager_backkend.controller;

import java.io.IOException;
import java.security.Principal;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.transformer_manager_backkend.entity.Admin;
import com.example.transformer_manager_backkend.entity.Inspection;
import com.example.transformer_manager_backkend.entity.User;
import com.example.transformer_manager_backkend.repository.AdminRepository;
import com.example.transformer_manager_backkend.repository.UserRepository;
import com.example.transformer_manager_backkend.service.InspectionService;

@RestController
@RequestMapping("/api/inspections")
@CrossOrigin(origins = "http://localhost:3000")
public class InspectionController {

    private final InspectionService inspectionService;
    private final AdminRepository adminRepository;
    private final UserRepository userRepository;

    public InspectionController(InspectionService inspectionService,
            AdminRepository adminRepository,
            UserRepository userRepository) {
        this.inspectionService = inspectionService;
        this.adminRepository = adminRepository;
        this.userRepository = userRepository;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<Inspection> createInspection(
            @RequestParam("transformerRecordId") Long transformerRecordId,
            @RequestParam(value = "notes", required = false) String notes,
            @RequestParam(value = "images", required = false) List<MultipartFile> images,
            @RequestParam(value = "inspectionDate", required = false) String inspectionDateStr,
            Authentication authentication,
            Principal principal) throws IOException {

        // Parse inspection date, default to now if not provided
        java.time.LocalDateTime inspectionDate = null;
        if (inspectionDateStr != null && !inspectionDateStr.isEmpty()) {
            try {
                inspectionDate = java.time.LocalDateTime.parse(inspectionDateStr);
            } catch (Exception e) {
                inspectionDate = java.time.LocalDateTime.now();
            }
        } else {
            inspectionDate = java.time.LocalDateTime.now();
        }

        // Determine if the user is an admin or regular user
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));

        Inspection inspection;
        if (isAdmin) {
            Admin admin = adminRepository.findByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));
            inspection = inspectionService.createInspectionByAdmin(
                    transformerRecordId, notes, images, admin, inspectionDate);
        } else {
            User user = userRepository.findByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            inspection = inspectionService.createInspectionByUser(
                    transformerRecordId, notes, images, user, inspectionDate);
        }

        return ResponseEntity.ok(inspection);
    }

    @GetMapping("/transformer/{transformerRecordId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<List<Inspection>> getInspectionsByTransformerRecord(
            @PathVariable Long transformerRecordId) {
        return ResponseEntity.ok(inspectionService.getInspectionsByTransformerRecordId(transformerRecordId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<Inspection> getInspectionById(@PathVariable Long id) {
        return ResponseEntity.ok(inspectionService.getInspectionById(id));
    }

    @PostMapping("/{id}/images")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<Inspection> addImagesToInspection(
            @PathVariable Long id,
            @RequestParam("images") List<MultipartFile> images,
            Authentication authentication,
            Principal principal) throws IOException {

        // Determine if the user is an admin or regular user
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));

        Inspection updatedInspection;
        if (isAdmin) {
            Admin admin = adminRepository.findByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));
            updatedInspection = inspectionService.addImagesToInspection(id, images, admin);
        } else {
            User user = userRepository.findByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            updatedInspection = inspectionService.addImagesToInspection(id, images, user);
        }

        return ResponseEntity.ok(updatedInspection);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<?> deleteInspection(@PathVariable Long id,
            Authentication authentication,
            Principal principal) throws IOException {

        // Allow users to delete only their own inspections, admins can delete any
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));

        if (isAdmin) {
            inspectionService.deleteInspection(id);
        } else {
            // For users, verify they are the ones who created the inspection
            User user = userRepository.findByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            inspectionService.deleteInspectionByUser(id, user);
        }

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/images/{imageId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<?> deleteInspectionImage(
            @PathVariable Long imageId,
            Authentication authentication,
            Principal principal) throws IOException {

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));

        if (isAdmin) {
            Admin admin = adminRepository.findByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));
            inspectionService.deleteInspectionImage(imageId, admin);
        } else {
            User user = userRepository.findByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            inspectionService.deleteInspectionImage(imageId, user);
        }

        return ResponseEntity.ok().build();
    }
}