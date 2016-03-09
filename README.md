# React-Native-Calendar-Events
React Native Module for IOS Calendar Events


## Install
```
npm install react-native-calendar-events
```
Then add `RNCalendarEvents`, as well as `EventKit.framework` to project libraries.

## Usage

Require the `react-native-calendar-events` module and React Native's `NativeAppEventEmitter` module.
```javascript
import React from 'react-native';
import RNCalendarEvents from 'react-native-calendar-events';

const {NativeAppEventEmitter} = React;
```

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

## Events

| Name        | Body            | Description |
| :--------------- | :---------------- | :----------- |
| calendarEventsChanged              | (empty)             | |
| eventSaveSuccess           | event id             | The ID of the successfully saved event |
| eventSaveError       | error message            | Error that occurred during save. |

Example:

```javascript
componentWillMount () {
  this.eventEmitter = NativeAppEventEmitter.addListener('calendarEventsChanged', () => {
    RNCalendarEvents.fetchAllEvents(startDate, endDate, events => {...});
  });
}

componentWillUnmount () {
  this.eventEmitter.remove();
}
```

## Get authorization status for IOS EventStore
Finds the current authorization status: "denied", "restricted", "authorized" or "undetermined".

```javascript
RNCalendarEvents.authorizationStatus(({status}) => {...});
```

## Request authorization to IOS EventStore
Authorization must be granted before accessing calendar events.

```javascript
RNCalendarEvents.authorizeEventStore(({status}) => {...});
```


## Fetch all calendar events from EventStore

```javascript
RNCalendarEvents.fetchAllEvents(startDate, endDate, events => {...});
```
## Create calendar event

```
RNCalendarEvents.saveEvent(title, settings);
```
Example:
```javascript
RNCalendarEvents.saveEvent('title', {
  location: 'location',
  notes: 'notes',
  startDate: '2016-10-01T09:45:00.000UTC'
});
```

## Create calendar event with alarms

### Alarm options:

| Property        | Value            | Description |
| :--------------- | :------------------| :----------- |
| date           | Date or Number    | If a Date is given, an alarm will be set with an absolute date. If a Number is given, an alarm will be set with a relative offset (in minutes) from the start date. |
| structuredLocation | Object             | The location to trigger an alarm. |

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
  alarms: [{
    date: -1 // or absolute date
  }]
});
```
Example with structuredLocation:

```javascript
RNCalendarEvents.saveEvent('title', {
  location: 'location',
  notes: 'notes',
  startDate: '2016-10-01T09:45:00.000UTC',
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
  alarms: [{
    date: -1 // or absolute date
  }],
  recurrence: 'daily'
});
```

## Update calendar event
Give the unique calendar event **id** to update an existing calendar event.

```javascript
RNCalendarEvents.saveEvent('title', {
  id: 'id',
  location: 'location',
  notes: 'notes',
  startDate: '2016-10-02T09:45:00.000UTC'
});
```

## Remove calendar event
Give the unique calendar event **id** to remove an existing calendar event.

```javascript
RNCalendarEvents.removeEvent('id');
```
