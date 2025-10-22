# ThermoSight - Transformer Management System with Anomaly Analysis

This system integrates anomaly detection capabilities into the transformer management system. When maintenance images are uploaded, they are automatically queued for analysis using a PatchCore-based anomaly detection model.

## Features

- **Automatic Queuing**: When maintenance images are uploaded to inspections, they are automatically queued for anomaly analysis
- **Queue Management**: View analysis queue status in the navigation bar
- **Real-time Updates**: Analysis status updates in real-time with polling
- **Visual Results**: View boxed images with detected anomalies and confidence scores
- **JSON Export**: Access detailed analysis results in JSON format
- **Status Tracking**: Track analysis progress from queued → processing → completed/failed

## How It Works

### Backend Components

1. **AnalysisJob Entity**: Tracks analysis jobs with status, queue position, and results
2. **AnomalyAnalysisService**: Manages the analysis queue and execution
3. **Queue Processor**: Background service that processes jobs sequentially
4. **WSL Integration**: Uses existing WSL setup with run_inference.sh script

### Frontend Components

1. **AnalysisDisplay**: Shows analysis status and results for inspection images
2. **QueueStatus**: Dropdown in navigation showing current queue status
3. **Real-time Updates**: Polling every 5-10 seconds for status updates

## Usage

### For Users

1. **Upload Maintenance Images**: When creating an inspection with maintenance images, they are automatically queued for analysis
2. **View Analysis Status**:
   - Check queue status in the navigation bar
   - View individual image analysis status in inspection details
3. **View Results**: Once analysis is complete:
   - Click "View Result" to see the boxed image with detected anomalies
   - Click "View JSON" to see detailed analysis data

### Analysis Results

The system detects various types of transformer anomalies:

- **Point Overload (Faulty)**: Red boxes around critical overload areas
- **Point Overload (Potential)**: Yellow boxes around potential problem areas
- **Full Wire Overload**: Entire wire section flagged
- **Loose Joint (Faulty/Potential)**: Central area heating issues
- **Tiny Spots**: Small anomalous areas

Each detection includes:

- Bounding box coordinates
- Confidence score (0-1)
- Anomaly type classification

## Configuration

### Application Properties
> **Note:** Change "app.anomaly.model.path" and "app.anomaly.venv.path" according to your paths

```properties
# Anomaly Analysis Configuration
app.anomaly.model.path=C:/path/to/Model_Inference
app.anomaly.venv.path=/mnt/c/path/to/.venv
app.anomaly.temp.dir=./temp/anomaly-analysis
app.anomaly.demo.mode=true  # Set to false for production
```

### Demo Mode

For testing purposes, set `app.anomaly.demo.mode=true` to use a demo script that creates placeholder results without running the actual ML model.

## File Structure

```
transformer-manager-backkend/
├── src/main/java/.../
│   ├── entity/AnalysisJob.java
│   ├── repository/AnalysisJobRepository.java
│   ├── service/AnomalyAnalysisService.java
│   └── controller/AnomalyAnalysisController.java
└── src/main/resources/application.properties

transformer-manager-frontend/
└── src/components/
    ├── AnalysisDisplay.js
    └── QueueStatus.js

automatic-anamoly-detection/Model_Inference/
├── run_inference.sh          # Production inference script
├── run_inference_demo.sh     # Demo script for testing
└── inference_core_local.py   # Core analysis logic
```

## API Endpoints

### Analysis Management

- `POST /api/analysis/queue/{imageId}` - Queue image for analysis
- `GET /api/analysis/status/{imageId}` - Get analysis status for image
- `GET /api/analysis/inspection/{inspectionId}` - Get all analysis jobs for inspection
- `GET /api/analysis/queue/status` - Get current queue status
- `GET /api/analysis/job/{jobId}` - Get detailed job information

### File Serving

- `GET /api/files/uploads/{filename}` - Serve uploaded images
- `GET /api/files/analysis/{subpath}/{filename}` - Serve analysis results

## Database Schema

### AnalysisJob Table

```sql
CREATE TABLE analysis_jobs (
    id BIGSERIAL PRIMARY KEY,
    image_id BIGINT NOT NULL REFERENCES images(id),
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    result_json TEXT,
    boxed_image_path VARCHAR(255),
    error_message TEXT,
    queue_position INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP
);
```

## Troubleshooting

### Common Issues

1. **WSL Not Found**: Ensure WSL is installed and accessible from PowerShell
2. **Analysis Fails**: Check WSL path configurations and venv setup
3. **Queue Stuck**: Check logs for processing errors
4. **File Not Found**: Verify upload directory permissions and paths
5. **Maintinence picture upload error:**
 > **Note:** Error: Analysis failed with exit code 127: /usr/bin/env: ‘bash\r’: No such file or directory
/usr/bin/env: use [-v]S to pass options in shebang lines

run in WSL terminal:
```bash
dos2unix /mnt/c/Users/HP/Desktop/Sem\ 7/Software\ Design\ Competition/transformer-image-manager-3/automatic-anamoly-detection/Model_Inference/run_inference.sh
```
### Logs

Check Spring Boot logs for detailed error messages:

```bash
# Look for AnomalyAnalysisService log messages
tail -f application.log | grep "AnomalyAnalysisService"
```

## Future Enhancements

1. **Batch Processing**: Process multiple images simultaneously
2. **Priority Queue**: Allow high-priority analysis requests
3. **Webhook Notifications**: Notify external systems when analysis completes
4. **Model Management**: Support for multiple analysis models
5. **Historical Analytics**: Track analysis performance and accuracy over time

## Development

### Running in Demo Mode

1. Set `app.anomaly.demo.mode=true` in application.properties
2. Demo script will create placeholder results for testing
3. No WSL or ML dependencies required

### Running in Production Mode

1. Set `app.anomaly.demo.mode=false`
2. Ensure WSL and Python environment are properly configured
3. Verify run_inference.sh script works independently
4. Monitor queue processing logs
