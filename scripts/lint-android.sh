#!/usr/bin/env bash
# Android Lint plus Kotlin warnings-as-errors for the library module.
set -euo pipefail

cd "$(dirname "$0")/../example/android"
./gradlew :react-native-calendar-events:lintDebug -PrncalendarStrict=true --no-daemon --console=plain "$@"
