import React, { useState, useEffect } from "react";
import axios from "axios";
import {
  Modal,
  Button,
  Card,
  Row,
  Col,
  Spinner,
  Alert,
  Badge,
  Image,
} from "react-bootstrap";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faArrowLeft,
  faMapMarkerAlt,
  faUser,
  faCalendar,
  faTrash,
  faClock,
} from "@fortawesome/free-solid-svg-icons";
import { useParams, useNavigate } from "react-router-dom";
import { useAuth } from "../AuthContext";

const InspectionDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { token, isAuthenticated } = useAuth();
  const [inspection, setInspection] = useState(null);
  const [transformerRecord, setTransformerRecord] = useState(null); // Add this state
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [previewImage, setPreviewImage] = useState(null);
  const [showPreview, setShowPreview] = useState(false);

  useEffect(() => {
    const fetchInspection = async () => {
      try {
        setLoading(true);
        const response = await axios.get(
          `http://localhost:8080/api/inspections/${id}`,
          isAuthenticated
            ? { headers: { Authorization: `Bearer ${token}` } }
            : {}
        );
        setInspection(response.data);

        // Fetch transformer record separately to get all images (same as TransformerRecordDetail)
        if (response.data.transformerRecord?.id) {
          const transformerResponse = await axios.get(
            `http://localhost:8080/api/transformer-records/${response.data.transformerRecord.id}`,
            isAuthenticated
              ? { headers: { Authorization: `Bearer ${token}` } }
              : {}
          );
          setTransformerRecord(transformerResponse.data);
        }

        setError("");
      } catch (err) {
        setError("Failed to fetch inspection details");
      } finally {
        setLoading(false);
      }
    };

    fetchInspection();
  }, [id, token]);

  // Get ALL images from transformer record (same as TransformerRecordDetail)
  const allImages = transformerRecord?.images || [];

  // Get maintenance images from this inspection
  const maintenanceImages = inspection?.images || [];

  if (loading) {
    return (
      <div className="text-center mt-5">
        <Spinner animation="border" variant="primary" />
        <p className="mt-2">Loading inspection details...</p>
      </div>
    );
  }

  if (error) {
    return (
      <Alert variant="danger" className="mt-4">
        {error}
      </Alert>
    );
  }

  if (!inspection) {
    return (
      <Alert variant="warning" className="mt-4">
        Inspection not found
      </Alert>
    );
  }

  return (
    <div className="moodle-container">
      <Button
        variant="outline-secondary"
        onClick={() => navigate(-1)}
        className="mb-3"
      >
        <FontAwesomeIcon icon={faArrowLeft} className="me-2" />
        Back to Inspections
      </Button>

      <Card className="mb-4">
        <Card.Body>
          <div className="d-flex justify-content-between align-items-start mb-4">
            <div>
              <h2>Inspection #{inspection.id}</h2>
              <div className="text-muted mb-3">
                <FontAwesomeIcon icon={faUser} className="me-2" />
                Conducted by: {inspection.conductedBy?.displayName || "Unknown"}
              </div>
              <div className="text-muted">
                <FontAwesomeIcon icon={faCalendar} className="me-2" />
                Conducted: {new Date(inspection.createdAt).toLocaleString()}
              </div>
            </div>
            <div>
              <Badge bg="info" className="fs-6 me-2">
                {allImages.length} Baseline Images
              </Badge>
              <Badge bg="success" className="fs-6">
                {maintenanceImages.length} Maintenance Images
              </Badge>
            </div>
          </div>

          <Row className="mb-4">
            <Col md={6}>
              <Card>
                <Card.Header>Transformer Details</Card.Header>
                <Card.Body>
                  <p>
                    <strong>Name:</strong>{" "}
                    {inspection.transformerRecord?.name || "Not specified"}
                  </p>
                  <p>
                    <strong>Location:</strong>{" "}
                    {inspection.transformerRecord?.locationName ||
                      "Not specified"}
                  </p>
                  <p>
                    <strong>Transformer Type:</strong>{" "}
                    {inspection.transformerRecord?.transformerType ||
                      "Not specified"}
                  </p>
                  <p>
                    <strong>Pole No:</strong>{" "}
                    {inspection.transformerRecord?.poleNo || "Not specified"}
                  </p>
                  <p>
                    <strong>Capacity:</strong>{" "}
                    {inspection.transformerRecord?.capacity
                      ? `${inspection.transformerRecord.capacity}kVA`
                      : "Not specified"}
                  </p>
                </Card.Body>
              </Card>
            </Col>
            <Col md={6}>
              <Card>
                <Card.Header>Inspection Notes</Card.Header>
                <Card.Body>
                  {inspection.notes ? (
                    <p>{inspection.notes}</p>
                  ) : (
                    <p className="text-muted">
                      No notes provided for this inspection.
                    </p>
                  )}
                </Card.Body>
              </Card>
            </Col>
          </Row>

          <Row className="mt-4">
            {/* Baseline Images - Left Side - SAME AS TransformerRecordDetail */}
            <Col md={6}>
              <h4 className="mb-3 text-center">Baseline Images</h4>
              {allImages.length > 0 ? (
                <Row className="g-3">
                  {allImages.map((image) => (
                    <Col key={image.id} xs={12}>
                      <Card className="h-100">
                        <div style={{ position: "relative" }}>
                          <Image
                            src={`http://localhost:8080${image.filePath}`}
                            alt={image.type}
                            fluid
                            style={{
                              height: "250px",
                              width: "100%",
                              objectFit: "cover",
                              cursor: "pointer",
                            }}
                            onClick={() => {
                              setPreviewImage(
                                `http://localhost:8080${image.filePath}`
                              );
                              setShowPreview(true);
                            }}
                          />
                        </div>
                        <Card.Body>
                          <div className="d-flex justify-content-between align-items-start">
                            <div>
                              <strong>Type:</strong> {image.type}
                              {image.weatherCondition && (
                                <div>
                                  <strong>Weather:</strong>{" "}
                                  {image.weatherCondition}
                                </div>
                              )}
                            </div>
                            <div className="text-muted small text-end">
                              <FontAwesomeIcon
                                icon={faClock}
                                className="me-1"
                              />
                              {new Date(
                                image.uploadTime || image.createdAt
                              ).toLocaleString()}
                            </div>
                          </div>
                        </Card.Body>
                      </Card>
                    </Col>
                  ))}
                </Row>
              ) : (
                <Alert variant="info" className="text-center">
                  No Baseline images available
                </Alert>
              )}
            </Col>

            {/* Maintenance Images - Right Side */}
            <Col md={6}>
              <h4 className="mb-3 text-center">Maintenance Images</h4>
              {maintenanceImages.length > 0 ? (
                <Row className="g-3">
                  {maintenanceImages.map((image) => (
                    <Col key={image.id} xs={12}>
                      <Card className="h-100">
                        <div style={{ position: "relative" }}>
                          <Image
                            src={`http://localhost:8080${image.filePath}`}
                            alt={image.type}
                            fluid
                            style={{
                              height: "250px",
                              width: "100%",
                              objectFit: "cover",
                              cursor: "pointer",
                            }}
                            onClick={() => {
                              setPreviewImage(
                                `http://localhost:8080${image.filePath}`
                              );
                              setShowPreview(true);
                            }}
                          />
                        </div>
                        <Card.Body>
                          <div className="d-flex justify-content-between align-items-start">
                            <div>
                              <strong>Type:</strong> {image.type}
                            </div>
                            <div className="text-muted small text-end">
                              <FontAwesomeIcon
                                icon={faClock}
                                className="me-1"
                              />
                              {new Date(
                                image.uploadTime || image.createdAt
                              ).toLocaleString()}
                            </div>
                          </div>
                        </Card.Body>
                      </Card>
                    </Col>
                  ))}
                </Row>
              ) : (
                <Alert variant="info" className="text-center">
                  No Maintenance images available for this inspection
                </Alert>
              )}
            </Col>
          </Row>
        </Card.Body>
      </Card>

      {/* Image Preview Modal */}
      <Modal
        show={showPreview}
        onHide={() => setShowPreview(false)}
        size="lg"
        centered
      >
        <Modal.Header closeButton>
          <Modal.Title>Image Preview</Modal.Title>
        </Modal.Header>
        <Modal.Body className="text-center">
          {previewImage && (
            <img
              src={previewImage}
              alt="Preview"
              style={{ maxWidth: "100%", maxHeight: "70vh" }}
            />
          )}
        </Modal.Body>
        <Modal.Footer>
          <Button variant="secondary" onClick={() => setShowPreview(false)}>
            Close
          </Button>
        </Modal.Footer>
      </Modal>
    </div>
  );
};

export default InspectionDetail;
