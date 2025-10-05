# Runs inference_core_local.py using the virtual environment in this folder.
# Usage examples (from any PowerShell):
#   ./run_inference.ps1
#   ./run_inference.ps1 -Input test_image -OutDir outputs
#   ./run_inference.ps1 -CPU
#   ./run_inference.ps1 -Size 320
#   ./run_inference.ps1 -VenvPath .venv
#   ./run_inference.ps1 -Install
#   ./run_inference.ps1 -Sensitivity 1.5

param(
  [string]$Input = "test_image",
  [string]$OutDir = "outputs",
  [string]$Config = "config/patchcore_transformers.yaml",
  [string]$Ckpt = "model_weights/model.ckpt",
  [int]$Size = 256,
  [double]$Sensitivity = 1.0,
  [switch]$CPU,
  [string]$VenvPath = ".venv",
  [switch]$Install
)

$ErrorActionPreference = "Stop"

# Move to this script's directory so relative paths (config, weights) work
Set-Location -Path $PSScriptRoot

# Create venv if missing
if (-not (Test-Path -Path $VenvPath)) {
  Write-Host "[INFO] Creating virtual environment at '$VenvPath'..."
  python -m venv $VenvPath
}

# Prefer running python directly from the venv (no activation policy issues)
$venvPython = Join-Path $VenvPath "Scripts/python.exe"
if (-not (Test-Path -Path $venvPython)) {
  throw "Python executable not found at $venvPython"
}

if ($Install) {
  Write-Host "[INFO] Upgrading pip and installing dependencies inside venv..."
  & $venvPython -m pip install --upgrade pip setuptools wheel
  # Minimal deps for inference_core_local.py
  & $venvPython -m pip install opencv-python Pillow omegaconf hydra-core timm scikit-learn scikit-image matplotlib pandas tqdm
  # Install torch CPU by default; adjust if you want CUDA
  & $venvPython -m pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cpu
  # Install anomalib (pin for PatchCore-only if desired)
  & $venvPython -m pip install anomalib
}

# Build arguments
$argsList = @(
  "inference_core_local.py",
  "--config", $Config,
  "--ckpt", $Ckpt,
  "--input", $Input,
  "--outdir", $OutDir,
  "--size", $Size,
  "--sensitivity", $Sensitivity
)
if ($CPU) { $argsList += "--cpu" }

Write-Host "[INFO] Using interpreter: $venvPython"
Write-Host "[INFO] Detection sensitivity: $Sensitivity"
Write-Host "[INFO] Running: python $($argsList -join ' ')"

# Run
& $venvPython @argsList

if ($LASTEXITCODE -ne 0) {
  throw "inference_core_local.py exited with code $LASTEXITCODE"
}

Write-Host "[DONE] Inference completed. Check '$OutDir' for results." -ForegroundColor Green
