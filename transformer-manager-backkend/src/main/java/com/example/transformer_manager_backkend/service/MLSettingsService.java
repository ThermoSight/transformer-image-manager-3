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
    public static final String FEEDBACK_LEARNING_RATE = "feedback_learning_rate";

    // Default values
    public static final double DEFAULT_SENSITIVITY = 1.0;
    public static final double DEFAULT_FEEDBACK_LEARNING_RATE = 0.0001; // 0.01%

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
        initializeSettingIfMissing(
                DETECTION_SENSITIVITY,
                String.valueOf(DEFAULT_SENSITIVITY),
                "ML detection sensitivity (0.1-2.0). Higher values = more sensitive detection = more boxes detected.");

        initializeSettingIfMissing(
                FEEDBACK_LEARNING_RATE,
                String.valueOf(DEFAULT_FEEDBACK_LEARNING_RATE),
                "Fractional learning rate for incorporating human annotation feedback into model confidence (0.00001-0.05).");
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
                return clamp(value, 0.1, 2.0);
            } catch (NumberFormatException e) {
                logger.warn("Invalid sensitivity value: {}, using default", setting.get().getSettingValue());
                return DEFAULT_SENSITIVITY;
            }
        }
        return DEFAULT_SENSITIVITY;
    }

    /**
     * Get feedback learning rate
     */
    public double getFeedbackLearningRate() {
        Optional<MLSettings> setting = mlSettingsRepository.findBySettingKey(FEEDBACK_LEARNING_RATE);
        if (setting.isPresent()) {
            try {
                double value = Double.parseDouble(setting.get().getSettingValue());
                return clamp(value, 0.00001, 0.05);
            } catch (NumberFormatException e) {
                logger.warn("Invalid feedback learning rate: {}, using default", setting.get().getSettingValue());
                return DEFAULT_FEEDBACK_LEARNING_RATE;
            }
        }
        return DEFAULT_FEEDBACK_LEARNING_RATE;
    }

    /**
     * Set detection sensitivity
     */
    @Transactional
    public void setDetectionSensitivity(double sensitivity) {
        // Clamp to valid range
        double clamped = clamp(sensitivity, 0.1, 2.0);
        upsertSetting(
                DETECTION_SENSITIVITY,
                String.valueOf(clamped),
                "ML detection sensitivity (0.1-2.0). Higher values = more sensitive detection = more boxes detected.");
        logger.info("Updated detection sensitivity to: {}", clamped);
    }

    /**
     * Set feedback learning rate
     */
    @Transactional
    public void setFeedbackLearningRate(double learningRate) {
        double clamped = clamp(learningRate, 0.00001, 0.05);
        upsertSetting(
                FEEDBACK_LEARNING_RATE,
                String.valueOf(clamped),
                "Fractional learning rate for incorporating human annotation feedback into model confidence (0.00001-0.05).");
        logger.info("Updated feedback learning rate to: {}", clamped);
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

    private void initializeSettingIfMissing(String key, String value, String description) {
        if (!mlSettingsRepository.existsBySettingKey(key)) {
            MLSettings setting = new MLSettings(key, value, description);
            mlSettingsRepository.save(setting);
            logger.info("Initialized default setting {}: {}", key, value);
        }
    }

    private void upsertSetting(String key, String value, String description) {
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
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
