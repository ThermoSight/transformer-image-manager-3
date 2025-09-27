import React, { useState, useRef, useEffect, useCallback } from "react";
import { Modal, Button, ButtonGroup } from "react-bootstrap";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faSearchPlus,
  faSearchMinus,
  faSearch,
  faExpand,
  faChevronLeft,
  faChevronRight,
  faTimes,
} from "@fortawesome/free-solid-svg-icons";
import "./ImageViewer.css";

const ImageViewer = ({
  show,
  onHide,
  images = [],
  currentIndex = 0,
  title = "Image Viewer",
}) => {
  const [scale, setScale] = useState(1);
  const [position, setPosition] = useState({ x: 0, y: 0 });
  const [isDragging, setIsDragging] = useState(false);
  const [dragStart, setDragStart] = useState({ x: 0, y: 0 });
  const [currentImageIndex, setCurrentImageIndex] = useState(currentIndex);
  const imageRef = useRef(null);
  const containerRef = useRef(null);

  // Reset when modal opens or image changes
  useEffect(() => {
    if (show) {
      setScale(1);
      setPosition({ x: 0, y: 0 });
      setCurrentImageIndex(currentIndex);
    }
  }, [show, currentIndex]);

  // Reset when changing images
  useEffect(() => {
    setScale(1);
    setPosition({ x: 0, y: 0 });
  }, [currentImageIndex]);

  // Zoom functions
  const zoomIn = useCallback(() => {
    setScale((prev) => Math.min(prev * 1.3, 5));
  }, []);

  const zoomOut = useCallback(() => {
    setScale((prev) => Math.max(prev / 1.3, 0.2));
  }, []);

  const resetZoom = useCallback(() => {
    setScale(1);
    setPosition({ x: 0, y: 0 });
  }, []);

  const fitToScreen = useCallback(() => {
    if (imageRef.current && containerRef.current) {
      const containerRect = containerRef.current.getBoundingClientRect();
      const imageNaturalWidth = imageRef.current.naturalWidth;
      const imageNaturalHeight = imageRef.current.naturalHeight;

      if (imageNaturalWidth && imageNaturalHeight) {
        const scaleX = (containerRect.width - 40) / imageNaturalWidth;
        const scaleY = (containerRect.height - 40) / imageNaturalHeight;
        const newScale = Math.min(scaleX, scaleY, 1);

        setScale(newScale);
        setPosition({ x: 0, y: 0 });
      }
    }
  }, []);

  // Navigation functions
  const nextImage = useCallback(() => {
    if (images.length > 1) {
      setCurrentImageIndex((prev) => (prev + 1) % images.length);
    }
  }, [images.length]);

  const prevImage = useCallback(() => {
    if (images.length > 1) {
      setCurrentImageIndex(
        (prev) => (prev - 1 + images.length) % images.length
      );
    }
  }, [images.length]);

  // Mouse drag handlers
  const handleMouseDown = useCallback(
    (e) => {
      if (scale > 1) {
        setIsDragging(true);
        setDragStart({
          x: e.clientX - position.x,
          y: e.clientY - position.y,
        });
        e.preventDefault();
      }
    },
    [scale, position]
  );

  const handleMouseMove = useCallback(
    (e) => {
      if (isDragging && scale > 1) {
        setPosition({
          x: e.clientX - dragStart.x,
          y: e.clientY - dragStart.y,
        });
      }
    },
    [isDragging, scale, dragStart]
  );

  const handleMouseUp = useCallback(() => {
    setIsDragging(false);
  }, []);

  // Touch handlers for mobile
  const handleTouchStart = useCallback(
    (e) => {
      if (e.touches.length === 1 && scale > 1) {
        const touch = e.touches[0];
        setIsDragging(true);
        setDragStart({
          x: touch.clientX - position.x,
          y: touch.clientY - position.y,
        });
      }
    },
    [scale, position]
  );

  const handleTouchMove = useCallback(
    (e) => {
      if (isDragging && e.touches.length === 1 && scale > 1) {
        const touch = e.touches[0];
        setPosition({
          x: touch.clientX - dragStart.x,
          y: touch.clientY - dragStart.y,
        });
        e.preventDefault();
      }
    },
    [isDragging, scale, dragStart]
  );

  // Wheel zoom
  const handleWheel = useCallback(
    (e) => {
      e.preventDefault();
      const rect = containerRef.current.getBoundingClientRect();
      const x = e.clientX - rect.left - rect.width / 2;
      const y = e.clientY - rect.top - rect.height / 2;

      const delta = e.deltaY > 0 ? 0.9 : 1.1;
      const newScale = Math.max(0.2, Math.min(5, scale * delta));

      if (newScale !== scale) {
        const factor = newScale / scale - 1;
        setScale(newScale);
        setPosition((prev) => ({
          x: prev.x - x * factor,
          y: prev.y - y * factor,
        }));
      }
    },
    [scale]
  );

  // Keyboard shortcuts
  useEffect(() => {
    const handleKeyPress = (e) => {
      if (!show) return;

      switch (e.key) {
        case "Escape":
          onHide();
          break;
        case "+":
        case "=":
          e.preventDefault();
          zoomIn();
          break;
        case "-":
          e.preventDefault();
          zoomOut();
          break;
        case "0":
          e.preventDefault();
          resetZoom();
          break;
        case "f":
        case "F":
          e.preventDefault();
          fitToScreen();
          break;
        case "ArrowLeft":
          e.preventDefault();
          prevImage();
          break;
        case "ArrowRight":
          e.preventDefault();
          nextImage();
          break;
        default:
          break;
      }
    };

    if (show) {
      document.addEventListener("keydown", handleKeyPress);
      document.addEventListener("mousemove", handleMouseMove);
      document.addEventListener("mouseup", handleMouseUp);
    }

    return () => {
      document.removeEventListener("keydown", handleKeyPress);
      document.removeEventListener("mousemove", handleMouseMove);
      document.removeEventListener("mouseup", handleMouseUp);
    };
  }, [
    show,
    onHide,
    zoomIn,
    zoomOut,
    resetZoom,
    fitToScreen,
    nextImage,
    prevImage,
    handleMouseMove,
    handleMouseUp,
  ]);

  if (!images || images.length === 0) return null;

  const currentImage = images[currentImageIndex];
  const imageUrl =
    typeof currentImage === "string"
      ? currentImage
      : currentImage?.filePath
      ? `http://localhost:8080${currentImage.filePath}`
      : currentImage?.url || currentImage;

  return (
    <Modal
      show={show}
      onHide={onHide}
      size="xl"
      centered
      className="image-viewer-modal"
      backdrop="static"
      fullscreen
    >
      <Modal.Header className="image-viewer-header">
        <Modal.Title>
          {title}{" "}
          {images.length > 1 &&
            `(${currentImageIndex + 1} of ${images.length})`}
        </Modal.Title>
        <Button
          variant="outline-light"
          onClick={onHide}
          className="btn-close-custom"
        >
          <FontAwesomeIcon icon={faTimes} />
        </Button>
      </Modal.Header>

      <Modal.Body className="image-viewer-body p-0">
        <div
          ref={containerRef}
          className="image-container"
          onWheel={handleWheel}
        >
          <img
            ref={imageRef}
            src={imageUrl}
            alt={`Image ${currentImageIndex + 1}`}
            className="viewer-image"
            style={{
              transform: `translate(${position.x}px, ${position.y}px) scale(${scale})`,
              cursor:
                scale > 1 ? (isDragging ? "grabbing" : "grab") : "default",
            }}
            onMouseDown={handleMouseDown}
            onTouchStart={handleTouchStart}
            onTouchMove={handleTouchMove}
            onTouchEnd={handleMouseUp}
            draggable={false}
            onLoad={fitToScreen}
          />
        </div>

        {/* Navigation arrows for multiple images */}
        {images.length > 1 && (
          <>
            <Button
              variant="outline-light"
              className="nav-arrow nav-arrow-left"
              onClick={prevImage}
              title="Previous image (←)"
            >
              <FontAwesomeIcon icon={faChevronLeft} />
            </Button>
            <Button
              variant="outline-light"
              className="nav-arrow nav-arrow-right"
              onClick={nextImage}
              title="Next image (→)"
            >
              <FontAwesomeIcon icon={faChevronRight} />
            </Button>
          </>
        )}

        {/* Control panel */}
        <div className="image-controls">
          <ButtonGroup size="sm">
            <Button
              variant="outline-light"
              onClick={zoomOut}
              title="Zoom Out (-)"
            >
              <FontAwesomeIcon icon={faSearchMinus} />
            </Button>
            <Button
              variant="outline-light"
              onClick={resetZoom}
              title="Reset Zoom (0)"
            >
              <FontAwesomeIcon icon={faSearch} />
            </Button>
            <Button
              variant="outline-light"
              onClick={zoomIn}
              title="Zoom In (+)"
            >
              <FontAwesomeIcon icon={faSearchPlus} />
            </Button>
            <Button
              variant="outline-light"
              onClick={fitToScreen}
              title="Fit to Screen (F)"
            >
              <FontAwesomeIcon icon={faExpand} />
            </Button>
          </ButtonGroup>

          <div className="zoom-info">{Math.round(scale * 100)}%</div>
        </div>

        {/* Image thumbnails for navigation */}
        {images.length > 1 && (
          <div className="image-thumbnails">
            {images.map((img, index) => {
              const thumbUrl =
                typeof img === "string"
                  ? img
                  : img?.filePath
                  ? `http://localhost:8080${img.filePath}`
                  : img?.url || img;

              return (
                <div
                  key={index}
                  className={`thumbnail ${
                    index === currentImageIndex ? "active" : ""
                  }`}
                  onClick={() => setCurrentImageIndex(index)}
                  title={`Image ${index + 1}`}
                >
                  <img src={thumbUrl} alt={`Thumbnail ${index + 1}`} />
                </div>
              );
            })}
          </div>
        )}
      </Modal.Body>

      <Modal.Footer className="image-viewer-footer">
        <small className="text-muted">
          <strong>Controls:</strong> Mouse wheel to zoom • Drag to pan • Arrow
          keys to navigate • ESC to close
        </small>
      </Modal.Footer>
    </Modal>
  );
};

export default ImageViewer;
