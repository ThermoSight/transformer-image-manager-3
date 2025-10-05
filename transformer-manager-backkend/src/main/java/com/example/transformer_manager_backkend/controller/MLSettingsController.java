package com.example.transformer_manager_backkend.controller;

import com.example.transformer_manager_backkend.entity.MLSettings;
import com.example.transformer_manager_backkend.service.MLSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/ml-settings")
@CrossOrigin(origins = "http://localhost:3000")
public class MLSettingsController {

    private final MLSettingsService mlSettingsService;

    public MLSettingsController(MLSettingsService mlSettingsService) {
        this.mlSettingsService = mlSettingsService;
    }

    /**
     * Get detection sensitivity
     */
    @GetMapping("/sensitivity")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<SensitivityResponse> getDetectionSensitivity() {
        double sensitivity = mlSettingsService.getDetectionSensitivity();
        return ResponseEntity.ok(new SensitivityResponse(sensitivity));
    }

    /**
     * Set detection sensitivity
     */
    @PutMapping("/sensitivity")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<SensitivityResponse> setDetectionSensitivity(@RequestBody SensitivityRequest request) {
        if (request.getSensitivity() < 0.1 || request.getSensitivity() > 2.0) {
            return ResponseEntity.badRequest().build();
        }

        mlSettingsService.setDetectionSensitivity(request.getSensitivity());
        double updatedSensitivity = mlSettingsService.getDetectionSensitivity();
        return ResponseEntity.ok(new SensitivityResponse(updatedSensitivity));
    }

    /**
     * Get all ML settings
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<MLSettings>> getAllSettings() {
        List<MLSettings> settings = mlSettingsService.getAllSettings();
        return ResponseEntity.ok(settings);
    }

    /**
     * Get setting by key
     */
    @GetMapping("/{key}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<MLSettings> getSettingByKey(@PathVariable String key) {
        Optional<MLSettings> setting = mlSettingsService.getSettingByKey(key);
        return setting.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Set generic setting (admin only)
     */
    @PutMapping("/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MLSettings> setSetting(@PathVariable String key, @RequestBody SettingRequest request) {
        mlSettingsService.setSetting(key, request.getValue(), request.getDescription());
        Optional<MLSettings> updatedSetting = mlSettingsService.getSettingByKey(key);
        return updatedSetting.map(ResponseEntity::ok)
                .orElse(ResponseEntity.internalServerError().build());
    }

    /**
     * Delete setting (admin only)
     */
    @DeleteMapping("/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteSetting(@PathVariable String key) {
        boolean deleted = mlSettingsService.deleteSetting(key);
        return deleted ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    // DTOs
    public static class SensitivityRequest {
        private double sensitivity;

        public double getSensitivity() {
            return sensitivity;
        }

        public void setSensitivity(double sensitivity) {
            this.sensitivity = sensitivity;
        }
    }

    public static class SensitivityResponse {
        private double sensitivity;
        private String description;

        public SensitivityResponse(double sensitivity) {
            this.sensitivity = sensitivity;
            this.description = "Detection sensitivity (0.1-2.0). Higher values = more sensitive detection = more boxes detected.";
        }

        public double getSensitivity() {
            return sensitivity;
        }

        public void setSensitivity(double sensitivity) {
            this.sensitivity = sensitivity;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    public static class SettingRequest {
        private String value;
        private String description;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}