import React, { useState, useRef, useEffect } from "react";
import { Modal, Button, Form, InputGroup } from "react-bootstrap";
import {
  MapContainer,
  TileLayer,
  Marker,
  useMapEvents,
  Popup,
  useMap,
} from "react-leaflet";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import { FaMapMarkerAlt } from "react-icons/fa";

// Fix for default marker icons
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: require("leaflet/dist/images/marker-icon-2x.png"),
  iconUrl: require("leaflet/dist/images/marker-icon.png"),
  shadowUrl: require("leaflet/dist/images/marker-shadow.png"),
});

// Custom hand cursor for marker selection
const handCursorStyle = `
  .leaflet-container {
    cursor: grab !important;
  }
  .leaflet-marker-icon {
    cursor: pointer !important;
  }
`;

function LocationMarker({ onLocationSelect }) {
  useMapEvents({
    async click(e) {
      try {
        // Reverse geocode to get location name
        const url = `https://nominatim.openstreetmap.org/reverse?format=json&lat=${e.latlng.lat}&lon=${e.latlng.lng}`;
        const res = await fetch(url);
        const data = await res.json();

        onLocationSelect(
          {
            lat: e.latlng.lat,
            lng: e.latlng.lng,
            name:
              data.display_name ||
              `Location (${e.latlng.lat.toFixed(4)}, ${e.latlng.lng.toFixed(
                4
              )})`,
          },
          data.display_name
        );
      } catch (error) {
        console.error("Reverse geocoding failed:", error);
        onLocationSelect(
          {
            lat: e.latlng.lat,
            lng: e.latlng.lng,
            name: `Location (${e.latlng.lat.toFixed(4)}, ${e.latlng.lng.toFixed(
              4
            )})`,
          },
          ""
        );
      }
    },
  });
  return null;
}

// Helper to zoom to searched location
function ZoomToLocation({ lat, lng, trigger }) {
  const map = useMap();
  useEffect(() => {
    if (trigger && lat && lng) {
      map.setView([lat, lng], 14, { animate: true });
    }
  }, [trigger, lat, lng, map]);
  return null;
}

const LocationPicker = ({ value, onChange }) => {
  const [showModal, setShowModal] = useState(false);
  const [search, setSearch] = useState("");
  const [searchResults, setSearchResults] = useState([]);
  const [showPopup, setShowPopup] = useState(false);
  const [zoomToLocation, setZoomToLocation] = useState(false);
  const lastSearchLocation = useRef(null);

  const handleSearchChange = async (e) => {
    const searchText = e.target.value;
    setSearch(searchText);

    if (searchText.length < 3) {
      setSearchResults([]);
      return;
    }

    try {
      const url = `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(
        searchText
      )}`;
      const res = await fetch(url);
      const data = await res.json();
      setSearchResults(data);
    } catch (error) {
      console.error("Error fetching location data:", error);
      setSearchResults([]);
    }
  };

  const handleResultClick = (result) => {
    const newLocation = {
      lat: parseFloat(result.lat),
      lng: parseFloat(result.lon),
      name: result.display_name,
    };
    onChange(newLocation);
    setSearch(result.display_name);
    setSearchResults([]);
    setShowPopup(true);
    setZoomToLocation(true);
    lastSearchLocation.current = { lat: newLocation.lat, lng: newLocation.lng };
  };

  const handleMapClick = (location, displayName) => {
    onChange(location);
    setSearch(displayName);
    setShowPopup(true);
    setZoomToLocation(true);
    lastSearchLocation.current = { lat: location.lat, lng: location.lng };
  };

  // Reset zoom trigger when modal is closed
  useEffect(() => {
    if (!showModal) {
      setZoomToLocation(false);
    }
  }, [showModal]);

  return (
    <>
      {/* Inject hand cursor style */}
      <style>{handCursorStyle}</style>
      <InputGroup>
        <Form.Control
          placeholder="Enter location name..."
          value={value?.name || ""}
          onChange={(e) => onChange({ ...value, name: e.target.value })}
        />
        <Button variant="outline-secondary" onClick={() => setShowModal(true)}>
          <FaMapMarkerAlt />
        </Button>
      </InputGroup>

      <Modal
        show={showModal}
        onHide={() => setShowModal(false)}
        size="lg"
        centered
      >
        <Modal.Header closeButton>
          <Modal.Title>Select Location</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <Form.Group className="mb-2">
            <Form.Label>Search Place</Form.Label>
            <Form.Control
              type="text"
              value={search}
              onChange={handleSearchChange}
              placeholder="Search for a location..."
            />
            {searchResults.length > 0 && (
              <div
                style={{
                  maxHeight: 150,
                  overflowY: "auto",
                  border: "1px solid #eee",
                  borderRadius: 4,
                  background: "#fff",
                  position: "absolute",
                  zIndex: 1000,
                  width: "100%",
                }}
              >
                {searchResults.map((r) => (
                  <div
                    key={r.place_id}
                    style={{
                      padding: "4px 8px",
                      cursor: "pointer",
                      borderBottom: "1px solid #eee",
                    }}
                    onClick={() => handleResultClick(r)}
                  >
                    {r.display_name}
                  </div>
                ))}
              </div>
            )}
          </Form.Group>
          <div style={{ height: 350, marginBottom: 12, position: "relative" }}>
            <MapContainer
              center={
                value
                  ? [value.lat, value.lng]
                  : [8.031477629198994, 80.75701690636198]
              }
              zoom={value ? 12 : 7}
              style={{ height: "100%", width: "100%" }}
            >
              <TileLayer
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                attribution="&copy; OpenStreetMap contributors"
              />
              {/* Enhancement 1: Zoom to searched location */}
              {zoomToLocation && lastSearchLocation.current && (
                <ZoomToLocation
                  lat={lastSearchLocation.current.lat}
                  lng={lastSearchLocation.current.lng}
                  trigger={zoomToLocation}
                />
              )}

              {/* Enhancement 2: Show balloon (Popup) with image and text */}
              {value && (
                <Marker position={[value.lat, value.lng]}>
                  {showPopup && (
                    <Popup closeButton={false} autoPan={true}>
                      <div style={{ textAlign: "center" }}>
                        <img
                          src="https://via.placeholder.com/60x40?text=Image"
                          alt="location"
                          style={{
                            borderRadius: 6,
                            marginBottom: 6,
                            boxShadow: "0 0 2px #999",
                          }}
                        />
                        <div style={{ fontWeight: "bold", marginBottom: 2 }}>
                          {value.name}
                        </div>
                        <small>
                          ({value.lat.toFixed(4)}, {value.lng.toFixed(4)})
                        </small>
                      </div>
                    </Popup>
                  )}
                </Marker>
              )}
              <LocationMarker onLocationSelect={handleMapClick} />
            </MapContainer>
          </div>
        </Modal.Body>
        <Modal.Footer>
          <Button variant="secondary" onClick={() => setShowModal(false)}>
            Cancel
          </Button>
          <Button
            variant="primary"
            onClick={() => {
              setShowModal(false);
              setShowPopup(false);
            }}
          >
            Confirm Selection
          </Button>
        </Modal.Footer>
      </Modal>
    </>
  );
};

export default LocationPicker;
