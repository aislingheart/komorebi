#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ADB:-$HOME/Android/Sdk/platform-tools/adb}"
TARGET="${KOMOREBI_PIXEL_TARGET:-100.95.41.47:5555}"
RELEASE_DIR="$ROOT/app/build/outputs/apk/release"
if [ -n "${1:-}" ]; then
  APK="$1"
elif [ -f "$RELEASE_DIR/app-arm64-v8a-release.apk" ]; then
  APK="$RELEASE_DIR/app-arm64-v8a-release.apk"
elif [ -f "$RELEASE_DIR/app-universal-release.apk" ]; then
  APK="$RELEASE_DIR/app-universal-release.apk"
else
  APK="$RELEASE_DIR/app-release.apk"
fi

if [ ! -x "$ADB" ]; then
  echo "adb not found or not executable: $ADB" >&2
  exit 3
fi

if [ ! -f "$APK" ]; then
  echo "APK not found: $APK" >&2
  exit 1
fi

if [ -z "$(find "$APK" -mmin -60 2>/dev/null)" ]; then
  echo "Refusing to install stale APK (older than 60 minutes): $APK" >&2
  exit 1
fi

HOST="${TARGET%%:*}"

wait_for_device() {
  local i
  for i in $(seq 1 10); do
    if [ "$("$ADB" -s "$1" get-state 2>/dev/null || true)" = "device" ]; then
      return 0
    fi
    sleep 1
  done
  return 1
}

# 1) Try the configured target first (adb over tcpip keeps 5555 across reboots).
"$ADB" connect "$TARGET" >/dev/null 2>&1 || true
TARGET_FOUND=""
if wait_for_device "$TARGET"; then
  TARGET_FOUND="$TARGET"
fi

# 2) Wireless debugging uses a random port; discover it by scanning the host.
if [ -z "$TARGET_FOUND" ]; then
  echo "Configured target $TARGET not reachable; scanning $HOST for wireless debugging ports..."
  CANDIDATES=$(seq 30000 60000 | xargs -P 200 -I{} timeout 1 bash -c \
    "echo > /dev/tcp/$HOST/{} && echo {}" 2>/dev/null | sort -n || true)
  for PORT in $CANDIDATES; do
    CAND="${HOST}:${PORT}"
    "$ADB" connect "$CAND" >/dev/null 2>&1 || true
    if wait_for_device "$CAND"; then
      TARGET_FOUND="$CAND"
      break
    fi
    "$ADB" disconnect "$CAND" >/dev/null 2>&1 || true
  done
fi

if [ -z "$TARGET_FOUND" ]; then
  echo "Pixel 9 Pro ($HOST) is not reachable over ADB." >&2
  echo "Enable Developer options -> Wireless debugging on the phone (accept the prompt if shown), then retry." >&2
  exit 2
fi

MODEL="$("$ADB" -s "$TARGET_FOUND" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
echo "Installing $(basename "$APK") on ${MODEL:-Pixel 9 Pro} ($TARGET_FOUND)..."
"$ADB" -s "$TARGET_FOUND" install -r "$APK"
