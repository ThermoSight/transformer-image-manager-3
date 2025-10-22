# ThermoSight - Transformer Management System

A comprehensive transformer inspection and anomaly detection system that combines thermal image analysis with machine learning for predictive maintenance of electrical transformers.

## 🏗️ System Architecture

### Overview

The system consists of three main components:

1. **Frontend**: React-based web interface for data management and visualization
2. **Backend**: Spring Boot REST API with PostgreSQL database
3. **ML Engine**: PatchCore-based anomaly detection using Python and WSL

### Technology Stack

#### Frontend (`transformer-manager-frontend/`)

- **React 19.1.1** with React Router for SPA navigation
- **Bootstrap 5.3.7** + React Bootstrap for responsive UI
- **Axios** for HTTP client communication
- **Leaflet** for interactive mapping and location selection
- **FontAwesome** for icons and visual elements

#### Backend (`transformer-manager-backkend/`)

- **Spring Boot 3.5.4** with Java 17
- **Spring Security** with JWT authentication
- **Spring Data JPA** with PostgreSQL
- **Jackson** for JSON processing
- **Maven** for dependency management

#### ML Engine (`automatic-anamoly-detection/`)

- **PatchCore** anomaly detection model
- **PyTorch** for deep learning inference
- **OpenCV** for image processing and analysis
- **Python 3** with virtual environment
- **WSL** (Windows Subsystem for Linux) integration

## 🧠 Machine Learning Pipeline

### Two-Stage Anomaly Detection

#### Stage 1: PatchCore Model

- Pre-trained anomaly detection model identifies suspicious areas
- Generates anomaly maps highlighting potential defects
- Creates filtered images focusing on detected anomalies

#### Stage 2: Color-Based Classification

The system analyzes filtered images using HSV color analysis to classify specific defect types:

**Defect Categories:**

- **Point Overload (Faulty)**: Red-colored hotspots (critical) 🔴
- **Point Overload (Potential)**: Yellow-colored warm spots (warning) 🟡
- **Full Wire Overload**: Extensive heating across wire sections
- **Loose Joint (Faulty/Potential)**: Heating at connection points
- **Wire Overload Strips**: Linear heating patterns along wires
- **Tiny Spots**: Small localized heating anomalies

**Confidence Scoring:**

- Based on coverage area (35%), intensity (15%), size ratio (10%), and base confidence (40%)
- Advanced post-processing with Non-Maximum Suppression (NMS)
- Intelligent filtering to remove redundant detections

### Sensitivity Control System

- **Adjustable Detection Sensitivity** (0.1 - 2.0 scale)
- **Persistent Settings** stored in database
- **Real-time Parameter Adjustment** affecting:
  - Detection thresholds for different anomaly types
  - Minimum area requirements for bounding boxes
  - Confidence score calculations
  - Merge distance for nearby detections

### Reinforcement Feedback Loop (New)

User annotations now feed a reinforcement-style update pipeline that keeps the
model aligned with the latest field feedback:

- **Automatic Feedback Capture** – every saved annotation is persisted to a
  JSONL dataset (`automatic-anamoly-detection/Model_Inference/feedback_dataset`).
- **Training Queue** – the backend records a `ModelTrainingRun` entry and, when
  enabled, automatically launches a background job.
- **Python Update Script** – `update_model_from_feedback.py` aggregates the
  dataset, snapshots metrics, copies the latest weights into a new
  `model_versions/<tag>/` folder, embeds a short feedback summary inside the
  checkpoint (when PyTorch is available), and updates `index.json` with a
  changelog.
- **Model Promotion** – on successful runs the freshly generated weights replace
  the live inference checkpoint (`model_weights/model.ckpt`).
- **Audit Trail for Clients** – the React admin view (`/model-training`) surfaces
  run history, appended annotations/boxes, and class/action distributions so you
  can demonstrate measurable model evolution to stakeholders.

## 🗄️ Database Architecture

### Database Schema (PostgreSQL on Neon)

#### Core Entities:

- **`users`**: User accounts with role-based access (ADMIN/USER)
- **`transformer_records`**: Physical transformer data with location info
- **`inspections`**: Inspection sessions with metadata
- **`images`**: Image storage with type classification (Maintenance/Regular)
- **`analysis_jobs`**: ML processing queue with status tracking
- **`ml_settings`**: Persistent ML configuration parameters

