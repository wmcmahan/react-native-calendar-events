#!/usr/bin/env bash
# Runs the library's instrumented tests on a connected device or running emulator.
set -euo pipefail

cd "$(dirname "$0")/../example/android"
./gradlew :react-native-calendar-events:connectedDebugAndroidTest --no-daemon --console=plain "$@"
