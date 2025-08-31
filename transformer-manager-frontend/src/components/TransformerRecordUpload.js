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
} from "react-bootstrap";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faArrowLeft,
  faUpload,
  faPlus,
  faTimes,
  faEdit,
} from "@fortawesome/free-solid-svg-icons";
import LocationPicker from "./LocationPicker";
import { useAuth } from "../AuthContext";
import { useNavigate, useSearchParams } from "react-router-dom";

const TransformerRecordUpload = ({ onUpload }) => {
  const [searchParams] = useSearchParams();
  const editId = searchParams.get("edit");
  const navigate = useNavigate();
  const { token } = useAuth();

  const [formData, setFormData] = useState({
    name: "",
    location: null,
    capacity: "",
    transformerType: "",
    poleNo: "",
    images: [],
    existingImages: [],
  });
  const [loading, setLoading] = useState(false);
  const [fetching, setFetching] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [showCancelModal, setShowCancelModal] = useState(false);
  const [showDeleteImageModal, setShowDeleteImageModal] = useState(false);
  const [imageToDelete, setImageToDelete] = useState(null);

  // Fetch existing data when in edit mode
  useEffect(() => {
    if (editId) {
      const fetchRecord = async () => {
        try {
          setFetching(true);
          const response = await axios.get(
            `http://localhost:8080/api/transformer-records/${editId}`,
            {
              headers: { Authorization: `Bearer ${token}` },
            }
          );
          const record = response.data;

          setFormData({
            name: record.name,
            location: {
              name: record.locationName || "",
              lat: record.locationLat || null,
              lng: record.locationLng || null,
            },
            capacity: record.capacity || "",
            transformerType: record.transformerType || "",
            poleNo: record.poleNo || "",
            images: [],
            existingImages: record.images || [],
          });
          setError("");
        } catch (err) {
          setError(
            "Failed to fetch transformer record data: " +
              (err.response?.data?.message || err.message)
          );
        } finally {
          setFetching(false);
        }
      };
      fetchRecord();
    }
  }, [editId, token]);

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const handleLocationChange = (location) => {
    setFormData((prev) => ({ ...prev, location }));
  };

  const handleFileChange = (index, e) => {
    const newImages = [...formData.images];
    newImages[index] = {
      ...newImages[index],
      file: e.target.files[0],
      type: "Baseline", // Force baseline type for transformers
      weatherCondition: newImages[index]?.weatherCondition || "Sunny",
    };
    setFormData((prev) => ({ ...prev, images: newImages }));
  };

  const handleWeatherChange = (index, weather) => {
    const newImages = [...formData.images];
    newImages[index] = {
      ...newImages[index],
      weatherCondition: weather,
    };
    setFormData((prev) => ({ ...prev, images: newImages }));
  };

  const addImageField = () => {
    setFormData((prev) => ({
      ...prev,
      images: [
        ...prev.images,
        { file: null, type: "Baseline", weatherCondition: "Sunny" },
      ],
    }));
  };

  const removeImageField = (index) => {
    const newImages = [...formData.images];
    newImages.splice(index, 1);
    setFormData((prev) => ({ ...prev, images: newImages }));
  };

  const handleDeleteExistingImage = async () => {
    if (!imageToDelete) return;

    try {
      await axios.delete(
        `http://localhost:8080/api/transformer-records/images/${imageToDelete}`,
        {
          headers: { Authorization: `Bearer ${token}` },
        }
      );

      setFormData((prev) => ({
        ...prev,
        existingImages: prev.existingImages.filter(
          (img) => img.id !== imageToDelete
        ),
      }));
      setSuccess("Image deleted successfully");
    } catch (err) {
      setError(
        "Failed to delete image: " +
          (err.response?.data?.message || err.message)
      );
    } finally {
      setShowDeleteImageModal(false);
      setImageToDelete(null);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!token) {
      setError("Authentication required. Please log in again.");
      return;
    }

    if (!formData.name || !formData.location || !formData.capacity) {
      setError("Please provide name, location, and capacity");
      return;
    }

    setLoading(true);
    setError("");
    setSuccess("");

    try {
      const data = new FormData();
      data.append("name", formData.name);
      data.append("locationName", formData.location?.name || "");
      data.append("locationLat", formData.location?.lat || "");
      data.append("locationLng", formData.location?.lng || "");
      data.append("capacity", formData.capacity);
      data.append("transformerType", formData.transformerType || "");
      data.append("poleNo", formData.poleNo || "");

      // Create arrays to maintain order - only include images that have files
      const imagesToUpload = formData.images.filter((img) => img.file);

      if (imagesToUpload.length > 0) {
        const types = [];
        const weatherConditions = [];

        // First collect all files and their metadata
        imagesToUpload.forEach((img) => {
          data.append("images", img.file);
          types.push(img.type);
          weatherConditions.push(img.weatherCondition || "Sunny");
        });

        // Then append the metadata arrays
        types.forEach((type) => data.append("types", type));
        weatherConditions.forEach((condition) =>
          data.append("weatherConditions", condition)
        );
      }

      const config = {
        headers: {
          "Content-Type": "multipart/form-data",
          Authorization: `Bearer ${token}`,
        },
        withCredentials: true,
      };

      let response;
      if (editId) {
        response = await axios.put(
          `http://localhost:8080/api/transformer-records/${editId}`,
          data,
          config
        );
      } else {
        response = await axios.post(
          "http://localhost:8080/api/transformer-records",
          data,
          config
        );
      }

      setSuccess(
        editId
          ? "Transformer record updated successfully!"
          : "Transformer record created successfully!"
      );
      if (onUpload) onUpload();
      setTimeout(() => navigate("/"), 1500);
    } catch (err) {
      if (err.response?.status === 403) {
        setError("Session expired. Please log in again.");
      } else {
        setError(err.response?.data?.message || "Failed to process request");
        console.error("Upload error:", err.response?.data);
      }
    } finally {
      setLoading(false);
    }
  };

  const handleCancel = () => {
    if (formData.name || formData.location || formData.images.length > 0) {
      setShowCancelModal(true);
    } else {
      navigate("/");
    }
  };

  if (fetching) {
    return (
      <div className="text-center mt-5">
        <Spinner animation="border" variant="primary" />
        <p className="mt-2">Loading transformer record data...</p>
      </div>
    );
  }

  return (
    <div className="moodle-container">
      <div className="page-header">
        <h2>
          <FontAwesomeIcon icon={editId ? faEdit : faUpload} className="me-2" />
          {editId ? "Edit Transformer Record" : "Upload New Transformer Record"}
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
            <Row className="mb-3">
              <Col md={6}>
                <Form.Group>
                  <Form.Label>Record Name *</Form.Label>
                  <Form.Control
                    type="text"
                    name="name"
                    value={formData.name}
                    onChange={handleInputChange}
                    required
                  />
                </Form.Group>
              </Col>
              <Col md={6}>
                <Form.Group>
                  <Form.Label>Capacity (kVA) *</Form.Label>
                  <Form.Control
                    type="number"
                    name="capacity"
                    value={formData.capacity}
                    onChange={handleInputChange}
                    placeholder="e.g., 100"
                    step="1"
                    min="0"
                    required
                  />
                  <Form.Text className="text-muted">
                    Enter capacity in kVA (kilovolt-amperes)
                  </Form.Text>
                </Form.Group>
              </Col>
            </Row>

            <Row className="mb-3">
              <Col md={6}>
                <Form.Group>
                  <Form.Label>Transformer Type</Form.Label>
                  <Form.Control
                    type="text"
                    name="transformerType"
                    value={formData.transformerType}
                    onChange={handleInputChange}
                    placeholder="e.g., Distribution, Power, etc."
                  />
                </Form.Group>
              </Col>
              <Col md={6}>
                <Form.Group>
                  <Form.Label>Pole Number</Form.Label>
                  <Form.Control
                    type="text"
                    name="poleNo"
                    value={formData.poleNo}
                    onChange={handleInputChange}
                    placeholder="e.g., P-1234"
                  />
                </Form.Group>
              </Col>
            </Row>

            <Form.Group className="mb-4">
              <Form.Label>Location *</Form.Label>
              <LocationPicker
                value={formData.location}
                onChange={handleLocationChange}
              />
            </Form.Group>

            {/* Existing Images Section */}
            {editId && formData.existingImages.length > 0 && (
              <>
                <h4 className="mb-3">Existing Baseline Images</h4>
                <Row className="g-3 mb-4">
                  {formData.existingImages.map((image) => (
                    <Col key={image.id} xs={12} sm={6} md={4} lg={3}>
                      <Card>
                        <div style={{ position: "relative" }}>
                          <img
                            src={`http://localhost:8080${image.filePath}`}
                            alt={image.type}
                            style={{
                              width: "100%",
                              height: "150px",
                              objectFit: "cover",
                            }}
                          />
                          <Button
                            variant="danger"
                            size="sm"
                            style={{
                              position: "absolute",
                              top: "5px",
                              right: "5px",
                            }}
                            onClick={() => {
                              setImageToDelete(image.id);
                              setShowDeleteImageModal(true);
                            }}
                          >
                            <FontAwesomeIcon icon={faTimes} />
                          </Button>
                        </div>
                        <Card.Body>
                          <p>
                            <strong>Type:</strong> {image.type}
                          </p>
                          {image.type === "Baseline" && (
                            <p>
                              <strong>Weather:</strong>{" "}
                              {image.weatherCondition || "N/A"}
                            </p>
                          )}
                        </Card.Body>
                      </Card>
                    </Col>
                  ))}
                </Row>
              </>
            )}

            {/* New Images Section */}
            <h4 className="mb-3">New Baseline Images (Optional)</h4>
            {formData.images.length === 0 && (
              <Alert variant="info">
                No new images added yet. Click "Add Image" below to upload
                baseline images (optional).
              </Alert>
            )}

            {formData.images.map((img, index) => (
              <Card key={index} className="mb-3">
                <Card.Body>
                  <Row>
                    <Col md={5}>
                      <Form.Group>
                        <Form.Label>
                          Baseline Image {img.file ? "(Selected)" : ""}
                        </Form.Label>
                        <Form.Control
                          type="file"
                          accept="image/*"
                          onChange={(e) => handleFileChange(index, e)}
                        />
                      </Form.Group>
                    </Col>
                    <Col md={3}>
                      <Form.Group>
                        <Form.Label>Weather Condition</Form.Label>
                        <Form.Select
                          value={img.weatherCondition}
                          onChange={(e) =>
                            handleWeatherChange(index, e.target.value)
                          }
                        >
                          <option value="Sunny">Sunny</option>
                          <option value="Cloudy">Cloudy</option>
                          <option value="Rainy">Rainy</option>
                        </Form.Select>
                      </Form.Group>
                    </Col>
                    <Col md={1} className="d-flex align-items-end">
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
                Add Baseline Image
              </Button>
              <Button variant="primary" type="submit" disabled={loading}>
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
                ) : editId ? (
                  "Update Transformer"
                ) : (
                  "Create Transformer"
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
          <Button variant="danger" onClick={() => navigate("/")}>
            Discard Changes
          </Button>
        </Modal.Footer>
      </Modal>

      {/* Delete Image Confirmation Modal */}
      <Modal
        show={showDeleteImageModal}
        onHide={() => setShowDeleteImageModal(false)}
      >
        <Modal.Header closeButton>
          <Modal.Title>Confirm Delete</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          Are you sure you want to delete this baseline image? This action
          cannot be undone.
        </Modal.Body>
        <Modal.Footer>
          <Button
            variant="secondary"
            onClick={() => setShowDeleteImageModal(false)}
          >
            Cancel
          </Button>
          <Button variant="danger" onClick={handleDeleteExistingImage}>
            Delete
          </Button>
        </Modal.Footer>
      </Modal>
    </div>
  );
};

export default TransformerRecordUpload;
