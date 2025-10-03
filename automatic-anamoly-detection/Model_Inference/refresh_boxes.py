#!/usr/bin/env python3
"""
refresh_boxes.py
Redraws boxed image from an editable JSON produced by inference_core_local.py.

Usage:
  # Single JSON
  python refresh_boxes.py --json outputs/boxed/test01.json

  # Or refresh ALL JSONs in a directory
  python refresh_boxes.py --json-dir outputs/boxed
"""

import os
import json
import glob
import argparse
import cv2

def color_for_label(label):
    # Same color rules as inference script (BGR)
    if "Potential" in label or "Full Wire Overload" in label:
        return (0, 255, 255)  # yellow
    else:
        return (0, 0, 255)    # red

def refresh_one(json_path):
    with open(json_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    image_path = data.get("image")
    if not image_path or not os.path.exists(image_path):
        raise FileNotFoundError(f"Original image not found (field 'image'): {image_path}")

    # output path: same dir as JSON, named <base>_boxed.<ext> (or .png fallback)
    base = os.path.splitext(os.path.basename(image_path))[0]
    ext = os.path.splitext(os.path.basename(image_path))[1] or ".png"
    out_dir = os.path.dirname(os.path.abspath(json_path))
    out_boxed_path = os.path.join(out_dir, f"{base}_boxed{ext}")

    img = cv2.imread(image_path)
    if img is None:
        raise FileNotFoundError(f"Could not read original image: {image_path}")

    boxes = data.get("boxes", [])
    label = data.get("label", "Unknown")

    # Draw boxes from JSON
    for entry in boxes:
        box = entry.get("box", None)
        l   = entry.get("type", "Unknown")
        conf= entry.get("confidence", None)
        if not box or len(box) != 4:
            continue
        x, y, w, h = [int(v) for v in box]
        cv2.rectangle(img, (x, y), (x + w, y + h), color_for_label(l), 2)
        if conf is not None:
            text = f"{l} ({float(conf):.2f})"
        else:
            text = l
        cv2.putText(img, text, (x, max(0, y - 10)), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 255), 2)

    # If no boxes section, still write the top-left label for clarity
    if not boxes:
        cv2.putText(img, label, (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 255), 2)

    ok = cv2.imwrite(out_boxed_path, img)
    if not ok:
        out_boxed_path = os.path.join(out_dir, f"{base}_boxed.png")
        cv2.imwrite(out_boxed_path, img)

    print(f"[REFRESH] {os.path.basename(json_path)} -> {out_boxed_path}")

def main():
    parser = argparse.ArgumentParser(description="Refresh boxed images from editable JSON")
    parser.add_argument("--json", type=str, help="Path to a single JSON file")
    parser.add_argument("--json-dir", type=str, help="Directory containing JSONs")
    args = parser.parse_args()

    if not args.json and not args.json_dir:
        parser.error("Provide --json or --json-dir")

    if args.json:
        if not os.path.exists(args.json):
            raise FileNotFoundError(args.json)
        refresh_one(args.json)
        return

    # Directory mode
    json_files = sorted(glob.glob(os.path.join(args.json_dir, "*.json")))
    if not json_files:
        print(f"No JSON files in {args.json_dir}")
        return
    for jp in json_files:
        refresh_one(jp)

if __name__ == "__main__":
    main()

"""
Run the code: 
python refresh_boxes.py --json ./outputs/boxed/test01.json
# or
python refresh_boxes.py --json-dir ./outputs/boxed
"""