#!/bin/bash
set -e

# Configuration
NDK_VERSION="26.1.10909125" # Update as needed
NDK_PATH="/opt/android-sdk/ndk/$NDK_VERSION"
API_LEVEL=24

# For standard environments, NDK might be elsewhere
if [ ! -d "$NDK_PATH" ]; then
    NDK_PATH=$(find /opt/android-sdk/ndk -maxdepth 1 -mindepth 1 | head -n 1 2>/dev/null || echo "")
    if [ -z "$NDK_PATH" ] || [ ! -d "$NDK_PATH" ]; then
        NDK_PATH=$(ls -d ~/Android/Sdk/ndk/* | head -n 1 2>/dev/null || echo "")
    fi
    echo "Using NDK at $NDK_PATH"
fi

export ANDROID_NDK_HOME="$NDK_PATH"

cargo_targets=("aarch64-linux-android" "armv7-linux-androideabi" "x86_64-linux-android" "i686-linux-android")
ndk_targets=("arm64-v8a" "armeabi-v7a" "x86_64" "x86")

for i in "${!cargo_targets[@]}"; do
    target="${cargo_targets[$i]}"
    ndk_target="${ndk_targets[$i]}"
    echo "Building for $target..."
    # Support 16KB page size for Android 15+ (AOSP 16kb page size support)
    # Ref: https://developer.android.com/guide/practices/page-alignment
    RUSTFLAGS="-Clink-arg=-Wl,-z,max-page-size=16384" cargo ndk -t "$ndk_target" --platform $API_LEVEL build --release --features server,jni --bin ncm-server
    
    # Also explicitly build the library to be sure it's verified
    RUSTFLAGS="-Clink-arg=-Wl,-z,max-page-size=16384" cargo ndk -t "$ndk_target" --platform $API_LEVEL build --release --features jni --lib
done

# Copy to android assets
mkdir -p android/app/src/main/assets/bin
cp target/aarch64-linux-android/release/ncm-server android/app/src/main/assets/bin/ncm-server-aarch64
cp target/armv7-linux-androideabi/release/ncm-server android/app/src/main/assets/bin/ncm-server-armv7
cp target/x86_64-linux-android/release/ncm-server android/app/src/main/assets/bin/ncm-server-x86_64
cp target/i686-linux-android/release/ncm-server android/app/src/main/assets/bin/ncm-server-i686
