#!/usr/bin/env python3
"""
inference_core_local.py
Local-only PatchCore inference + rich post-processing + JSON box export.

Usage:
  python inference_core_local.py \
      --config config/patchcore_transformers.yaml \
      --ckpt model_weights/model.ckpt \
      --input test_image \
      --outdir outputs

Creates:
  outputs/masks/<base>_mask.png
  outputs/filtered/<base>_filtered.png
  outputs/boxed/<base>_boxed.<ext>
  outputs/boxed/<base>.json   <-- editable: list of boxes with coords/labels/conf
"""

import os
import glob
import json
import argparse
import numpy as np
import cv2
from PIL import Image
import torch
from omegaconf import OmegaConf
from anomalib.models import Patchcore

# -------------------------
# Defaults
# -------------------------
DEFAULT_CONFIG = "config/patchcore_transformers.yaml"
DEFAULT_CKPT   = "model_weights/model.ckpt"
DEFAULT_INPUT  = "test_image"
DEFAULT_OUTDIR = "outputs"
DEFAULT_INFER_SIZE = 256  # match your training setup if different

# -------------------------
# Helpers
# -------------------------
def _ensure_dir(d):
    os.makedirs(d, exist_ok=True)
    return d

def is_image_file(p):
    ext = os.path.splitext(p.lower())[1]
    return ext in [".jpg", ".jpeg", ".png", ".bmp", ".tif", ".tiff", ".webp"]

def collect_images(inp):
    if os.path.isdir(inp):
        files = []
        for ext in ("*.jpg", "*.jpeg", "*.png", "*.bmp", "*.tif", "*.tiff", "*.webp"):
            files.extend(glob.glob(os.path.join(inp, ext)))
        return sorted(files)
    elif os.path.isfile(inp) and is_image_file(inp):
        return [inp]
    else:
        raise FileNotFoundError(f"No images found at: {inp}")

# -------------------------
# Geometry / box utilities
# -------------------------
def _iou(boxA, boxB):
    xA = max(boxA[0], boxB[0])
    yA = max(boxA[1], boxB[1])
    xB = min(boxA[0] + boxA[2], boxB[0] + boxB[2])
    yB = min(boxA[1] + boxA[3], boxB[1] + boxB[3])
    interW = max(0, xB - xA)
    interH = max(0, yB - yA)
    interArea = interW * interH
    boxAArea = boxA[2] * boxA[3]
    boxBArea = boxB[2] * boxB[3]
    return interArea / float(boxAArea + boxBArea - interArea + 1e-6)

def _merge_close_boxes(boxes, labels, dist_thresh=20, confidences=None):
    if confidences is None:
        confidences = [0.5] * len(boxes)
    merged, merged_labels, merged_confidences = [], [], []
    used = [False] * len(boxes)
    for i in range(len(boxes)):
        if used[i]:
            continue
        x1, y1, w1, h1 = boxes[i]
        label1 = labels[i]
        conf1 = confidences[i]
        x2, y2, w2, h2 = x1, y1, w1, h1
        max_conf = conf1
        for j in range(i + 1, len(boxes)):
            if used[j]:
                continue
            bx, by, bw, bh = boxes[j]
            cx1, cy1 = x1 + w1 // 2, y1 + h1 // 2
            cx2, cy2 = bx + bw // 2, by + bh // 2
            if abs(cx1 - cx2) < dist_thresh and abs(cy1 - cy2) < dist_thresh and label1 == labels[j]:
                x2 = min(x2, bx)
                y2 = min(y2, by)
                w2 = max(x1 + w1, bx + bw) - x2
                h2 = max(y1 + h1, by + bh) - y2
                max_conf = max(max_conf, confidences[j])
                used[j] = True
        merged.append((x2, y2, w2, h2))
        merged_labels.append(label1)
        merged_confidences.append(max_conf)
        used[i] = True
    return merged, merged_labels, merged_confidences

