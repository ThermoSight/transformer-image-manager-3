import React from "react";
import { Navigate } from "react-router-dom";
import { Alert } from "react-bootstrap";
import { useAuth } from "./AuthContext";

const ProtectedRoute = ({ children, adminOnly = false }) => {
  const { isAuthenticated, isAdmin, loading } = useAuth();

  if (loading) {
    return <div>Loading...</div>;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (adminOnly && !isAdmin) {
    return (
      <Alert variant="danger" className="m-4">
        <Alert.Heading>Access Denied</Alert.Heading>
        <p>You need administrator privileges to access this page.</p>
      </Alert>
    );
  }

  return children;
};

export default ProtectedRoute;
