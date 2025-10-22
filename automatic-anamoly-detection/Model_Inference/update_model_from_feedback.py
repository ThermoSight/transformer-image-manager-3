#!/usr/bin/env python3
"""
Lightweight reinforcement-style feedback integration script.

This utility consumes the JSONL feedback dataset produced by the backend and
creates a new model version directory with aggregated statistics. The intent is
not to perform heavyweight training in-place but to keep a reproducible audit
trail that can be demonstrated to stakeholders.
"""

import argparse
import json
import os
import shutil
import statistics
import sys
from collections import Counter
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List

try:
    import torch  # type: ignore
except Exception:  # pragma: no cover - torch optional
    torch = None


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Update model using feedback annotations")
    parser.add_argument("--dataset-json", required=True, help="Path to feedback JSONL dataset")
    parser.add_argument("--images-dir", required=True, help="Directory containing copied feedback images")
    parser.add_argument("--base-model", required=True, help="Path to current baseline model file")
    parser.add_argument("--versions-root", required=True, help="Directory where versioned models are stored")
    parser.add_argument("--result-file", required=True, help="Path to write training result JSON")
    parser.add_argument("--run-id", required=True, help="Unique identifier for this run")
    parser.add_argument("--notes", default="", help="Optional notes to embed in summary")
    return parser.parse_args()


