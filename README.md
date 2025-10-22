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

## 📝 Interactive Annotation System

The ThermoSight system includes a sophisticated annotation system that allows users to interactively edit, validate, and improve ML-generated anomaly detections. This system serves both quality assurance and model training data generation purposes.

### 🎯 Core Components

#### 1. Interactive Annotation Editor (`InteractiveAnnotationEditor.js`)

A full-featured visual editor built with HTML5 Canvas that provides:

- **Canvas-based Drawing Interface**: Direct manipulation of bounding boxes on thermal images
- **Multi-mode Interaction**: Create new annotations, edit existing ones, or delete unwanted detections
- **Real-time Visual Feedback**: Immediate updates as users modify annotations
- **Fullscreen Support**: Expandable interface for detailed annotation work

#### 2. Annotation Data Management

**Backend Entity Structure** (`Annotation.java`):
```java
// Core annotation tracking
- originalResultJson: AI-generated detections (immutable)
- modifiedResultJson: User-edited version (versioned)
- annotationBoxes: Individual bounding box data
- annotationType: ADDED, EDITED, DELETED, VALIDATED
- comments: User feedback and notes
- annotatedByUser/Admin: Attribution tracking
```

**Database Schema**:
- `annotations` table: Main annotation records with versioning
- `annotation_boxes` table: Individual bounding box coordinates and metadata
- Foreign key relationships to `analysis_jobs` and `users`/`admins`

### 🔧 How to Use the Annotation System

#### Step 1: Access the Annotation Editor

1. **Complete ML Analysis**: Upload a thermal image and run anomaly detection
2. **View Results**: Once analysis completes, you'll see detected anomalies with bounding boxes
3. **Open Editor**: Click the **"Edit Annotations"** button on any completed analysis result
4. The Interactive Annotation Editor modal will open showing the analyzed image

#### Step 2: Understanding the Interface

**Main Areas**:
- **Canvas Area**: Displays the thermal image with overlay annotations
- **Toolbar**: Contains drawing tools, undo/redo, and annotation type selector
- **Properties Panel**: Shows details of selected annotations and overall statistics
- **Status Indicators**: Color-coded badges showing AI-generated vs user-added annotations

**Visual Indicators**:
- 🔴 **Red boxes**: AI-generated anomaly detections
- 🟢 **Green boxes**: User-added annotations
- 🟡 **Yellow boxes**: Potential (warning-level) anomalies
- ⚪ **White border**: Currently selected annotation

#### Step 3: Editing Annotations

**Creating New Annotations**:
1. Select annotation type from toolbar dropdown (e.g., "Loose Joint (Faulty)")
2. Click and drag on the image to draw a new bounding box
3. The new annotation appears immediately with a green border

**Modifying Existing Annotations**:
1. Click on any existing bounding box to select it
2. **Move**: Click and drag the box to reposition
3. **Resize**: Use corner handles to adjust box dimensions
4. **Change Type**: Use the "Change Type" dropdown to reclassify the anomaly
5. **Add Comments**: Use the properties panel to add notes about the annotation

**Deleting Annotations**:
1. Select the unwanted annotation by clicking on it
2. Click the trash icon (🗑️) in the toolbar
3. The annotation is immediately removed

#### Step 4: Advanced Features

**Undo/Redo Operations**:
- Use undo (↶) and redo (↷) buttons to reverse recent changes
- Full history tracking maintains all editing steps

**Annotation Types Available**:
- **Loose Joint (Faulty)**: Critical connection heating
- **Point Overload (Faulty)**: Severe localized overheating  
- **Full Wire Overload (Faulty)**: Extensive wire heating
- **Tiny Faulty Spot**: Small critical hotspots
- **Tiny Potential Spot**: Minor warning areas
- **Custom Anomaly**: User-defined categories

**Comments System**:
- **Per-Box Comments**: Add specific notes to individual annotations
- **Overall Comments**: General observations about the entire analysis
- Comments are preserved and exported with annotation data

#### Step 5: Saving and Exporting

**Save Annotations**:
1. Click **"Save Annotations"** to persist all changes
2. The system updates both the database and the displayed image
3. Success confirmation appears before auto-closing the editor

**Export Options**:
- **JSON Report**: Complete annotation data with coordinates and metadata
- **Training Data**: Formatted for ML model retraining
- **Audit Trail**: Full history of changes with timestamps and user attribution

### 🔄 Integration with ML Pipeline

#### Model Feedback Loop

**Data Collection**:
- User modifications are tracked as feedback signals
- **Model Feedback Service** (`ModelFeedbackService.java`) analyzes annotation patterns
- Confidence adjustments calculated based on user corrections

**Feedback Application**:
```java
// Example feedback structure
{
  "global_adjustment": -0.023,
  "learning_rate": 0.001,
  "per_box": [
    {
      "label": "Point Overload (Faulty)",
      "original_confidence": 0.85,
      "adjusted_confidence": 0.78,
      "adjustment": -0.07
    }
  ]
}
```

**Continuous Learning**:
- User corrections influence future detection sensitivity
- Popular annotation patterns improve model accuracy
- Feedback accumulates across all user interactions

#### Quality Assurance Workflow

1. **Initial Detection**: AI generates preliminary anomaly detections
2. **Human Review**: Expert users validate and correct annotations  
3. **Feedback Integration**: Corrections influence model parameters
4. **Improved Accuracy**: Subsequent analyses benefit from accumulated feedback
5. **Export Training Data**: Validated annotations can retrain the base model

### 📊 Annotation Analytics

**Real-time Statistics**:
- Total annotations count (AI + user-added)
- Breakdown by annotation type and confidence levels
- User activity tracking and contribution metrics

**Data Export Formats**:

