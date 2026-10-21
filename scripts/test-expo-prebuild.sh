#!/usr/bin/env bash
# End-to-end check of the Expo config plugin and the published package layout:
# packs the library, installs the tarball into a throwaway Expo app, runs
# `expo prebuild`, and asserts the native projects carry the calendar
# permissions. Needs network (Expo template + CLI). No Xcode or JDK required.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${EXPO_PREBUILD_DIR:-$(mktemp -d)}"
CREATE_EXPO_APP="${CREATE_EXPO_APP_VERSION:-create-expo-app@latest}"

mkdir -p "$WORK"
echo "Working in $WORK"
cd "$ROOT"
# `npm pack` runs the `prepare` build itself and logs to stdout; the tarball name is the last line.
TARBALL="$(npm pack --pack-destination "$WORK" 2>/dev/null | tail -n 1)"
echo "Packed $TARBALL"

cd "$WORK"
rm -rf app
npx --yes "$CREATE_EXPO_APP" app --template blank --no-install >/dev/null
cd app
npm install --no-audit --no-fund --loglevel=error "../$TARBALL" >/dev/null

node - <<'JS'
const fs = require('fs');
const config = JSON.parse(fs.readFileSync('app.json', 'utf8'));
config.expo.ios = { ...config.expo.ios, bundleIdentifier: 'com.rncalendarevents.prebuildtest' };
config.expo.android = { ...config.expo.android, package: 'com.rncalendarevents.prebuildtest' };
config.expo.plugins = [
  ['react-native-calendar-events', {
    calendarPermission: 'RNCAL_TEST_FULL',
    writeOnlyCalendarPermission: 'RNCAL_TEST_WRITE',
  }],
];
fs.writeFileSync('app.json', JSON.stringify(config, null, 2));
JS

CI=1 npx expo prebuild --platform all --no-install >/dev/null
echo "Prebuild finished"

fail() { echo "FAIL: $1" >&2; exit 1; }

PLIST="$(ls ios/*/Info.plist | head -1)"
for key in NSCalendarsFullAccessUsageDescription NSCalendarsWriteOnlyAccessUsageDescription NSCalendarsUsageDescription; do
  grep -q "<key>$key</key>" "$PLIST" || fail "$key missing from $PLIST"
done
grep -q "RNCAL_TEST_FULL" "$PLIST" || fail "custom calendar string not written"
grep -q "RNCAL_TEST_WRITE" "$PLIST" || fail "custom write-only string not written"
grep -q "NSRemindersFullAccessUsageDescription" "$PLIST" && fail "reminders key written although not requested"
echo "iOS Info.plist OK"

MANIFEST=android/app/src/main/AndroidManifest.xml
for perm in android.permission.READ_CALENDAR android.permission.WRITE_CALENDAR; do
  grep -q "$perm" "$MANIFEST" || fail "$perm missing from $MANIFEST"
done
echo "AndroidManifest OK"

# Autolinking must see the published package layout (podspec, android/ dir).
if npx expo-modules-autolinking react-native-config --platform ios --json 2>/dev/null \
  | node -e 'const c=JSON.parse(require("fs").readFileSync(0,"utf8"));const d=c.dependencies["react-native-calendar-events"];if(!d||!d.platforms||!d.platforms.ios)process.exit(1);console.log("Autolinking OK:",d.platforms.ios.podspecPath)'; then
  :
else
  echo "Autolinking check skipped or failed (expo-modules-autolinking react-native-config unavailable)" >&2
  exit 1
fi

echo "expo prebuild check passed"
