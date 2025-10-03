package com.example.transformer_manager_backkend.controller;

import com.example.transformer_manager_backkend.entity.AnalysisJob;
import com.example.transformer_manager_backkend.entity.Image;
import com.example.transformer_manager_backkend.repository.ImageRepository;
import com.example.transformer_manager_backkend.service.AnomalyAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/analysis")
@CrossOrigin(origins = "http://localhost:3000")
public class AnomalyAnalysisController {

    private final AnomalyAnalysisService anomalyAnalysisService;
    private final ImageRepository imageRepository;

    public AnomalyAnalysisController(AnomalyAnalysisService anomalyAnalysisService,
            ImageRepository imageRepository) {
        this.anomalyAnalysisService = anomalyAnalysisService;
        this.imageRepository = imageRepository;
    }

    /**
     * Queue an image for anomaly analysis
     */
    @PostMapping("/queue/{imageId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<AnalysisJob> queueImageForAnalysis(@PathVariable Long imageId) {
        Optional<Image> imageOpt = imageRepository.findById(imageId);
        if (imageOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Image image = imageOpt.get();

        // Only process maintenance images
        if (!"Maintenance".equalsIgnoreCase(image.getType())) {
            return ResponseEntity.badRequest().build();
        }

        AnalysisJob job = anomalyAnalysisService.queueImageForAnalysis(image);
        return ResponseEntity.ok(job);
    }

    /**
     * Get analysis job status for an image
     */
    @GetMapping("/status/{imageId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<AnalysisJob> getAnalysisStatus(@PathVariable Long imageId) {
        Optional<Image> imageOpt = imageRepository.findById(imageId);
        if (imageOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Optional<AnalysisJob> jobOpt = anomalyAnalysisService.getAnalysisJobByImage(imageOpt.get());
        if (jobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(jobOpt.get());
    }

    /**
     * Get all analysis jobs for an inspection
     */
    @GetMapping("/inspection/{inspectionId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<List<AnalysisJob>> getAnalysisJobsByInspection(@PathVariable Long inspectionId) {
        List<AnalysisJob> jobs = anomalyAnalysisService.getAnalysisJobsByInspection(inspectionId);
        return ResponseEntity.ok(jobs);
    }

    /**
     * Get current queue status
     */
    @GetMapping("/queue/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<AnomalyAnalysisService.QueueStatus> getQueueStatus() {
        AnomalyAnalysisService.QueueStatus status = anomalyAnalysisService.getQueueStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * Get analysis job details
     */
    @GetMapping("/job/{jobId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<AnalysisJob> getAnalysisJob(@PathVariable Long jobId) {
        Optional<AnalysisJob> jobOpt = anomalyAnalysisService.getAnalysisJobById(jobId);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(jobOpt.get());
    }
}