**Standard JSON Export**:
```json
{
  "analysis_job_id": 123,
  "original_detections": [...],
  "user_modifications": [...],
  "final_annotations": [
    {
      "type": "Point Overload (Faulty)",
      "confidence": 0.89,
      "coordinates": {"x": 150, "y": 200, "width": 45, "height": 30},
      "source": "AI_GENERATED",
      "modified": false,
      "comments": "Confirmed critical hotspot"
    }
  ],
  "metadata": {
    "annotated_by": "expert_user",
    "timestamp": "2025-10-22T10:30:00Z",
    "total_time_spent": "00:05:30"
  }
}
```

## Overview of Feedback Integration 

This module captures every annotation, compares it to the model output, and applies small, explainable confidence adjustments per fault label.

### How It Works
- **User Annotations:**  
  Engineers can add, edit, resize, or delete anomaly boxes in the image viewer.  
  Each change is automatically saved to the backend with both the AI’s original JSON (`originalResultJson`) and the user-corrected JSON (`modifiedResultJson`).

- **Backend Aggregation:**  
  The backend service (`ModelFeedbackService`) compares AI and human annotations and calculates three deltas per label:  
  - **Count change:** how many boxes were added or removed  
  - **Area change:** how much total annotated area grew or shrank  
  - **Confidence change:** how humans adjusted model certainty  

  These signals are combined and scaled by a configurable **learning rate** (e.g., 0.0001 = 0.01 %) to create a per-label bias.  
  Each bias is updated smoothly using an exponential moving average (EMA).

- **Confidence Adjustment:**  
  During inference, each detection’s confidence is gently adjusted using its bias:  
  - Positive bias → increases confidence (model was under-sensitive)  
  - Negative bias → decreases confidence (model was over-confident)  

- **Global Confidence Bias:**  
  The average of all label biases provides a single numeric trend indicator displayed in the UI:  
  - Positive → model is too conservative  
  - Negative → model is too confident  
  - Near 0 → model and humans agree  

- **Learning Rate Control:**  
  Users can tune how strongly the model responds to feedback:  
  - Very low (0.00001) – minimal effect, slow adaptation  
  - Default (0.00010) – gentle, audit-friendly updates  
  - Moderate (0.001) – faster adaptation  
  - High (>0.01) – aggressive biasing, may cause instability  

- **User Interface:**  
  The ML Settings page displays the current learning rate, global confidence bias, and per-label impact.  
  Saving settings instantly updates backend parameters.

### Result
- Human corrections are automatically stored and analyzed.  
- Per-label biases continuously align the AI with expert judgment.  
- The system adapts in real time without retraining.  
- All feedback snapshots are versioned for later auditing or model retraining.
**For a more detailed explanation, see the [FEEDBACK_INTEGRATION.md](./docs/FEEDBACK_INTEGRATION.md) file.**
---

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
│   │   │   ├── AnalysisDisplay.js        # ML results viewer with annotation access
│   │   │   ├── InteractiveAnnotationEditor.js # Canvas-based annotation editor
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
│   │   │   ├── AnnotationController.java # Annotation CRUD and export APIs
│   │   │   ├── AnomalyAnalysisController.java
│   │   │   └── MLSettingsController.java # ML configuration API
│   │   ├── service/                      # Business Logic
│   │   │   ├── AnnotationService.java    # Annotation management logic
│   │   │   ├── ModelFeedbackService.java # ML feedback integration
│   │   │   ├── AnomalyAnalysisService.java # ML pipeline orchestration
│   │   │   └── MLSettingsService.java    # Persistent ML settings
│   │   ├── entity/                       # JPA Entities
│   │   │   ├── Annotation.java          # Main annotation record
│   │   │   ├── AnnotationBox.java       # Individual bounding box data
│   │   │   ├── AnalysisJob.java         # ML processing queue
│   │   │   └── MLSettings.java          # ML configuration storage
│   │   └── repository/                   # Data Access Layer
│   │       ├── AnnotationRepository.java # Annotation data access
│   │       └── AnnotationBoxRepository.java # Bounding box operations
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
    └── anomaly-analysis/               # Annotation session workspaces
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

### Interactive Annotation System

- **Visual annotation editor** with canvas-based drawing interface
- **Real-time bounding box editing** with drag, resize, and move operations
- **AI-generated and user-added annotations** with clear distinction
- **Comprehensive annotation management** with undo/redo functionality
- **Export capabilities** for training data generation
- **Feedback integration** for continuous model improvement

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

## 📚 Quick Reference

### Annotation System API Endpoints

```bash
# Get or create annotation for analysis job
GET /api/annotations/analysis-job/{analysisJobId}

# Update annotation with user edits
PUT /api/annotations/{annotationId}
Body: {
  "boxes": [
    {
      "x": 150, "y": 200, "width": 45, "height": 30,
      "type": "Point Overload (Faulty)",
      "confidence": 0.89,
      "action": "MODIFIED",
      "comments": "Confirmed critical hotspot"
    }
  ],
  "comments": "Overall inspection notes"
}

# Export annotation report as JSON
GET /api/annotations/analysis-job/{analysisJobId}/export

# Get all annotations for an inspection
GET /api/annotations/inspection/{inspectionId}

# Export feedback log (Admin only)
GET /api/annotations/feedback-log/export
```

### Annotation Keyboard Shortcuts

- **Ctrl+Z**: Undo last action
- **Ctrl+Y**: Redo last action  
- **Delete**: Remove selected annotation
- **Escape**: Deselect current annotation
- **F11**: Toggle fullscreen mode

### Color Code Reference

- 🔴 **Red**: AI-generated critical anomalies
- 🟡 **Yellow**: AI-generated potential issues
- 🟢 **Green**: User-added annotations
- ⚪ **White border**: Currently selected annotation
- 🔵 **Blue handles**: Resize control points

---

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
