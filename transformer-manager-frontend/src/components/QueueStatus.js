import React, { useState, useEffect } from "react";
import axios from "axios";
import { Badge, Dropdown, ListGroup, Spinner } from "react-bootstrap";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faClock,
  faSpinner,
  faCheckCircle,
  faExclamationTriangle,
  faRobot,
} from "@fortawesome/free-solid-svg-icons";
import { useAuth } from "../AuthContext";

const QueueStatus = () => {
  const [queueStatus, setQueueStatus] = useState(null);
  const [recentJobs, setRecentJobs] = useState([]);
  const { token, isAuthenticated } = useAuth();

  useEffect(() => {
    if (isAuthenticated) {
      fetchQueueStatus();

      // Poll every 10 seconds
      const interval = setInterval(fetchQueueStatus, 10000);

      return () => clearInterval(interval);
    }
  }, [isAuthenticated, token]);

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

  if (!isAuthenticated || !queueStatus) {
    return null;
  }

  const totalActive = queueStatus.queuedCount + queueStatus.processingCount;

  return (
    <Dropdown align="end">
      <Dropdown.Toggle
        variant="outline-info"
        size="sm"
        id="queue-status-dropdown"
      >
        <FontAwesomeIcon icon={faRobot} className="me-2" />
        Analysis Queue
        {totalActive > 0 && (
          <Badge bg="warning" className="ms-2">
            {totalActive}
          </Badge>
        )}
      </Dropdown.Toggle>

      <Dropdown.Menu style={{ minWidth: "300px" }}>
        <Dropdown.Header>
          <FontAwesomeIcon icon={faRobot} className="me-2" />
          Anomaly Analysis Queue
        </Dropdown.Header>

        <div className="px-3 py-2">
          <div className="d-flex justify-content-between align-items-center mb-2">
            <span className="text-muted">Queued:</span>
            <Badge bg="secondary">
              <FontAwesomeIcon icon={faClock} className="me-1" />
              {queueStatus.queuedCount}
            </Badge>
          </div>

          <div className="d-flex justify-content-between align-items-center">
            <span className="text-muted">Processing:</span>
            <Badge bg="warning">
              <FontAwesomeIcon icon={faSpinner} spin className="me-1" />
              {queueStatus.processingCount}
            </Badge>
          </div>
        </div>

        {totalActive === 0 && (
          <div className="px-3 py-2 text-center text-muted">
            <FontAwesomeIcon icon={faCheckCircle} className="me-2" />
            All analysis jobs completed
          </div>
        )}

        <Dropdown.Divider />

        <div className="px-3 py-1">
          <small className="text-muted">Auto-refreshes every 10 seconds</small>
        </div>
      </Dropdown.Menu>
    </Dropdown>
  );
};

export default QueueStatus;
