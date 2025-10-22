import React, { useEffect, useMemo, useState } from "react";
import { Alert, Badge, Button, Card, Spinner, Table } from "react-bootstrap";
import axiosInstance from "../axiosConfig";
import { useAuth } from "../AuthContext";

const statusVariant = {
  SUCCEEDED: "success",
  RUNNING: "info",
  QUEUED: "secondary",
  FAILED: "danger",
  SKIPPED: "warning",
};

const prettyDate = (value) => {
  if (!value) {
    return "-";
  }
  const date = typeof value === "string" ? new Date(value) : value;
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString();
};

const MetricsSummary = ({ metrics }) => {
  const classSummary = useMemo(() => {
    if (!metrics?.class_counts) {
      return "-";
    }
    return Object.entries(metrics.class_counts)
      .map(([label, count]) => `${label}: ${count}`)
      .join(", ");
  }, [metrics]);

  const actionSummary = useMemo(() => {
    if (!metrics?.action_counts) {
      return "-";
    }
    return Object.entries(metrics.action_counts)
      .map(([label, count]) => `${label}: ${count}`)
      .join(", ");
  }, [metrics]);

  return (
    <div className="small text-muted">
      <div>Classes: {classSummary || "-"}</div>
      <div>Actions: {actionSummary || "-"}</div>
    </div>
  );
};

const ModelTrainingHistory = () => {
  const { user } = useAuth();
  const [runs, setRuns] = useState([]);
  const [loading, setLoading] = useState(false);
  const [triggering, setTriggering] = useState(false);
  const [error, setError] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);

  const loadRuns = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await axiosInstance.get("/model-training/runs");
      setRuns(response.data || []);
    } catch (err) {
      setError("Failed to load model update history.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadRuns();
  }, []);

  const triggerRun = async () => {
    setTriggering(true);
    setError(null);
    setSuccessMessage(null);
    try {
      await axiosInstance.post("/model-training/runs/trigger", {
        requestedBy: user?.username,
        notes: "Manual trigger from dashboard",
      });
      setSuccessMessage("Model update run queued successfully.");
      await loadRuns();
    } catch (err) {
      setError("Failed to queue model update run.");
    } finally {
      setTriggering(false);
    }
  };

  return (
    <div className="mt-4">
      <Card>
        <Card.Header className="d-flex justify-content-between align-items-center">
          <div>
            <h5 className="mb-0">Model Feedback Learning</h5>
            <small className="text-muted">
              Every user annotation feeds a reinforcement-style update pipeline.
            </small>
          </div>
          <div className="d-flex gap-2">
            <Button
              variant="outline-secondary"
              onClick={loadRuns}
              disabled={loading || triggering}
            >
              Refresh
            </Button>
            <Button
              onClick={triggerRun}
              disabled={triggering}
              variant="primary"
            >
              {triggering ? (
                <>
                  <Spinner
                    size="sm"
                    animation="border"
                    className="me-2"
                  />
                  Queuing...
                </>
              ) : (
                "Trigger Model Update"
              )}
            </Button>
          </div>
        </Card.Header>
        <Card.Body>
          {error && (
            <Alert variant="danger" onClose={() => setError(null)} dismissible>
              {error}
            </Alert>
          )}
          {successMessage && (
            <Alert
              variant="success"
              onClose={() => setSuccessMessage(null)}
              dismissible
            >
              {successMessage}
            </Alert>
          )}

          {loading ? (
            <div className="d-flex justify-content-center py-4">
              <Spinner animation="border" />
            </div>
          ) : (
            <Table responsive hover size="sm" className="align-middle">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Status</th>
                  <th>Version</th>
                  <th>Triggered By</th>
                  <th>New Data</th>
                  <th>Created</th>
                  <th>Completed</th>
                  <th>Summary</th>
                </tr>
              </thead>
              <tbody>
                {runs.length === 0 && (
                  <tr>
                    <td colSpan="8" className="text-center text-muted py-4">
                      No model update history yet. Annotate an image or trigger a
                      run manually to get started.
                    </td>
                  </tr>
                )}
                {runs.map((run) => {
                  const variant = statusVariant[run.status] || "secondary";
                  const metrics = run.metrics || {};
                  const newAnnotations =
                    run.appendedAnnotations ?? metrics.appended_annotations ?? 0;
                  const newBoxes =
                    run.appendedBoxes ?? metrics.appended_boxes ?? 0;

                  const runIdShort = run.runId
                    ? run.runId.slice(0, 8)
                    : run.id;

                  return (
                    <tr key={run.runId || run.id}>
                      <td>{runIdShort}</td>
                      <td>
                        <Badge bg={variant}>{run.status}</Badge>
                      </td>
                      <td>{run.versionTag || "pending"}</td>
                      <td>{run.requestedBy || "auto"}</td>
                      <td>
                        <div>{newAnnotations} annotations</div>
                        <div className="text-muted small">{newBoxes} boxes</div>
                      </td>
                      <td>{prettyDate(run.createdAt)}</td>
                      <td>{prettyDate(run.completedAt)}</td>
                      <td>
                        <div className="small">
                          <strong>Feedback:</strong> {run.feedbackSummary || "-"}
                        </div>
                        <MetricsSummary metrics={metrics} />
                        {run.modelOutputPath && run.status === "SUCCEEDED" && (
                          <div className="small text-muted">
                            Artifact: {run.modelOutputPath}
                          </div>
                        )}
                        {run.errorMessage && (
                          <div className="text-danger small">{run.errorMessage}</div>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </Table>
          )}
        </Card.Body>
      </Card>
    </div>
  );
};

export default ModelTrainingHistory;
