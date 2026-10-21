/**
 * Expo config plugin for react-native-calendar-events.
 *
 * Usage in app.json / app.config.js:
 *   "plugins": [
 *     ["react-native-calendar-events", {
 *       "calendarPermission": "Allow $(PRODUCT_NAME) to access your calendar",
 *       "writeOnlyCalendarPermission": "Allow $(PRODUCT_NAME) to add events to your calendar",
 *       "remindersPermission": "Allow $(PRODUCT_NAME) to access your reminders"
 *     }]
 *   ]
 *
 * Pass `false` for a string to leave that Info.plist key untouched.
 * Only the iOS usage strings are configurable. Android permissions are added
 * unconditionally because the library declares them in its manifest anyway.
 */
const {
  AndroidConfig,
  withAndroidManifest,
  withInfoPlist,
} = require('expo/config-plugins');

const DEFAULT_CALENDAR_PERMISSION =
  'Allow $(PRODUCT_NAME) to access your calendar';
const DEFAULT_WRITE_ONLY_PERMISSION =
  'Allow $(PRODUCT_NAME) to add events to your calendar';

const ANDROID_PERMISSIONS = [
  'android.permission.READ_CALENDAR',
  'android.permission.WRITE_CALENDAR',
];

function withCalendarEventsIos(config, props) {
  return withInfoPlist(config, (mod) => {
    const plist = mod.modResults;
    // `false` opts out of writing a key (Expo convention); undefined uses the default.
    const calendar = props.calendarPermission ?? DEFAULT_CALENDAR_PERMISSION;
    const writeOnly =
      props.writeOnlyCalendarPermission ?? DEFAULT_WRITE_ONLY_PERMISSION;

    if (calendar !== false) {
      // iOS 17+ key, plus the pre-iOS 17 key still read by older systems.
      plist.NSCalendarsFullAccessUsageDescription = calendar;
      plist.NSCalendarsUsageDescription = calendar;
    }
    if (writeOnly !== false) {
      plist.NSCalendarsWriteOnlyAccessUsageDescription = writeOnly;
    }

    if (props.remindersPermission) {
      plist.NSRemindersFullAccessUsageDescription = props.remindersPermission;
      plist.NSRemindersUsageDescription = props.remindersPermission;
    }
    return mod;
  });
}

function withCalendarEventsAndroid(config) {
  return withAndroidManifest(config, (mod) => {
    mod.modResults = AndroidConfig.Permissions.ensurePermissions(
      mod.modResults,
      ANDROID_PERMISSIONS
    );
    return mod;
  });
}

function withCalendarEvents(config, props = {}) {
  return withCalendarEventsAndroid(withCalendarEventsIos(config, props));
}

module.exports = withCalendarEvents;
