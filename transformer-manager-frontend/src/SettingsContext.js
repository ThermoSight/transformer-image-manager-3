import React, { createContext, useContext, useState, useEffect } from "react";
import axios from "axios";
import { useAuth } from "./AuthContext";

const SettingsContext = createContext();

export const useSettings = () => {
  const context = useContext(SettingsContext);
  if (!context) {
    throw new Error("useSettings must be used within a SettingsProvider");
  }
  return context;
};

export const SettingsProvider = ({ children }) => {
  const { token } = useAuth();
  const [settings, setSettings] = useState({
    detectionSensitivity: 1.0,
    loading: false,
    error: null,
  });

  // Load settings on component mount and when token changes
  useEffect(() => {
    if (token) {
      loadSettings();
    }
  }, [token]);

  const loadSettings = async () => {
    try {
      setSettings((prev) => ({ ...prev, loading: true, error: null }));

      const config = {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      };

      const response = await axios.get(
        "http://localhost:8080/api/ml-settings/sensitivity",
        config
      );

      setSettings((prev) => ({
        ...prev,
        detectionSensitivity: response.data.sensitivity,
        loading: false,
      }));
    } catch (error) {
      console.error("Failed to load settings:", error);
      setSettings((prev) => ({
        ...prev,
        loading: false,
        error: "Failed to load ML settings",
      }));
    }
  };

  const updateDetectionSensitivity = async (sensitivity) => {
    try {
      setSettings((prev) => ({ ...prev, loading: true, error: null }));

      const config = {
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
      };

      const response = await axios.put(
        "http://localhost:8080/api/ml-settings/sensitivity",
        { sensitivity },
        config
      );

      setSettings((prev) => ({
        ...prev,
        detectionSensitivity: response.data.sensitivity,
        loading: false,
      }));

      return { success: true, sensitivity: response.data.sensitivity };
    } catch (error) {
      console.error("Failed to update sensitivity:", error);
      setSettings((prev) => ({
        ...prev,
        loading: false,
        error: "Failed to update sensitivity",
      }));

      return {
        success: false,
        error: error.response?.data?.message || "Failed to update sensitivity",
      };
    }
  };

  const value = {
    settings,
    loadSettings,
    updateDetectionSensitivity,
  };

  return (
    <SettingsContext.Provider value={value}>
      {children}
    </SettingsContext.Provider>
  );
};
