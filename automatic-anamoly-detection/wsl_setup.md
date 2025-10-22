# 🔹 Full Setup Steps in WSL

### **1. Update Ubuntu and install basics**

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y python3 python3-pip python3-venv git wget unzip dos2unix
```

---

### **2. Create and activate a virtual environment**

```bash
cd /mnt/path/to/your/automatic-anamoly-detection # add path to the automatic-anamoly-detection folder
python3 -m venv .venv
source .venv/bin/activate
```

👉 Every time you open WSL, run:

```bash
source .venv/bin/activate
```

---

### **3. Upgrade pip/setuptools inside venv**

```bash
pip install --upgrade pip setuptools wheel
```

---

### **4. Install required packages**

Minimal set for PatchCore:

```bash
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cpu
pip install opencv-python omegaconf hydra-core python-dotenv
pip install timm scikit-learn scikit-image matplotlib pandas tqdm
```

Then install anomalib (latest):

```bash
pip install anomalib
```

⚠️ If you only need PatchCore and don’t want VLM/WinCLIP deps, you can instead:

```bash
pip install "anomalib==0.6.1"
```

---

### **5. Handle Anomalib’s PatchCore deps**

If you stayed with `anomalib==2.x`, you must also:

```bash
pip install open-clip-torch
```

---

### **6. Check installation**

```bash
python - << 'PY'
import torch, anomalib
from anomalib.models import Patchcore
print("Torch:", torch.__version__, "CUDA:", torch.cuda.is_available())
print("Anomalib:", anomalib.__version__)
print("Patchcore available:", Patchcore is not None)
PY
```

---

### **7. Config file fix**

Your error:

```
FileNotFoundError: configs/patchcore_transformers.yaml not found
```

Solutions:

- Check configs folder:

```bash
ls configs
```

- If missing, create one:

```bash
mkdir -p configs
nano configs/patchcore_transformers.yaml
```

Paste a minimal config (like I gave in my last reply), save, exit.

---

### **8. Run your pipeline**

```bash
python pipeline.py
```

---

### **9. Dataset setup (if needed)**

Most PatchCore configs expect:

```
data/
  train/
    normal/
  test/
    normal/
    abnormal/
```

Put your images accordingly, or update `dataset.root` in YAML.

---

# 🚀 Summary

1. Update + install python3-pip + venv.
2. Create `.venv` and activate it.
3. Upgrade pip/setuptools.
4. Install torch, torchvision, torchaudio.
5. Install support packages (opencv, omegaconf, hydra-core, python-dotenv, timm, scikit-learn, scikit-image, pandas, matplotlib, tqdm).
6. Install anomalib (2.x + open-clip, or pin 0.6.1 for PatchCore-only).
7. Add missing config YAML (`configs/patchcore_transformers.yaml`).
8. Run `pipeline.py`.

---
