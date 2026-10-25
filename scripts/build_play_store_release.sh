#!/usr/bin/env bash
set -euo pipefail

# Build automated production release bundle for Google Play Store upload.
# Output: app/build/outputs/bundle/release/app-release.aab

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"

if [ ! -d "$JAVA_HOME" ]; then
    echo "Warning: JAVA_HOME not found at $JAVA_HOME. Falling back to system Java."
fi

echo "=================================================="
echo "Mummy Ludo - Google Play Store Release Builder"
echo "=================================================="
echo "Project root: $ROOT_DIR"
echo "Java version: $("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)"
echo ""

# Clean any iCloud sync duplicates in build directory
find "$ROOT_DIR/app/build" -name "* [0-9]*" -delete 2>/dev/null || true

# Check release signing configuration
if [ -f "$ROOT_DIR/keystore.properties" ]; then
    echo "Signing: Using configuration from keystore.properties"
elif [ -n "${MUMMY_LUDO_RELEASE_STORE_FILE:-}" ]; then
    echo "Signing: Using configuration from environment variables"
else
    echo "Signing: No release keystore detected."
    echo "Note: The bundle will be built with default signing."
    echo "Tip: Run './scripts/generate_upload_keystore.sh' to create an upload key."
    echo ""
fi

# Step 1: Run full unit test suite
echo "Step 1/2: Running unit tests..."
./gradlew testDebugUnitTest --no-daemon

# Step 2: Build release Android App Bundle (AAB)
echo ""
echo "Step 2/2: Building release Android App Bundle (.aab)..."
./gradlew bundleRelease --no-daemon

AAB_PATH="$ROOT_DIR/app/build/outputs/bundle/release/app-release.aab"

if [ ! -f "$AAB_PATH" ]; then
    echo "Error: Release bundle was not generated at expected location: $AAB_PATH"
    exit 1
fi

FILE_SIZE=$(ls -lh "$AAB_PATH" | awk '{print $5}')
SHA256=$(shasum -a 256 "$AAB_PATH" | awk '{print $1}')

echo ""
echo "=================================================="
echo "Release Bundle Successfully Built!"
echo "=================================================="
echo "Bundle file: $AAB_PATH"
echo "Bundle size: $FILE_SIZE"
echo "SHA256:      $SHA256"
echo ""
echo "Next Steps for Google Play Console:"
echo "1. Open https://play.google.com/console"
echo "2. Select Mummy Ludo > Release > Internal testing (or Production)"
echo "3. Upload: $AAB_PATH"
echo "4. Copy listing details & release notes from: PLAY_STORE_LISTING.md"
echo "5. Provide Privacy Policy URL from: PRIVACY_POLICY.md"
echo "=================================================="
