# Feedback Integration for Model Improvement

## Overview

This module enables human-in-the-loop feedback to continuously refine anomaly detection accuracy.  
It uses user annotations to compute small, explainable confidence “nudges” for each fault label, allowing the AI to gradually align with expert feedback during real-world operation.

---

## How It Works

### 1. User Annotations (Frontend)

After the anomaly detection runs, users can add, edit, resize, or delete bounding boxes on the image viewer.

The React UI maintains two parallel JSON structures:

- `originalResultJson` → AI detections from the model  
- `modifiedResultJson` → human-edited annotations  

Each edit automatically triggers a PATCH or PUT request to persist the change in the backend.

Whenever the user makes a change (add/delete/resize/etc.), the frontend automatically sends a PATCH or PUT request to the backend with the updated JSON and metadata:

```json
{
  "image_id": 12,
  "user_id": 7,
  "originalResultJson": { ... },
  "modifiedResultJson": { ... },
  "timestamp": "2025-10-22T11:00:00Z"
}
```
The backend writes this into the annotation table so every edit is permanently stored with:
- who made the change
- when it was made
- which image it belongs to
- both the AI prediction and the human correction

### 2. Backend Aggregation (ModelFeedbackService)

- The `ModelFeedbackService` periodically (or when requested) scans all annotations and compares each `originalResultJson` vs. `modifiedResultJson`, grouped by label.  
- For every label (for example, *“Loose Joint”* or *“Point Overload”*), it computes three key deltas that quantify how humans corrected the model output.


#### a. Count Change
- Measures how many boxes humans added or deleted.  
- A value of **+1** means the model missed one anomaly; **–1** means the model detected one incorrectly.


#### b. Area Change
- Measures how much the total annotated area grew or shrank.  
- For example, **+0.4** means users drew larger boxes compared to the model’s predictions.  
- Normalized so that small labels are not overshadowed by large ones.


#### c. Confidence Change
- Measures how humans adjusted the model’s confidence.  
- Only considers the AI’s own detections; user-added boxes have no AI confidence value.  

---

1. It then blends these three using weights (typically 0.5, 0.3, 0.2) to produce a single adjustment signal per label.
2. Scale by learningRate (e.g., 0.0001 = 0.01%)
3. Accumulate into the stored per-label bias

### 3. At inference response time, adjust each detection’s confidence for label
This adjustment signal is:
- clamped between –1 and +1,
- scaled by the learningRate (e.g., 0.0001 = 0.01% influence),
- capped to ±0.2 so it never shifts too aggressively.

**Each label’s bias  is updated using an exponential moving average (EMA)**


### 4. Global Confidence bias is calculated and displayed

This is displayed in the UI. The Global Confidence Bias is a single numeric summary that represents the overall direction and magnitude of all the per-label feedback adjustments in your system. It gives admins a high-level indicator of how feedback is trending.
- Example: “Global confidence bias: +0.000027 (model trending slightly under-sensitive).”

It’s essentially the average bias across all anomaly labels.
- If globalBias is positive, it means that users generally added or accepted more anomalies than the model predicted.
 > **Note:** The model has been too conservative, and future detections should become slightly more sensitive (confidence increases)
- If globalBias is negative, it means that users mostly rejected model detections.
 > **Note:** The model has been too confident, and future detections should become slightly more cautious (confidence decreases).
- If globalBias is near zero, it means that, on average, the model and human annotators agree — the model’s overall confidence calibration is healthy.
  
### 5. Once those label-wise adjustments are computed, the service bundles them into a feedback payload

```json
{
  "learning_rate": 0.0001,
  "global_adjustment": 0.000027,
  "label_adjustments": {
    "Loose Joint (Potential)": { "adjustment": -0.000011, "Δcount": 0.0, "Δarea": -0.373 },
    "Point Overload (Potential)": { "adjustment": -0.000058, "Δcount": -0.5, "Δarea": -0.893 }
  },
  "annotation_count": 6,
  "generated_at": "2025-10-22T11:07:36Z"
}
```

### 6.  Apply Bias During Inference
When new detections are produced, their confidence values are gently adjusted:

-A positive bias increases confidence (AI was previously under-sensitive).
- A negative bias decreases confidence (AI was previously over-confident).

### 7. Feedback Learning Rate
The learning rate controls how strongly the AI responds to user corrections.
It determines how much of each aggregated feedback signal actually affects label confidence.Changing this in the ML Settings page immediately affects how new feedback snapshots are weighted.

- Very low (e.g. 0.00001) --> Almost no visible effect — used for cautious, long-term drift
- Default (0.00010)-->Gentle, audit-friendly adaptation (~0.01% influence per update)
- Moderate (0.001)-->Faster reaction to new human trends
- High (>0.01)-->Aggressive biasing — AI quickly mirrors user input, may cause instability


### 8. User Controls
- Adjust learning rate on the UI
- View latest impact — shows:
- Global confidence bias
- Per-label adjustments and deltas
- Save Settings → instantly updates the backend value.

Result
- The backend now tracks and aggregates all human corrections.
- Confidence biases per fault type are automatically updated.
- The model output becomes progressively closer to human expert judgment.
- All feedback is versioned in feedback_snapshot for future retraining or auditing.

 Summary
- Human edits → automatically saved and compared to AI output
- Aggregator computes deltas → per-label bias calculated
- Biases adjust future confidence values in real time
- User can tune the feedback learning rate for faster or slower adaptation
- No model retraining — only explainable runtime bias correction


