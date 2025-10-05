// App.js (updated for transformer/inspection separation)
import React, { useState, useEffect } from "react";
import {
  BrowserRouter as Router,
  Routes,
  Route,
  useLocation,
} from "react-router-dom";
import { Container } from "react-bootstrap";
import "bootstrap/dist/css/bootstrap.min.css";
import { AuthProvider } from "./AuthContext";
import { SettingsProvider } from "./SettingsContext";
import Login from "./Login";
import TransformerRecordUpload from "./components/TransformerRecordUpload";
import TransformerList from "./components/TransformerList";
import InspectionList from "./components/InspectionList";
import InspectionDetail from "./components/InspectionDetail";
import InspectionUpload from "./components/InspectionUpload";
import TransformerRecordDetail from "./components/TransformerRecordDetail";
import ProtectedRoute from "./ProtectedRoute";
import MoodleNavbar from "./components/MoodleNavbar";
import "./App.css";

// Component to handle title changes
function TitleHandler() {
  const location = useLocation();

  useEffect(() => {
    switch (location.pathname) {
      case "/":
        document.title = "Transformers - ThermoSight TMS";
        break;
      case "/inspections":
        document.title = "Inspections - ThermoSight TMS";
        break;
      case "/upload":
        document.title = "Upload Transformer - ThermoSight TMS";
        break;
      case "/upload-inspection":
        document.title = "Upload Inspection - ThermoSight TMS";
        break;
      case "/login":
        document.title = "Login - ThermoSight TMS";
        break;
      default:
        if (location.pathname.startsWith("/records/")) {
          document.title = "Transformer Details - ThermoSight TMS";
        } else if (location.pathname.startsWith("/inspections/list/")) {
          document.title = "Inspections List - ThermoSight TMS";
        } else if (location.pathname.startsWith("/inspections/")) {
          if (location.pathname.startsWith("/inspections/add/")) {
            document.title = "Add Inspection - ThermoSight TMS";
          } else {
            document.title = "Inspection Details - ThermoSight TMS";
          }
        } else {
          document.title = "ThermoSight TMS";
        }
    }
  }, [location]);

  return null;
}

function App() {
  const [refresh, setRefresh] = useState(false);

  const handleUpload = () => {
    setRefresh(!refresh);
  };

  return (
    <AuthProvider>
      <SettingsProvider>
        <Router>
          <TitleHandler />
          <MoodleNavbar />
          <Container fluid className="main-container">
            <Routes>
              <Route path="/login" element={<Login />} />
              <Route
                path="/"
                element={
                  <ProtectedRoute>
                    <TransformerList key={refresh} />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/inspections"
                element={
                  <ProtectedRoute>
                    <InspectionList key={refresh} />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/upload"
                element={
                  <ProtectedRoute adminOnly>
                    <TransformerRecordUpload onUpload={handleUpload} />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/upload-inspection"
                element={
                  <ProtectedRoute>
                    <InspectionUpload onUpload={handleUpload} />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/records/:id"
                element={
                  <ProtectedRoute>
                    <TransformerRecordDetail />
                  </ProtectedRoute>
                }
              />
              {/* New inspection routes */}
              <Route
                path="/inspections/list/:transformerId"
                element={
                  <ProtectedRoute>
                    <InspectionList />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/inspections/:id"
                element={
                  <ProtectedRoute>
                    <InspectionDetail />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/inspections/add/:transformerId"
                element={
                  <ProtectedRoute>
                    <InspectionUpload />
                  </ProtectedRoute>
                }
              />
            </Routes>
          </Container>
        </Router>
      </SettingsProvider>
    </AuthProvider>
  );
}

export default App;
