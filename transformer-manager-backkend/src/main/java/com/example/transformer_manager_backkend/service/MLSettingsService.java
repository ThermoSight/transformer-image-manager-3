package com.example.transformer_manager_backkend.service;

import com.example.transformer_manager_backkend.entity.MLSettings;
import com.example.transformer_manager_backkend.repository.MLSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class MLSettingsService {

    private static final Logger logger = LoggerFactory.getLogger(MLSettingsService.class);

    // Setting keys
    public static final String DETECTION_SENSITIVITY = "detection_sensitivity";

    // Default values
    public static final double DEFAULT_SENSITIVITY = 1.0;

    private final MLSettingsRepository mlSettingsRepository;

    public MLSettingsService(MLSettingsRepository mlSettingsRepository) {
        this.mlSettingsRepository = mlSettingsRepository;
        initializeDefaultSettings();
    }

    /**
     * Initialize default settings if they don't exist
     */
    @Transactional
    public void initializeDefaultSettings() {
        if (!mlSettingsRepository.existsBySettingKey(DETECTION_SENSITIVITY)) {
            MLSettings sensitivity = new MLSettings(
                    DETECTION_SENSITIVITY,
                    String.valueOf(DEFAULT_SENSITIVITY),
                    "ML detection sensitivity (0.1-2.0). Higher values = more sensitive detection = more boxes detected.");
            mlSettingsRepository.save(sensitivity);
            logger.info("Initialized default detection sensitivity: {}", DEFAULT_SENSITIVITY);
        }
    }

    /**
     * Get detection sensitivity
     */
    public double getDetectionSensitivity() {
        Optional<MLSettings> setting = mlSettingsRepository.findBySettingKey(DETECTION_SENSITIVITY);
        if (setting.isPresent()) {
            try {
                double value = Double.parseDouble(setting.get().getSettingValue());
                // Clamp to valid range
                return Math.max(0.1, Math.min(2.0, value));
            } catch (NumberFormatException e) {
                logger.warn("Invalid sensitivity value: {}, using default", setting.get().getSettingValue());
                return DEFAULT_SENSITIVITY;
            }
        }
        return DEFAULT_SENSITIVITY;
    }

    /**
     * Set detection sensitivity
     */
    @Transactional
    public void setDetectionSensitivity(double sensitivity) {
        // Clamp to valid range
        sensitivity = Math.max(0.1, Math.min(2.0, sensitivity));

        Optional<MLSettings> existingSetting = mlSettingsRepository.findBySettingKey(DETECTION_SENSITIVITY);
        if (existingSetting.isPresent()) {
            MLSettings setting = existingSetting.get();
            setting.setSettingValue(String.valueOf(sensitivity));
            mlSettingsRepository.save(setting);
        } else {
            MLSettings setting = new MLSettings(
                    DETECTION_SENSITIVITY,
                    String.valueOf(sensitivity),
                    "ML detection sensitivity (0.1-2.0). Higher values = more sensitive detection = more boxes detected.");
            mlSettingsRepository.save(setting);
        }

        logger.info("Updated detection sensitivity to: {}", sensitivity);
    }

    /**
     * Get all ML settings
     */
    public List<MLSettings> getAllSettings() {
        return mlSettingsRepository.findAll();
    }

    /**
     * Get setting by key
     */
    public Optional<MLSettings> getSettingByKey(String key) {
        return mlSettingsRepository.findBySettingKey(key);
    }

    /**
     * Set generic setting
     */
    @Transactional
    public void setSetting(String key, String value, String description) {
        Optional<MLSettings> existingSetting = mlSettingsRepository.findBySettingKey(key);
        if (existingSetting.isPresent()) {
            MLSettings setting = existingSetting.get();
            setting.setSettingValue(value);
            if (description != null) {
                setting.setDescription(description);
            }
            mlSettingsRepository.save(setting);
        } else {
            MLSettings setting = new MLSettings(key, value, description);
            mlSettingsRepository.save(setting);
        }

        logger.info("Updated setting {}: {}", key, value);
    }

    /**
     * Delete setting
     */
    @Transactional
    public boolean deleteSetting(String key) {
        Optional<MLSettings> setting = mlSettingsRepository.findBySettingKey(key);
        if (setting.isPresent()) {
            mlSettingsRepository.delete(setting.get());
            logger.info("Deleted setting: {}", key);
            return true;
        }
        return false;
    }
}