# Changelog for `react-native-calendar-events`

## 2.2.0 - 2021-01-08

- Fixed `findEventByID` on iOS - return null if event is not found (_for consistency with Android_) [#337](https://github.com/wmcmahan/react-native-calendar-events/pull/337) by [@dstop75](https://github.com/dstop75)

- Fixed android calendar query to catch events that intersect date range but aren't entirely a subset (_for consistency with iOS_) [#333](https://github.com/wmcmahan/react-native-calendar-events/pull/333) by [@jenniferburch](https://github.com/jenniferburch)

- Added ability to set timezone on events in iOS (necessary for recurrence expansion to work correctly across daylight savings time transitions) [#335](https://github.com/wmcmahan/react-native-calendar-events/pull/335) by [@LorienHW](https://github.com/LorienHW) and [@mcarlson](https://github.com/mcarlson)

Also includes minor documentation tweaks.

## 2.1.2 - 2020-11-08

- Fixed `exceptionDate` option being optional in TypeScript bindings [#329](https://github.com/wmcmahan/react-native-calendar-events/pull/329) by [@MoOx](https://github.com/MoOx)

## 2.1.1 - 2020-10-20

- Fixed TypeScript declaration in `package.json` file [#328](https://github.com/wmcmahan/react-native-calendar-events/pull/328) by [@wmcmahan](https://github.com/wmcmahan)
- Fixed Android import into the README manual linking section [#321](https://github.com/wmcmahan/react-native-calendar-events/pull/321) by [@chiformihai](https://github.com/chiformihai)

## 2.1.0 - 2020-08-11

- Added ability to restrict to read-only permission on Android [#320](https://github.com/wmcmahan/react-native-calendar-events/pull/320) by [@mikehardy](https://github.com/mikehardy)
- Added a _Troubleshooting_ section in README, to document the issue about saved event not being kept on device during sync [#317](https://github.com/wmcmahan/react-native-calendar-events/pull/317) by [@MoOx](https://github.com/MoOx)

## 2.0.1 - 2020-08-01

- Fixed TypeScript definition for missing `requestPermissions` [#316](https://github.com/wmcmahan/react-native-calendar-events/pull/316) by [@wmcmahan](https://github.com/wmcmahan)

## 2.0.0 - 2020-08-01

In addition to bugfixes, this release introduces some minor breaking changes:

- Support for React Native 0.60+ only
- AndroidX support!
- Android & iOS package name have changed. If you rely on React Native autolinking, you don't have to change a thing, otherwise, please see README to update installation instruction.
- Permissions method names have changed for more explicit `checkPermissions` & `requestPermissions`.
- For iOS, we now avoid crashes at all cost, which means
  - if a native method fails, it should be recoverable from JavaScript (promise rejection) - we tried to cover most native code part in the bridge to be able to catch all kind of exception
  - An exception has been made for fetching event: if some part of the serialization fails, a NSLog is emitted & the specific problematic part is ommited (eg: an alarm or a structuredLocation could be missing) and the process continues on other events.
    We keep in mind the idea of adding an `error` field into calendar event so the information is explicitely available from JavaScript.
    This is to avoid receving a promise rejection if you fetch 2 months of events & have a single tiny information that we failed to serialize. In this cases, you will receive all fetched calendar events with just a tiny information missing, which offers a better UX.

### All platforms

- Added `removeCalendar` method [#269](https://github.com/wmcmahan/react-native-calendar-events/pull/269) by [@hmcheu](https://github.com/hmcheu)

### Android

- Package is now `com.calendarevents.RNCalendarEvents` [a39efe7](https://github.com/wmcmahan/react-native-calendar-events/commit/a39efe79c730c578abe8614986d63520005a8e59) by [@MoOx](https://github.com/MoOx)
- Fixed `'boolean android.database.Cursor.moveToNext()' on a null object reference` error [e7c9680](https://github.com/wmcmahan/react-native-calendar-events/commit/e7c9680dd24a84229df234abf82277115d3f4f00) by [@MoOx](https://github.com/MoOx)
- Fixed parsing allowed availability [#268](https://github.com/wmcmahan/react-native-calendar-events/pull/268) by [@saghul](https://github.com/saghul)
- Added AndroidX support for react-native 0.60 [#263](https://github.com/wmcmahan/react-native-calendar-events/pull/263) by [@yfuks](https://github.com/yfuks)
- Added: use PermissionListener to avoid Android manual steps [#252](https://github.com/wmcmahan/react-native-calendar-events/pull/252) by [@saghul](https://github.com/saghul)
- Added "NEVER ASK ME AGAIN" status added in Android [#273](https://github.com/wmcmahan/react-native-calendar-events/pull/273) by [@webtaculars](https://github.com/webtaculars)
- Added key to skip setting timezone on Android [#271](https://github.com/wmcmahan/react-native-calendar-events/pull/271) by [@eleddie](https://github.com/eleddie)

### iOS

- Package is now `RNCalendarEvents` [5ea007c](https://github.com/wmcmahan/react-native-calendar-events/commit/5ea007c0cbb147f37b7c1b748e6acae0a9485b88) by [@MoOx](https://github.com/MoOx)
- Fixed crashes related to structured location [#253](https://github.com/wmcmahan/react-native-calendar-events/pull/253) by [@eladgel](https://github.com/eladgel) & [4560a2f](https://github.com/wmcmahan/react-native-calendar-events/commit/4560a2ff883e1a8bad97ec16f3325d52ccccdff5) by [@MoOx](https://github.com/MoOx)
- Fixed iOS 13 show bug [#279](https://github.com/wmcmahan/react-native-calendar-events/pull/279) by [@huang303513](https://github.com/huang303513)
- Minimal version to iOS 9.0, like react-native 0.60 [5ea007c](https://github.com/wmcmahan/react-native-calendar-events/commit/5ea007c0cbb147f37b7c1b748e6acae0a9485b88) by [@MoOx](https://github.com/MoOx)
- Avoid iOS crashes at all cost [314](https://github.com/wmcmahan/react-native-calendar-events/pull/314) by [@MoOx](https://github.com/MoOx)

### Docs

- Various minor README update [92c3238](https://github.com/wmcmahan/react-native-calendar-events/commit/92c3238eead14eb9a7d36398c3b9d17df0c9e270) by [@MoOx](https://github.com/MoOx)
- Updated docs with correct type for string dates [#250](https://github.com/wmcmahan/react-native-calendar-events/pull/250) by [@hugofelp](https://github.com/eladgel)

## Pre 2.0.0

For 1.7.x & before, please refer to
[git history](https://github.com/wmcmahan/react-native-calendar-events/commits/master).