def _nms_iou_with_confidence(boxes, labels, confidences, iou_thresh=0.4):
    if len(boxes) == 0:
        return [], [], []
    idxs = np.argsort([w * h for (x, y, w, h) in boxes])[::-1]
    keep, keep_labels, keep_confidences = [], [], []
    while len(idxs) > 0:
        i = idxs[0]
        keep.append(boxes[i])
        keep_labels.append(labels[i])
        keep_confidences.append(confidences[i])
        remove = [0]
        for j in range(1, len(idxs)):
            if _iou(boxes[i], boxes[idxs[j]]) > iou_thresh:
                remove.append(j)
        idxs = np.delete(idxs, remove)
    return keep, keep_labels, keep_confidences

def _filter_faulty_inside_potential(boxes, labels, confidences=None):
    if confidences is None:
        confidences = [0.5] * len(boxes)
    filtered_boxes, filtered_labels, filtered_confidences = [], [], []
    for (box, label, conf) in zip(boxes, labels, confidences):
        if label == "Point Overload (Potential)":
            keep = True
            x, y, w, h = box
            for (fbox, flabel) in zip(boxes, labels):
                if flabel == "Point Overload (Faulty)":
                    fx, fy, fw, fh = fbox
                    if fx >= x and fy >= y and fx + fw <= x + w and fy + fh <= y + h:
                        keep = False
                        break
            if keep:
                filtered_boxes.append(box); filtered_labels.append(label); filtered_confidences.append(conf)
        else:
            filtered_boxes.append(box); filtered_labels.append(label); filtered_confidences.append(conf)
    return filtered_boxes, filtered_labels, filtered_confidences

def _filter_faulty_overlapping_potential(boxes, labels, confidences=None):
    if confidences is None:
        confidences = [0.5] * len(boxes)
    def is_overlapping(boxA, boxB):
        xA = max(boxA[0], boxB[0]); yA = max(boxA[1], boxB[1])
        xB = min(boxA[0] + boxA[2], boxB[0] + boxB[2]); yB = min(boxA[1] + boxA[3], boxB[1] + boxB[3])
        return (xB > xA) and (yB > yA)
    filtered_boxes, filtered_labels, filtered_confidences = [], [], []
    for (box, label, conf) in zip(boxes, labels, confidences):
        if label == "Point Overload (Potential)":
            keep = True
            for (fbox, flabel) in zip(boxes, labels):
                if flabel == "Point Overload (Faulty)" and is_overlapping(box, fbox):
                    keep = False; break
            if keep:
                filtered_boxes.append(box); filtered_labels.append(label); filtered_confidences.append(conf)
        else:
            filtered_boxes.append(box); filtered_labels.append(label); filtered_confidences.append(conf)
    return filtered_boxes, filtered_labels, filtered_confidences

def _calculate_confidence(img, box, mask, label):
    x, y, w, h = box
    roi = img[y:y+h, x:x+w]
    mask_roi = mask[y:y+h, x:x+w]
    if roi.size == 0 or mask_roi.size == 0:
        return 0.5
    coverage = np.sum(mask_roi > 0) / mask_roi.size
    intensity = (np.mean(roi[mask_roi > 0]) / 255.0) if np.sum(mask_roi > 0) > 0 else 0.0
    total_pixels = img.shape[0] * img.shape[1]
    size_ratio = (w * h) / total_pixels
    if size_ratio < 0.0001: size_conf = size_ratio / 0.0001
    elif size_ratio > 0.1:  size_conf = max(0.3, 1.0 - (size_ratio - 0.1) / 0.9)
    else:                   size_conf = 1.0
    if "Faulty" in label: base_conf = 0.7
    elif "Potential" in label: base_conf = 0.6
    elif "Tiny" in label: base_conf = 0.5
    elif "Wire" in label or "Full" in label: base_conf = 0.8
    elif "Loose Joint" in label: base_conf = 0.7
    else: base_conf = 0.6
    conf = base_conf * 0.4 + coverage * 0.35 + intensity * 0.15 + size_conf * 0.10
    return round(max(0.3, min(0.99, conf)), 3)

