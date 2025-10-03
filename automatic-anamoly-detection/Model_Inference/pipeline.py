import os
import torch
import numpy as np
import cv2
from PIL import Image
from omegaconf import OmegaConf
from anomalib.models import Patchcore

# -------------------------
# Paths
# -------------------------
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
CONFIG_PATH = os.path.join(BASE_DIR, "config", "patchcore_transformers.yaml")
CKPT_PATH = os.path.join(BASE_DIR, "model_weights", "model.ckpt")
TEST_IMAGE = os.path.join(BASE_DIR, "test_image", "test01.jpg")
OUT_PATH = os.path.join(BASE_DIR, "output_image", "test01_labeled.png")
os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)

# -------------------------
# Load Patchcore model
# -------------------------
print("🔹 Loading Patchcore model...")
config = OmegaConf.load(CONFIG_PATH)
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

model = Patchcore.load_from_checkpoint(CKPT_PATH, **config.model.init_args)
model.eval()
model = model.to(device)
print("✅ Model loaded.")

# -------------------------
# Run inference and create filtered image
# -------------------------
print("🔹 Running inference...")
img = Image.open(TEST_IMAGE).convert("RGB")
img_tensor = torch.tensor(np.array(img)).permute(2,0,1).unsqueeze(0).float()/255.0
img_tensor = img_tensor.to(device)

with torch.no_grad():
    output = model(img_tensor)
    if hasattr(output, 'anomaly_map'):
        anomaly_map = output.anomaly_map.squeeze().cpu().numpy()
    elif isinstance(output, (tuple, list)) and len(output) > 1:
        anomaly_map = output[1].squeeze().cpu().numpy()
    else:
        raise RuntimeError("No anomaly map generated from model.")

# Normalize anomaly map
norm_map = (255 * (anomaly_map - anomaly_map.min()) / (np.ptp(anomaly_map) + 1e-8)).astype(np.uint8)
mask_img = Image.fromarray(norm_map).resize(img.size, resample=Image.BILINEAR)

# Filtered image
bin_mask = np.array(mask_img) > 128
orig_np = np.array(img)
filtered_img = np.zeros_like(orig_np)
filtered_img[bin_mask] = orig_np[bin_mask]

# -------------------------
# Heuristic labeling
# -------------------------
def classify_anomalies(filtered_img):
    hsv = cv2.cvtColor(filtered_img, cv2.COLOR_RGB2HSV)
    h, w = filtered_img.shape[:2]
    total = h*w

    # Color masks
    blue_mask = cv2.inRange(hsv,(90,50,20),(130,255,255))
    black_mask = cv2.inRange(hsv,(0,0,0),(180,255,50))
    yellow_mask = cv2.inRange(hsv,(20,130,130),(35,255,255))
    orange_mask = cv2.inRange(hsv,(10,100,100),(25,255,255))
    red_mask1 = cv2.inRange(hsv,(0,100,100),(10,255,255))
    red_mask2 = cv2.inRange(hsv,(160,100,100),(180,255,255))
    red_mask = cv2.bitwise_or(red_mask1, red_mask2)

    label_list = []
    box_list = []

    # -------------------------
    # Full Wire Overload (Potentially Faulty)
    # -------------------------
    if (red_mask.sum() + orange_mask.sum() + yellow_mask.sum()) / total > 0.7:
        label_list.append("Full Wire Overload")
        box_list.append((0,0,w,h))

    # -------------------------
    # Spot detection (Point Overload)
    # -------------------------
    min_area_faulty = 120
    min_area_potential = 180
    max_area = 0.05*total

    for mask, spot_label, min_a in [
        (red_mask, "Point Overload (Faulty)", min_area_faulty),
        (yellow_mask, "Point Overload (Potential)", min_area_potential)
    ]:
        contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        for cnt in contours:
            area = cv2.contourArea(cnt)
            if min_a < area < max_area:
                x,y,wb,hb = cv2.boundingRect(cnt)
                box_list.append((x,y,wb,hb))
                label_list.append(spot_label)

    # -------------------------
    # Loose Joint detection
    # -------------------------
    # Only check center quarter area for hotspots
    center = filtered_img[h//4:3*h//4, w//4:3*w//4]
    center_hsv = cv2.cvtColor(center, cv2.COLOR_RGB2HSV)
    center_yellow = cv2.inRange(center_hsv,(20,130,130),(35,255,255))
    center_orange = cv2.inRange(center_hsv,(10,100,100),(25,255,255))
    center_red1 = cv2.inRange(center_hsv,(0,100,100),(10,255,255))
    center_red2 = cv2.inRange(center_hsv,(160,100,100),(180,255,255))
    center_red = cv2.bitwise_or(center_red1, center_red2)
    loose_mask_faulty = cv2.bitwise_or(center_red, center_orange)
    loose_mask_potential = center_yellow

    # Contours for Loose Joint Faulty
    contours, _ = cv2.findContours(loose_mask_faulty, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    for cnt in contours:
        if cv2.contourArea(cnt) > 50:  # small threshold for actual hotspot
            x,y,wb,hb = cv2.boundingRect(cnt)
            # Adjust coordinates relative to full image
            box_list.append((x + w//4, y + h//4, wb, hb))
            label_list.append("Loose Joint (Faulty)")

    # Contours for Loose Joint Potential
    contours, _ = cv2.findContours(loose_mask_potential, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    for cnt in contours:
        if cv2.contourArea(cnt) > 50:
            x,y,wb,hb = cv2.boundingRect(cnt)
            box_list.append((x + w//4, y + h//4, wb, hb))
            label_list.append("Loose Joint (Potential)")

    # -------------------------
    # Normal if no boxes
    # -------------------------
    if len(box_list) == 0:
        return "Normal", [], []
    return None, box_list, label_list

# -------------------------
# Draw boxes and save
# -------------------------
image_label, box_list, label_list = classify_anomalies(filtered_img)

for (x,y,wb,hb), l in zip(box_list,label_list):
    if "Potential" in l or "Full Wire Overload" in l:
        color_box = (0,255,255)  # yellow box
        color_text = (0,0,0)     # black text
    else:
        color_box = (0,0,255)    # red box
        color_text = (0,255,255) # yellow text
    cv2.rectangle(filtered_img,(x,y),(x+wb,y+hb),color_box,2)
    cv2.putText(filtered_img,l,(x,y-10),cv2.FONT_HERSHEY_SIMPLEX,0.6,color_text,2)

# Normal label at top-left if no boxes
if not box_list:
    cv2.putText(filtered_img,"Normal",(10,30),cv2.FONT_HERSHEY_SIMPLEX,1,(0,255,255),2)

cv2.imwrite(OUT_PATH, cv2.cvtColor(filtered_img, cv2.COLOR_RGB2BGR))
print(f"✅ Labeled image saved at {OUT_PATH}")
