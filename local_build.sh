#!/bin/bash

# Exit on any error
set -e

echo "======================================"
echo " Starting Local Build & Package"
echo "======================================"

# Step 1: Build Rust Binaries
echo "[1/4] Building Rust JNI library for arm64-v8a..."

# Ensure cargo-ndk is installed
if ! command -v cargo-ndk &> /dev/null; then
    echo "cargo-ndk not found, installing..."
    cargo install cargo-ndk
fi

# Ensure target is installed
rustup target add aarch64-linux-android

# Create destination directory
mkdir -p android/app/src/main/jniLibs/arm64-v8a

# Support 16KB page size for Android 15+ (AOSP 16kb page size support)
export RUSTFLAGS="-Clink-arg=-Wl,-z,max-page-size=16384"

# Build for arm64-v8a
echo "Running cargo ndk..."
cargo ndk -t arm64-v8a --platform 24 build --release --features jni

# Copy the generated .so file to the jniLibs directory
echo "Copying generated libncm_api.so..."
find target/aarch64-linux-android/release -name "lib*.so" -exec cp {} android/app/src/main/jniLibs/arm64-v8a/libncm_api.so \;

echo "Rust JNI library built and copied successfully!"

# Step 2: Build Android App (APK)
echo "[2/4] Building Android APKs (Debug & Release)..."
cd android

# Ensure gradlew is executable
chmod +x gradlew

# Run gradle build
./gradlew assembleDebug assembleRelease --parallel --build-cache

cd ..

# Step 3: Copy APKs to output directory
echo "[3/4] Copying APKs to output directory..."
OUTPUT_DIR="build_outputs"
mkdir -p "$OUTPUT_DIR"

cp android/app/build/outputs/apk/debug/app-debug.apk "$OUTPUT_DIR/"
cp android/app/build/outputs/apk/release/app-release.apk "$OUTPUT_DIR/"

echo "[4/4] Build complete! APKs are available in the $OUTPUT_DIR/ directory:"
ls -lh "$OUTPUT_DIR" | grep ".apk"

echo "======================================"
echo " Done!"
echo "======================================"
