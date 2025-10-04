## ML Model Labeling System

The ML model uses a **two-stage approach**:

### 1. **PatchCore Model** (Initial Detection)

- Uses a pre-trained PatchCore model to detect anomalies
- Creates an anomaly map and filtered image highlighting suspicious areas

### 2. **Color-Based Classification** (Label Assignment)

The system analyzes the filtered image using HSV color ranges to classify anomalies:

#### **Main Labels Generated:**

1. **"Normal"**

   - When 80%+ of the image is blue/black (normal transformer colors)

2. **"Full Wire Overload"**

   - When 50%+ is red/orange colors OR 50%+ is yellow
   - Or when 70%+ is red/orange/yellow combined
   - Covers the entire wire section

3. **"Point Overload (Faulty)"** ⭐ Most Common

   - Red-colored spots between 120-max area pixels
   - High confidence (0.7+ base confidence)
   - **Red bounding boxes**

4. **"Point Overload (Potential)"** ⭐ Common

   - Yellow-colored spots between 1000-max area pixels
   - Medium confidence (0.6+ base confidence)
   - **Yellow bounding boxes**

5. **"Loose Joint (Faulty)"**

   - Red/orange heating in the center 50% of the image
   - Indicates joint heating issues

6. **"Loose Joint (Potential)"**

   - Yellow heating in the center 50% of the image
   - Potential joint issues

7. **"Tiny Faulty Spot"**

   - Very small red spots (10-30 pixels)

8. **"Tiny Potential Spot"**

   - Very small yellow spots (10-30 pixels)

9. **"Wire Overload (Red/Yellow/Orange Strip)"**

   - Wire-like strips with high aspect ratio (>5:1)
   - Large area coverage (>1% of image)

10. **"Unknown"**
    - Default fallback when no specific patterns are detected

#### **Confidence Calculation:**

- Based on: coverage area (35%), intensity (15%), size ratio (10%), and base confidence (40%)
- Filtered through algorithms that:
  - Remove overlapping detections (NMS)
  - Filter out potential issues inside faulty areas
  - Merge nearby boxes of the same type

#### **Color Coding:**

- **Red boxes**: Faulty conditions (critical issues)
- **Yellow boxes**: Potential issues (warning level)

The system is quite sophisticated - it's not just labeling as "faulty" or "normal", but providing **specific defect types** with **confidence scores** and **precise bounding box locations** for each detected anomaly!
