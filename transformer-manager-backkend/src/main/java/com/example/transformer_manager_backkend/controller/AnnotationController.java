package com.example.transformer_manager_backkend.controller;

import com.example.transformer_manager_backkend.entity.Annotation;
import com.example.transformer_manager_backkend.repository.AdminRepository;
import com.example.transformer_manager_backkend.repository.UserRepository;
import com.example.transformer_manager_backkend.service.AnnotationService;
import org.springframework.http.ResponseEntity;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/annotations")
@CrossOrigin(origins = "*")
public class AnnotationController {

    private final AnnotationService annotationService;
    private final AdminRepository adminRepository;
    private final UserRepository userRepository;

    public AnnotationController(AnnotationService annotationService,
            AdminRepository adminRepository,
            UserRepository userRepository) {
        this.annotationService = annotationService;
        this.adminRepository = adminRepository;
        this.userRepository = userRepository;
    }

    /**
     * Get or create annotation for an analysis job
     */
    @GetMapping("/analysis-job/{analysisJobId}")
    @PermitAll
    public ResponseEntity<Annotation> getOrCreateAnnotation(
            @PathVariable Long analysisJobId,
            Authentication authentication,
            Principal principal) {

        Object annotator = getAnnotator(authentication, principal);
        Annotation annotation = annotationService.getOrCreateAnnotation(analysisJobId, annotator);

        return ResponseEntity.ok(annotation);
    }

    /**
     * Update annotation with new box data
     */
    @PutMapping("/{annotationId}")
    @PermitAll
    public ResponseEntity<?> updateAnnotation(
            @PathVariable Long annotationId,
            @RequestBody UpdateAnnotationRequest request,
            Authentication authentication,
            Principal principal) {

        try {
            Object annotator = getAnnotator(authentication, principal);

            annotationService.updateAnnotation(
                    annotationId,
                    request != null ? request.getBoxes() : null,
                    request != null ? request.getComments() : null,
                    annotator);

            return ResponseEntity.ok(java.util.Map.of("status", "ok"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    java.util.Map.of(
                            "message", "Failed to update annotation",
                            "error", e.getMessage()));
        }
    }

    /**
     * Get annotation by ID
     */
    @GetMapping("/{annotationId}")
    @PermitAll
    public ResponseEntity<?> getAnnotationById(@PathVariable Long annotationId) {
        try {
            Optional<Annotation> annotation = annotationService.getAnnotationById(annotationId);
            return annotation.<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    java.util.Map.of(
                            "message", "Failed to fetch annotation",
                            "error", e.getMessage()));
        }
    }

    /**
     * Get annotation by analysis job ID
     */
    @GetMapping("/by-analysis-job/{analysisJobId}")
    @PermitAll
    public ResponseEntity<Annotation> getAnnotationByAnalysisJobId(@PathVariable Long analysisJobId) {
        Optional<Annotation> annotation = annotationService.getAnnotationByAnalysisJobId(analysisJobId);

        if (annotation.isPresent()) {
            return ResponseEntity.ok(annotation.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get all annotations for an inspection
     */
    @GetMapping("/inspection/{inspectionId}")
    @PermitAll
    public ResponseEntity<List<Annotation>> getAnnotationsByInspectionId(@PathVariable Long inspectionId) {
        List<Annotation> annotations = annotationService.getAnnotationsByInspectionId(inspectionId);
        return ResponseEntity.ok(annotations);
    }

    /**
     * Export feedback log as JSON
     */
    @GetMapping("/feedback-log/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> exportFeedbackLog() {
        String feedbackLogJson = annotationService.exportFeedbackLogAsJson();

        return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .header("Content-Disposition", "attachment; filename=feedback-log.json")
                .body(feedbackLogJson);
    }

    /**
     * Get annotator object based on authentication
     */
    private Object getAnnotator(Authentication authentication, Principal principal) {
        try {
            if (authentication == null || principal == null) {
                return null; // allow unauthenticated usage; annotator is unknown
            }
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));

            if (isAdmin) {
                return adminRepository.findByUsername(principal.getName())
                        .orElse(null);
            } else {
                return userRepository.findByUsername(principal.getName())
                        .orElse(null);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Request DTO for updating annotations
     */
    public static class UpdateAnnotationRequest {
        private List<AnnotationService.AnnotationBoxDTO> boxes;
        private String comments;

        // Constructors
        public UpdateAnnotationRequest() {
        }

        public UpdateAnnotationRequest(List<AnnotationService.AnnotationBoxDTO> boxes, String comments) {
            this.boxes = boxes;
            this.comments = comments;
        }

        // Getters and Setters
        public List<AnnotationService.AnnotationBoxDTO> getBoxes() {
            return boxes;
        }

        public void setBoxes(List<AnnotationService.AnnotationBoxDTO> boxes) {
            this.boxes = boxes;
        }

        public String getComments() {
            return comments;
        }

        public void setComments(String comments) {
            this.comments = comments;
        }
    }
}
