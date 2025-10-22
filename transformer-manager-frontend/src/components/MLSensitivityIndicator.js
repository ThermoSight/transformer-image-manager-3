import React, { useState } from "react";
import { Card, Badge, Button, Spinner } from "react-bootstrap";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faCog,
  faEye,
  faExclamationTriangle,
  faCheckCircle,
} from "@fortawesome/free-solid-svg-icons";
import { useSettings } from "../SettingsContext";
import SettingsModal from "./SettingsModal";

const SensitivityIndicator = () => {
  const { settings } = useSettings();
  const [showSettings, setShowSettings] = useState(false);

  const getSensitivityLevel = (value) => {
    if (value < 0.5)
      return { level: "Very Low", color: "info", icon: faCheckCircle };
    if (value < 0.8)
      return { level: "Low", color: "success", icon: faCheckCircle };
    if (value < 1.2) return { level: "Normal", color: "primary", icon: faEye };
    if (value < 1.5)
      return { level: "High", color: "warning", icon: faExclamationTriangle };
    return { level: "Very High", color: "danger", icon: faExclamationTriangle };
  };

  const sensitivity = settings.detectionSensitivity;
  const { level, color, icon } = getSensitivityLevel(sensitivity);
  const feedbackRate = settings.feedbackLearningRate;
  const feedbackSummary = settings.feedbackSummary;
  const history = Array.isArray(settings.feedbackHistory)
    ? settings.feedbackHistory
    : [];
  const lastSnapshot = history.length > 0 ? history[history.length - 1] : null;
  const priorSnapshot = history.length > 1 ? history[history.length - 2] : null;
  const globalDelta =
    lastSnapshot && priorSnapshot
      ? lastSnapshot.globalAdjustment - priorSnapshot.globalAdjustment
      : null;
  const formatSmallNumber = (value, digits = 6) => {
    if (typeof value !== "number" || Number.isNaN(value)) {
      return "-";
    }
    return value.toFixed(digits);
  };

  if (settings.loading) {
    return (
      <Card className="mb-3 border-secondary">
        <Card.Body className="py-2">
          <div className="d-flex align-items-center">
            <Spinner size="sm" className="me-2" />
            <span className="text-muted">Loading ML settings...</span>
          </div>
        </Card.Body>
      </Card>
    );
  }

  return (
    <>
      <Card className={`mb-3 border-${color}`}>
        <Card.Body className="py-2">
          <div className="d-flex justify-content-between align-items-center">
            <div className="d-flex align-items-center">
              <FontAwesomeIcon icon={icon} className={`text-${color} me-2`} />
              <span className="small fw-bold">
                ML Detection Mode:
                <Badge bg={color} className="ms-2">
                  {level} ({sensitivity})
                </Badge>
              </span>
            </div>
            <Button
              variant="outline-secondary"
              size="sm"
              onClick={() => setShowSettings(true)}
              className="py-1 px-2"
            >
              <FontAwesomeIcon icon={faCog} className="me-1" />
              Adjust
            </Button>
          </div>
          <div className="small text-muted mt-1">
            {sensitivity < 1.0
              ? "Conservative detection - fewer boxes, less false positives"
              : sensitivity > 1.0
              ? "Sensitive detection - more boxes, may include minor anomalies"
              : "Balanced detection - recommended for most cases"}
          </div>
          <div className="small text-muted">
            Feedback learning rate: {formatSmallNumber(feedbackRate, 5)}
            {feedbackSummary?.annotationSamples > 0 && (
              <>
                {" "}| Global bias{" "}
                {formatSmallNumber(feedbackSummary.globalAdjustment)}
              </>
            )}
          </div>
          <div className="small text-muted">
            {feedbackSummary?.annotationSamples > 0
              ? `Grounded in ${feedbackSummary.annotationSamples} annotated result${
                  feedbackSummary.annotationSamples === 1 ? "" : "s"
                }.`
              : "Awaiting user feedback to calibrate confidences."}
          </div>
          {lastSnapshot && (
            <div className="small text-muted">
              Last snapshot {new Date(lastSnapshot.createdAt).toLocaleString()}
              {globalDelta !== null && (
                <>
                  {" "}(Δglobal {formatSmallNumber(globalDelta)})
                </>
              )}
            </div>
          )}
        </Card.Body>
      </Card>

      <SettingsModal
        show={showSettings}
        onHide={() => setShowSettings(false)}
      />
    </>
  );
};

export default SensitivityIndicator;
