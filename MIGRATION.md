# Migrating from 2.x to 3.0

Version 3 is a rewrite of the native code as a React Native Turbo Module with a
Swift core on iOS and a Kotlin core on Android. The JavaScript API is kept as
close to 2.x as possible. Most apps only need to update the install
instructions and the iOS permission strings.

## Requirements

| | 2.x | 3.0 |
| --- | --- | --- |
| React Native | 0.60+ | **0.76+** with the New Architecture (the default since 0.76, the only option since 0.82) |
| iOS | 9.0+ | **15.1+** |
| Android | API 16+ | **API 24+** |
| Expo | manual native setup | config plugin, see the README |

The legacy bridge is not supported. If you still run the old architecture,
stay on 2.x.

## Install changes

- iOS 17 and later require new `Info.plist` keys. Add
  `NSCalendarsFullAccessUsageDescription` (and
  `NSCalendarsWriteOnlyAccessUsageDescription` if you want to offer write-only
  access). Keep `NSCalendarsUsageDescription` for iOS 16 and earlier. The Expo
  plugin writes all three for you.
- Manual linking instructions are gone. Autolinking is required.
- The pod is still named `RNCalendarEvents`, the Android package is still
  `com.calendarevents`.

## Breaking changes

- **`removeEvent` on a recurring event without `exceptionDate` removes the whole
  series on iOS**, as it always did on Android. 2.x removed only the first
  occurrence on iOS.
- **`saveEvent` with an unknown `id` rejects on Android** instead of resolving
  and writing orphan reminders.
- **`removeEvent` resolves `false` instead of rejecting** when the event does
  not exist, on both platforms. 2.x rejected on iOS and resolved `false` on
  Android.
- **Events without `calendarId` go to the primary writable calendar** on
  Android. 2.x hardcoded calendar id `1`, which is often a read-only or
  holiday calendar on real devices.
- **Alarm offsets.** A relative alarm is a number of minutes from the start
  where **negative means before**, on both platforms. Android cannot ring after
  the start, so positive values are treated as "before" there too. In practice
  existing code keeps working: iOS code already used negative numbers, and
  Android code that used positive numbers still rings before the start.
- **Reading alarms on Android** now returns the correct absolute time. 2.x
  added the reminder minutes to the start instead of subtracting them.
- **`findEventById` on Android** returns `null` for a non-numeric id instead of
  crashing. `fetchAllEvents` ignores empty or non-numeric calendar ids instead
  of producing a broken query.
- **Calendars created with `saveCalendar` on Android are now synced and visible.**
  2.x inserted them with the provider's default `sync_events = 0`, and the
  provider silently excludes such calendars when it expands events, so events
  saved to them never showed up in `fetchAllEvents` or other apps.
- **Recurring events on Android are stored with `DURATION` instead of
  `DTEND`**, as the provider requires. 2.x wrote `DTEND`, which made the simple
  `recurrence` option fail and could reject `recurrenceRule` saves.
- **Cancelling or editing a single occurrence on Android now works for local
  calendars.** The provider only links an exception to its series through sync
  ids, which only sync adapters can write. For calendars of type `LOCAL` the
  library now assigns one itself. For synced calendars (Google, Exchange) the
  change applies once the account has synced the series, as in 2.x.
- **Recurrence rules on Android** are parsed by key, so rules whose parts are
  stored in a different order no longer lose `interval`, `endDate` or
  `occurrence`.
- **Editing one occurrence on iOS** with `exceptionDate` no longer overwrites
  the occurrence's start date with `exceptionDate`, so you can move an
  occurrence. The span defaults to that occurrence only; pass
  `futureEvents: true` to also change later occurrences. Saving without
  `exceptionDate` still updates the whole series as in 2.x.
- **Non-recurring events on iOS no longer carry `recurrence: ''` and an empty
  `recurrenceRule` object.** Both keys are omitted, as they always were on
  Android, so `if (event.recurrenceRule)` now behaves the same on both platforms.
- **Date strings are validated in JavaScript.** Any ISO-8601 string is
  accepted and normalized to UTC before reaching native code; an unparseable
  string makes the promise reject with `Invalid date: …` instead of being
  silently ignored or failing natively.
- **`skipAndroidTimezone` is gone.** Dates are normalized to UTC in
  JavaScript before reaching native code, so the flag no longer has anything to
  act on. Pass a `Date`, or an ISO string with an offset, to express local times.
- **`removeCalendar` resolves `false` for an unknown id on iOS** instead of
  rejecting, matching Android.
- **TypeScript types** are now generated from the source. A few names changed:
  `Alarm<D>` is deprecated in favour of `AlarmWritable` and `AlarmReadable`,
  and the required-on-Android calendar options are optional in the type with
  their requirement documented, since they do not apply on iOS.

## New in 3.0

- Every date argument accepts a `Date` as well as an ISO string.
- `AuthorizationStatus` gains `writeOnly` (iOS 17+); `saveEvent` works under write-only access.
- Absolute alarm dates work on Android, converted to minutes before the start.
- `openEventInCalendar` works under Android 11+ package visibility rules.
- Events include `status` (`none`, `confirmed`, `tentative`, `canceled`) and
  `timeZone` on both platforms, and `originalId` / `syncId` on Android.
- Recurrence rules read back `daysOfWeek` and `weekPositionInMonth` when set.
- Attendees on iOS expose `name`, `email` and `phone` from the participant
  record instead of parsing a debug string.
- `expo` config plugin (`app.plugin.js`).
- Native test suites on both platforms: `yarn test:ios`, `yarn test:android`.

## Unchanged

`checkPermissions`, `requestPermissions`, `findCalendars`, `saveCalendar`,
`removeCalendar`, `findEventById`, `fetchAllEvents`, `saveEvent`,
`removeEvent`, `removeFutureEvents`, `uriForCalendar` and
`openEventInCalendar` keep their 2.x signatures. Reminder calendars
(`entityType: 'reminder'`) are still accepted by `saveCalendar` on iOS; as in
2.x the library does not request reminders permission, so your app must have
obtained it for that to succeed on iOS 17 and later.
