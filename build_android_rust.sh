#!/bin/bash
set -e

# Configuration
NDK_VERSION="26.1.10909125" # Update as needed
NDK_PATH="/opt/android-sdk/ndk/$NDK_VERSION"
API_LEVEL=24

# For standard environments, NDK might be elsewhere
if [ ! -d "$NDK_PATH" ]; then
    NDK_PATH=$(find /opt/android-sdk/ndk -maxdepth 1 -mindepth 1 | head -n 1)
    echo "Using NDK at $NDK_PATH"
fi

targets=("aarch64-linux-android" "armv7-linux-androideabi" "x86_64-linux-android" "i686-linux-android")

for target in "${targets[@]}"; do
    echo "Building for $target..."
    cargo build --release --features server --bin ncm-server --target "$target"
done

# Copy to android assets
mkdir -p android/app/src/main/assets/bin
cp target/aarch64-linux-android/release/ncm-server android/app/src/main/assets/bin/ncm-server-aarch64
cp target/armv7-linux-androideabi/release/ncm-server android/app/src/main/assets/bin/ncm-server-armv7
cp target/x86_64-linux-android/release/ncm-server android/app/src/main/assets/bin/ncm-server-x86_64
cp target/i686-linux-android/release/ncm-server android/app/src/main/assets/bin/ncm-server-i686
