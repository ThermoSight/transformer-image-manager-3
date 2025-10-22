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
    feedbackLearningRate: 0.0001,
    feedbackSummary: null,
    feedbackHistory: [],
    loading: false,
    error: null,
  });

  // Load settings on component mount and when token changes
  useEffect(() => {
    if (token) {
      loadSettings();
    }
  }, [token]);

  const getAuthConfig = (contentTypeJson = false) => {
    if (!token) {
      return {};
    }
    const headers = {
      Authorization: `Bearer ${token}`,
    };
    if (contentTypeJson) {
      headers["Content-Type"] = "application/json";
    }
    return { headers };
  };

  const loadSettings = async () => {
    try {
      setSettings((prev) => ({ ...prev, loading: true, error: null }));

      const config = getAuthConfig();
      const [sensitivityRes, feedbackRateRes, summaryRes, historyRes] = await Promise.all([
        axios.get("http://localhost:8080/api/ml-settings/sensitivity", config),
        axios.get("http://localhost:8080/api/ml-settings/feedback-rate", config),
        axios.get("http://localhost:8080/api/ml-settings/feedback-summary", config),
        axios.get("http://localhost:8080/api/ml-settings/feedback-history?limit=25", config),
      ]);

      setSettings((prev) => ({
        ...prev,
        detectionSensitivity: sensitivityRes.data.sensitivity,
        feedbackLearningRate: feedbackRateRes.data.learningRate,
        feedbackSummary: summaryRes.data,
        feedbackHistory: historyRes.data || [],
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

      const config = getAuthConfig(true);
      const response = await axios.put(
        "http://localhost:8080/api/ml-settings/sensitivity",
        { sensitivity },
        config
      );

      const summaryRes = await axios.get(
        "http://localhost:8080/api/ml-settings/feedback-summary",
        getAuthConfig()
      );
      const historyRes = await axios.get(
        "http://localhost:8080/api/ml-settings/feedback-history?limit=25",
        getAuthConfig()
      );

      setSettings((prev) => ({
        ...prev,
        detectionSensitivity: response.data.sensitivity,
        feedbackSummary: summaryRes.data,
        feedbackHistory: historyRes.data || prev.feedbackHistory,
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

  const updateFeedbackLearningRate = async (learningRate) => {
    try {
      setSettings((prev) => ({ ...prev, loading: true, error: null }));

      const response = await axios.put(
        "http://localhost:8080/api/ml-settings/feedback-rate",
        { learningRate },
        getAuthConfig(true)
      );

      const summaryRes = await axios.get(
        "http://localhost:8080/api/ml-settings/feedback-summary",
        getAuthConfig()
      );
      const historyRes = await axios.get(
        "http://localhost:8080/api/ml-settings/feedback-history?limit=25",
        getAuthConfig()
      );

      setSettings((prev) => ({
        ...prev,
        feedbackLearningRate: response.data.learningRate,
        feedbackSummary: summaryRes.data,
        feedbackHistory: historyRes.data || prev.feedbackHistory,
        loading: false,
      }));

      return { success: true, learningRate: response.data.learningRate };
    } catch (error) {
      console.error("Failed to update feedback learning rate:", error);
      setSettings((prev) => ({
        ...prev,
        loading: false,
        error: "Failed to update feedback learning rate",
      }));

      return {
        success: false,
        error:
          error.response?.data?.message ||
          "Failed to update feedback learning rate",
      };
    }
  };

  const refreshFeedbackSummary = async () => {
    try {
      const summaryRes = await axios.get(
        "http://localhost:8080/api/ml-settings/feedback-summary",
        getAuthConfig()
      );
      const historyRes = await axios.get(
        "http://localhost:8080/api/ml-settings/feedback-history?limit=25",
        getAuthConfig()
      );
      setSettings((prev) => ({
        ...prev,
        feedbackSummary: summaryRes.data,
        feedbackHistory: historyRes.data || prev.feedbackHistory,
      }));
      return summaryRes.data;
    } catch (error) {
      console.error("Failed to refresh feedback summary:", error);
      return null;
    }
  };

  const value = {
    settings,
    loadSettings,
    updateDetectionSensitivity,
    updateFeedbackLearningRate,
    refreshFeedbackSummary,
    loadSettings,
  };

  return (
    <SettingsContext.Provider value={value}>
      {children}
    </SettingsContext.Provider>
  );
};
