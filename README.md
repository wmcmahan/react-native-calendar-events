# React Native Calendar Events

[![npm](https://img.shields.io/npm/v/react-native-calendar-events.svg?style=flat-square)](https://www.npmjs.com/package/react-native-calendar-events)
[![npm](https://img.shields.io/npm/dm/react-native-calendar-events.svg?style=flat-square)](https://www.npmjs.com/package/react-native-calendar-events)
[![npm](https://img.shields.io/npm/l/react-native-calendar-events.svg?style=flat-square)](https://github.com/wmcmahan/react-native-calendar-events/blob/master/LICENSE.md)

A React Native module to help access and save events to iOS and Android calendars.

## Getting started

This package assumes that you already have a React Native project or are familiar with React Native. If not, checkout the official documentation for more details about getting started with [React Native](https://facebook.github.io/react-native/docs/getting-started.html).

### Support

| version    | react-native version |
| ---------- | -------------------- |
| 2.0.0+     | 0.60.0+              |
| pre 2.0.0+ | 0.40.0+              |

For 0.59-, you should use [`jetify -r`](https://github.com/mikehardy/jetifier/blob/master/README.md#to-reverse-jetify--convert-node_modules-dependencies-to-support-libraries)

## Installation

```bash
$ npm install --save react-native-calendar-events
# --- or ---
$ yarn add react-native-calendar-events
```

Don't forget going into the `ios` directory to execute a `pod install`.

## 🆘 Manual linking

Because this package targets React Native 0.60.0+, you will probably don't need to link it manually. Otherwise if it's not the case, follow this additional instructions:

<details>
  <summary><b>👀 See manual linking instructions</b></summary>

### iOS

Add this line to your `ios/Podfile` file, then run `pod install`.

```bash
target 'YourAwesomeProject' do
  # …
  pod 'RNCalendarEvents', :path => '../node_modules/react-native-calendar-events'
end
```

### Android

1 - Add the following lines to `android/settings.gradle`:

```gradle
include ':react-native-calendar-events'
project(':react-native-calendar-events').projectDir = new File(rootProject.projectDir, '../node_modules/react-native-calendar-events/android')
```

2 - Add the implementation line to the dependencies in `android/app/build.gradle`:

```gradle
dependencies {
  // ...
  implementation project(':react-native-calendar-events')
}
```

3 - Add the import and link the package in `MainApplication.java`:

```java
import com.calendarevents.RNCalendarEventsPackage; // <- add the RNCalendarEventsPackage import

public class MainApplication extends Application implements ReactApplication {

  // …

  @Override
  protected List<ReactPackage> getPackages() {
    @SuppressWarnings("UnnecessaryLocalVariable")
    List<ReactPackage> packages = new PackageList(this).getPackages();
    // …
    packages.add(new RNCalendarEventsPackage());
    return packages;
  }

  // …
}
```

</details>

### iOS specific instructions

Add `RNCalendarEvents`, as well as `EventKit.framework` to project libraries if not already done.

Setting up privacy usage descriptions may also be required depending on which iOS version is supported. This involves updating the Property List, `Info.plist`, with the corresponding key for the EKEventStore api. [Info.plist reference](https://developer.apple.com/library/content/documentation/General/Reference/InfoPlistKeyReference/Articles/CocoaKeys.html).

For updating the `Info.plist` key/value via XCode, add a `Privacy - Calendars Usage Description` key with a usage description as the value. Resulting change to `Info.plist` should look something like:

```xml
<key>NSCalendarsUsageDescription</key>
<string>This app requires access to the calendar</string>
```

## API

The following API allows for interacting with both iOS and Android device calendars. See the full list of available [event fields](#event-fields).

```javascript
import RNCalendarEvents from "react-native-calendar-events";
```

### `checkPermissions`

Get calendar authorization status.
You may check for the default read/write access with no argument, or read-only access on Android by passing boolean true. iOS is always read/write.

```javascript
RNCalendarEvents.checkPermissions((readOnly = false));
```

Returns: **Promise**

- fulfilled: String - `denied`, `restricted`, `authorized` or `undetermined`
- rejected: Error

### `requestPermissions`

Request calendar authorization. Authorization must be granted before accessing calendar events.

```javascript
RNCalendarEvents.requestPermissions((readOnly = false));
```

(readOnly is for Android only, see below)

> Android note: this is necessary for targeted SDK of >=23.
> iOS note: This method will crash, if you didn't update `Info.plist`. Follow carefully installation instruction.

Returns: **Promise**

- fulfilled: String - `denied`, `restricted`, `authorized` or `undetermined`
- rejected: Error

### Read-Only `requestPermissions` (_Android only_)

⚠️ Note that to restrict to read-only usage on Android (iOS is always read/write) you will need to alter the included Android permissions
as the `AndroidManifest.xml` is merged during the Android build.

You do that by altering your AndroidManifest.xml to "remove" the WRITE_CALENDAR permission with an entry like so:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
  xmlns:tools="http://schemas.android.com/tools"
  >
  <!-- ... -->
  <uses-permission tools:node="remove" android:name="android.permission.WRITE_CALENDAR" />
```

### `findCalendars`

Finds all the calendars on the device.

```javascript
RNCalendarEvents.findCalendars();
```

Returns: **Promise**

- fulfilled: Array - A list of known calendars on the device
- rejected: Error

### `saveCalendar`

Create a calendar.

```javascript
RNCalendarEvents.saveCalendar(calendar);
```

⚠️ When you want to save a calendar, you need to use a valid source (find using [`findCalendars`](#findcalendars)).

Arguments:

- [calendar](#Calendar-options): Object - Calendar to create.

Returns: **Promise**

- fulfilled: The id of the created calendar
- rejected: Error

### `removeCalendar`

Removes a calendar.

```javascript
RNCalendarEvents.removeCalendar(id);
```

Arguments:

- id: String - The id of the calendar to remove.

Returns: **Promise**

- fulfilled: Bool - Successful
- rejected: Error

### `findEventById`

Find calendar event by id.
Returns a promise with fulfilled found events.

```javascript
RNCalendarEvents.findEventById(id);
```

Arguments:

- id: String - The events unique id.

Returns: **Promise**

- fulfilled: Object | null - Found event with unique id.
- rejected: Error

### `fetchAllEvents`

Fetch all calendar events.
Returns a promise with fulfilled found events.

```javascript
RNCalendarEvents.fetchAllEvents(startDate, endDate, calendars);
```

Arguments:

- startDate: String - The start date of the range of events fetched.
- endDate: String - The end date of the range of events fetched.
- calendars: Array - List of calendar id strings to specify calendar events. Defaults to all calendars if empty.

Returns: **Promise**

- fulfilled: Array - Matched events within the specified date range.
- rejected: Error

### `saveEvent`

Creates or updates a calendar event. - [wiki guide](https://github.com/wmcmahan/react-native-calendar-events/wiki/Creating-basic-event)

```javascript
RNCalendarEvents.saveEvent(title, details, options);
```

Arguments:

- title: String - The title of the event.
- [details](#event-fields): Object - The event's details.
- [options](#options): Object - Options specific to the saved event. Note that on Android, `saveEvent` accepts an additional option `sync` (boolean) to prevent syncing issues.

Returns: **Promise**

- fulfilled: String - Created event's ID.
- rejected: Error

To update an event, the event `id` must be defined. - [wiki guide](https://github.com/wmcmahan/react-native-calendar-events/wiki/Updating-events)

```javascript
RNCalendarEvents.saveEvent(title, {
  id: "FE6B128F-C0D8-4FB8-8FC6-D1D6BA015CDE",
});
```

#### Example for saveEvent

Creating events is fairly straightforward. Hopefully the following explanation can help.

##### Basic `saveEvent`
For both iOS and Android the pattern is simple; the event needs a `title` as well as a `startDate` and `endDate`. The `endDate` should also be a date later than the `startDate`.

```javascript
RNCalendarEvents.saveEvent("Title of event", {
  startDate: "2016-08-19T19:26:00.000Z",
  endDate: "2017-08-19T19:26:00.000Z",
});
```

##### Specify a calendar `saveEvent`

The example above will simply save the event to your devices default calendar. If you wish to control which calendar the event is saved to, you must provide the `calendarId`. This will ensure your event is saved to an expected calendar.

```javascript
RNCalendarEvents.saveEvent("Title of event", {
  calendarId: "141",
  startDate: "2016-08-19T19:26:00.000Z",
  endDate: "2017-08-19T19:26:00.000Z",
});
```

##### Additional fields with `saveEvent`

There are also other writable fields available. For example, you may wish to specify the location of the event or add additional notes for the event. Complete list of fields can be found [in the wiki](https://github.com/wmcmahan/react-native-calendar-events/wiki/Event-Fields#event-details).

```javascript
RNCalendarEvents.saveEvent('Title of event', {
  calendarId: '141',
  startDate: '2016-08-19T19:26:00.000Z',
  endDate: '2017-08-19T19:26:00.000Z',
  location: 'Los Angeles, CA',
  notes: 'Bring sunglasses'
}) 
```

### `removeEvent`

Removes calendar event.

```javascript
RNCalendarEvents.removeEvent(id, options);
```

Arguments:

- id: String - The id of the event to remove.
- [options](#options): Object - Options specific to event removal.

Returns: **Promise**

- fulfilled: Bool - Successful
- rejected: Error

## Event fields

| Property                                             | Type   | Description                                                                                           | iOS | Android |
| :--------------------------------------------------- | :----- | :---------------------------------------------------------------------------------------------------- | :-: | :-----: |
| **id\***                                             | String | Unique id for the calendar event.                                                                     |  ✓  |    ✓    |
| **calendarId\*\***                                   | String | Unique id for the calendar where the event will be saved. Defaults to the device's default calendar.  |  ✓  |    ✓    |
| **title**                                            | String | The title for the calendar event.                                                                     |  ✓  |    ✓    |
| **startDate**                                        | String | The start date of the calendar event in ISO format.                                                   |  ✓  |    ✓    |
| **endDate**                                          | String | The end date of the calendar event in ISO format.                                                     |  ✓  |    ✓    |
| **allDay**                                           | Bool   | Indicates whether the event is an all-day                                                             |  ✓  |    ✓    |
| **recurrence**                                       | String | The simple recurrence frequency of the calendar event `daily`, `weekly`, `monthly`, `yearly` or none. |  ✓  |    ✓    |
| [**recurrenceRule**](#recurrence-rule) \*\*          | Object | The events recurrence settings.                                                                       |  ✓  |    ✓    |
| **occurrenceDate\***                                 | String | The original occurrence date of an event if it is part of a recurring series.                         |  ✓  |         |
| **isDetached**                                       | Bool   | Indicates whether an event is a detached instance of a repeating event.                               |  ✓  |         |
| **status**                                           | String | The status of the calendar event `confirmed`, `tentative`, `cancelled`, `none` or `notSupported`.     |  ✓  |         |
| **url**                                              | String | The url associated with the calendar event.                                                           |  ✓  |         |
| **location**                                         | String | The location associated with the calendar event.                                                      |  ✓  |    ✓    |
| [**structuredLocation**](#alarm-structuredlocation)  | String | The structuredLocation associated with the calendar event.                                            |  ✓  |         |
| **notes**                                            | String | The notes associated with the calendar event.                                                         |  ✓  |         |
| **description**                                      | String | The description associated with the calendar event.                                                   |     |    ✓    |
| [**alarms**](#alarms)                                | Array  | The alarms associated with the calendar event, as an array of alarm objects.                          |  ✓  |    ✓    |
| [**attendees**](#attendees)\*                        | Array  | The attendees of the event, including the organizer.                                                  |  ✓  |    ✓    |
| [**calendar**](#calendar)\*                          | Object | The calendar containing the event.                                                                    |  ✓  |    ✓    |
| **skipAndroidTimezone**                              | Bool   | Skip the process of setting automatic timezone on android                                             |     |    ✓    |
| **timeZone**                                         | String | The time zone associated with the event                                                               |  ✓  |         |

### Calendar

| Property                    | Type   | Description                                                                | iOS | Android |
| :-------------------------- | :----- | :------------------------------------------------------------------------- | :-: | :-----: |
| **id**                      | String | Unique calendar ID.                                                        |  ✓  |    ✓    |
| **title**                   | String | The calendar’s title.                                                      |  ✓  |    ✓    |
| **type**                    | String | The calendar’s type.                                                       |  ✓  |    ✓    |
| **source**                  | String | The source object representing the account to which this calendar belongs. |  ✓  |    ✓    |
| **isPrimary\***             | Bool   | Indicates if the calendar is assigned as primary.                          |  ✓  |    ✓    |
| **allowsModifications\***   | Bool   | Indicates if the calendar allows events to be written, edited or removed.  |  ✓  |    ✓    |
| **color\***                 | String | The color assigned to the calendar represented as a hex value.             |  ✓  |    ✓    |
| **allowedAvailabilities\*** | Array  | The event availability settings supported by the calendar.                 |  ✓  |    ✓    |

### Attendees

| Property    | Type   | Description                        | iOS | Android |
| :---------- | :----- | :--------------------------------- | :-: | :-----: |
| **name**    | String | The name of the attendee.          |  ✓  |    ✓    |
| **email\*** | String | The email address of the attendee. |  ✓  |    ✓    |
| **phone\*** | String | The phone number of the attendee.  |  ✓  |         |

### Recurrence rule

| Property       | Type   | Description                                                                           | iOS | Android |
| :------------- | :----- | :------------------------------------------------------------------------------------ | :-: | :-----: |
| **frequency**  | String | Event recurring frequency. Allowed values are `daily`, `weekly`, `monthly`, `yearly`. |  ✓  |    ✓    |
| **endDate**    | String | Event recurring end date. This overrides occurrence.                                  |  ✓  |    ✓    |
| **occurrence** | Number | Number of event occurrences.                                                          |  ✓  |    ✓    |
| **interval**   | Number | The interval between events of this recurrence.                                       |  ✓  |    ✓    |

### Alarms

| Property                                            | Type             | Description                                                                                                                                                           | iOS | Android |
| :-------------------------------------------------- | :--------------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :-: | :-----: |
| **date**                                            | String or Number | If a String is given, an alarm will be set with an absolute date. If a Number is given, an alarm will be set with a relative offset (in minutes) from the start date. |  ✓  |    ✓    |
| [**structuredLocation**](#alarm-structuredlocation) | Object           | The location to trigger an alarm.                                                                                                                                     |  ✓  |         |

### Alarm structuredLocation

| Property      | Type   | Description                                                                                            | iOS | Android |
| :------------ | :----- | :----------------------------------------------------------------------------------------------------- | :-: | :-----: |
| **title**     | String | The title of the location.                                                                             |  ✓  |         |
| **proximity** | String | A value indicating how a location-based alarm is triggered. Possible values: `enter`, `leave`, `none`. |  ✓  |         |
| **radius**    | Number | A minimum distance from the core location that would trigger the calendar event's alarm.               |  ✓  |         |
| **coords**    | Object | The geolocation coordinates, as an object with latitude and longitude properties                       |  ✓  |         |

### Options

| Property          | Type   | Description                                                                                                    | iOS | Android |
| :---------------- | :----- | :------------------------------------------------------------------------------------------------------------- | :-: | :-----: |
| **exceptionDate** | String | The start date of a recurring event's exception instance. Used for updating single event in a recurring series |  ✓  |    ✓    |
| **futureEvents**  | Bool   | If `true` the update will span all future events. If `false` it only update the single instance.               |  ✓  |         |

### Calendar options

| Property                  | Type   | Description                                                                                                                                                                                                                                                                            | iOS | Android |
| :------------------------ | :----- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :-: | :-----: |
| **title**                 | String | The calendar title (required)                                                                                                                                                                                                                                                          |  ✓  |    ✓    |
| **color**                 | String | The calendar color (required)                                                                                                                                                                                                                                                          |  ✓  |    ✓    |
| **entityType**            | String | 'event' or 'reminder' (required)                                                                                                                                                                                                                                                       |  ✓  |         |
| **name**                  | String | The calendar name (required)                                                                                                                                                                                                                                                           |     |    ✓    |
| **accessLevel**           | String | Defines how the event shows up for others when the calendar is shared [doc](https://developer.android.com/reference/android/provider/CalendarContract.EventsColumns.html#ACCESS_LEVEL)(required) `'contributor', 'editor', 'freebusy', 'override', 'owner', 'read', 'respond', 'root'` |     |    ✓    |
| **ownerAccount**          | String | The owner account for this calendar, based on the calendar feed [doc](https://developer.android.com/reference/android/provider/CalendarContract.CalendarColumns#OWNER_ACCOUNT)(required)                                                                                               |     |    ✓    |
| **source**                | Object | The calendar Account source (required)                                                                                                                                                                                                                                                 |     |    ✓    |
| **source.name**           | String | The Account name (required)                                                                                                                                                                                                                                                            |     |    ✓    |
| **source.type**           | String | The Account type                                                                                                                                                                                                                                                                       |     |    ✓    |
| **source.isLocalAccount** | Bool   | The source (required if **source.type** is not used)                                                                                                                                                                                                                                   |     |    ✓    |

\* _Read only_, \*\* \_Write only

## Troubleshooting

These are some common issues you may run into while using `react-native-calendar-events` library.
If you encounter something that is not listed here, try [searching in GitHub issues of `react-native-calendar-events`](https://github.com/wmcmahan/react-native-calendar-events/issues).

### After saving an event, it disappear form the calendar

This might be related to a sync issue.
You need to be sure that the event you saved is matching what your device will keep in sync.

For iOS, you might have not all event synced. You might need to update this iOS settings in _Settings_ > _Calendar_ > _Sync_ > **All Events**. If that's not enough, it might be worth checking [iOS iCloud sync documentation](https://support.apple.com/en-us/HT203521).  
For Android, you can have a look to [Google Calendar sync problems documentation](https://support.google.com/calendar/answer/6261951).

### Duplicated events after editing and saving an event

Another symptom of syncing issue. See the issue above.
Note that on Android, `saveEvent` accepts an additional option `sync` (boolean) to prevent syncing issues.

## Wiki

- [Create basic event](https://github.com/wmcmahan/react-native-calendar-events/wiki/Creating-basic-event)
- [Create recurring event](https://github.com/wmcmahan/react-native-calendar-events/wiki/Create-recurring-event)
- [Updating events](https://github.com/wmcmahan/react-native-calendar-events/wiki/Updating-events)
- [Adding alarms](https://github.com/wmcmahan/react-native-calendar-events/wiki/Event-alarms)

## Authors

- **Will McMahan** - Initial code - [github.com/wmcmahan](https://github.com/wmcmahan)

See also the list of [contributors](https://github.com/wmcmahan/react-native-calendar-events/contributors) who participated in this project.

## License

This project is licensed under the MIT License - see the [LICENSE.md](https://github.com/wmcmahan/react-native-calendar-events/blob/master/LICENSE.md) file for details

## Acknowledgments

Big thanks to all who have contributed, raised an issue or simply find use in this project. Cheers!
