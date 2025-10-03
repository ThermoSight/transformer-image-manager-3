#!/usr/bin/env bash
# Test script to debug path issues

echo "=== DEBUG TEST SCRIPT ==="
echo "Current working directory: $(pwd)"
echo "Arguments received:"
echo "INPUT: $1"
echo "OUTPUT: $2"

echo ""
echo "=== Checking INPUT path ==="
if [[ -d "$1" ]]; then
    echo "✓ INPUT directory exists: $1"
    echo "Files in INPUT directory:"
    ls -la "$1"
else
    echo "✗ INPUT directory does not exist: $1"
fi

echo ""
echo "=== Checking OUTPUT path ==="
if [[ -d "$2" ]]; then
    echo "✓ OUTPUT directory exists: $2"
else
    echo "✗ OUTPUT directory does not exist: $2"
    echo "Creating OUTPUT directory..."
    mkdir -p "$2"
    if [[ $? -eq 0 ]]; then
        echo "✓ OUTPUT directory created successfully"
    else
        echo "✗ Failed to create OUTPUT directory"
    fi
fi

echo ""
echo "=== Environment info ==="
echo "Python version: $(python --version)"
echo "Working directory: $(pwd)"
echo "User: $(whoami)"
echo ""