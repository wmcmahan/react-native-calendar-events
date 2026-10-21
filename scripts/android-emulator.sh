#!/usr/bin/env bash
# Boots or stops a headless Android emulator for the instrumented tests.
#   scripts/android-emulator.sh start   # installs the image if needed, creates the AVD, boots, waits
#   scripts/android-emulator.sh stop
# Needs ANDROID_HOME (or ANDROID_SDK_ROOT) and a JDK on PATH or JAVA_HOME.
set -euo pipefail

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
API_LEVEL="${ANDROID_API_LEVEL:-35}"
AVD_NAME="${ANDROID_AVD_NAME:-rncal}"
case "$(uname -m)" in
  arm64|aarch64) ARCH="arm64-v8a" ;;
  *) ARCH="x86_64" ;;
esac
IMAGE="system-images;android-${API_LEVEL};google_apis;${ARCH}"

SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="$SDK/cmdline-tools/latest/bin/avdmanager"
ADB="$SDK/platform-tools/adb"
EMULATOR="$SDK/emulator/emulator"
PID_FILE="${TMPDIR:-/tmp}/rncal-emulator.pid"
LOG_FILE="${TMPDIR:-/tmp}/rncal-emulator.log"

start() {
  if ! "$SDKMANAGER" --sdk_root="$SDK" --list_installed 2>/dev/null | grep -q "$IMAGE"; then
    echo "Installing emulator and $IMAGE"
    yes | "$SDKMANAGER" --sdk_root="$SDK" --licenses >/dev/null 2>&1 || true
    "$SDKMANAGER" --sdk_root="$SDK" --install "emulator" "platform-tools" "$IMAGE" >/dev/null
  fi
  # Pin the AVD directory so avdmanager and the emulator agree on where it lives.
  export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.android/avd}"
  mkdir -p "$ANDROID_AVD_HOME"
  if ! "$EMULATOR" -list-avds | grep -qx "$AVD_NAME"; then
    echo "Creating AVD $AVD_NAME in $ANDROID_AVD_HOME"
    if ! echo "no" | "$AVDMANAGER" create avd -n "$AVD_NAME" -k "$IMAGE" -d pixel_6 --force; then
      echo "Retrying without a device profile" >&2
      echo "no" | "$AVDMANAGER" create avd -n "$AVD_NAME" -k "$IMAGE" --force
    fi
    echo "AVDs: $("$EMULATOR" -list-avds | tr '\n' ' ')"
    ls -la "$ANDROID_AVD_HOME"
  fi

  echo "Booting $AVD_NAME ($IMAGE)"
  "$EMULATOR" -avd "$AVD_NAME" -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim \
    -camera-back none -no-snapshot -no-metrics >"$LOG_FILE" 2>&1 &
  local pid=$!
  echo "$pid" >"$PID_FILE"

  # Poll instead of `adb wait-for-device`, which blocks forever if the emulator exits.
  for _ in $(seq 1 180); do
    if ! kill -0 "$pid" 2>/dev/null; then
      echo "Emulator process exited during boot" >&2
      dump_log
      return 1
    fi
    if [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
      "$ADB" shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
      "$ADB" shell settings put global transition_animation_scale 0 >/dev/null 2>&1 || true
      "$ADB" shell settings put global animator_duration_scale 0 >/dev/null 2>&1 || true
      echo "Emulator ready"
      return 0
    fi
    sleep 2
  done
  echo "Emulator did not finish booting within 6 minutes" >&2
  dump_log
  stop
  return 1
}

dump_log() {
  echo "--- emulator log ($LOG_FILE) ---" >&2
  tail -n 60 "$LOG_FILE" >&2 || true
  echo "--- end emulator log ---" >&2
}

stop() {
  "$ADB" emu kill >/dev/null 2>&1 || true
  if [ -f "$PID_FILE" ]; then
    kill "$(cat "$PID_FILE")" >/dev/null 2>&1 || true
    rm -f "$PID_FILE"
  fi
  echo "Emulator stopped"
}

case "${1:-}" in
  start) start ;;
  stop) stop ;;
  *) echo "usage: $0 start|stop" >&2; exit 2 ;;
esac