def load_feedback_adjustments(path):
    """
    Load feedback adjustments generated by the backend. Returns a normalized dictionary.
    """
    if not path or str(path).strip() in {"", "none", "None"}:
        return {
            "global_adjustment": 0.0,
            "label_adjustments": {},
            "label_feedback": [],
            "learning_rate": 0.0,
            "generated_at": None,
            "total_annotations_considered": 0,
            "source": "none",
        }
    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        if not isinstance(data, dict):
            raise ValueError("Feedback JSON root must be an object.")
    except Exception as exc:
        print(f"[WARN] Failed to load feedback adjustments from {path}: {exc}")
        return {
            "global_adjustment": 0.0,
            "label_adjustments": {},
            "label_feedback": [],
            "learning_rate": 0.0,
            "generated_at": None,
            "total_annotations_considered": 0,
            "source": "error",
            "error": str(exc),
        }

    # Normalize expected fields
    global_adj = float(data.get("global_adjustment", 0.0) or 0.0)
    label_adj = data.get("label_adjustments") or {}
    if not isinstance(label_adj, dict):
        label_adj = {}
    label_feedback = data.get("label_feedback")
    if not isinstance(label_feedback, list):
        label_feedback = []

    return {
        "global_adjustment": global_adj,
        "label_adjustments": label_adj,
        "label_feedback": label_feedback,
        "learning_rate": float(data.get("learning_rate", 0.0) or 0.0),
        "generated_at": data.get("generated_at"),
        "total_annotations_considered": int(data.get("total_annotations_considered", 0) or 0),
        "source": data.get("source", "annotation_feedback"),
    }

def _apply_feedback_to_confidences(labels, confidences, feedback):
    """
    Apply feedback adjustments to per-box confidences.
    Returns (adjusted_confidences, per_box_feedback_details).
    """
    if not confidences:
        return confidences, []

    label_adjustments = feedback.get("label_adjustments", {}) or {}
    global_adj = float(feedback.get("global_adjustment", 0.0) or 0.0)
    adjusted_confidences = []
    per_box = []

    for label, conf in zip(labels, confidences):
        label_info = label_adjustments.get(label, {}) or {}
        label_adj = float(label_info.get("adjustment", 0.0) or 0.0)
        total_adj = global_adj + label_adj
        adjusted = round(float(np.clip(conf + total_adj, 0.05, 0.99)), 3)
        adjusted_confidences.append(adjusted)

        detail = {
            "label": label,
            "original_confidence": round(float(conf), 3),
            "adjustment": round(float(total_adj), 6),
            "components": {
                "global": round(float(global_adj), 6),
                "label": round(float(label_adj), 6),
            },
            "adjusted_confidence": adjusted,
        }
        # Include optional source metrics when available
        source_metrics = {}
        for key in ("avg_count_delta", "avg_area_ratio", "avg_confidence_delta", "samples"):
            if key in label_info and label_info[key] is not None:
                source_metrics[key] = label_info[key]
        if source_metrics:
            detail["source_metrics"] = source_metrics
        per_box.append(detail)

    return adjusted_confidences, per_box

