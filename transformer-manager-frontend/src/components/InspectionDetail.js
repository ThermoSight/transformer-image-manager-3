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
  Form,
} from "react-bootstrap";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faArrowLeft,
  faMapMarkerAlt,
  faUser,
  faCalendar,
  faTrash,
  faClock,
  faPlus,
  faTimes,
  faUpload,
} from "@fortawesome/free-solid-svg-icons";
import { useParams, useNavigate } from "react-router-dom";
import { useAuth } from "../AuthContext";

const InspectionDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { token, isAuthenticated, user: authUser } = useAuth();
  const [inspection, setInspection] = useState(null);
  const [transformerRecord, setTransformerRecord] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [previewImage, setPreviewImage] = useState(null);
  const [showPreview, setShowPreview] = useState(false);

  // Image upload states
  const [showImageUpload, setShowImageUpload] = useState(false);
  const [newImages, setNewImages] = useState([]);
  const [uploadLoading, setUploadLoading] = useState(false);
  const [uploadError, setUploadError] = useState("");
  const [uploadSuccess, setUploadSuccess] = useState("");

  // Delete states
  const [deletingImageId, setDeletingImageId] = useState(null);
  const [deleteError, setDeleteError] = useState("");

  useEffect(() => {
    const fetchInspection = async () => {
      try {
        setLoading(true);

        if (!isAuthenticated || !token) {
          setError(
            "Authentication required. Please log in to view inspection details."
          );
          return;
        }

        const config = {
          headers: { Authorization: `Bearer ${token}` },
          withCredentials: true,
        };

        const response = await axios.get(
          `http://localhost:8080/api/inspections/${id}`,
          config
        );
        setInspection(response.data);

        if (response.data.transformerRecord?.id) {
          const transformerResponse = await axios.get(
            `http://localhost:8080/api/transformer-records/${response.data.transformerRecord.id}`,
            config
          );
          setTransformerRecord(transformerResponse.data);
        }

        setError("");
      } catch (err) {
        if (err.response?.status === 403) {
          setError(
            "Access denied. You don't have permission to view this inspection."
          );
        } else if (err.response?.status === 401) {
          setError("Authentication expired. Please log in again.");
        } else {
          setError("Failed to fetch inspection details");
        }
      } finally {
        setLoading(false);
      }
    };

    fetchInspection();
  }, [id, token, isAuthenticated]);

  // Debug: Log auth and inspection data
  useEffect(() => {
    console.log("Auth User:", authUser);
    console.log("Inspection:", inspection);
    console.log("Can delete images:", canDeleteImages());
  }, [authUser, inspection]);

  // Check if current user can delete images from this inspection
  const canDeleteImages = () => {
    if (!inspection || !authUser) {
      console.log("Cannot delete: Missing inspection or authUser");
      return false;
    }
    
    // If user is ADMIN, always allow delete
    if (authUser.role === "ADMIN" || authUser.role === "ROLE_ADMIN") {
      console.log("User is ADMIN - can delete");
      return true;
    }
    
    // Check if user conducted this inspection
    const userConductedInspection = inspection.conductedByUser && 
      inspection.conductedByUser.id === authUser.id;
    
    if (userConductedInspection) {
      console.log("User conducted this inspection - can delete");
      return true;
    }
    
    // Fallback: check conductor name or other properties
    const conductorName = inspection.conductorName || inspection.getConductorName?.();
    const currentUserName = authUser.displayName || authUser.username;
    
    if (conductorName && currentUserName && conductorName === currentUserName) {
      console.log("User matches conductor name - can delete");
      return true;
    }
    
    console.log("Cannot delete - no permission match");
    return false;
  };

  // Alternative simpler permission check
  const canDeleteImagesSimple = () => {
    if (!isAuthenticated) return false;
    
    // For testing, allow all authenticated users to delete
    // Remove this in production
    console.log("Allowing delete for testing");
    return true;
    
    // In production, use this:
    // if (authUser?.role === "ADMIN" || authUser?.role === "ROLE_ADMIN") return true;
    // return inspection?.conductedByUser?.id === authUser?.id;
  };

  const allImages = transformerRecord?.images || [];
  const maintenanceImages = inspection?.images || [];

  // Image upload handlers
  const addImageField = () => {
    setNewImages([...newImages, null]);
  };

  const removeImageField = (index) => {
    const updatedImages = [...newImages];
    updatedImages.splice(index, 1);
    setNewImages(updatedImages);
  };

  const handleImageChange = (index, file) => {
    const updatedImages = [...newImages];
    updatedImages[index] = file;
    setNewImages(updatedImages);
  };

  const handleImageUpload = async () => {
    if (!isAuthenticated || !token) {
      setUploadError("Authentication required. Please log in again.");
      return;
    }

    const validImages = newImages.filter((img) => img !== null);
    if (validImages.length === 0) {
      setUploadError("Please select at least one image to upload.");
      return;
    }

    setUploadLoading(true);
    setUploadError("");
    setUploadSuccess("");

    try {
      const formData = new FormData();
      validImages.forEach((image) => {
        formData.append("images", image);
      });

      const config = {
        headers: {
          "Content-Type": "multipart/form-data",
          Authorization: `Bearer ${token}`,
        },
        withCredentials: true,
      };

      await axios.post(
        `http://localhost:8080/api/inspections/${id}/images`,
        formData,
        config
      );

      setUploadSuccess(`${validImages.length} image(s) uploaded successfully!`);

      // Refresh inspection data to show new images
      const response = await axios.get(
        `http://localhost:8080/api/inspections/${id}`,
        {
          headers: { Authorization: `Bearer ${token}` },
          withCredentials: true,
        }
      );
      setInspection(response.data);

      // Reset form
      setNewImages([]);
      setTimeout(() => {
        setShowImageUpload(false);
        setUploadSuccess("");
      }, 2000);
    } catch (err) {
      if (err.response?.status === 403) {
        setUploadError(
          "Session expired or insufficient permissions. Please log in again."
        );
      } else if (err.response?.status === 401) {
        setUploadError("Authentication failed. Please log in again.");
      } else {
        setUploadError(
          err.response?.data?.message || "Failed to upload images"
        );
      }
    } finally {
      setUploadLoading(false);
    }
  };

  // Delete image handler
  const handleDeleteImage = async (imageId) => {
    if (!isAuthenticated || !token) {
      setDeleteError("Authentication required. Please log in again.");
      return;
    }

    if (!window.confirm("Are you sure you want to delete this image? This action cannot be undone.")) {
      return;
    }

    setDeletingImageId(imageId);
    setDeleteError("");

    try {
      const config = {
        headers: {
          Authorization: `Bearer ${token}`,
        },
        withCredentials: true,
      };

      await axios.delete(
        `http://localhost:8080/api/inspections/images/${imageId}`,
        config
      );

      // Remove image from state
      const updatedMaintenanceImages = maintenanceImages.filter(
        (img) => img.id !== imageId
      );
      
      setInspection({
        ...inspection,
        images: updatedMaintenanceImages,
      });

      // Show success message
      setUploadSuccess("Image deleted successfully!");
      setTimeout(() => setUploadSuccess(""), 3000);

    } catch (err) {
      if (err.response?.status === 403) {
        setDeleteError("You don't have permission to delete this image.");
      } else if (err.response?.status === 401) {
        setDeleteError("Authentication failed. Please log in again.");
      } else {
        setDeleteError(err.response?.data?.message || "Failed to delete image");
      }
    } finally {
      setDeletingImageId(null);
    }
  };

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
      <div className="moodle-container">
        <Alert variant="danger" className="mt-4">
          <Alert.Heading>Access Error</Alert.Heading>
          <p>{error}</p>
          {!isAuthenticated && (
            <div className="mt-3">
              <Button variant="primary" onClick={() => navigate("/login")}>
                Go to Login
              </Button>
              <Button
                variant="outline-secondary"
                className="ms-2"
                onClick={() => navigate(-1)}
              >
                Go Back
              </Button>
            </div>
          )}
        </Alert>
      </div>
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

      {/* Success/Error Alerts */}
      {uploadSuccess && (
        <Alert variant="success" dismissible onClose={() => setUploadSuccess("")}>
          {uploadSuccess}
        </Alert>
      )}
      {deleteError && (
        <Alert variant="danger" dismissible onClose={() => setDeleteError("")}>
          {deleteError}
        </Alert>
      )}

      <Card className="mb-4">
        <Card.Body>
          <div className="d-flex justify-content-between align-items-start mb-4">
            <div>
              <h2>Inspection #{inspection.id}</h2>
              <div className="text-muted mb-3">
                <FontAwesomeIcon icon={faUser} className="me-2" />
                Conducted by: {inspection.conductorName || inspection.conductedBy?.displayName || "Unknown"}
                {inspection.conductedByUser && ` (User)`}
                {inspection.conductedByAdmin && ` (Admin)`}
              </div>
              {inspection.inspectionDate && (
                <div className="text-muted mb-3">
                  <FontAwesomeIcon icon={faCalendar} className="me-2" />
                  Inspection Date:{" "}
                  {new Date(inspection.inspectionDate).toLocaleString()}
                </div>
              )}
              <div className="text-muted">
                <FontAwesomeIcon icon={faClock} className="me-2" />
                Added: {new Date(inspection.createdAt).toLocaleString()}
              </div>
              {/* Debug info */}
              <div className="text-muted small mt-2">
                <strong>Debug:</strong> User ID: {authUser?.id}, Role: {authUser?.role}
                {inspection.conductedByUser && `, Conductor User ID: ${inspection.conductedByUser.id}`}
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
            {/* Baseline Images - Left Side */}
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
              <div className="d-flex justify-content-between align-items-center mb-3">
                <h4 className="mb-0 text-center flex-grow-1">
                  Maintenance Images
                </h4>
                {isAuthenticated && (
                  <Button
                    variant="success"
                    size="sm"
                    onClick={() => setShowImageUpload(true)}
                  >
                    <FontAwesomeIcon icon={faPlus} className="me-2" />
                    Add Images
                  </Button>
                )}
              </div>
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
                          {/* Delete Button Overlay - Using simple permission check for testing */}
                          {canDeleteImagesSimple() && (
                            <Button
                              variant="danger"
                              size="sm"
                              style={{
                                position: "absolute",
                                top: "10px",
                                right: "10px",
                                opacity: 0.9,
                                width: "40px",
                                height: "40px",
                                borderRadius: "50%",
                                display: "flex",
                                alignItems: "center",
                                justifyContent: "center",
                                border: "2px solid white",
                                boxShadow: "0 2px 4px rgba(0,0,0,0.3)"
                              }}
                              onClick={(e) => {
                                e.stopPropagation(); // Prevent image preview
                                handleDeleteImage(image.id);
                              }}
                              disabled={deletingImageId === image.id}
                            >
                              {deletingImageId === image.id ? (
                                <Spinner
                                  as="span"
                                  animation="border"
                                  size="sm"
                                />
                              ) : (
                                <FontAwesomeIcon icon={faTrash} />
                              )}
                            </Button>
                          )}
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

      {/* Image Upload Modal */}
      <Modal
        show={showImageUpload}
        onHide={() => {
          setShowImageUpload(false);
          setNewImages([]);
          setUploadError("");
          setUploadSuccess("");
        }}
        size="lg"
        centered
      >
        <Modal.Header closeButton>
          <Modal.Title>
            <FontAwesomeIcon icon={faUpload} className="me-2" />
            Add Images to Inspection
          </Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {uploadError && (
            <Alert
              variant="danger"
              dismissible
              onClose={() => setUploadError("")}
            >
              {uploadError}
            </Alert>
          )}
          {uploadSuccess && (
            <Alert
              variant="success"
              dismissible
              onClose={() => setUploadSuccess("")}
            >
              {uploadSuccess}
            </Alert>
          )}

          <p className="text-muted mb-3">
            Upload additional maintenance images for this inspection. These
            images will be associated with the inspection and visible in the
            maintenance images section.
          </p>

          {newImages.length === 0 && (
            <Alert variant="info">
              No images selected yet. Click "Add Image" below to select images
              to upload.
            </Alert>
          )}

          {newImages.map((img, index) => (
            <Card key={index} className="mb-3">
              <Card.Body>
                <Row>
                  <Col md={10}>
                    <Form.Group>
                      <Form.Label>
                        Maintenance Image {index + 1} {img ? "(Selected)" : ""}
                      </Form.Label>
                      <Form.Control
                        type="file"
                        accept="image/*"
                        onChange={(e) =>
                          handleImageChange(index, e.target.files[0])
                        }
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

          <div className="text-center">
            <Button variant="outline-primary" onClick={addImageField}>
              <FontAwesomeIcon icon={faPlus} className="me-2" />
              Add Image
            </Button>
          </div>
        </Modal.Body>
        <Modal.Footer>
          <Button
            variant="secondary"
            onClick={() => {
              setShowImageUpload(false);
              setNewImages([]);
              setUploadError("");
              setUploadSuccess("");
            }}
          >
            Cancel
          </Button>
          <Button
            variant="primary"
            onClick={handleImageUpload}
            disabled={
              uploadLoading ||
              newImages.filter((img) => img !== null).length === 0
            }
          >
            {uploadLoading ? (
              <>
                <Spinner
                  as="span"
                  animation="border"
                  size="sm"
                  className="me-2"
                />
                Uploading...
              </>
            ) : (
              <>
                <FontAwesomeIcon icon={faUpload} className="me-2" />
                Upload Images
              </>
            )}
          </Button>
        </Modal.Footer>
      </Modal>

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