def load_records(dataset_path: Path) -> List[Dict[str, Any]]:
    if not dataset_path.exists():
        return []

    records: List[Dict[str, Any]] = []
    with dataset_path.open("r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            try:
                records.append(json.loads(line))
            except json.JSONDecodeError:
                continue
    return records


def load_index(index_path: Path) -> Dict[str, Any]:
    if not index_path.exists():
        return {
            "versions": [],
            "latest": None,
            "dataset_record_count": 0,
            "box_count": 0,
        }
    try:
        with index_path.open("r", encoding="utf-8") as handle:
            data = json.load(handle)
            data.setdefault("versions", [])
            data.setdefault("latest", None)
            data.setdefault("dataset_record_count", 0)
            data.setdefault("box_count", 0)
            return data
    except json.JSONDecodeError:
        return {
            "versions": [],
            "latest": None,
            "dataset_record_count": 0,
            "box_count": 0,
        }


def compute_stats(records: List[Dict[str, Any]]) -> Dict[str, Any]:
    box_areas: List[float] = []
    confidences: List[float] = []
    class_counts: Counter[str] = Counter()
    action_counts: Counter[str] = Counter()

    for record in records:
        boxes = record.get("boxes", [])
        for box in boxes:
            width = max(int(box.get("width", 0)), 0)
            height = max(int(box.get("height", 0)), 0)
            area = width * height
            if area > 0:
                box_areas.append(area)
            if isinstance(box.get("confidence"), (int, float)):
                confidences.append(float(box["confidence"]))
            label = box.get("type", "Unknown")
            class_counts[label] += 1
            action = box.get("action", "UNCHANGED")
            action_counts[action] += 1

    stats = {
        "avg_box_area": statistics.mean(box_areas) if box_areas else 0.0,
        "min_box_area": min(box_areas) if box_areas else 0.0,
        "max_box_area": max(box_areas) if box_areas else 0.0,
        "avg_confidence": statistics.mean(confidences) if confidences else None,
        "class_counts": dict(class_counts),
        "action_counts": dict(action_counts),
    }
    return stats


def ensure_directory(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True)


def next_version_tag(index_data: Dict[str, Any]) -> str:
    existing = index_data.get("versions", [])
    next_sequence = 1
    if existing:
        last = existing[-1]
        next_sequence = int(last.get("sequence", len(existing))) + 1
    return f"v{next_sequence:04d}"


def write_markdown_summary(summary_path: Path, context: Dict[str, Any]) -> None:
    lines = [
        f"# Model Update {context['version_tag']}",
        "",
        f"Run ID: `{context['run_id']}`",
        f"Created at: {context['created_at']}",
        "",
        "## Snapshot",
        f"* Feedback records processed: {context['dataset_record_count']}",
        f"* Boxes evaluated: {context['box_count']}",
        f"* Newly appended annotations: {context['appended_annotations']}",
        f"* Newly appended boxes: {context['appended_boxes']}",
        "",
        "## Class distribution",
    ]
    if context.get("class_counts"):
        for label, count in context["class_counts"].items():
            lines.append(f"* {label}: {count}")
    else:
        lines.append("* (no classes recorded)")

    lines.extend([
        "",
        "## Action distribution",
    ])
    if context.get("action_counts"):
        for label, count in context["action_counts"].items():
            lines.append(f"* {label}: {count}")
    else:
        lines.append("* (no actions recorded)")

    lines.extend([
        "",
        "## Box geometry",
        f"* Average area: {context['avg_box_area']:.2f}",
        f"* Min area: {context['min_box_area']:.2f}",
        f"* Max area: {context['max_box_area']:.2f}",
    ])

    if context.get("avg_confidence") is not None:
        lines.append(f"* Average confidence: {context['avg_confidence']:.3f}")

    if context.get("notes"):
        lines.extend(["", "## Notes", context["notes"]])

    summary_path.write_text("\n".join(lines), encoding="utf-8")


def clone_and_annotate_model(base_model_path: Path, model_output_path: Path, metadata: Dict[str, Any]) -> bool:
    if torch is None:
        shutil.copy2(base_model_path, model_output_path)
        return False

    try:
        checkpoint = torch.load(base_model_path, map_location="cpu")
    except Exception:
        shutil.copy2(base_model_path, model_output_path)
        return False

    if isinstance(checkpoint, dict):
        history_key = "feedback_history"
        history = checkpoint.get(history_key)
        if not isinstance(history, list):
            history = []
        history.append(metadata)
        checkpoint[history_key] = history[-20:]
        checkpoint["last_feedback_update"] = metadata.get("created_at")
        checkpoint["last_feedback_version"] = metadata.get("version_tag")
        checkpoint["last_feedback_summary"] = metadata.get("summary")
        try:
            torch.save(checkpoint, model_output_path)
            return True
        except Exception:
            shutil.copy2(base_model_path, model_output_path)
            return False

    shutil.copy2(base_model_path, model_output_path)
    return False


def main() -> int:
    args = parse_args()

    dataset_path = Path(args.dataset_json).expanduser().resolve()
    versions_root = Path(args.versions_root).expanduser().resolve()
    base_model_path = Path(args.base_model).expanduser().resolve()
    result_path = Path(args.result_file).expanduser().resolve()
    notes = args.notes.strip()

    ensure_directory(result_path.parent)
    ensure_directory(versions_root)

    records = load_records(dataset_path)
    total_records = len(records)
    total_boxes = sum(len(r.get("boxes", [])) for r in records)

    index_path = versions_root / "index.json"
    index_data = load_index(index_path)
    previous_records = int(index_data.get("dataset_record_count", 0))
    previous_boxes = int(index_data.get("box_count", 0))

    appended_records = max(total_records - previous_records, 0)
    appended_boxes = max(total_boxes - previous_boxes, 0)

    timestamp = datetime.utcnow().isoformat() + "Z"

    if total_records == 0 or appended_records == 0:
        result = {
            "status": "skipped",
            "message": "No new annotated feedback available",
            "run_id": args.run_id,
            "created_at": timestamp,
            "dataset_record_count": total_records,
            "previous_dataset_count": previous_records,
            "box_count": total_boxes,
            "previous_box_count": previous_boxes,
            "appended_annotations": 0,
            "appended_boxes": 0,
            "notes": notes,
        }
        result_path.write_text(json.dumps(result, indent=2), encoding="utf-8")
        return 0

    if not base_model_path.exists():
        result = {
            "status": "failed",
            "message": f"Base model not found at {str(base_model_path)}",
            "run_id": args.run_id,
            "created_at": timestamp,
            "dataset_record_count": total_records,
            "previous_dataset_count": previous_records,
            "box_count": total_boxes,
            "previous_box_count": previous_boxes,
            "appended_annotations": appended_records,
            "appended_boxes": appended_boxes,
            "notes": notes,
        }
        result_path.write_text(json.dumps(result, indent=2), encoding="utf-8")
        return 0

    version_tag = next_version_tag(index_data)
    version_dir = versions_root / version_tag
    ensure_directory(version_dir)

    model_filename = base_model_path.name or "model.ckpt"
    model_output_path = version_dir / model_filename

    stats = compute_stats(records)

    new_records = records[-appended_records:] if appended_records <= len(records) else records
    annotation_ids = [record.get("annotationId") for record in new_records]

    summary_text = (
        f"Run {args.run_id} appended {appended_records} annotations and {appended_boxes} boxes"
    )

    metadata = {
        "run_id": args.run_id,
        "version_tag": version_tag,
        "created_at": timestamp,
        "appended_annotations": appended_records,
        "appended_boxes": appended_boxes,
        "dataset_record_count": total_records,
        "box_count": total_boxes,
        "notes": notes,
        "annotation_ids": annotation_ids,
        "summary": summary_text,
    }

    embedded_feedback = clone_and_annotate_model(base_model_path, model_output_path, metadata)

    version_context = {
        "version_tag": version_tag,
        "run_id": args.run_id,
        "created_at": timestamp,
        "dataset_record_count": total_records,
        "previous_dataset_count": previous_records,
        "box_count": total_boxes,
        "previous_box_count": previous_boxes,
        "appended_annotations": appended_records,
        "appended_boxes": appended_boxes,
        "class_counts": stats.get("class_counts", {}),
        "action_counts": stats.get("action_counts", {}),
        "avg_box_area": stats.get("avg_box_area", 0.0),
        "min_box_area": stats.get("min_box_area", 0.0),
        "max_box_area": stats.get("max_box_area", 0.0),
        "avg_confidence": stats.get("avg_confidence"),
        "annotation_ids": annotation_ids,
        "notes": notes,
        "summary": summary_text,
    }

    summary_payload = {
        "class_counts": stats.get("class_counts", {}),
        "action_counts": stats.get("action_counts", {}),
        "avg_box_area": stats.get("avg_box_area", 0.0),
        "min_box_area": stats.get("min_box_area", 0.0),
        "max_box_area": stats.get("max_box_area", 0.0),
        "avg_confidence": stats.get("avg_confidence"),
        "dataset_record_count": total_records,
        "box_count": total_boxes,
        "embedded_feedback": embedded_feedback,
        "summary": summary_text,
    }

    (version_dir / "metrics.json").write_text(json.dumps(summary_payload, indent=2), encoding="utf-8")

    write_markdown_summary(version_dir / "model_summary.md", version_context)

    index_entry = {
        "version_tag": version_tag,
        "sequence": len(index_data.get("versions", [])) + 1,
        "created_at": timestamp,
        "dataset_record_count": total_records,
        "box_count": total_boxes,
        "appended_annotations": appended_records,
        "appended_boxes": appended_boxes,
        "run_id": args.run_id,
        "notes": notes,
        "summary": summary_text,
        "embedded_feedback": embedded_feedback,
    }
    index_data.setdefault("versions", []).append(index_entry)
    index_data["latest"] = version_tag
    index_data["dataset_record_count"] = total_records
    index_data["box_count"] = total_boxes

    index_path.write_text(json.dumps(index_data, indent=2), encoding="utf-8")

    result = {
        "status": "ok",
        "message": "Model version created",
        "version_tag": version_tag,
        "run_id": args.run_id,
        "created_at": timestamp,
        "dataset_record_count": total_records,
        "previous_dataset_count": previous_records,
        "box_count": total_boxes,
        "previous_box_count": previous_boxes,
        "appended_annotations": appended_records,
        "appended_boxes": appended_boxes,
        "class_counts": stats.get("class_counts", {}),
        "action_counts": stats.get("action_counts", {}),
        "avg_box_area": stats.get("avg_box_area", 0.0),
        "min_box_area": stats.get("min_box_area", 0.0),
        "max_box_area": stats.get("max_box_area", 0.0),
        "avg_confidence": stats.get("avg_confidence"),
        "annotation_ids": annotation_ids,
        "model_path": str(model_output_path.resolve()),
        "relative_model_path": os.path.relpath(model_output_path.resolve(), versions_root),
        "version_directory": str(version_dir.resolve()),
        "notes": notes,
        "summary": summary_text,
        "embedded_feedback": embedded_feedback,
    }
    result_path.write_text(json.dumps(result, indent=2), encoding="utf-8")

    return 0


if __name__ == "__main__":
    sys.exit(main())
