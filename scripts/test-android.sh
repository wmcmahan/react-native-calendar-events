#!/usr/bin/env bash
# Runs the library's Robolectric unit tests through the example app's Gradle build.
set -euo pipefail

cd "$(dirname "$0")/../example/android"
./gradlew :react-native-calendar-events:testDebugUnitTest --no-daemon --console=plain "$@"
