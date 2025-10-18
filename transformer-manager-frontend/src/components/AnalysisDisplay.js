import React, { useState, useEffect } from "react";
import axios from "axios";
import {
  Card,
  Button,
  Spinner,
  Alert,
  Badge,
  Modal,
  Row,
  Col,
  Table,
  ProgressBar,
  Toast,
  ToastContainer,
} from "react-bootstrap";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faEye,
  faDownload,
  faClock,
  faCheckCircle,
  faExclamationTriangle,
  faSpinner,
  faFileCode,
  faImage,
  faRefresh,
  faEdit,
  faDrawPolygon,
} from "@fortawesome/free-solid-svg-icons";
import { useAuth } from "../AuthContext";
import InteractiveAnnotationEditor from "./InteractiveAnnotationEditor";

const AnalysisDisplay = ({ inspectionId, images }) => {
  const [analysisJobs, setAnalysisJobs] = useState([]);
  const [loading, setLoading] = useState(false);
  const [queueStatus, setQueueStatus] = useState(null);
  const [selectedJson, setSelectedJson] = useState(null);
  const [selectedJsonJob, setSelectedJsonJob] = useState(null);
  const [showJsonModal, setShowJsonModal] = useState(false);
  const [showToast, setShowToast] = useState(false);
  const [toastMessage, setToastMessage] = useState("");

  // Annotation editor state
  const [showAnnotationEditor, setShowAnnotationEditor] = useState(false);
  const [selectedJobForAnnotation, setSelectedJobForAnnotation] =
    useState(null);
  const [imageRefreshToken, setImageRefreshToken] = useState(Date.now());

  const { token, isAuthenticated } = useAuth();

  useEffect(() => {
    if (inspectionId) {
      fetchAnalysisJobs();
      fetchQueueStatus();

      // Set up polling for updates
      const interval = setInterval(() => {
        fetchAnalysisJobs();
        fetchQueueStatus();
      }, 5000); // Poll every 5 seconds

      return () => clearInterval(interval);
    }
  }, [inspectionId, token]);

  const fetchAnalysisJobs = async () => {
    try {
      setLoading(true);
      const response = await axios.get(
        `http://localhost:8080/api/analysis/inspection/${inspectionId}`,
        isAuthenticated ? { headers: { Authorization: `Bearer ${token}` } } : {}
      );
      setAnalysisJobs(response.data);
    } catch (err) {
      console.error("Failed to fetch analysis jobs", err);
    } finally {
      setLoading(false);
    }
  };

  const fetchQueueStatus = async () => {
    try {
      const response = await axios.get(
        "http://localhost:8080/api/analysis/queue/status",
        isAuthenticated ? { headers: { Authorization: `Bearer ${token}` } } : {}
      );
      setQueueStatus(response.data);
    } catch (err) {
      console.error("Failed to fetch queue status", err);
    }
  };

  const queueImageForAnalysis = async (imageId) => {
    try {
      await axios.post(
        `http://localhost:8080/api/analysis/queue/${imageId}`,
        {},
        { headers: { Authorization: `Bearer ${token}` } }
      );
      setToastMessage("Image queued for analysis");
      setShowToast(true);
      fetchAnalysisJobs();
      fetchQueueStatus();
    } catch (err) {
      setToastMessage("Failed to queue image for analysis");
      setShowToast(true);
      console.error("Failed to queue image", err);
    }
  };

  const getStatusBadge = (status) => {
    switch (status) {
      case "QUEUED":
        return (
          <Badge bg="secondary">
            <FontAwesomeIcon icon={faClock} className="me-1" />
            Queued
          </Badge>
        );
      case "PROCESSING":
        return (
          <Badge bg="warning">
            <FontAwesomeIcon icon={faSpinner} spin className="me-1" />
            Processing
          </Badge>
        );
      case "COMPLETED":
        return (
          <Badge bg="success">
            <FontAwesomeIcon icon={faCheckCircle} className="me-1" />
            Completed
          </Badge>
        );
      case "FAILED":
        return (
          <Badge bg="danger">
            <FontAwesomeIcon icon={faExclamationTriangle} className="me-1" />
            Failed
          </Badge>
        );
      default:
        return <Badge bg="secondary">Unknown</Badge>;
    }
  };

  const viewJsonResults = async (jobId) => {
    try {
      const response = await axios.get(
        `http://localhost:8080/api/analysis/job/${jobId}`,
        isAuthenticated ? { headers: { Authorization: `Bearer ${token}` } } : {}
      );
      const jobData = response.data;
      if (!jobData || !jobData.resultJson) {
        setToastMessage("JSON results are not available for this analysis yet.");
        setShowToast(true);
        return;
      }

      setSelectedJsonJob(jobData);
      let parsedJson;
      try {
        parsedJson = JSON.parse(jobData.resultJson);
      } catch (parseError) {
        console.warn("Failed to parse result JSON, falling back to raw string", parseError);
        parsedJson = jobData.resultJson;
      }
      setSelectedJson(parsedJson);
      setShowJsonModal(true);
    } catch (err) {
      console.error("Failed to fetch job details", err);
      setToastMessage("Failed to load JSON results for download.");
      setShowToast(true);
    }
  };

  const getImageJob = (imageId) => {
    return analysisJobs.find((job) => job.image.id === imageId);
  };

  const buildJsonDownloadUrl = (job) => {
    if (!job || !job.boxedImagePath) {
      return null;
    }

    const normalizedPath = job.boxedImagePath.replace(/\\/g, "/");
    const segments = normalizedPath.split("/");
    const boxedFileName = segments[segments.length - 1];
    if (!boxedFileName) {
      return null;
    }

    const dotIndex = boxedFileName.lastIndexOf(".");
    const nameWithoutExtension =
      dotIndex >= 0 ? boxedFileName.substring(0, dotIndex) : boxedFileName;
    const baseName = nameWithoutExtension.endsWith("_boxed")
      ? nameWithoutExtension.substring(0, nameWithoutExtension.length - "_boxed".length)
      : nameWithoutExtension;
    const jsonFileName = `${baseName}.json`;

    return `http://localhost:8080/api/files/analysis/${jsonFileName}`;
  };

  const downloadJsonResults = (job) => {
    const url = buildJsonDownloadUrl(job);
    if (!url) {
      setToastMessage("JSON file not available for download.");
      setShowToast(true);
      return;
    }

    const link = document.createElement("a");
    link.href = url;
    link.download = url.substring(url.lastIndexOf("/") + 1) || "annotation.json";
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const openAnnotationEditor = (job) => {
    setSelectedJobForAnnotation(job);
    setShowAnnotationEditor(true);
  };

  const closeAnnotationEditor = () => {
    setShowAnnotationEditor(false);
    setSelectedJobForAnnotation(null);
    // Refresh analysis jobs to show any updates
    fetchAnalysisJobs();
  };

  const handleAnnotationSaved = () => {
    setImageRefreshToken(Date.now());
    fetchAnalysisJobs();
  };

  const closeJsonModal = () => {
    setShowJsonModal(false);
    setSelectedJson(null);
    setSelectedJsonJob(null);
  };

  const jsonModalDownloadUrl = buildJsonDownloadUrl(selectedJsonJob);

  const buildImageSrc = (path) => {
    if (!path) {
      return "";
    }
    const separator = path.includes("?") ? "&" : "?";
    return `http://localhost:8080/api/files${path}${separator}cb=${imageRefreshToken}`;
  };

  const maintenanceImages =
    images?.filter((img) => img.type === "Maintenance") || [];

  if (maintenanceImages.length === 0) {
    return (
      <Card className="mb-4">
        <Card.Body>
          <Card.Title>Anomaly Analysis</Card.Title>
          <p className="text-muted">No maintenance images to analyze.</p>
        </Card.Body>
      </Card>
    );
  }

  return (
    <>
      <Card className="mb-4">
        <Card.Header className="d-flex justify-content-between align-items-center">
          <h5 className="mb-0">Anomaly Analysis</h5>
          <div className="d-flex align-items-center">
            {queueStatus && (
              <div className="me-3">
                <Badge bg="info" className="me-2">
                  Queue: {queueStatus.queuedCount}
                </Badge>
                <Badge bg="warning">
                  Processing: {queueStatus.processingCount}
                </Badge>
              </div>
            )}
            <Button
              variant="outline-secondary"
              size="sm"
              onClick={fetchAnalysisJobs}
            >
              <FontAwesomeIcon icon={faRefresh} />
            </Button>
          </div>
        </Card.Header>
        <Card.Body>
          {/* Fixed height container to prevent layout jumping */}
          <div className="text-center mb-3" style={{ minHeight: "40px" }}>
            {loading && (
              <>
                <Spinner animation="border" size="sm" />
                <span className="ms-2">Loading analysis status...</span>
              </>
            )}
          </div>

          <Row>
            {maintenanceImages.map((image) => {
              const job = getImageJob(image.id);
              const jsonDownloadUrl = buildJsonDownloadUrl(job);
              return (
                <Col md={6} lg={4} key={image.id} className="mb-4">
                  <Card className="h-100">
                    <Card.Img
                      variant="top"
                      src={buildImageSrc(image.filePath)}
                      style={{ height: "200px", objectFit: "cover" }}
                    />
                    <Card.Body>
                      <div className="d-flex justify-content-between align-items-center mb-2">
                        <h6 className="mb-0">Image #{image.id}</h6>
                        {job ? (
                          getStatusBadge(job.status)
                        ) : (
                          <Badge bg="light" text="dark">
                            Not Analyzed
                          </Badge>
                        )}
                      </div>

                      {job && job.status === "QUEUED" && (
                        <div className="mb-2">
                          <small className="text-muted">
                            Queue Position: {job.queuePosition}
                          </small>
                          <ProgressBar
                            variant="info"
                            now={
                              job.queuePosition
                                ? (1 / job.queuePosition) * 100
                                : 0
                            }
                            style={{ height: "4px" }}
                          />
                        </div>
                      )}

                      {job && job.status === "COMPLETED" && job.resultJson && (
                        <div className="mb-2">
                          {(() => {
                            try {
                              const result = JSON.parse(job.resultJson);
                              return (
                                <Alert variant="info" className="py-2 mb-2">
                                  <strong>Analysis Result:</strong>{" "}
                                  {result.label}
                                </Alert>
                              );
                            } catch (e) {
                              return null;
                            }
                          })()}
                        </div>
                      )}

                      {job && job.status === "FAILED" && (
                        <Alert variant="danger" className="py-2 mb-2">
                          <strong>Error:</strong>{" "}
                          {job.errorMessage || "Analysis failed"}
                        </Alert>
                      )}

                      <div className="d-flex flex-wrap gap-2">
                        {!job && isAuthenticated && (
                          <Button
                            variant="primary"
                            size="sm"
                            onClick={() => queueImageForAnalysis(image.id)}
                          >
                            <FontAwesomeIcon
                              icon={faSpinner}
                              className="me-1"
                            />
                            Analyze
                          </Button>
                        )}

                        {job &&
                          job.status === "COMPLETED" &&
                          job.boxedImagePath && (
                            <>
                              <Button
                                variant="success"
                                size="sm"
                                onClick={() =>
                                  window.open(
                                    `http://localhost:8080/api/files${job.boxedImagePath}`,
                                    "_blank"
                                  )
                                }
                              >
                                <FontAwesomeIcon
                                  icon={faImage}
                                  className="me-1"
                                />
                                View Result
                              </Button>

                              <Button
                                variant="warning"
                                size="sm"
                                onClick={() => openAnnotationEditor(job)}
                                title="Edit annotations interactively"
                                className="ms-1"
                              >
                                <FontAwesomeIcon
                                  icon={faEdit}
                                  className="me-1"
                                />
                                Edit Annotations
                              </Button>
                            </>
                          )}

                        {job &&
                          job.status === "COMPLETED" &&
                          job.resultJson && (
                            <Button
                              variant="outline-info"
                              size="sm"
                              onClick={() => viewJsonResults(job.id)}
                            >
                              <FontAwesomeIcon
                                icon={faFileCode}
                                className="me-1"
                              />
                              View JSON
                            </Button>
                          )}

                        {job &&
                          job.status === "COMPLETED" &&
                          jsonDownloadUrl && (
                            <Button
                              variant="outline-secondary"
                              size="sm"
                              onClick={() => downloadJsonResults(job)}
                            >
                              <FontAwesomeIcon
                                icon={faDownload}
                                className="me-1"
                              />
                              Download JSON
                            </Button>
                          )}
                      </div>
                    </Card.Body>
                  </Card>
                </Col>
              );
            })}
          </Row>
        </Card.Body>
      </Card>

      {/* JSON Results Modal */}
      <Modal show={showJsonModal} onHide={closeJsonModal} size="lg">
        <Modal.Header closeButton>
          <Modal.Title>Analysis Results (JSON)</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {selectedJson && (
            <div>
              <h6>
                Overall Label: <Badge bg="primary">{selectedJson.label}</Badge>
              </h6>
              {selectedJson.boxes && selectedJson.boxes.length > 0 && (
                <>
                  <h6 className="mt-3">Detected Anomalies:</h6>
                  <Table striped bordered hover size="sm">
                    <thead>
                      <tr>
                        <th>Type</th>
                        <th>Confidence</th>
                        <th>Coordinates</th>
                      </tr>
                    </thead>
                    <tbody>
                      {selectedJson.boxes.map((box, index) => (
                        <tr key={index}>
                          <td>{box.type}</td>
                          <td>{(box.confidence * 100).toFixed(1)}%</td>
                          <td>
                            ({box.box[0]}, {box.box[1]}) {box.box[2]}×
                            {box.box[3]}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </Table>
                </>
              )}
              <details className="mt-3">
                <summary>Raw JSON Data</summary>
                <pre
                  className="bg-light p-3 mt-2"
                  style={{
                    fontSize: "0.8rem",
                    maxHeight: "300px",
                    overflow: "auto",
                  }}
                >
                  {JSON.stringify(selectedJson, null, 2)}
                </pre>
              </details>
            </div>
          )}
        </Modal.Body>
        <Modal.Footer>
          {jsonModalDownloadUrl && (
            <Button
              variant="primary"
              as="a"
              href={jsonModalDownloadUrl}
              download
              target="_blank"
              rel="noopener noreferrer"
            >
              <FontAwesomeIcon icon={faDownload} className="me-1" />
              Download JSON
            </Button>
          )}
          <Button variant="secondary" onClick={closeJsonModal}>
            Close
          </Button>
        </Modal.Footer>
      </Modal>

      {/* Toast Notifications */}
      <ToastContainer position="top-end" className="p-3">
        <Toast
          show={showToast}
          onClose={() => setShowToast(false)}
          delay={3000}
          autohide
        >
          <Toast.Body>{toastMessage}</Toast.Body>
        </Toast>
      </ToastContainer>

      {/* Interactive Annotation Editor */}
      <InteractiveAnnotationEditor
        show={showAnnotationEditor}
        onHide={closeAnnotationEditor}
        analysisJobId={selectedJobForAnnotation?.id}
        boxedImagePath={selectedJobForAnnotation?.boxedImagePath}
        originalResultJson={selectedJobForAnnotation?.resultJson}
        onSaved={handleAnnotationSaved}
      />
    </>
  );
};

export default AnalysisDisplay;