#### Analysis Workflow:

1. User uploads maintenance images through inspection interface
2. Images are queued for anomaly analysis (`analysis_jobs` table)
3. Background processor executes WSL-based ML pipeline
4. Results stored as JSON with bounding box coordinates
5. Original image file paths updated to point to analyzed versions

### Cloud Database (Neon PostgreSQL)

- **Serverless PostgreSQL** with automatic scaling
- **512MB storage** on free tier
- **Row-level security** and built-in dashboard
- **Global accessibility** with SSL encryption

## 🔄 System Integration Flow

### Frontend → Backend Communication

```
React Components → Axios HTTP → Spring Boot Controllers → Services → JPA Repositories → PostgreSQL
```

### ML Processing Pipeline

```
1. Frontend uploads image → Backend stores in /uploads
2. Backend creates AnalysisJob → Queue processor detects new job
3. Service copies image to temp directory → Executes WSL command
4. WSL runs inference_core_local.py with current sensitivity settings
5. Python script processes image → Returns JSON results + boxed image
6. Backend parses results → Updates database → Serves boxed image to frontend
```

### WSL Integration Command

```bash
wsl --cd "/mnt/c/.../Model_Inference" -- ./run_inference.sh \
  --venv "/mnt/c/.../automatic-anamoly-detection/.venv" \
  --input "temp_input_dir" \
  --outdir "temp_output_dir" \
  --sensitivity 1.5
```

## 📁 Project Structure

```
transformer-image-manager-2/
├── transformer-manager-frontend/          # React Frontend
│   ├── src/
│   │   ├── components/                    # React Components
│   │   │   ├── InspectionUpload.js       # Image upload interface
│   │   │   ├── MLSensitivityIndicator.js # Real-time sensitivity display
│   │   │   ├── SettingsModal.js          # ML settings configuration
│   │   │   └── MoodleNavbar.js           # Navigation with ML settings
│   │   ├── AuthContext.js                # Authentication context
│   │   ├── SettingsContext.js            # ML settings state management
│   │   └── App.js                        # Main application
│   └── package.json                      # Node.js dependencies
│
├── transformer-manager-backkend/          # Spring Boot Backend
│   ├── src/main/java/com/example/transformer_manager_backkend/
│   │   ├── controller/                   # REST Controllers
│   │   │   ├── AnomalyAnalysisController.java
│   │   │   └── MLSettingsController.java # ML configuration API
│   │   ├── service/                      # Business Logic
│   │   │   ├── AnomalyAnalysisService.java # ML pipeline orchestration
│   │   │   └── MLSettingsService.java    # Persistent ML settings
│   │   ├── entity/                       # JPA Entities
│   │   │   ├── AnalysisJob.java         # ML processing queue
│   │   │   └── MLSettings.java          # ML configuration storage
│   │   └── repository/                   # Data Access Layer
│   └── pom.xml                          # Maven dependencies
│
├── automatic-anamoly-detection/           # ML Engine
│   ├── Model_Inference/                  # Inference Pipeline
│   │   ├── inference_core_local.py      # Main ML processing script
│   │   ├── run_inference.sh             # Linux execution script
│   │   ├── run_inference.ps1            # Windows PowerShell script
│   │   ├── config/                      # Model configuration
│   │   └── model_weights/               # Pre-trained model files
│   ├── ml_model.md                      # ML model documentation
│   └── wsl_setup.md                     # WSL environment setup
│
├── uploads/                             # File storage
│   └── analysis/                        # Processed images with bounding boxes
└── temp/                               # Temporary processing workspace
```

## 🚀 Setup Instructions

### Prerequisites

- **Windows 10/11** with WSL2 enabled
- **Node.js 16+** and npm
- **Java 17** and Maven
- **Python 3.8+** in WSL environment
- **Git** for repository cloning

### 1. Database Setup (Neon PostgreSQL)

```bash
# Database is already configured and mounted on Neon
# Connection details are in application.properties
# No additional setup required - tables auto-create via JPA
```

### 2. Frontend Setup

