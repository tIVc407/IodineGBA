#!/bin/bash
# Compile script for IodineGBA Java Edition

echo "Compiling IodineGBA Java Edition..."

# Create bin directory if it doesn't exist
mkdir -p bin

# Find all Java files and compile them
find src -name "*.java" > sources.txt

# Compile all Java files
javac -d bin @sources.txt

# Check if compilation was successful
if [ $? -eq 0 ]; then
    echo "Compilation successful!"
    echo "Run with: ./run.sh"
    rm sources.txt
    exit 0
else
    echo "Compilation failed!"
    rm sources.txt
    exit 1
fi
