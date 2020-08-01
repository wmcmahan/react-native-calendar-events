# Changelog for `react-native-calendar-events`

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
