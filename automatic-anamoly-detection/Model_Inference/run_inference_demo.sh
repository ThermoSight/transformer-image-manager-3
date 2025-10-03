#!/usr/bin/env bash
# Demo script to test the anomaly analysis integration
# This will create directories and some placeholder output for testing

set -euo pipefail

echo "=== ThermoSight Anomaly Analysis Demo ==="
echo "This demo simulates the anomaly analysis process for testing the integration"

# Parse arguments
INPUT_DIR=""
OUTPUT_DIR=""
VENV_PATH=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --venv) VENV_PATH="$2"; shift 2;;
        --input) INPUT_DIR="$2"; shift 2;;
        --outdir) OUTPUT_DIR="$2"; shift 2;;
        *) shift;;
    esac
done

if [[ -z "$INPUT_DIR" || -z "$OUTPUT_DIR" ]]; then
    echo "Error: --input and --outdir are required"
    exit 1
fi

echo "[DEMO] VENV Path: $VENV_PATH"
echo "[DEMO] Input Dir: $INPUT_DIR"
echo "[DEMO] Output Dir: $OUTPUT_DIR"

# Create output directories
mkdir -p "$OUTPUT_DIR/masks"
mkdir -p "$OUTPUT_DIR/filtered"
mkdir -p "$OUTPUT_DIR/boxed"

# Find images in input directory
processed=0
find "$INPUT_DIR" -type f \( -iname "*.jpg" -o -iname "*.jpeg" -o -iname "*.png" \) | while read -r IMAGE_PATH; do
    BASENAME=$(basename "$IMAGE_PATH")
    BASENAME_NO_EXT="${BASENAME%.*}"
    
    echo "[DEMO] Processing: $IMAGE_PATH"
    
    # Create dummy mask (copy original image)
    cp "$IMAGE_PATH" "$OUTPUT_DIR/masks/${BASENAME_NO_EXT}_mask.png"
    
    # Create dummy filtered image
    cp "$IMAGE_PATH" "$OUTPUT_DIR/filtered/${BASENAME_NO_EXT}_filtered.png"
    
    # Create dummy boxed image  
    cp "$IMAGE_PATH" "$OUTPUT_DIR/boxed/${BASENAME_NO_EXT}_boxed.jpg"
    
    # Create JSON result with demo anomaly detection results
    cat > "$OUTPUT_DIR/boxed/${BASENAME_NO_EXT}.json" << EOF
{
  "image": "$IMAGE_PATH",
  "boxed_image": "$OUTPUT_DIR/boxed/${BASENAME_NO_EXT}_boxed.jpg",
  "mask_image": "$OUTPUT_DIR/masks/${BASENAME_NO_EXT}_mask.png",
  "filtered_image": "$OUTPUT_DIR/filtered/${BASENAME_NO_EXT}_filtered.png",
  "label": "Point Overload (Faulty)",
  "boxes": [
    {
      "box": [150, 200, 80, 120],
      "type": "Point Overload (Faulty)",
      "confidence": 0.87
    },
    {
      "box": [300, 150, 60, 90],
      "type": "Point Overload (Potential)",
      "confidence": 0.65
    }
  ]
}
EOF

    echo "[DEMO]   -> Created mask: $OUTPUT_DIR/masks/${BASENAME_NO_EXT}_mask.png"
    echo "[DEMO]   -> Created filtered: $OUTPUT_DIR/filtered/${BASENAME_NO_EXT}_filtered.png"
    echo "[DEMO]   -> Created boxed: $OUTPUT_DIR/boxed/${BASENAME_NO_EXT}_boxed.jpg"
    echo "[DEMO]   -> Created JSON: $OUTPUT_DIR/boxed/${BASENAME_NO_EXT}.json"
    
    processed=$((processed + 1))
done

echo "[DEMO] Processed $processed images"
echo "[DEMO] Demo analysis completed successfully!"
echo "[DEMO] Check the output directory: $OUTPUT_DIR"

exit 0