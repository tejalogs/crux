#!/usr/bin/env bash
# Signs an unsigned release APK with zipalign + apksigner.
#
# Usage: sign_apk.sh <unsigned.apk> [signed-output.apk]
#
# CI runs this only in the dedicated `sign` job, on a fresh runner that never ran Gradle or any
# other project code. It receives the signing secrets through the environment and deletes the
# keystore when it exits. The build-tools version is pinned (BUILD_TOOLS_VERSION) instead of
# picking the highest installed directory, so a planted build-tools/999.x can never be chosen.
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 <unsigned.apk> [signed-output.apk]" >&2
  exit 2
fi

UNSIGNED="$1"
OUT="${2:-dist/app-release.apk}"

if [[ ! -f "$UNSIGNED" || -L "$UNSIGNED" ]]; then
  echo "ERROR: unsigned APK not found or not a regular file: $UNSIGNED" >&2
  exit 1
fi

: "${SIGNING_KEY:?SIGNING_KEY secret is required for tagged releases}"
: "${ALIAS:?ALIAS secret is required}"
: "${KEY_STORE_PASSWORD:?KEY_STORE_PASSWORD secret is required}"
: "${KEY_PASSWORD:?KEY_PASSWORD secret is required}"
: "${BUILD_TOOLS_VERSION:?BUILD_TOOLS_VERSION is required (for example 36.0.0)}"

SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
: "${SDK_DIR:?ANDROID_HOME or ANDROID_SDK_ROOT must be set}"
BUILD_TOOLS="${SDK_DIR}/build-tools/${BUILD_TOOLS_VERSION}"
for tool in zipalign apksigner; do
  if [[ ! -x "${BUILD_TOOLS}/${tool}" ]]; then
    echo "ERROR: ${tool} not found in ${BUILD_TOOLS}" >&2
    exit 1
  fi
done

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT
KEYSTORE="${WORK_DIR}/release.jks"
ALIGNED="${WORK_DIR}/aligned.apk"

(umask 077 && printf '%s' "$SIGNING_KEY" | tr -d '\r\n ' | base64 -d > "$KEYSTORE")

mkdir -p "$(dirname "$OUT")"
"${BUILD_TOOLS}/zipalign" -f -p 4 "$UNSIGNED" "$ALIGNED"
# Passwords are passed through the environment, never on the command line.
"${BUILD_TOOLS}/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-key-alias "$ALIAS" \
  --ks-pass env:KEY_STORE_PASSWORD \
  --key-pass env:KEY_PASSWORD \
  --out "$OUT" \
  "$ALIGNED"
rm -f "${OUT}.idsig"
echo "Signed ${OUT}"
