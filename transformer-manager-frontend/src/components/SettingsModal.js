import React, { useState, useEffect } from "react";
import { Modal, Button, Form, Alert, Row, Col, Spinner } from "react-bootstrap";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faCog,
  faSave,
  faTimes,
  faInfoCircle,
} from "@fortawesome/free-solid-svg-icons";
import { useSettings } from "../SettingsContext";

const SettingsModal = ({ show, onHide }) => {
  const { settings, updateDetectionSensitivity } = useSettings();
  const [sensitivity, setSensitivity] = useState(1.0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  // Update local state when settings change
  useEffect(() => {
    setSensitivity(settings.detectionSensitivity);
  }, [settings.detectionSensitivity]);

  const handleSensitivityChange = (e) => {
    const value = parseFloat(e.target.value);
    setSensitivity(value);
    setError("");
    setSuccess("");
  };

  const handleSave = async () => {
    if (sensitivity < 0.1 || sensitivity > 2.0) {
      setError("Sensitivity must be between 0.1 and 2.0");
      return;
    }

    setLoading(true);
    setError("");
    setSuccess("");

    const result = await updateDetectionSensitivity(sensitivity);

    if (result.success) {
      setSuccess(
        "Settings saved successfully! All future analyses will use this sensitivity."
      );
      setTimeout(() => {
        setSuccess("");
        onHide();
      }, 2000);
    } else {
      setError(result.error || "Failed to save settings");
    }

    setLoading(false);
  };

  const handleClose = () => {
    setSensitivity(settings.detectionSensitivity); // Reset to original value
    setError("");
    setSuccess("");
    onHide();
  };

  const getSensitivityDescription = (value) => {
    if (value < 0.5) return "Very Low - Only strongest anomalies detected";
    if (value < 0.8) return "Low - Conservative detection";
    if (value < 1.2) return "Normal - Balanced detection (Recommended)";
    if (value < 1.5) return "High - More sensitive detection";
    return "Very High - Maximum sensitivity, may detect false positives";
  };

  const getSensitivityColor = (value) => {
    if (value < 0.5) return "text-info";
    if (value < 0.8) return "text-success";
    if (value < 1.2) return "text-primary";
    if (value < 1.5) return "text-warning";
    return "text-danger";
  };

  return (
    <Modal show={show} onHide={handleClose} size="lg" centered>
      <Modal.Header closeButton>
        <Modal.Title>
          <FontAwesomeIcon icon={faCog} className="me-2" />
          ML Settings
        </Modal.Title>
      </Modal.Header>

      <Modal.Body>
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

        <Form>
          <Row>
            <Col md={12}>
              <Form.Group className="mb-4">
                <Form.Label className="h5">
                  Detection Sensitivity
                  <FontAwesomeIcon
                    icon={faInfoCircle}
                    className="ms-2 text-muted"
                  />
                </Form.Label>

                <Form.Range
                  min="0.1"
                  max="2.0"
                  step="0.1"
                  value={sensitivity}
                  onChange={handleSensitivityChange}
                  className="mb-3"
                />

                <div className="d-flex justify-content-between small text-muted mb-2">
                  <span>0.1 (Less Sensitive)</span>
                  <span>1.0 (Default)</span>
                  <span>2.0 (More Sensitive)</span>
                </div>

                <Row className="align-items-center">
                  <Col md={6}>
                    <Form.Control
                      type="number"
                      min="0.1"
                      max="2.0"
                      step="0.1"
                      value={sensitivity}
                      onChange={handleSensitivityChange}
                      className="mb-2"
                    />
                  </Col>
                  <Col md={6}>
                    <strong className={getSensitivityColor(sensitivity)}>
                      {getSensitivityDescription(sensitivity)}
                    </strong>
                  </Col>
                </Row>

                <Form.Text className="text-muted">
                  <div className="mt-3 p-3 bg-light rounded">
                    <strong>How it works:</strong>
                    <ul className="mb-0 mt-2">
                      <li>
                        <strong>Lower values (0.1-0.8):</strong> Only detect the
                        most obvious anomalies. Fewer boxes, less false
                        positives.
                      </li>
                      <li>
                        <strong>Normal values (0.9-1.1):</strong> Balanced
                        detection. Good for most cases.
                      </li>
                      <li>
                        <strong>Higher values (1.2-2.0):</strong> More sensitive
                        detection. More boxes, may include minor anomalies.
                      </li>
                    </ul>
                    <div className="mt-2 text-info">
                      <FontAwesomeIcon icon={faInfoCircle} className="me-1" />
                      This setting affects all future image analyses and
                      persists across sessions.
                    </div>
                  </div>
                </Form.Text>
              </Form.Group>
            </Col>
          </Row>
        </Form>
      </Modal.Body>

      <Modal.Footer>
        <Button variant="secondary" onClick={handleClose} disabled={loading}>
          <FontAwesomeIcon icon={faTimes} className="me-2" />
          Cancel
        </Button>
        <Button variant="primary" onClick={handleSave} disabled={loading}>
          {loading ? (
            <>
              <Spinner
                as="span"
                animation="border"
                size="sm"
                className="me-2"
              />
              Saving...
            </>
          ) : (
            <>
              <FontAwesomeIcon icon={faSave} className="me-2" />
              Save Settings
            </>
          )}
        </Button>
      </Modal.Footer>
    </Modal>
  );
};

export default SettingsModal;
