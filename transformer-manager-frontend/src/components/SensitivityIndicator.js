import React from "react";
import { Badge, OverlayTrigger, Tooltip } from "react-bootstrap";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faTachometerAlt } from "@fortawesome/free-solid-svg-icons";
import { useSettings } from "../SettingsContext";

const SensitivityIndicator = ({ size = "sm", className = "" }) => {
  const { settings } = useSettings();

  const getSensitivityLabel = (value) => {
    if (value < 0.5) return "Very Low";
    if (value < 0.8) return "Low";
    if (value < 1.2) return "Normal";
    if (value < 1.5) return "High";
    return "Very High";
  };

  const getSensitivityColor = (value) => {
    if (value < 0.5) return "info";
    if (value < 0.8) return "success";
    if (value < 1.2) return "primary";
    if (value < 1.5) return "warning";
    return "danger";
  };

  const sensitivity = settings.detectionSensitivity;

  const tooltip = (
    <Tooltip>
      <div>
        <strong>Detection Sensitivity: {sensitivity}</strong>
        <br />
        {getSensitivityLabel(sensitivity)} sensitivity level
        <br />
        <small>All analyses use this sensitivity setting</small>
      </div>
    </Tooltip>
  );

  return (
    <OverlayTrigger placement="bottom" overlay={tooltip}>
      <Badge
        bg={getSensitivityColor(sensitivity)}
        className={`d-flex align-items-center ${className}`}
        style={{ cursor: "help" }}
      >
        <FontAwesomeIcon icon={faTachometerAlt} className="me-1" />
        {getSensitivityLabel(sensitivity)} ({sensitivity})
      </Badge>
    </OverlayTrigger>
  );
};

export default SensitivityIndicator;
