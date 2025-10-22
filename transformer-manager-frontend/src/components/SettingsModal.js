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
  const {
    settings,
    updateDetectionSensitivity,
    updateFeedbackLearningRate,
  } = useSettings();
  const [sensitivity, setSensitivity] = useState(1.0);
  const [feedbackRate, setFeedbackRate] = useState(0.0001);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  // Update local state when settings change
  useEffect(() => {
    setSensitivity(settings.detectionSensitivity);
    setFeedbackRate(settings.feedbackLearningRate);
  }, [settings.detectionSensitivity, settings.feedbackLearningRate]);

  const handleSensitivityChange = (e) => {
    const value = parseFloat(e.target.value);
    setSensitivity(value);
    setError("");
    setSuccess("");
  };

  const handleFeedbackRateChange = (e) => {
    const value = parseFloat(e.target.value);
    setFeedbackRate(value);
    setError("");
    setSuccess("");
  };

  const handleSave = async () => {
    if (Number.isNaN(sensitivity)) {
      setError("Sensitivity must be a number");
      return;
    }
    if (sensitivity < 0.1 || sensitivity > 2.0) {
      setError("Sensitivity must be between 0.1 and 2.0");
      return;
    }
    if (Number.isNaN(feedbackRate)) {
      setError("Feedback learning rate must be a number");
      return;
    }
    if (feedbackRate < 0.00001 || feedbackRate > 0.05) {
      setError("Feedback learning rate must be between 0.00001 and 0.05");
      return;
    }

    const needsSensitivityUpdate =
      Math.abs(sensitivity - settings.detectionSensitivity) > 1e-6;
    const needsFeedbackUpdate =
      Math.abs(feedbackRate - settings.feedbackLearningRate) > 1e-8;

    if (!needsSensitivityUpdate && !needsFeedbackUpdate) {
      setSuccess("No changes to save.");
      return;
    }

    setLoading(true);
    setError("");
    setSuccess("");

    let updatesSucceeded = true;

    if (needsSensitivityUpdate) {
      const result = await updateDetectionSensitivity(sensitivity);
      if (!result.success) {
        updatesSucceeded = false;
        setError(result.error || "Failed to update detection sensitivity");
      }
    }

    if (needsFeedbackUpdate) {
      const result = await updateFeedbackLearningRate(feedbackRate);
      if (!result.success) {
        updatesSucceeded = false;
        setError(
          result.error || "Failed to update feedback learning rate"
        );
      }
    }

    if (updatesSucceeded) {
      setSuccess(
        "Settings saved successfully. Future analyses will reflect these adjustments."
      );
      setTimeout(() => {
        setSuccess("");
        onHide();
      }, 2000);
    }

    setLoading(false);
  };

  const handleClose = () => {
    setSensitivity(settings.detectionSensitivity); // Reset to original value
    setFeedbackRate(settings.feedbackLearningRate);
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

          <Row>
            <Col md={12}>
              <Form.Group className="mb-3">
                <Form.Label className="h5">
                  Feedback Learning Rate
                  <FontAwesomeIcon
                    icon={faInfoCircle}
                    className="ms-2 text-muted"
                  />
                </Form.Label>
                <Row className="align-items-center">
                  <Col md={6}>
                    <Form.Control
                      type="number"
                      min="0.00001"
                      max="0.05"
                      step="0.00001"
                      value={feedbackRate}
                      onChange={handleFeedbackRateChange}
                      className="mb-2"
                    />
                  </Col>
                  <Col md={6}>
                    <div className="small text-muted">
                      Default: 0.00010 (0.01% influence)
                    </div>
                    <div className="small text-muted">
                      Higher values respond faster to human adjustments.
                    </div>
                  </Col>
                </Row>
                <Form.Text className="text-muted">
                  <div className="mt-3 p-3 bg-light rounded">
                    <strong>How feedback blending works:</strong>
                    <ul className="mb-0 mt-2">
                      <li>
                        The model nudges anomaly confidences using user
                        annotations for similar fault types.
                      </li>
                      <li>
                        The learning rate controls how strong those nudges are —
                        keep it small for gentle, audit-friendly updates.
                      </li>
                      <li>
                        Set higher only if you want the AI to follow new
                        annotations more aggressively.
                      </li>
                    </ul>
                  </div>
                </Form.Text>
              </Form.Group>
            </Col>
          </Row>
        </Form>

        <FeedbackSummaryPanel summary={settings.feedbackSummary} />
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

const FeedbackSummaryPanel = ({ summary }) => {
  if (!summary) {
    return (
      <div className="mt-4 small text-muted">
        Feedback summary not available yet. Save settings to refresh.
      </div>
    );
  }

  const labels = Array.isArray(summary.labels) ? summary.labels : [];
  const topLabels = labels.slice(0, 3);
  const remaining = Math.max(0, labels.length - topLabels.length);

  const formatNumber = (value, digits = 6) => {
    if (typeof value !== "number" || Number.isNaN(value)) {
      return value ?? "-";
    }
    return value.toFixed(digits);
  };

  const generatedAt = summary.generatedAt
    ? new Date(summary.generatedAt).toLocaleString()
    : "pending";

  return (
    <div className="mt-4">
      <div className="d-flex justify-content-between align-items-center">
        <h6 className="mb-0">Latest Feedback Impact</h6>
        <small className="text-muted">Updated {generatedAt}</small>
      </div>
      <div className="small mt-2">
        {summary.annotationSamples > 0 ? (
          <>
            <div className="text-muted">
              Based on {summary.annotationSamples} annotated result
              {summary.annotationSamples === 1 ? "" : "s"}.
            </div>
            <div className="fw-semibold">
              Global confidence bias: {formatNumber(summary.globalAdjustment)}
            </div>
          </>
        ) : (
          <span className="text-muted">
            No user feedback has been applied yet.
          </span>
        )}
      </div>
      {topLabels.length > 0 && (
        <ul className="small mt-2 mb-0">
          {topLabels.map((item) => (
            <li key={item.label}>
              <strong>{item.label}</strong>: adj {formatNumber(item.adjustment)}{" "}
              (Δcount {formatNumber(item.avgCountDelta, 3)}, Δarea{" "}
              {formatNumber(item.avgAreaRatio, 3)})
            </li>
          ))}
          {remaining > 0 && (
            <li className="text-muted">
              …plus {remaining} more fault type
              {remaining === 1 ? "" : "s"}
            </li>
          )}
        </ul>
      )}
    </div>
  );
};
