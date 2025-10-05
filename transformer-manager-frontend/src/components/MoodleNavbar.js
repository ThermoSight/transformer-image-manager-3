import React, { useState } from "react";
import {
  Navbar,
  Nav,
  Container,
  Button,
  Dropdown,
  Badge,
} from "react-bootstrap";
import { useAuth } from "../AuthContext";
import { useNavigate, Link } from "react-router-dom";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faUser,
  faSignOutAlt,
  faUpload,
  faBolt,
  faClipboardCheck,
  faCog,
} from "@fortawesome/free-solid-svg-icons";
import QueueStatus from "./QueueStatus";
import SettingsModal from "./SettingsModal";

const MoodleNavbar = () => {
  const { user, logout, isAuthenticated, isAdmin, role } = useAuth();
  const navigate = useNavigate();
  const [showSettings, setShowSettings] = useState(false);

  const handleLogout = () => {
    logout();
    navigate("/login");
  };

  return (
    <Navbar
      bg="primary"
      variant="dark"
      expand="lg"
      className="moodle-navbar sticky-top"
    >
      <Container fluid>
        <Navbar.Brand as={Link} to="/" className="d-flex align-items-center">
          <img src="/logo.png" alt="Logo" height="30" className="me-2" />
          <span>ThermoSight - TMS</span>
        </Navbar.Brand>

        <Navbar.Toggle aria-controls="basic-navbar-nav" />

        <Navbar.Collapse id="basic-navbar-nav">
          <Nav className="me-auto">
            {isAuthenticated && (
              <>
                <Nav.Link as={Link} to="/">
                  <FontAwesomeIcon icon={faBolt} className="me-1" />
                  Transformers
                </Nav.Link>
                <Nav.Link as={Link} to="/inspections">
                  <FontAwesomeIcon icon={faClipboardCheck} className="me-1" />
                  Inspections
                </Nav.Link>
                {isAdmin && (
                  <Nav.Link as={Link} to="/upload">
                    <FontAwesomeIcon icon={faUpload} className="me-1" />
                    Upload Transformer
                  </Nav.Link>
                )}
                <Nav.Link as={Link} to="/upload-inspection">
                  <FontAwesomeIcon icon={faUpload} className="me-1" />
                  Upload Inspection
                </Nav.Link>
              </>
            )}
          </Nav>

          <div className="d-flex align-items-center">
            {isAuthenticated && (
              <>
                <QueueStatus />
                <Dropdown align="end" className="ms-2">
                  <Dropdown.Toggle
                    variant="light"
                    id="dropdown-user"
                    className="d-flex align-items-center"
                  >
                    <span className="me-2">
                      {user.displayName}
                      {role && (
                        <Badge
                          bg={isAdmin ? "success" : "info"}
                          className="ms-1"
                        >
                          {role}
                        </Badge>
                      )}
                    </span>
                    <FontAwesomeIcon icon={faUser} />
                  </Dropdown.Toggle>

                  <Dropdown.Menu>
                    <Dropdown.Item disabled>
                      {user.username} ({role})
                    </Dropdown.Item>
                    <Dropdown.Divider />
                    <Dropdown.Item onClick={() => setShowSettings(true)}>
                      <FontAwesomeIcon icon={faCog} className="me-2" />
                      ML Settings
                    </Dropdown.Item>
                    <Dropdown.Divider />
                    <Dropdown.Item onClick={handleLogout}>
                      <FontAwesomeIcon icon={faSignOutAlt} className="me-2" />
                      Logout
                    </Dropdown.Item>
                  </Dropdown.Menu>
                </Dropdown>
              </>
            )}
          </div>
        </Navbar.Collapse>
      </Container>

      {/* Settings Modal */}
      <SettingsModal
        show={showSettings}
        onHide={() => setShowSettings(false)}
      />
    </Navbar>
  );
};

export default MoodleNavbar;
