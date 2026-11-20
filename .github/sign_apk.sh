#!/usr/bin/env bash
# Signs the unsigned release APK built by Gradle. Runs only on tagged releases, receives the
# signing secrets through the environment, and deletes the keystore when it exits.
set -euo pipefail

: "${SIGNING_KEY:?SIGNING_KEY secret is required for tagged releases}"
: "${ALIAS:?ALIAS secret is required}"
: "${KEY_STORE_PASSWORD:?KEY_STORE_PASSWORD secret is required}"
: "${KEY_PASSWORD:?KEY_PASSWORD secret is required}"

KEYSTORE="$(mktemp -d)/release.jks"
trap 'rm -f "$KEYSTORE"' EXIT
printf '%s' "$SIGNING_KEY" | tr -d '\r\n ' | base64 -d > "$KEYSTORE"
chmod 600 "$KEYSTORE"

UNSIGNED=$(ls -1 app/build/outputs/apk/release/*-unsigned.apk | head -n 1)
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
BUILD_TOOLS=$(ls -1d "${SDK_DIR}/build-tools"/*/ | sort -V | tail -n 1)

mkdir -p dist
"${BUILD_TOOLS}zipalign" -f -p 4 "$UNSIGNED" dist/aligned.apk
# Passwords are passed through the environment, never on the command line.
"${BUILD_TOOLS}apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-key-alias "$ALIAS" \
  --ks-pass env:KEY_STORE_PASSWORD \
  --key-pass env:KEY_PASSWORD \
  --out dist/app-release.apk \
  dist/aligned.apk
rm -f dist/aligned.apk dist/app-release.apk.idsig
echo "Signed dist/app-release.apk"
