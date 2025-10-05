-- Add ML settings table for persistent configuration
CREATE TABLE IF NOT EXISTS ml_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    setting_key VARCHAR(255) NOT NULL UNIQUE,
    setting_value TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Insert default detection sensitivity
INSERT IGNORE INTO ml_settings (setting_key, setting_value, description) 
VALUES ('detection_sensitivity', '1.0', 'ML detection sensitivity (0.1-2.0). Higher values = more sensitive detection = more boxes detected.');