```bash
cd transformer-manager-frontend
npm install
npm start
# Runs on http://localhost:3000
```

### 3. Backend Setup

```bash
cd transformer-manager-backkend
# Run the main application class
# In IDE: TransformerManagerBackkendApplication.java
# Or via Maven: mvn spring-boot:run
# Runs on http://localhost:8080
```

### 4. ML Environment Setup (WSL)

```bash
# Install WSL2 and Ubuntu
wsl --install

# Inside WSL, navigate to project
cd /mnt/c/path/to/automatic-anamoly-detection

# Create virtual environment
python3 -m venv .venv
source .venv/bin/activate

# Install dependencies
pip install --upgrade pip setuptools wheel
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cpu
pip install opencv-python omegaconf hydra-core timm scikit-learn scikit-image matplotlib pandas tqdm
pip install anomalib

# Verify installation
python -c "import torch, anomalib; print('Setup complete!')"
```

### 5. Model Configuration

```bash
# Ensure model weights and config files are in place
ls automatic-anamoly-detection/Model_Inference/model_weights/model.ckpt
ls automatic-anamoly-detection/Model_Inference/config/patchcore_transformers.yaml
```

## 🔧 Key Features

### User Management

- **Role-based authentication** (Admin/User)
- **JWT-based security** with token persistence
- **Protected routes** and API endpoints

### Transformer Management

- **Geographic location tracking** with interactive maps
- **Detailed transformer records** with specifications
- **Hierarchical inspection organization**

### Advanced ML Integration

- **Real-time sensitivity adjustment** (0.1x - 2.0x)
- **Persistent ML settings** across sessions
- **Queue-based processing** for scalability
- **Automatic confidence scoring** for all detections

### Intelligent Analysis

- **10+ specific defect categories** with precise labeling
- **Bounding box coordinates** for exact anomaly locations
- **Visual result overlay** on original images
- **JSON export** for integration with other systems

## 🔒 Security & Performance

### Authentication

- **JWT tokens** with configurable expiration
- **BCrypt password hashing**
- **CORS protection** for cross-origin requests

### File Handling

- **Secure file upload** with type validation
- **Temporary workspace isolation** for ML processing
- **Automatic cleanup** of processing artifacts

### Scalability

- **Asynchronous ML processing** with queue management
- **Connection pooling** for database efficiency
- **Stateless architecture** for horizontal scaling

## 📊 Monitoring & Logging

### Application Logs

- **Structured logging** with SLF4J
- **ML pipeline traceability** with job tracking
- **Error handling** with user-friendly messages

### Queue Management

- **Real-time status tracking** for ML jobs
- **Position-based queue ordering**
- **Failure recovery** with error messaging

## ⚠️ Known Limitations

### Current Deployment Status

- **Local Development Only**: The system is currently configured for local development and has not been deployed to cloud infrastructure
- **Manual Setup Required**: Each component (Frontend, Backend, ML Environment) requires individual setup and configuration
- **WSL Dependency**: The ML pipeline requires Windows Subsystem for Linux, limiting deployment to Windows environments or requiring containerization for other platforms

### Technical Constraints

- **Single-threaded ML Processing**: Anomaly analysis processes one image at a time through the queue system
- **File Storage**: Images are stored locally in the `/uploads` directory rather than cloud storage solutions
- **Database Scaling**: Using Neon's free tier limits concurrent connections and storage capacity
- **Model Portability**: PatchCore model weights are stored locally and not automatically distributed

### Future Improvements

- **Cloud Deployment**: Migrate to containerized deployment (Docker) with cloud hosting (AWS, Azure, GCP)
- **Distributed Processing**: Implement parallel ML processing for higher throughput
- **Cloud Storage Integration**: Move to S3/Azure Blob Storage for scalable file management
- **CI/CD Pipeline**: Automated testing and deployment workflows
- **Model Versioning**: MLOps pipeline for model updates and A/B testing

---

## 🤝 Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit changes (`git commit -m 'Add AmazingFeature'`)
4. Push to branch (`git push origin feature/AmazingFeature`)
5. Open Pull Request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

---

**ThermoSight Team** - Transforming electrical maintenance through intelligent thermal analysis
