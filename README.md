# React-Native-Calendar-Events
React Native Module for IOS Calendar Events

## Install
```
npm install react-native-calendar-events
```

### iOS
Add `RNCalendarEvents`, as well as `EventKit.framework` to project libraries.

For iOS 8 compatibility, you may need to link your project with `CoreFoundation.framework` (status = Optional) under Link Binary With Libraries on the Build Phases page of your project settings.

### Android

- Edit `build.gradle` to look like this:
```java
apply plugin: 'com.android.application'

android {
  ...
}

dependencies {
  ...
+ compile project(':react-native-calendar-events')
}
```

- In `settings.gradle`, insert the following code:
```java
include ':react-native-calendar-events'
project(':react-native-calendar-events').projectDir = new File(rootProject.projectDir, '../node_modules/react-native-calendar-events/android')
```

- Edit your `MainActivity.java` to look like this:
```java
package com.myapp;

....
import com.calendarevents.CalendarEventsPackage;

public class MainActivity extends extends ReactActivity {

  @Override
	protected List<ReactPackage> getPackages() {
		return Arrays.<ReactPackage>asList(
						new MainReactPackage(),
						new CalendarEventsPackage()
		);
	}
	...
}
```

- If your apps targetSDK is 23 or higher, edit your `MainActivity.java` to look like this:
```java
import com.calendarevents.CalendarEventsPackage;

public class MainActivity extends ReactActivity {
  ...

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
      CalendarEventsPackage.onRequestPermissionsResult(requestCode, permissions, grantResults);
      super.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }

  ...
}
```

## Usage

Require the `react-native-calendar-events` module.

```javascript
import RNCalendarEvents from 'react-native-calendar-events';
```

> **NOTE**: Starting from `1.0.0`, this package will use Promises instead of Events.

## Properties

| Property        | Value            | Description |
| :--------------- | :---------------- | :----------- |
| id              | String (read-only)             | Unique id for the calendar event. |
| title           | String             | The title for the calendar event. |
| startDate       | Date             | The start date of the calendar event. |
| endDate         | Date             | The end date of the calendar event. |
| allDay          | Bool             | Indicates whether the event is an all-day event. |
| recurrence      | String           | The simple recurrence frequency of the calendar event `daily`, `weekly`, `monthly`, `yearly` or none. |
| occurrenceDate  | Date (read-only) | The original occurrence date of an event if it is part of a recurring series. |
| isDetached      | Bool             | Indicates whether an event is a detached instance of a repeating event. |
| location        | String           | The location associated with the calendar event. |
| notes           | String           | The notes associated with the calendar event. |
| alarms          | Array            | The alarms associated with the calendar event, as an array of alarm objects. |


## authorizationStatus
Get authorization status for IOS EventStore.

```javascript
RNCalendarEvents.authorizationStatus()
```

Returns: Promise 
- fulfilled: String - `denied`, `restricted`, `authorized` or `undetermined`
- rejected: Error

Example:
```javascript
RNCalendarEvents.authorizationStatus()
  .then(status => {
    // handle status
  })
  .catch(error => {
   // handle error
  });
```

## authorizeEventStore
Request authorization to IOS EventStore. Authorization must be granted before accessing calendar events.

```javascript
RNCalendarEvents.authorizeEventStore()
```

Returns: Promise 
 - fulfilled: String - `denied`, `restricted`, `authorized` or `undetermined`
 - rejected: Error

Example:
```javascript
RNCalendarEvents.authorizeEventStore()
  .then(status => {
    // handle status
  })
  .catch(error => {
   // handle error
  });
```

## fetchAllEvents (iOS Only)
Fetch all calendar events from EventStore
Returns a promise with fulfilled with found events.

```javascript
RNCalendarEvents.fetchAllEvents(startDate, endDate)
```

Parameters: 
 - startDate: Date - The start date of the range of events fetched.
 - endDate: Date - The end date of the range of events fetched.

Returns: Promise 
 - fulfilled: Array - Matched events within the specified date range.
 - rejected: Error

Example:
```javascript
RNCalendarEvents.fetchAllEvents('2016-08-19T19:26:00.000Z', '2017-08-19T19:26:00.000Z')
  .then(events => {
    // handle events
  })
  .catch(error => {
   // handle error
  });
```

## saveEvent
Creates calendar event.

```
RNCalendarEvents.saveEvent(title, settings);
```

