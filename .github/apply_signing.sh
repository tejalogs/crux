#!/usr/bin/env bash
set -euo pipefail

if [[ -n "${SIGNING_KEY:-}" ]]; then
    if [[ -z "${ALIAS:-}" || -z "${KEY_STORE_PASSWORD:-}" || -z "${KEY_PASSWORD:-}" ]]; then
        echo "ERROR: SIGNING_KEY is set, but ALIAS, KEY_STORE_PASSWORD, or KEY_PASSWORD is missing." >&2
        exit 1
    fi
    printf '%s' "$SIGNING_KEY" | tr -d '\r\n ' | base64 -d > key.jks
    cat > signing.properties <<EOF
storeFile=key.jks
storePassword=${KEY_STORE_PASSWORD}
keyAlias=${ALIAS}
keyPassword=${KEY_PASSWORD}
EOF
    chmod 600 key.jks signing.properties
    echo "Configured release signing keystore (alias=${ALIAS})."
elif [[ "${REQUIRE_RELEASE_SIGNING:-false}" == "true" ]]; then
    echo "ERROR: Release signing secrets (SIGNING_KEY, ALIAS, KEY_STORE_PASSWORD, KEY_PASSWORD) are required for tagged releases." >&2
    exit 1
else
    echo "No SIGNING_KEY secret provided; falling back to debug signing for non-release build."
fi
