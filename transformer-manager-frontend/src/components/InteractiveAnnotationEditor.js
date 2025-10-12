import React, { useState, useEffect, useRef, useCallback } from "react";
import {
  Modal,
  Button,
  Alert,
  Form,
  Spinner,
  Badge,
  Card,
  Row,
  Col,
  Table,
  Dropdown,
} from "react-bootstrap";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faSave,
  faEdit,
  faPlus,
  faTrash,
  faUndo,
  faRedo,
  faDownload,
  faTimes,
  faExpand,
  faCompress,
} from "@fortawesome/free-solid-svg-icons";
import axios from "axios";
import { useAuth } from "../AuthContext";

const InteractiveAnnotationEditor = ({
  show,
  onHide,
  analysisJobId,
  boxedImagePath,
  originalResultJson,
}) => {
  const { token, isAuthenticated } = useAuth();
  const canvasRef = useRef(null);
  const imageRef = useRef(null);
  const [image, setImage] = useState(null);
  const [annotation, setAnnotation] = useState(null);
  const [boxes, setBoxes] = useState([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [comments, setComments] = useState("");
  const [isFullscreen, setIsFullscreen] = useState(false);

  // Drawing state
  const [isDrawing, setIsDrawing] = useState(false);
  const [drawingBox, setDrawingBox] = useState(null);
  const [selectedBox, setSelectedBox] = useState(null);
  const [dragMode, setDragMode] = useState(null); // 'move', 'resize-nw', 'resize-ne', etc.
  const [dragStart, setDragStart] = useState({ x: 0, y: 0 });
  const [originalBoxState, setOriginalBoxState] = useState(null);

  // History for undo/redo
  const [history, setHistory] = useState([]);
  const [historyIndex, setHistoryIndex] = useState(-1);

  // Available annotation types
  const ANNOTATION_TYPES = [
    "Loose Joint (Faulty)",
    "Point Overload (Faulty)",
    "Full Wire Overload (Faulty)",
    "Tiny Faulty Spot",
    "Tiny Potential Spot",
    "Custom Anomaly",
  ];

  const [newBoxType, setNewBoxType] = useState(ANNOTATION_TYPES[0]);

  // Load annotation data when modal opens
  useEffect(() => {
    if (show && analysisJobId) {
      loadAnnotationData();
    }
  }, [show, analysisJobId]);

  // Load image when boxedImagePath changes
  useEffect(() => {
    if (boxedImagePath) {
      loadImage();
    }
  }, [boxedImagePath]);

  // Draw canvas when boxes or image changes
  useEffect(() => {
    if (image && canvasRef.current) {
      drawCanvas();
    }
  }, [boxes, image, selectedBox, drawingBox]);

  const loadAnnotationData = async () => {
    setLoading(true);
    setError("");

    try {
      // Prepare headers - include authorization if available
      const headers = {};
      if (isAuthenticated && token) {
        headers.Authorization = `Bearer ${token}`;
      }

      const response = await axios.get(
        `http://localhost:8080/api/annotations/analysis-job/${analysisJobId}`,
        { headers }
      );

      setAnnotation(response.data);
      setComments(response.data.comments || "");

      // Convert annotation boxes to canvas-friendly format
      const annotationBoxes = response.data.annotationBoxes.map(
        (box, index) => ({
          id: box.id || `temp-${index}`,
          x: box.x,
          y: box.y,
          width: box.width,
          height: box.height,
          type: box.type,
          confidence: box.confidence,
          action: box.action,
          comments: box.comments || "",
          isUserAdded: box.confidence === null,
        })
      );

      setBoxes(annotationBoxes);
      addToHistory(annotationBoxes);
    } catch (err) {
      console.error("Failed to load annotation data", err);
      setError(
        `Failed to load annotation data: ${
          err.response?.data?.message || err.message
        }`
      );
    } finally {
      setLoading(false);
    }
  };

  const loadImage = () => {
    const img = new Image();
    img.crossOrigin = "anonymous";
    img.onload = () => {
      setImage(img);
      if (canvasRef.current) {
        const canvas = canvasRef.current;
        canvas.width = img.width;
        canvas.height = img.height;
      }
    };
    img.onerror = () => {
      setError("Failed to load image");
    };
    img.src = `http://localhost:8080/api/files${boxedImagePath}`;
  };

  const drawCanvas = () => {
    if (!canvasRef.current || !image) return;

    const canvas = canvasRef.current;
    const ctx = canvas.getContext("2d");

    // Clear canvas
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    // Draw image
    ctx.drawImage(image, 0, 0);

    // Draw all boxes
    boxes.forEach((box) => {
      drawBox(ctx, box, box.id === selectedBox?.id);
    });

    // Draw current drawing box
    if (drawingBox) {
      drawBox(ctx, drawingBox, false, true);
    }
  };

  const drawBox = (ctx, box, isSelected = false, isDrawing = false) => {
    const { x, y, width, height, type, confidence, isUserAdded } = box;

    // Set box color based on type and user/AI origin
    let color = isUserAdded ? "#00ff00" : "#ff0000"; // Green for user, red for AI
    if (type.includes("Potential")) color = "#ffff00"; // Yellow for potential issues

    // Draw box
    ctx.strokeStyle = isSelected ? "#ffffff" : color;
    ctx.lineWidth = isSelected ? 3 : 2;
    ctx.strokeRect(x, y, width, height);

    // Fill for drawing box
    if (isDrawing) {
      ctx.fillStyle = color + "20";
      ctx.fillRect(x, y, width, height);
    }

    // Draw selection handles for selected box
    if (isSelected) {
      drawSelectionHandles(ctx, box);
    }

    // Draw label
    const label =
      confidence !== null
        ? `${type} (${(confidence * 100).toFixed(1)}%)`
        : type;
    ctx.fillStyle = "#ffffff";
    ctx.fillRect(x, y - 20, ctx.measureText(label).width + 10, 20);
    ctx.fillStyle = "#000000";
    ctx.font = "12px Arial";
    ctx.fillText(label, x + 5, y - 5);
  };

  const drawSelectionHandles = (ctx, box) => {
    const { x, y, width, height } = box;
    const handleSize = 8;

    ctx.fillStyle = "#ffffff";
    ctx.strokeStyle = "#000000";
    ctx.lineWidth = 1;

    // Corner handles
    const handles = [
      { x: x - handleSize / 2, y: y - handleSize / 2 }, // NW
      { x: x + width - handleSize / 2, y: y - handleSize / 2 }, // NE
      { x: x + width - handleSize / 2, y: y + height - handleSize / 2 }, // SE
      { x: x - handleSize / 2, y: y + height - handleSize / 2 }, // SW
    ];

    handles.forEach((handle) => {
      ctx.fillRect(handle.x, handle.y, handleSize, handleSize);
      ctx.strokeRect(handle.x, handle.y, handleSize, handleSize);
    });
  };

  const getMousePos = (e) => {
    const canvas = canvasRef.current;
    const rect = canvas.getBoundingClientRect();
    const scaleX = canvas.width / rect.width;
    const scaleY = canvas.height / rect.height;

    return {
      x: (e.clientX - rect.left) * scaleX,
      y: (e.clientY - rect.top) * scaleY,
    };
  };

  const getBoxAtPoint = (x, y) => {
    // Check in reverse order (top to bottom)
    for (let i = boxes.length - 1; i >= 0; i--) {
      const box = boxes[i];
      if (
        x >= box.x &&
        x <= box.x + box.width &&
        y >= box.y &&
        y <= box.y + box.height
      ) {
        return box;
      }
    }
    return null;
  };

  const getResizeHandle = (x, y, box) => {
    if (!box) return null;

    const handleSize = 8;
    const tolerance = handleSize / 2;

    // Check corner handles
    if (Math.abs(x - box.x) <= tolerance && Math.abs(y - box.y) <= tolerance)
      return "nw";
    if (
      Math.abs(x - (box.x + box.width)) <= tolerance &&
      Math.abs(y - box.y) <= tolerance
    )
      return "ne";
    if (
      Math.abs(x - (box.x + box.width)) <= tolerance &&
      Math.abs(y - (box.y + box.height)) <= tolerance
    )
      return "se";
    if (
      Math.abs(x - box.x) <= tolerance &&
      Math.abs(y - (box.y + box.height)) <= tolerance
    )
      return "sw";

    return null;
  };

  const handleMouseDown = (e) => {
    if (!image) return;

    const { x, y } = getMousePos(e);
    const clickedBox = getBoxAtPoint(x, y);

    if (clickedBox) {
      setSelectedBox(clickedBox);
      const resizeHandle = getResizeHandle(x, y, clickedBox);

      if (resizeHandle) {
        setDragMode(`resize-${resizeHandle}`);
      } else {
        setDragMode("move");
      }

      setDragStart({ x, y });
      setOriginalBoxState({ ...clickedBox });
    } else {
      // Start drawing new box
      setSelectedBox(null);
      setIsDrawing(true);
      setDrawingBox({
        x,
        y,
        width: 0,
        height: 0,
        type: newBoxType,
        confidence: null,
        isUserAdded: true,
      });
      setDragStart({ x, y });
    }
  };

  const handleMouseMove = (e) => {
    if (!image) return;

    const { x, y } = getMousePos(e);

    if (isDrawing && drawingBox) {
      // Update drawing box
      const newWidth = x - dragStart.x;
      const newHeight = y - dragStart.y;

      setDrawingBox({
        ...drawingBox,
        x: Math.min(dragStart.x, x),
        y: Math.min(dragStart.y, y),
        width: Math.abs(newWidth),
        height: Math.abs(newHeight),
      });
    } else if (dragMode && selectedBox && originalBoxState) {
      // Update selected box
      const dx = x - dragStart.x;
      const dy = y - dragStart.y;

      let updatedBox = { ...originalBoxState };

      if (dragMode === "move") {
        updatedBox.x = originalBoxState.x + dx;
        updatedBox.y = originalBoxState.y + dy;
      } else if (dragMode.startsWith("resize-")) {
        const handle = dragMode.split("-")[1];

        switch (handle) {
          case "nw":
            updatedBox.x = originalBoxState.x + dx;
            updatedBox.y = originalBoxState.y + dy;
            updatedBox.width = originalBoxState.width - dx;
            updatedBox.height = originalBoxState.height - dy;
            break;
          case "ne":
            updatedBox.y = originalBoxState.y + dy;
            updatedBox.width = originalBoxState.width + dx;
            updatedBox.height = originalBoxState.height - dy;
            break;
          case "se":
            updatedBox.width = originalBoxState.width + dx;
            updatedBox.height = originalBoxState.height + dy;
            break;
          case "sw":
            updatedBox.x = originalBoxState.x + dx;
            updatedBox.width = originalBoxState.width - dx;
            updatedBox.height = originalBoxState.height + dy;
            break;
        }
      }

      // Ensure minimum size and bounds
      updatedBox.width = Math.max(10, updatedBox.width);
      updatedBox.height = Math.max(10, updatedBox.height);
      updatedBox.x = Math.max(
        0,
        Math.min(image.width - updatedBox.width, updatedBox.x)
      );
      updatedBox.y = Math.max(
        0,
        Math.min(image.height - updatedBox.height, updatedBox.y)
      );

      // Update boxes array
      setBoxes(
        boxes.map((box) =>
          box.id === selectedBox.id
            ? { ...updatedBox, action: "MODIFIED" }
            : box
        )
      );
      setSelectedBox(updatedBox);
    } else {
      // Update cursor based on hover
      const hoveredBox = getBoxAtPoint(x, y);
      if (hoveredBox) {
        const resizeHandle = getResizeHandle(x, y, hoveredBox);
        if (resizeHandle) {
          canvasRef.current.style.cursor = `${resizeHandle}-resize`;
        } else {
          canvasRef.current.style.cursor = "move";
        }
      } else {
        canvasRef.current.style.cursor = "crosshair";
      }
    }
  };

  const handleMouseUp = () => {
    if (isDrawing && drawingBox) {
      if (drawingBox.width > 10 && drawingBox.height > 10) {
        const newBox = {
          ...drawingBox,
          id: `new-${Date.now()}`,
          action: "ADDED",
        };
        const newBoxes = [...boxes, newBox];
        setBoxes(newBoxes);
        addToHistory(newBoxes);
      }
      setIsDrawing(false);
      setDrawingBox(null);
    } else if (dragMode && selectedBox) {
      addToHistory(boxes);
    }

    setDragMode(null);
    setDragStart({ x: 0, y: 0 });
    setOriginalBoxState(null);
  };

  const addToHistory = (newBoxes) => {
    const newHistory = history.slice(0, historyIndex + 1);
    newHistory.push(JSON.parse(JSON.stringify(newBoxes)));
    setHistory(newHistory);
    setHistoryIndex(newHistory.length - 1);
  };

  const undo = () => {
    if (historyIndex > 0) {
      setHistoryIndex(historyIndex - 1);
      setBoxes(JSON.parse(JSON.stringify(history[historyIndex - 1])));
      setSelectedBox(null);
    }
  };

  const redo = () => {
    if (historyIndex < history.length - 1) {
      setHistoryIndex(historyIndex + 1);
      setBoxes(JSON.parse(JSON.stringify(history[historyIndex + 1])));
      setSelectedBox(null);
    }
  };

  const deleteSelectedBox = () => {
    if (selectedBox) {
      const newBoxes = boxes.filter((box) => box.id !== selectedBox.id);
      setBoxes(newBoxes);
      setSelectedBox(null);
      addToHistory(newBoxes);
    }
  };

  const updateSelectedBoxType = (newType) => {
    if (selectedBox) {
      const updatedBoxes = boxes.map((box) =>
        box.id === selectedBox.id
          ? { ...box, type: newType, action: "MODIFIED" }
          : box
      );
      setBoxes(updatedBoxes);
      setSelectedBox({ ...selectedBox, type: newType });
      addToHistory(updatedBoxes);
    }
  };

  const saveAnnotations = async () => {
    setSaving(true);
    setError("");
    setSuccess("");

    try {
      const boxDTOs = boxes.map((box) => ({
        x: Math.round(box.x),
        y: Math.round(box.y),
        width: Math.round(box.width),
        height: Math.round(box.height),
        type: box.type,
        confidence: box.confidence,
        action: box.action || "UNCHANGED",
        comments: box.comments || "",
      }));

      // Prepare headers - include authorization if available
      const headers = {
        "Content-Type": "application/json",
      };
      if (isAuthenticated && token) {
        headers.Authorization = `Bearer ${token}`;
      }

      await axios.put(
        `http://localhost:8080/api/annotations/${annotation.id}`,
        {
          boxes: boxDTOs,
          comments: comments,
        },
        { headers }
      );

      setSuccess("Annotations saved successfully! The image has been updated.");

      // Close modal after a delay
      setTimeout(() => {
        onHide();
      }, 2000);
    } catch (err) {
      console.error("Failed to save annotations", err);
      setError(
        err.response?.data?.message ||
          `Failed to save annotations: ${err.message}`
      );
    } finally {
      setSaving(false);
    }
  };

  const modalSize = isFullscreen ? "xl" : "lg";

  return (
    <Modal
      show={show}
      onHide={onHide}
      size={modalSize}
      fullscreen={isFullscreen}
      centered={!isFullscreen}
      backdrop="static"
    >
      <Modal.Header>
        <Modal.Title>Interactive Annotation Editor</Modal.Title>
        <div className="d-flex align-items-center me-3">
          <Button
            variant="outline-secondary"
            size="sm"
            onClick={() => setIsFullscreen(!isFullscreen)}
            className="me-2"
          >
            <FontAwesomeIcon icon={isFullscreen ? faCompress : faExpand} />
          </Button>
          <Button variant="outline-secondary" size="sm" onClick={onHide}>
            <FontAwesomeIcon icon={faTimes} />
          </Button>
        </div>
      </Modal.Header>

      <Modal.Body className="p-0">
        {loading && (
          <div className="text-center p-4">
            <Spinner animation="border" />
            <p className="mt-2">Loading annotation data...</p>
          </div>
        )}

        {error && (
          <Alert variant="danger" className="m-3">
            {error}
          </Alert>
        )}

        {success && (
          <Alert variant="success" className="m-3">
            {success}
          </Alert>
        )}

        <Row
          className="g-0"
          style={{ height: isFullscreen ? "calc(100vh - 120px)" : "600px" }}
        >
          {/* Toolbar */}
          <Col xs={12} className="bg-light border-bottom p-2">
            <div className="d-flex justify-content-between align-items-center flex-wrap">
              <div className="d-flex align-items-center gap-2 mb-1">
                <Button
                  variant="outline-primary"
                  size="sm"
                  onClick={undo}
                  disabled={historyIndex <= 0}
                  title="Undo"
                >
                  <FontAwesomeIcon icon={faUndo} />
                </Button>
                <Button
                  variant="outline-primary"
                  size="sm"
                  onClick={redo}
                  disabled={historyIndex >= history.length - 1}
                  title="Redo"
                >
                  <FontAwesomeIcon icon={faRedo} />
                </Button>

                <div className="vr mx-2"></div>

                <Form.Select
                  size="sm"
                  value={newBoxType}
                  onChange={(e) => setNewBoxType(e.target.value)}
                  style={{ width: "200px" }}
                >
                  {ANNOTATION_TYPES.map((type) => (
                    <option key={type} value={type}>
                      {type}
                    </option>
                  ))}
                </Form.Select>

                {selectedBox && (
                  <>
                    <Button
                      variant="outline-danger"
                      size="sm"
                      onClick={deleteSelectedBox}
                      title="Delete Selected Box"
                    >
                      <FontAwesomeIcon icon={faTrash} />
                    </Button>
                    <Dropdown>
                      <Dropdown.Toggle variant="outline-info" size="sm">
                        Change Type
                      </Dropdown.Toggle>
                      <Dropdown.Menu>
                        {ANNOTATION_TYPES.map((type) => (
                          <Dropdown.Item
                            key={type}
                            onClick={() => updateSelectedBoxType(type)}
                            active={selectedBox.type === type}
                          >
                            {type}
                          </Dropdown.Item>
                        ))}
                      </Dropdown.Menu>
                    </Dropdown>
                  </>
                )}
              </div>

              <div className="d-flex align-items-center gap-2">
                <Badge bg="info">{boxes.length} boxes</Badge>
                <Badge bg="success">
                  {boxes.filter((b) => b.isUserAdded).length} user-added
                </Badge>
                <Badge bg="primary">
                  {boxes.filter((b) => !b.isUserAdded).length} AI-generated
                </Badge>
              </div>
            </div>
          </Col>

          {/* Canvas Area */}
          <Col lg={isFullscreen ? 9 : 8} className="position-relative">
            <div
              style={{
                overflow: "auto",
                height: "100%",
                backgroundColor: "#f8f9fa",
              }}
            >
              {image && (
                <canvas
                  ref={canvasRef}
                  onMouseDown={handleMouseDown}
                  onMouseMove={handleMouseMove}
                  onMouseUp={handleMouseUp}
                  style={{
                    maxWidth: "100%",
                    maxHeight: "100%",
                    cursor: "crosshair",
                    display: "block",
                    margin: "auto",
                  }}
                />
              )}

              {!image && !loading && (
                <div className="d-flex align-items-center justify-content-center h-100">
                  <Alert variant="info">
                    No image loaded. Please select an analysis result with a
                    boxed image.
                  </Alert>
                </div>
              )}
            </div>
          </Col>

          {/* Properties Panel */}
          <Col lg={isFullscreen ? 3 : 4} className="bg-light border-start">
            <div className="p-3" style={{ height: "100%", overflow: "auto" }}>
              <h6>Annotation Properties</h6>

              {selectedBox ? (
                <Card className="mb-3">
                  <Card.Header>Selected Box</Card.Header>
                  <Card.Body>
                    <p>
                      <strong>Type:</strong> {selectedBox.type}
                    </p>
                    <p>
                      <strong>Position:</strong> ({selectedBox.x},{" "}
                      {selectedBox.y})
                    </p>
                    <p>
                      <strong>Size:</strong> {selectedBox.width} ×{" "}
                      {selectedBox.height}
                    </p>
                    {selectedBox.confidence !== null && (
                      <p>
                        <strong>Confidence:</strong>{" "}
                        {(selectedBox.confidence * 100).toFixed(1)}%
                      </p>
                    )}
                    <p>
                      <strong>Source:</strong>{" "}
                      {selectedBox.isUserAdded ? "User Added" : "AI Generated"}
                    </p>

                    <Form.Group className="mt-2">
                      <Form.Label>Comments</Form.Label>
                      <Form.Control
                        as="textarea"
                        rows={2}
                        value={selectedBox.comments || ""}
                        onChange={(e) => {
                          const updatedBoxes = boxes.map((box) =>
                            box.id === selectedBox.id
                              ? { ...box, comments: e.target.value }
                              : box
                          );
                          setBoxes(updatedBoxes);
                          setSelectedBox({
                            ...selectedBox,
                            comments: e.target.value,
                          });
                        }}
                        placeholder="Add comments for this annotation..."
                      />
                    </Form.Group>
                  </Card.Body>
                </Card>
              ) : (
                <Alert variant="info" className="mb-3">
                  Click on a box to select and edit it, or draw a new box on the
                  image.
                </Alert>
              )}

              <h6>All Annotations</h6>
              <div style={{ maxHeight: "300px", overflow: "auto" }}>
                <Table size="sm" striped>
                  <thead>
                    <tr>
                      <th>Type</th>
                      <th>Source</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {boxes.map((box, index) => (
                      <tr
                        key={box.id}
                        className={
                          selectedBox?.id === box.id ? "table-active" : ""
                        }
                        style={{ cursor: "pointer" }}
                        onClick={() => setSelectedBox(box)}
                      >
                        <td>{box.type}</td>
                        <td>
                          <Badge bg={box.isUserAdded ? "success" : "primary"}>
                            {box.isUserAdded ? "User" : "AI"}
                          </Badge>
                        </td>
                        <td>
                          <Badge bg="secondary" className="small">
                            {box.action || "UNCHANGED"}
                          </Badge>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </Table>
              </div>

              <Form.Group className="mt-3">
                <Form.Label>Overall Comments</Form.Label>
                <Form.Control
                  as="textarea"
                  rows={3}
                  value={comments}
                  onChange={(e) => setComments(e.target.value)}
                  placeholder="Add general comments about this annotation session..."
                />
              </Form.Group>
            </div>
          </Col>
        </Row>
      </Modal.Body>

      <Modal.Footer>
        <div className="d-flex justify-content-between w-100">
          <div>
            <small className="text-muted">
              Instructions: Click and drag to create new boxes. Click existing
              boxes to select and edit. Use resize handles to adjust box size.
            </small>
          </div>
          <div>
            <Button variant="secondary" onClick={onHide} className="me-2">
              Cancel
            </Button>
            <Button
              variant="primary"
              onClick={saveAnnotations}
              disabled={saving || !annotation}
            >
              {saving ? (
                <>
                  <Spinner animation="border" size="sm" className="me-2" />
                  Saving...
                </>
              ) : (
                <>
                  <FontAwesomeIcon icon={faSave} className="me-2" />
                  Save Annotations
                </>
              )}
            </Button>
          </div>
        </div>
      </Modal.Footer>
    </Modal>
  );
};

export default InteractiveAnnotationEditor;
