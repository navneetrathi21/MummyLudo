#!/usr/bin/env bash
set -euo pipefail

# Script to generate a Google Play Store upload keystore for Mummy Ludo.
# Output keystore: release-signing/mummyludo-upload-key.jks
# Local properties: keystore.properties

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
KEYTOOL="$JAVA_HOME/bin/keytool"

if [ ! -x "$KEYTOOL" ]; then
    KEYTOOL="$(command -v keytool || true)"
fi

if [ -z "$KEYTOOL" ] || [ ! -x "$KEYTOOL" ]; then
    echo "Error: keytool executable not found. Please set JAVA_HOME."
    exit 1
fi

KEYSTORE_DIR="$ROOT_DIR/release-signing"
mkdir -p "$KEYSTORE_DIR"
KEYSTORE_PATH="$KEYSTORE_DIR/mummyludo-upload-key.jks"
ALIAS="mummyludo-upload"

if [ -f "$KEYSTORE_PATH" ]; then
    echo "Warning: Keystore already exists at $KEYSTORE_PATH"
    read -p "Overwrite existing keystore? (y/N): " -r CONFIRM
    if [[ ! "$CONFIRM" =~ ^[Yy]$ ]]; then
        echo "Aborted."
        exit 0
    fi
    rm -f "$KEYSTORE_PATH"
fi

echo "=================================================="
echo "Generating Google Play Upload Keystore for Mummy Ludo"
echo "=================================================="
read -s -p "Enter keystore password (at least 6 characters): " PASSWORD
echo ""
read -s -p "Confirm keystore password: " PASSWORD_CONFIRM
echo ""

if [ "$PASSWORD" != "$PASSWORD_CONFIRM" ]; then
    echo "Error: Passwords do not match."
    exit 1
fi

if [ ${#PASSWORD} -lt 6 ]; then
    echo "Error: Password must be at least 6 characters."
    exit 1
fi

echo "Generating keystore with RSA 2048-bit key..."
"$KEYTOOL" -genkeypair \
    -v \
    -keystore "$KEYSTORE_PATH" \
    -storepass "$PASSWORD" \
    -keypass "$PASSWORD" \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Mummy Ludo Developer, OU=Mobile, O=Mummy Ludo, L=Bangalore, ST=Karnataka, C=IN"

echo "Creating local keystore.properties..."
cat > "$ROOT_DIR/keystore.properties" <<EOF
storeFile=release-signing/mummyludo-upload-key.jks
storePassword=$PASSWORD
keyAlias=$ALIAS
keyPassword=$PASSWORD
EOF

chmod 600 "$ROOT_DIR/keystore.properties"
chmod 600 "$KEYSTORE_PATH"

echo "=================================================="
echo "Successfully created upload keystore and keystore.properties!"
echo "Keystore file: release-signing/mummyludo-upload-key.jks"
echo "Configuration: keystore.properties"
echo ""
echo "You can now run: ./gradlew bundleRelease"
echo "=================================================="