Parameters: 
 - title: String - The title of the event.
 - settings: Object - The event's settings.

Returns: Promise 
 - fulfilled: String - Created event's ID.
 - rejected: Error

Example:

```javascript
RNCalendarEvents.saveEvent('title', {
    location: 'location',
    notes: 'notes',
    startDate: '2016-10-01T09:45:00.000UTC',
    endDate: '2016-10-02T09:45:00.000UTC'
  })
  .then(id => {
    // handle success
  })
  .catch(error => {
    // handle error
  });
```

## Update Event (iOS Only)
Give the unique calendar event **ID** to update an existing calendar event.

Parameters: 
 - title: String - The title of the event.
 - settings: Object - The event's settings.

Returns: Promise 
 - fulfilled: String - Updated event's ID.
 - rejected: Error

Example:

```javascript
RNCalendarEvents.saveEvent('title', {
    id: 'FE6B128F-C0D8-4FB8-8FC6-D1D6BA015CDE',
    location: 'location',
    notes: 'notes',
    startDate: '2016-10-01T09:45:00.000UTC',
    endDate: '2016-10-02T09:45:00.000UTC'
  })
  .then(id => {
    // handle success
  })
  .catch(error => {
    // handle error
  });
```

## Create calendar event with alarms

### Alarm options:

| Property        | Value            | Description |
| :--------------- | :------------------| :----------- |
| date           | Date or Number    | If a Date is given, an alarm will be set with an absolute date. If a Number is given, an alarm will be set with a relative offset (in minutes) from the start date. |
| structuredLocation | Object             | (iOS Only) The location to trigger an alarm. |

### Alarm structuredLocation properties:

| Property        | Value            | Description |
| :--------------- | :------------------| :----------- |
| title           | String  | The title of the location.|
| proximity | String             | A value indicating how a location-based alarm is triggered. Possible values: `enter`, `leave`, `none`. |
| radius | Number             | A minimum distance from the core location that would trigger the calendar event's alarm. |
| coords | Object             | The geolocation coordinates, as an object with latitude and longitude properties |

Example with date:

```javascript
RNCalendarEvents.saveEvent('title', {
  location: 'location',
  notes: 'notes',
  startDate: '2016-10-01T09:45:00.000UTC',
  endDate: '2016-10-02T09:45:00.000UTC',
  alarms: [{
    date: -1 // or absolute date - iOS Only
  }]
});

```
Example with structuredLocation (iOS Only):

```javascript
RNCalendarEvents.saveEvent('title', {
  location: 'location',
  notes: 'notes',
  startDate: '2016-10-01T09:45:00.000UTC',
  endDate: '2016-10-02T09:45:00.000UTC',
  alarms: [{
    structuredLocation: {
      title: 'title',
      proximity: 'enter',
      radius: 500,
      coords: {
        latitude: 30.0000,
        longitude: 97.0000
      }
    }
  }]
});
```

Example with recurrence:

```javascript
RNCalendarEvents.saveEvent('title', {
  location: 'location',
  notes: 'notes',
  startDate: '2016-10-01T09:45:00.000UTC',
  endDate: '2016-10-02T09:45:00.000UTC',
  alarms: [{
    date: -1 // or absolute date - iOS Only
  }],
  recurrence: 'daily'
});
```

## removeEvent (iOS Only)
Removes calendar event.

```javascript
RNCalendarEvents.removeEvent(id);
```

Parameters:
 - id: String - The id of the event to remove.

Returns: Promise 
 - fulfilled: Bool - Successful
 - rejected: Error

Example:

```javascript
RNCalendarEvents.removeEvent('FE6B128F-C0D8-4FB8-8FC6-D1D6BA015CDE')
  .then(success => {
    // handle success
  })
  .catch(error => {
    // handle error
  });
```

## removeFutureEvents (iOS Only)
Removes future (recurring) calendar events.

```javascript
RNCalendarEvents.removeFutureEvents(id);
```

Parameters:
 - id: String - The id of the event to remove.

Returns: Promise 
 - fulfilled: Bool - Successful
 - rejected: Error

Example:

```javascript
RNCalendarEvents.removeFutureEvents('FE6B128F-C0D8-4FB8-8FC6-D1D6BA015CDE')
  .then(success => {
    // handle success
  })
  .catch(error => {
    // handle error
  });
```
