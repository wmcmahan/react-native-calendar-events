#!/usr/bin/env bash
# Runs the native XCTest suite (RNCalendarEvents-Unit-Tests) on an iOS simulator.
# Requires `pod install` to have been run in example/ios.
set -euo pipefail

cd "$(dirname "$0")/../example/ios"

APP_HOST_BUNDLE_ID="org.cocoapods.AppHost-RNCalendarEvents-Unit-Tests"

# Pick the first available iPhone simulator unless one is given.
DEVICE_ID="${IOS_SIMULATOR_ID:-$(xcrun simctl list devices available --json \
  | node -p 'const d=JSON.parse(require("fs").readFileSync(0,"utf8")).devices; Object.values(d).flat().find(x=>/iPhone/.test(x.name)).udid')}"

echo "Using simulator $DEVICE_ID"
xcrun simctl boot "$DEVICE_ID" 2>/dev/null || true
xcrun simctl bootstatus "$DEVICE_ID" -b

# EventKit needs calendar access; grant it to the CocoaPods app host so the
# integration tests run instead of being skipped.
xcrun simctl privacy "$DEVICE_ID" grant calendar "$APP_HOST_BUNDLE_ID"

xcodebuild \
  -workspace CalendarEventsExample.xcworkspace \
  -scheme RNCalendarEvents-Unit-Tests \
  -configuration Debug \
  -destination "platform=iOS Simulator,id=$DEVICE_ID" \
  -derivedDataPath build \
  CODE_SIGNING_ALLOWED=NO \
  test | tee xctest.log | grep -E "Test Case|Executed|error:|TEST (SUCCEEDED|FAILED)"

grep -q "TEST SUCCEEDED" xctest.log