# -------------------------
# Classification (on FILTERED image)
# -------------------------
def classify_filtered_image(filtered_img_path: str, sensitivity=1.0, feedback=None):
    """
    Classify filtered image with adjustable sensitivity.
    
    Args:
        filtered_img_path: Path to the filtered image
        sensitivity: Detection sensitivity (0.1-2.0)
                    - Higher values (>1.0) = more sensitive = more boxes
                    - Lower values (<1.0) = less sensitive = fewer boxes
    """
    img = cv2.imread(filtered_img_path)
    if img is None:
        raise FileNotFoundError(f"Could not read filtered image: {filtered_img_path}")
    if img.dtype != np.uint8:
        img = img.astype(np.uint8)

    feedback = feedback or {}

    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    blue_mask   = cv2.inRange(hsv, (90, 50, 20), (130, 255, 255))
    black_mask  = cv2.inRange(hsv, (0, 0, 0),   (180, 255, 50))
    yellow_mask = cv2.inRange(hsv, (20, 130, 130), (35, 255, 255))
    orange_mask = cv2.inRange(hsv, (10, 100, 100), (25, 255, 255))
    red_mask1   = cv2.inRange(hsv, (0, 100, 100),  (10, 255, 255))
    red_mask2   = cv2.inRange(hsv, (160, 100, 100),(180, 255, 255))
    red_mask    = cv2.bitwise_or(red_mask1, red_mask2)

    total = img.shape[0] * img.shape[1]
    blue_count   = np.sum(blue_mask > 0)
    black_count  = np.sum(black_mask > 0)
    yellow_count = np.sum(yellow_mask > 0)
    orange_count = np.sum(orange_mask > 0)
    red_count    = np.sum(red_mask > 0)

    label = "Unknown"
    box_list, label_list, confidence_list = [], [], []

    if (blue_count + black_count) / total > 0.8:
        label = "Normal"
    elif (red_count + orange_count) / total > 0.5:
        label = "Full Wire Overload"
    elif (yellow_count) / total > 0.5:
        label = "Full Wire Overload"

    full_wire_thresh = max(0.5, 0.7 / sensitivity)  # Prevent threshold from going too low
    if (red_count + orange_count + yellow_count) / total > full_wire_thresh:
        label = "Full Wire Overload"
        box = (0, 0, img.shape[1], img.shape[0])
        box_list.append(box)
        conf = min(0.95, 0.7 + ((red_count + orange_count + yellow_count) / total - full_wire_thresh) * 0.8)
        label_list.append(label); confidence_list.append(round(conf, 3))
    else:
        # Scale area thresholds by sensitivity (inverse relationship)
        min_area_faulty = max(10, int(120 / sensitivity))
        min_area_potential = max(50, int(1000 / sensitivity))
        max_area = min(0.2 * total, 0.05 * total * sensitivity)  # Allow larger areas with higher sensitivity

        for mask, spot_label, min_a in [
            (red_mask, "Point Overload (Faulty)", min_area_faulty),
            (yellow_mask, "Point Overload (Potential)", min_area_potential),
        ]:
            contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
            for cnt in contours:
                area = cv2.contourArea(cnt)
                if min_a < area < max_area:
                    x, y, w, h = cv2.boundingRect(cnt)
                    box = (x, y, w, h)
                    box_list.append(box)
                    label_list.append(spot_label)
                    confidence_list.append(_calculate_confidence(img, box, mask, spot_label))

        # Middle area (Loose Joint)
        h, w = img.shape[:2]
        center = img[h // 4 : 3 * h // 4, w // 4 : 3 * w // 4]
        center_hsv = cv2.cvtColor(center, cv2.COLOR_BGR2HSV)
        center_yellow = cv2.inRange(center_hsv, (20, 130, 130), (35, 255, 255))
        center_orange = cv2.inRange(center_hsv, (10, 100, 100), (25, 255, 255))
        center_red1 = cv2.inRange(center_hsv, (0, 100, 100), (10, 255, 255))
        center_red2 = cv2.inRange(center_hsv, (160, 100, 100), (180, 255, 255))
        center_red = cv2.bitwise_or(center_red1, center_red2)

        # Scale center area threshold by sensitivity
        center_thresh = max(0.05, 0.1 / sensitivity) * center.size
        if np.sum(center_red > 0) + np.sum(center_orange > 0) > center_thresh:
            label = "Loose Joint (Faulty)"
            box = (w // 4, h // 4, w // 2, h // 2)
            box_list.append(box)
            label_list.append(label)
            center_coverage = (np.sum(center_red > 0) + np.sum(center_orange > 0)) / center.size
            confidence_list.append(round(min(0.85, 0.6 + center_coverage), 3))
        elif np.sum(center_yellow > 0) > center_thresh:
            label = "Loose Joint (Potential)"
            box = (w // 4, h // 4, w // 2, h // 2)
            box_list.append(box)
            label_list.append(label)
            center_coverage = np.sum(center_yellow > 0) / center.size
            confidence_list.append(round(min(0.75, 0.5 + center_coverage), 3))

    # Tiny spots - scale thresholds by sensitivity
    min_area_tiny = max(5, int(10 / sensitivity))
    max_area_tiny = min(100, int(30 * sensitivity))
    for mask, spot_label in [
        (red_mask, "Tiny Faulty Spot"),
        (yellow_mask, "Tiny Potential Spot"),
    ]:
        contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        for cnt in contours:
            area = cv2.contourArea(cnt)
            if min_area_tiny < area < max_area_tiny:
                x, y, w, h = cv2.boundingRect(cnt)
                box = (x, y, w, h)
                box_list.append(box)
                label_list.append(spot_label)
                confidence_list.append(_calculate_confidence(img, box, mask, spot_label))

    # Wire-like strips - scale thresholds by sensitivity
    aspect_ratio_thresh = max(3, 5 / sensitivity)  # Lower aspect ratio = more sensitive
    min_strip_area = max(0.005 * total, 0.01 * total / sensitivity)
    wire_boxes, wire_labels, wire_confidences = [], [], []
    for mask, strip_label in [
        (red_mask, "Wire Overload (Red Strip)"),
        (yellow_mask, "Wire Overload (Yellow Strip)"),
        (orange_mask, "Wire Overload (Orange Strip)"),
    ]:
        contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        for cnt in contours:
            area = cv2.contourArea(cnt)
            if area > min_strip_area:
                x, y, w, h = cv2.boundingRect(cnt)
                aspect_ratio = max(w, h) / (min(w, h) + 1e-6)
                if aspect_ratio > aspect_ratio_thresh:
                    box = (x, y, w, h)
                    wire_boxes.append(box)
                    wire_labels.append(strip_label)
                    wire_confidences.append(_calculate_confidence(img, box, mask, strip_label))

    box_list = wire_boxes[:] + box_list
    label_list = wire_labels[:] + label_list
    confidence_list = wire_confidences[:] + confidence_list

    # Post-processing with sensitivity-adjusted parameters
    iou_thresh = max(0.2, min(0.6, 0.4 + (sensitivity - 1.0) * 0.1))  # Higher sensitivity = less aggressive NMS
    merge_dist = max(20, min(200, int(100 / sensitivity)))  # Higher sensitivity = merge closer boxes
    
    box_list, label_list, confidence_list = _nms_iou_with_confidence(box_list, label_list, confidence_list, iou_thresh=iou_thresh)
    box_list, label_list, confidence_list = _filter_faulty_inside_potential(box_list, label_list, confidence_list)
    box_list, label_list, confidence_list = _filter_faulty_overlapping_potential(box_list, label_list, confidence_list)
    box_list, label_list, confidence_list = _merge_close_boxes(box_list, label_list, dist_thresh=merge_dist, confidences=confidence_list)

    confidence_list, per_box_feedback = _apply_feedback_to_confidences(label_list, confidence_list, feedback)

    return label, box_list, label_list, confidence_list, per_box_feedback

# -------------------------
# PatchCore inference
# -------------------------
def load_model(config_path, ckpt_path, device):
    cfg = OmegaConf.load(config_path)
    model = Patchcore.load_from_checkpoint(ckpt_path, **cfg.model.init_args)
    model.eval()
    model.to(device)
    return model, cfg

def infer_single_image_with_patchcore(model, device, image_path, infer_size=DEFAULT_INFER_SIZE,
                                      out_mask_dir=None, out_filtered_dir=None):
    fixed_path = os.path.abspath(os.path.normpath(image_path))
    orig_img = Image.open(fixed_path).convert("RGB")
    orig_w, orig_h = orig_img.size

    img_resized = orig_img.resize((infer_size, infer_size))
    img_tensor = torch.from_numpy(np.array(img_resized)).permute(2, 0, 1).float() / 255.0
    img_tensor = img_tensor.unsqueeze(0).to(device)

    with torch.no_grad():
        output = model(img_tensor)
        if hasattr(output, "anomaly_map"):
            anomaly_map = output.anomaly_map.squeeze().detach().cpu().numpy()
        elif isinstance(output, (tuple, list)) and len(output) > 1:
            anomaly_map = output[1].squeeze().detach().cpu().numpy()
        else:
            anomaly_map = None

    base = os.path.splitext(os.path.basename(fixed_path))[0]
    mask_path = None
    filtered_path = None

    if anomaly_map is not None:
        norm_map = (255 * (anomaly_map - anomaly_map.min()) / (np.ptp(anomaly_map) + 1e-8)).astype(np.uint8)
        if norm_map.ndim > 2:
            norm_map = np.squeeze(norm_map)
            if norm_map.ndim > 2:
                norm_map = norm_map[0]

        mask_img_256 = Image.fromarray(norm_map)
        mask_img = mask_img_256.resize((orig_w, orig_h), resample=Image.BILINEAR)

        if out_mask_dir:
            mask_path = os.path.join(out_mask_dir, f"{base}_mask.png")
            mask_img.save(mask_path)

        bin_mask = np.array(mask_img) > 128
        orig_np = np.array(orig_img)
        filtered_np = np.zeros_like(orig_np)
        filtered_np[bin_mask] = orig_np[bin_mask]
        filtered_img = Image.fromarray(filtered_np)

        if out_filtered_dir:
            filtered_path = os.path.join(out_filtered_dir, f"{base}_filtered.png")
            filtered_img.save(filtered_path)
    else:
        print(f"[WARN] No anomaly_map for {image_path}")

    return {
        "orig_path": fixed_path,
        "mask_path": mask_path,
        "filtered_path": filtered_path,
        "orig_size": (orig_w, orig_h),
    }

# -------------------------
# Draw + JSON save
# -------------------------
def color_for_label(label):
    # BGR colors (OpenCV)
    if "Potential" in label or "Full Wire Overload" in label:
        return (0, 255, 255)  # yellow box
    else:
        return (0, 0, 255)    # red box

def run_pipeline_for_image(model, device, image_path, out_boxed_dir, out_mask_dir, out_filtered_dir,
                           infer_size=DEFAULT_INFER_SIZE, sensitivity=1.0, feedback=None):
    feedback = feedback or {}
    pc_out = infer_single_image_with_patchcore(
        model, device, image_path, infer_size=infer_size,
        out_mask_dir=out_mask_dir, out_filtered_dir=out_filtered_dir
    )
    filtered_path = pc_out["filtered_path"] or pc_out["orig_path"]
    orig_path = pc_out["orig_path"]

    # Classify with sensitivity parameter
    label, boxes, labels, confidences, per_box_feedback = classify_filtered_image(
        filtered_path, sensitivity=sensitivity, feedback=feedback)

    # Draw on original
    draw_img = cv2.imread(orig_path)
    if draw_img is None:
        raise FileNotFoundError(f"Could not read original image: {orig_path}")

    # Prepare outputs
    base = os.path.splitext(os.path.basename(orig_path))[0]
    ext = os.path.splitext(os.path.basename(orig_path))[1] or ".png"
    out_boxed_path = os.path.join(out_boxed_dir, f"{base}_boxed{ext}")
    out_json_path = os.path.join(out_boxed_dir, f"{base}.json")

    # Draw boxes
    for (x, y, w, h), l, conf in zip(boxes, labels, confidences):
        cv2.rectangle(draw_img, (x, y), (x + w, y + h), color_for_label(l), 2)
        text = f"{l} ({conf:.2f})"
        cv2.putText(draw_img, text, (x, max(0, y - 10)), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 255), 2)

    if not boxes:
        cv2.putText(draw_img, label, (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 255), 2)

    # Save image
    ok = cv2.imwrite(out_boxed_path, draw_img)
    if not ok:
        out_boxed_path = os.path.join(out_boxed_dir, f"{base}_boxed.png")
        cv2.imwrite(out_boxed_path, draw_img)

    # Save JSON (editable)
    feedback_section = {
        "applied": bool(per_box_feedback) or abs(float(feedback.get("global_adjustment", 0.0) or 0.0)) > 1e-9,
        "global_adjustment": round(float(feedback.get("global_adjustment", 0.0) or 0.0), 6),
        "learning_rate": feedback.get("learning_rate"),
        "generated_at": feedback.get("generated_at"),
        "source": feedback.get("source", "annotation_feedback"),
        "total_annotations_considered": feedback.get("total_annotations_considered", 0),
        "label_adjustments": feedback.get("label_adjustments", {}),
        "label_feedback": feedback.get("label_feedback", []),
        "per_box": per_box_feedback,
    }
    if feedback.get("error"):
        feedback_section["error"] = feedback["error"]

    json_obj = {
        "image": os.path.abspath(orig_path),
        "boxed_image": os.path.abspath(out_boxed_path),
        "mask_image": os.path.abspath(pc_out["mask_path"]) if pc_out["mask_path"] else None,
        "filtered_image": os.path.abspath(pc_out["filtered_path"]) if pc_out["filtered_path"] else None,
        "label": label,
        "boxes": [
            {"box": [int(x), int(y), int(w), int(h)], "type": l, "confidence": float(conf)}
            for (x, y, w, h), l, conf in zip(boxes, labels, confidences)
        ],
        "feedback_adjustments": feedback_section,
    }
    with open(out_json_path, "w", encoding="utf-8") as f:
        json.dump(json_obj, f, indent=2)

    return {
        "label": label,
        "boxed_path": out_boxed_path,
        "json_path": out_json_path,
        "feedback": feedback_section,
    }

# -------------------------
# Main
# -------------------------
def main():
    parser = argparse.ArgumentParser(description="Local PatchCore inference with JSON export")
    parser.add_argument("--config", default=DEFAULT_CONFIG, help="Path to PatchCore YAML config")
    parser.add_argument("--ckpt",   default=DEFAULT_CKPT,   help="Path to PatchCore checkpoint .ckpt")
    parser.add_argument("--input",  default=DEFAULT_INPUT,  help="Image file or folder")
    parser.add_argument("--outdir", default=DEFAULT_OUTDIR, help="Output base directory")
    parser.add_argument("--size",   type=int, default=DEFAULT_INFER_SIZE, help="Inference resize (match training)")
    parser.add_argument("--sensitivity", type=float, default=1.0, help="Detection sensitivity (0.1-2.0). Higher = more boxes")
    parser.add_argument("--feedback", default="", help="Path to feedback adjustments JSON generated by backend")
    parser.add_argument("--cpu",    action="store_true", help="Force CPU")
    args = parser.parse_args()

    # Validate sensitivity range
    if not 0.1 <= args.sensitivity <= 2.0:
        print(f"[WARN] Sensitivity {args.sensitivity} outside recommended range [0.1, 2.0]. Clamping.")
        args.sensitivity = max(0.1, min(2.0, args.sensitivity))

    device = torch.device("cpu" if args.cpu or not torch.cuda.is_available() else "cuda")
    print(f"[INFO] Using device: {device}")
    print(f"[INFO] Detection sensitivity: {args.sensitivity}")

    model, _cfg = load_model(args.config, args.ckpt, device)
    print("[INFO] Model loaded.")

    feedback_data = load_feedback_adjustments(args.feedback)
    if feedback_data.get("error"):
        print(f"[WARN] Feedback adjustments will be skipped due to error: {feedback_data['error']}")
    elif feedback_data.get("label_adjustments"):
        print(f"[INFO] Loaded feedback adjustments for {len(feedback_data['label_adjustments'])} labels "
              f"(global bias {feedback_data.get('global_adjustment', 0.0)})")
    else:
        print("[INFO] No feedback adjustments available.")

    out_base = _ensure_dir(args.outdir)
    out_mask_dir = _ensure_dir(os.path.join(out_base, "masks"))
    out_filtered_dir = _ensure_dir(os.path.join(out_base, "filtered"))
    out_boxed_dir = _ensure_dir(os.path.join(out_base, "boxed"))

    images = collect_images(args.input)
    if not images:
        print(f"[WARN] No images found in {args.input}")
        return

    for img_path in images:
        print(f"[RUN] {img_path}")
        result = run_pipeline_for_image(
            model, device, img_path,
            out_boxed_dir=out_boxed_dir,
            out_mask_dir=out_mask_dir,
            out_filtered_dir=out_filtered_dir,
            infer_size=args.size,
            sensitivity=args.sensitivity,
            feedback=feedback_data,
        )
        print(f"  -> Label: {result['label']}")
        print(f"  -> Boxed: {result['boxed_path']}")
        print(f"  -> JSON : {result['json_path']}")
        if result.get("feedback", {}).get("applied"):
            print(f"  -> Feedback applied: global {result['feedback'].get('global_adjustment')} "
                  f"from {len(result['feedback'].get('label_adjustments', {}))} label signals")

if __name__ == "__main__":
    main()
