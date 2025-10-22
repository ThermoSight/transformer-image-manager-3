-- Track feedback adjustments applied to ML inference over time
CREATE TABLE IF NOT EXISTS feedback_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    learning_rate DOUBLE NOT NULL,
    global_adjustment DOUBLE NOT NULL,
    annotation_samples INT NOT NULL,
    label_adjustments_json TEXT NOT NULL,
    label_feedback_json TEXT NOT NULL
);
