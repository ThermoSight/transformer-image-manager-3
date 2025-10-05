import React, { useState, useEffect } from "react";
import axios from "axios";
import {
  Button,
  Form,
  Card,
  Alert,
  Spinner,
  Row,
  Col,
  Modal,
  Dropdown,
} from "react-bootstrap";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faArrowLeft,
  faUpload,
  faPlus,
  faTimes,
  faSearch,
} from "@fortawesome/free-solid-svg-icons";
import { useAuth } from "../AuthContext";
import { useNavigate, useParams } from "react-router-dom";
import MLSensitivityIndicator from "./MLSensitivityIndicator";
import SensitivityIndicator from "./SensitivityIndicator";

const InspectionUpload = ({ onUpload }) => {
  const { transformerId } = useParams();
  const navigate = useNavigate();
  const { token } = useAuth();

  const [formData, setFormData] = useState({
    transformerRecordId: transformerId || "",
    notes: "",
    images: [],
    inspectionDate: new Date().toISOString().slice(0, 16), // Default to current date/time in datetime-local format
  });
  const [transformers, setTransformers] = useState([]);
  const [selectedTransformer, setSelectedTransformer] = useState(null);
  const [loading, setLoading] = useState(false);
  const [fetching, setFetching] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [showCancelModal, setShowCancelModal] = useState(false);

  // Fetch all transformers for selection
  useEffect(() => {
    const fetchTransformers = async () => {
      try {
        setFetching(true);
        const config = {
          headers: token ? { Authorization: `Bearer ${token}` } : {},
        };
        const response = await axios.get(
          "http://localhost:8080/api/transformer-records",
          config
        );
        setTransformers(response.data);

        // If transformerId is provided, set the selected transformer
        if (transformerId) {
          const selected = response.data.find(
            (t) => t.id.toString() === transformerId
          );
          if (selected) {
            setSelectedTransformer(selected);
            setFormData((prev) => ({
              ...prev,
              transformerRecordId: transformerId,
            }));
          }
        }

        setError("");
      } catch (err) {
        setError("Failed to fetch transformers data");
      } finally {
        setFetching(false);
      }
    };

    fetchTransformers();
  }, [transformerId, token]);

  const handleTransformerSelect = (transformer) => {
    setSelectedTransformer(transformer);
    setFormData((prev) => ({
      ...prev,
      transformerRecordId: transformer.id.toString(),
    }));
  };

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const handleFileChange = (index, e) => {
    const newImages = [...formData.images];
    newImages[index] = e.target.files[0];
    setFormData((prev) => ({ ...prev, images: newImages }));
  };

  const addImageField = () => {
    setFormData((prev) => ({
      ...prev,
      images: [...prev.images, null],
    }));
  };

  const removeImageField = (index) => {
    const newImages = [...formData.images];
    newImages.splice(index, 1);
    setFormData((prev) => ({ ...prev, images: newImages }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!token) {
      setError("Authentication required. Please log in again.");
      return;
    }

    setLoading(true);
    setError("");
    setSuccess("");

    try {
      const data = new FormData();
      data.append("transformerRecordId", formData.transformerRecordId);
      data.append("notes", formData.notes);
      data.append(
        "inspectionDate",
        new Date(formData.inspectionDate).toISOString()
      );

      // Add images that have files
      formData.images.forEach((image) => {
        if (image) {
          data.append("images", image);
        }
      });

      const config = {
        headers: {
          "Content-Type": "multipart/form-data",
          Authorization: `Bearer ${token}`,
        },
        withCredentials: true,
      };

      const response = await axios.post(
        "http://localhost:8080/api/inspections",
        data,
        config
      );

      setSuccess("Inspection created successfully!");
      if (onUpload) onUpload();
      setTimeout(() => {
        if (formData.transformerRecordId) {
          navigate(`/inspections/list/${formData.transformerRecordId}`);
        } else {
          navigate("/inspections");
        }
      }, 1500);
    } catch (err) {
      if (err.response?.status === 403) {
        setError("Session expired. Please log in again.");
      } else {
        setError(err.response?.data?.message || "Failed to create inspection");
        console.error("Upload error:", err.response?.data);
      }
    } finally {
      setLoading(false);
    }
  };

  const handleCancel = () => {
    if (formData.notes || formData.images.some((img) => img !== null)) {
      setShowCancelModal(true);
    } else {
      navigate("/inspections");
    }
  };

  if (fetching) {
    return (
      <div className="text-center mt-5">
        <Spinner animation="border" variant="primary" />
        <p className="mt-2">Loading transformer data...</p>
      </div>
    );
  }

  return (
    <div className="moodle-container">
      <div className="page-header">
        <h2>
          <FontAwesomeIcon icon={faUpload} className="me-2" />
          Add New Inspection
          {selectedTransformer && (
            <span>
              {" "}
              for {selectedTransformer.name}
              {selectedTransformer.poleNo &&
                ` (Pole #${selectedTransformer.poleNo})`}
            </span>
          )}
        </h2>
        <Button variant="outline-secondary" onClick={handleCancel}>
          <FontAwesomeIcon icon={faArrowLeft} className="me-2" />
          Cancel
        </Button>
      </div>

      <Card>
        <Card.Body>
          {error && (
            <Alert variant="danger" dismissible onClose={() => setError("")}>
              {error}
            </Alert>
          )}
          {success && (
            <Alert variant="success" dismissible onClose={() => setSuccess("")}>
              {success}
            </Alert>
          )}

          <Form onSubmit={handleSubmit}>
            {/* Transformer Selection */}
            <Row className="mb-4">
              <Col md={12}>
                <Form.Group>
                  <Form.Label>Select Transformer *</Form.Label>
                  <Dropdown>
                    <Dropdown.Toggle
                      variant="outline-primary"
                      id="dropdown-transformer"
                      className="w-100 text-start"
                      style={{
                        textOverflow: "ellipsis",
                        overflow: "hidden",
                        whiteSpace: "nowrap",
                        minHeight: "38px",
                      }}
                    >
                      {selectedTransformer ? (
                        <div className="d-flex align-items-center">
                          <FontAwesomeIcon
                            icon={faSearch}
                            className="me-2 flex-shrink-0"
                          />
                          <span
                            style={{
                              overflow: "hidden",
                              textOverflow: "ellipsis",
                              whiteSpace: "nowrap",
                            }}
                            title={`${selectedTransformer.name}${
                              selectedTransformer.locationName
                                ? ` - ${selectedTransformer.locationName}`
                                : ""
                            }${
                              selectedTransformer.poleNo
                                ? ` (Pole #${selectedTransformer.poleNo})`
                                : ""
                            }`}
                          >
                            {selectedTransformer.name}
                            {selectedTransformer.locationName &&
                              ` - ${selectedTransformer.locationName}`}
                            {selectedTransformer.poleNo &&
                              ` (Pole #${selectedTransformer.poleNo})`}
                          </span>
                        </div>
                      ) : (
                        <>
                          <FontAwesomeIcon icon={faSearch} className="me-2" />
                          Choose a transformer...
                        </>
                      )}
                    </Dropdown.Toggle>

                    <Dropdown.Menu
                      style={{
                        maxHeight: "300px",
                        overflowY: "auto",
                        width: "100%",
                        minWidth: "400px",
                      }}
                    >
                      {transformers.map((transformer) => (
                        <Dropdown.Item
                          key={transformer.id}
                          onClick={() => handleTransformerSelect(transformer)}
                          className="py-2"
                        >
                          <div style={{ maxWidth: "380px" }}>
                            <strong className="d-block text-truncate">
                              {transformer.name}
                            </strong>
                            {transformer.locationName && (
                              <div className="text-muted small text-truncate">
                                📍 {transformer.locationName}
                              </div>
                            )}
                            {transformer.poleNo && (
                              <div className="text-muted small">
                                🏛️ Pole #{transformer.poleNo}
                              </div>
                            )}
                          </div>
                        </Dropdown.Item>
                      ))}
                      {transformers.length === 0 && (
                        <Dropdown.Item disabled>
                          No transformers available
                        </Dropdown.Item>
                      )}
                    </Dropdown.Menu>
                  </Dropdown>
                  {!selectedTransformer && (
                    <Form.Text className="text-danger">
                      Please select a transformer to continue
                    </Form.Text>
                  )}
                </Form.Group>
              </Col>
            </Row>

            <Row className="mb-4">
              <Col md={6}>
                <Form.Group>
                  <Form.Label>Inspection Date</Form.Label>
                  <Form.Control
                    type="datetime-local"
                    name="inspectionDate"
                    value={formData.inspectionDate}
                    onChange={handleInputChange}
                  />
                  <Form.Text className="text-muted">
                    Select the date and time when the inspection was conducted
                  </Form.Text>
                </Form.Group>
              </Col>
              <Col md={6}>
                <Form.Group>
                  <Form.Label>Notes (Optional)</Form.Label>
                  <Form.Control
                    as="textarea"
                    rows={3}
                    name="notes"
                    value={formData.notes}
                    onChange={handleInputChange}
                    placeholder="Add any notes about this inspection..."
                  />
                </Form.Group>
              </Col>
            </Row>

            <h4 className="mb-3">Maintenance Images</h4>

            {/* ML Sensitivity Indicator */}
            <MLSensitivityIndicator />

            {formData.images.length === 0 && (
              <Alert variant="info">
                No images added yet. Click "Add Image" below to upload
                maintenance images (optional).
              </Alert>
            )}

            {formData.images.map((img, index) => (
              <Card key={index} className="mb-3">
                <Card.Body>
                  <Row>
                    <Col md={10}>
                      <Form.Group>
                        <Form.Label>
                          Maintenance Image {img ? "(Selected)" : ""}
                        </Form.Label>
                        <Form.Control
                          type="file"
                          accept="image/*"
                          onChange={(e) => handleFileChange(index, e)}
                        />
                      </Form.Group>
                    </Col>
                    <Col md={2} className="d-flex align-items-end">
                      <Button
                        variant="danger"
                        onClick={() => removeImageField(index)}
                        className="w-100"
                      >
                        <FontAwesomeIcon icon={faTimes} />
                      </Button>
                    </Col>
                  </Row>
                </Card.Body>
              </Card>
            ))}

            <div className="d-flex justify-content-between mt-4">
              <Button variant="outline-primary" onClick={addImageField}>
                <FontAwesomeIcon icon={faPlus} className="me-2" />
                Add Image
              </Button>
              <Button
                variant="primary"
                type="submit"
                disabled={loading || !selectedTransformer}
              >
                {loading ? (
                  <>
                    <Spinner
                      as="span"
                      animation="border"
                      size="sm"
                      className="me-2"
                    />
                    Processing...
                  </>
                ) : (
                  "Create Inspection"
                )}
              </Button>
            </div>
          </Form>
        </Card.Body>
      </Card>

      {/* Cancel Confirmation Modal */}
      <Modal show={showCancelModal} onHide={() => setShowCancelModal(false)}>
        <Modal.Header closeButton>
          <Modal.Title>Confirm Cancel</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          You have unsaved changes. Are you sure you want to cancel?
        </Modal.Body>
        <Modal.Footer>
          <Button variant="secondary" onClick={() => setShowCancelModal(false)}>
            Continue Editing
          </Button>
          <Button variant="danger" onClick={() => navigate("/inspections")}>
            Discard Changes
          </Button>
        </Modal.Footer>
      </Modal>
    </div>
  );
};

export default InspectionUpload;
