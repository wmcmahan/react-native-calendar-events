# React Native Calendar Events

[![npm](https://img.shields.io/npm/v/react-native-calendar-events.svg?style=flat-square)](https://www.npmjs.com/package/react-native-calendar-events)
[![npm](https://img.shields.io/npm/dm/react-native-calendar-events.svg?style=flat-square)](https://www.npmjs.com/package/react-native-calendar-events)
[![npm](https://img.shields.io/npm/l/react-native-calendar-events.svg?style=flat-square)](https://github.com/wmcmahan/react-native-calendar-events/blob/master/LICENSE.md)

A React Native module to read and write events in the iOS and Android calendars.

Version 3 is a React Native Turbo Module with a Swift core on iOS (EventKit) and a Kotlin core on Android (CalendarContract), with native test suites on both platforms. Upgrading from 2.x? Read [MIGRATION.md](./MIGRATION.md).

## Requirements

| react-native-calendar-events | React Native | iOS | Android |
| ---------------------------- | ------------ | ---- | ------- |
| 3.x | 0.76+ (New Architecture) | 15.1+ | API 24+ |
| 2.x | 0.60+ (legacy bridge) | 9.0+ | API 16+ |

## Installation

```sh
npm install react-native-calendar-events
# or
yarn add react-native-calendar-events
```

### iOS

Run `pod install` in your `ios` directory, then add the usage descriptions to `Info.plist`. iOS 17 introduced separate keys for full and write-only access; the older key is still read on iOS 16 and earlier.

```xml
<key>NSCalendarsFullAccessUsageDescription</key>
<string>This app reads and writes your calendar to …</string>
<key>NSCalendarsWriteOnlyAccessUsageDescription</key>
<string>This app adds events to your calendar to …</string>
<key>NSCalendarsUsageDescription</key>
<string>This app reads and writes your calendar to …</string>
```

If you create reminder lists with `saveCalendar({ entityType: 'reminder' })`, also add `NSRemindersFullAccessUsageDescription` and request reminders access in your app.

### Android

Nothing to do. The library's manifest declares `READ_CALENDAR` and `WRITE_CALENDAR`, and they merge into your app. Permissions are requested at runtime with `requestPermissions()`.

### Expo

The package ships a config plugin. Add it to `app.json` and rebuild your development build:

```json
{
  "expo": {
    "plugins": [
      [
        "react-native-calendar-events",
        {
          "calendarPermission": "Allow $(PRODUCT_NAME) to access your calendar",
          "writeOnlyCalendarPermission": "Allow $(PRODUCT_NAME) to add events to your calendar"
        }
      ]
    ]
  }
}
```

Both strings are optional. Pass `remindersPermission` as well if you use reminder lists. The plugin does not work in Expo Go.

## Usage

```ts
import RNCalendarEvents from 'react-native-calendar-events';

const status = await RNCalendarEvents.requestPermissions();
if (status !== 'authorized') return;

const calendars = await RNCalendarEvents.findCalendars();
const primary = calendars.find((c) => c.isPrimary && c.allowsModifications);

const id = await RNCalendarEvents.saveEvent('Lunch with Sam', {
  calendarId: primary?.id,
  startDate: new Date('2026-03-02T12:00:00Z'),
  endDate: new Date('2026-03-02T13:00:00Z'),
  location: 'Los Angeles, CA',
  alarms: [{ date: -15 }], // fifteen minutes before
});

const events = await RNCalendarEvents.fetchAllEvents(
  new Date(),
  new Date(Date.now() + 7 * 24 * 3600 * 1000)
);
```

Every date you pass in may be a `Date` or an ISO-8601 string. Every date you read back is an ISO-8601 string in UTC with milliseconds, such as `2026-03-02T12:00:00.000Z`.

## API

All methods return promises and are available as static members of the default export.

### `checkPermissions(readOnly?)`

Returns the current [`AuthorizationStatus`](#authorizationstatus) without prompting.

| Argument | Type | Description |
| --- | --- | --- |
| `readOnly` | `boolean` | Android only, default `false`. Check for read access only instead of read and write. Ignored on iOS. |

### `requestPermissions(readOnly?)`

Prompts the user if needed and resolves with the resulting [`AuthorizationStatus`](#authorizationstatus).

| Argument | Type | Description |
| --- | --- | --- |
| `readOnly` | `boolean` | Android only, default `false`. Request read access only. Ignored on iOS. |

On iOS the library asks for full access. If the user granted write-only access instead, the status is `writeOnly`: creating events with `saveEvent` works, but reading, updating and removing reject with `unauthorized to access calendar`.

### `findCalendars()`

Resolves with an array of [`Calendar`](#calendar) for every calendar on the device.

### `saveCalendar(options)`

Creates a calendar and resolves with its id. See [`CalendarOptions`](#calendaroptions). iOS needs only `title`; Android also needs `name`, `color`, `accessLevel`, `ownerAccount` and `source`.

```ts
const id = await RNCalendarEvents.saveCalendar({
  title: 'Travel',
  color: '#2196F3',
  // Android only
  name: 'travel',
  accessLevel: 'owner',
  ownerAccount: 'me@example.com',
  source: { name: 'me@example.com', isLocalAccount: true },
});
```

### `removeCalendar(id)`

Removes a calendar and resolves with whether a calendar was removed.

### `findEventById(id)`

Resolves with the [`CalendarEventReadable`](#calendareventreadable), or `null` when no event has that id.

### `fetchAllEvents(startDate, endDate, calendarIds?)`

Resolves with every event instance that overlaps the range, sorted by start date. Recurring events are expanded into their occurrences.

| Argument | Type | Description |
| --- | --- | --- |
| `startDate` | `Date \| string` | Start of the range. |
| `endDate` | `Date \| string` | End of the range. |
| `calendarIds` | `string[]` | Optional. Restrict to these calendars. All calendars when empty. |

### `saveEvent(title, details, options?)`

Creates an event, or updates one when `details.id` is set, and resolves with the event id. See [`CalendarEventWritable`](#calendareventwritable) and [`Options`](#options).

Creating requires `startDate` and `endDate`. Updating only needs the fields you want to change.

```ts
// Update the title of one occurrence of a recurring event
await RNCalendarEvents.saveEvent('Standup (moved)', { id }, {
  exceptionDate: '2026-03-04T10:00:00.000Z',
});

// Update that occurrence and all later ones (iOS)
await RNCalendarEvents.saveEvent('Standup', { id }, {
  exceptionDate: '2026-03-04T10:00:00.000Z',
  futureEvents: true,
});
```

### `removeEvent(id, options?)`

Removes an event, or a single occurrence when `options.exceptionDate` is set. For a recurring event without `exceptionDate` the whole series is removed. Resolves with `true` when something was removed and `false` when no such event exists.

### `removeFutureEvents(id, options?)`

iOS only. Same as `removeEvent` with `futureEvents: true`: removes the occurrence at `exceptionDate` and every later one.

### `uriForCalendar()`

Android only. Resolves with the content URI of the calendar provider. Rejects on iOS.

### `openEventInCalendar(id)`

Android only. Opens the event in the system calendar app. Does nothing on iOS.

## Types

The package ships TypeScript definitions; the tables below summarize them.

### `AuthorizationStatus`

`'authorized' | 'denied' | 'restricted' | 'undetermined' | 'writeOnly'`

`writeOnly` is iOS 17 and later only. On Android, `restricted` means the user chose "don't ask again".

### `Calendar`

| Field | Type | Description |
| --- | --- | --- |
| `id` | `string` | Unique calendar id. |
| `title` | `string` | Display name. |
| `type` | `string` | iOS: `local`, `calDAV`, `exchange`, `subscription`, `birthday`. Android: the account type. |
| `source` | `string` | The account the calendar belongs to. |
| `isPrimary` | `boolean` | The device's default calendar for new events. |
| `allowsModifications` | `boolean` | Whether events can be written. |
| `color` | `string` | Hex color, e.g. `#FF0000`. |
| `allowedAvailabilities` | `string[]` | Availability values the calendar supports. |

### `CalendarEventReadable`

| Field | Type | Platform | Description |
| --- | --- | --- | --- |
| `id` | `string` | both | Event id. |
| `title` | `string` | both | |
| `startDate`, `endDate` | `string` | both | ISO-8601, UTC. |
| `allDay` | `boolean` | both | |
| `location` | `string` | both | |
| `calendarId` | `string` | both | |
| `calendar` | `Calendar` | both | The containing calendar. |
| `attendees` | `Attendee[]` | both | |
| `alarms` | `AlarmReadable[]` | both | Each alarm's absolute `date`. |
| `recurrence` | `RecurrenceFrequency \| ''` | both | Simple frequency, empty when not recurring. |
| `recurrenceRule` | `RecurrenceRule` | both | Full rule when recurring. |
| `availability` | `Availability` | both | `busy`, `free`, `tentative`, `unavailable`, `notSupported`. |
| `status` | `EventStatus` | both | `none`, `confirmed`, `tentative`, `canceled`. |
| `timeZone` | `string` | both | |
| `notes`, `url`, `structuredLocation`, `occurrenceDate`, `isDetached` | | iOS | |
| `description`, `originalId`, `syncId` | | Android | |

### `CalendarEventWritable`

Everything in `CalendarEventReadable` that makes sense to write, plus:

| Field | Type | Description |
| --- | --- | --- |
| `id` | `string` | Set to update an existing event. |
| `startDate`, `endDate` | `Date \| string` | Required when creating. |
| `alarms` | `AlarmWritable[]` | See [Alarms](#alarms). |
| `recurrence` | `RecurrenceFrequency` | Shortcut for a simple rule. |
| `recurrenceRule` | `RecurrenceRule` | See [Recurrence](#recurrence). |
| `attendees` | `{ name, email }[]` | Replaces the attendees on update. |

### Alarms

```ts
alarms: [
  { date: -30 }, // thirty minutes before the start
  { date: new Date('2026-03-02T08:00:00Z') }, // absolute
  {
    // iOS only: geofenced alarm
    date: 0,
    structuredLocation: {
      title: 'Office',
      proximity: 'enter',
      radius: 100,
      coords: { latitude: 37.33, longitude: -122.03 },
    },
  },
];
```

A relative alarm is a number of minutes from the start where negative means before. Android's calendar provider cannot ring after the start, so positive numbers are treated as "before" there too, and an absolute date after the start rings at the start.

### Recurrence

| Field | Type | Description |
| --- | --- | --- |
| `frequency` | `'daily' \| 'weekly' \| 'monthly' \| 'yearly'` | Required. |
| `interval` | `number` | Every n periods. |
| `occurrence` | `number` | Stop after n occurrences. |
| `endDate` | `Date \| string` | Stop at this date. Overrides `occurrence`. |
| `daysOfWeek` | `('MO' \| 'TU' \| 'WE' \| 'TH' \| 'FR' \| 'SA' \| 'SU')[]` | Weekly: which days. Monthly: combined with `weekPositionInMonth`. |
| `weekPositionInMonth` | `number` | Monthly: which week (1 = first, -1 = last). |
| `duration` | `string` | Android only. RFC 5545 duration of each occurrence, default `PT1H`. |
| `weekStart` | weekday | Android only. |

### `Options`

| Field | Type | Platform | Description |
| --- | --- | --- | --- |
| `exceptionDate` | `Date \| string` | both | Target a single occurrence of a recurring event. |
| `futureEvents` | `boolean` | iOS | With `exceptionDate`: also apply to later occurrences. |
| `sync` | `boolean` | Android | Write through the sync adapter and force a sync. Helps when saved events disappear. |

### `CalendarOptions`

| Field | Type | Platform | Description |
| --- | --- | --- | --- |
| `title` | `string` | both | Required. |
| `color` | `string` | both (required on Android) | Any color React Native understands. |
| `entityType` | `'event' \| 'reminder'` | iOS | Default `event`. |
| `name` | `string` | Android, required | |
| `accessLevel` | `'contributor' \| 'editor' \| 'freebusy' \| 'override' \| 'owner' \| 'read' \| 'respond' \| 'root'` | Android, required | |
| `ownerAccount` | `string` | Android, required | |
| `source` | `{ name, type }` or `{ name, isLocalAccount: true }` | Android, required | |

## Troubleshooting

### After saving an event, it disappears from the calendar

Usually a sync issue: the event was saved to a calendar the device does not keep in sync.

On iOS check _Settings_ > _Calendar_ > _Sync_ > **All Events**, and see [Apple's iCloud calendar sync help](https://support.apple.com/en-us/HT203521). On Android pass `{ sync: true }` as the options argument to `saveEvent`, and see [Google Calendar sync help](https://support.google.com/calendar/answer/6261951).

### Duplicated events after editing an event

Another symptom of the sync issue above.

### `unauthorized to access calendar` on iOS 17

Add the `NSCalendarsFullAccessUsageDescription` key. Without it the system denies the request silently.

### Read methods reject although the user granted access

On iOS 17 the user may have granted write-only access. Check for `writeOnly` from `checkPermissions()` and point the user to Settings.

## Contributing

See [CONTRIBUTING.md](./CONTRIBUTING.md) for the development workflow. Useful commands:

```sh
yarn test          # JavaScript unit tests
yarn lint:ios      # swift-format
yarn lint:android  # Android Lint
yarn test:expo     # Expo config plugin + published package check
yarn test:ios      # XCTest suite on a simulator
yarn test:android  # Robolectric suite
yarn test:android:emulator  # instrumented tests on a device or emulator
yarn example ios   # run the example app
yarn example android
```

## License

MIT. See [LICENSE.md](./LICENSE.md).
