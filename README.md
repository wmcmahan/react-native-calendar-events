# React Native Calendar Events
React Native Module for accessing and saving events to iOS and Android calendars.

## Install
```
npm install --save react-native-calendar-events
```

## Link

Check with React Native [documention on linking libraries](https://facebook.github.io/react-native/docs/linking-libraries-ios.html) for more details.

```
react-native link
```
## OS instructions
- [iOS setup](https://github.com/wmcmahan/react-native-calendar-events/wiki/iOS-setup) 
- [Android setup](https://github.com/wmcmahan/react-native-calendar-events/wiki/Android-setup) 

## Usage

```javascript
import RNCalendarEvents from 'react-native-calendar-events';
```

### Event Properties

| Property        | Type            | Description | iOS | Android |
| :--------------- | :---------------- | :----------- | :-----------: | :-----------: |
| **id***  | String  | Unique id for the calendar event. | ✓ | ✓ |
| **calendarId****   | String           | Unique id for the calendar where the event will be saved. Defaults to the device's default calendar. | ✓ | ✓ |
| **title**           | String           | The title for the calendar event. | ✓ | ✓ |
| **startDate**       | Date             | The start date of the calendar event in ISO format. | ✓ | ✓ |
| **endDate**         | Date             | The end date of the calendar event in ISO format. | ✓ | ✓ |
| **allDay**          | Bool             | Indicates whether the event is an all-day event. | ✓ | ✓ |
| **recurrence**      | String           | The simple recurrence frequency of the calendar event `daily`, `weekly`, `monthly`, `yearly` or none. | ✓ | ✓ |
| [**recurrenceRule**](#recurrence-rule-properties) **  | Object           | The events recurrence settings. | ✓ | ✓ |
| **occurrenceDate***  | Date | The original occurrence date of an event if it is part of a recurring series. | ✓ |  |
| **isDetached**      | Bool        | Indicates whether an event is a detached instance of a repeating event. | ✓ |  |
| **url**             | String           | The url associated with the calendar event. | ✓ | ✓ |
| **location**        | String           | The location associated with the calendar event. | ✓ | ✓ |
| **notes**           | String           | The notes associated with the calendar event. | ✓ |  |
| **description**     | String           | The description associated with the calendar event. |  | ✓ |
| [**alarms**](#create-calendar-event-with-alarms)          | Array            | The alarms associated with the calendar event, as an array of alarm objects. | ✓ | ✓ |
| **calendar***    | Object           | The calendar containing the event.| ✓ | ✓ |

<p>* <i>Read only</i>, ** <i>Write only</i> </p>

### Recurrence Rule properties:
| Property        | Type            | Description |  iOS | Android |
| :--------------- | :---------------- | :----------- | :-----------: | :-----------: |
| **frequency**     | String           | Event recurring frequency `daily`, `weekly`, `monthly`, `yearly` | ✓ | ✓ |
| **endDate**       | Date             | Event recurring end date. This overrides occurrence | ✓ | ✓ |
| **occurrence**    | Number           | Number of event occurrences. | ✓ | ✓ |
| **interval**      | Number           | The interval between events of this recurrence. | ✓ | ✓ |

<p>* <i>Read only</i>, ** <i>Write only</i> </p>

### Options:
| Property        | Type            | Description |  iOS | Android |
| :--------------- | :---------------- | :----------- | :-----------: | :-----------: |
| **exceptionDate**   | Date           | The start date of a recurring event's exception instance. Used for updating single event in a recurring series | ✓ | ✓ |
| **futureEvents**   | Bool            | If `true` the update will span all future events. If `false` it only update the single instance.  | ✓ |  |

<br/>

## authorizationStatus
Get calendar authorization status.

```javascript
RNCalendarEvents.authorizationStatus()
```

Returns: **Promise** 
- fulfilled: String - `denied`, `restricted`, `authorized` or `undetermined`
- rejected: Error

<br/>

## authorizeEventStore
Request calendar authorization. Authorization must be granted before accessing calendar events.

> Android note: This is only necessary for targeted SDK of 23 and higher.

```javascript
RNCalendarEvents.authorizeEventStore()
```

Returns: **Promise** 
 - fulfilled: String - `denied`, `restricted`, `authorized` or `undetermined`
 - rejected: Error

<br/>

## findEventById
Find calendar event by id.
Returns a promise with fulfilled found events.

```javascript
RNCalendarEvents.findEventById(id)
```

Arguments: 
 - id: String - The events unique id.

Returns: **Promise**  
 - fulfilled: Object | null - Found event with unique id.
 - rejected: Error

<br/>

## fetchAllEvents
Fetch all calendar events.
Returns a promise with fulfilled found events.

```javascript
RNCalendarEvents.fetchAllEvents(startDate, endDate, calendars)
```

Arguments: 
 - startDate: Date - The start date of the range of events fetched.
 - endDate: Date - The end date of the range of events fetched.
 - calendars: Array - List of calendar id strings to specify calendar events. Defaults to all calendars if empty.

Returns: **Promise**  
 - fulfilled: Array - Matched events within the specified date range.
 - rejected: Error

<br/>

## saveEvent
Creates calendar event.

```
RNCalendarEvents.saveEvent(title, details, options);
```

Arguments: 
 - title: String - The title of the event.
 - [details](#event-properties): Object - The event's details.
 - [options](#options): Object - Options specific to the saved event.

Returns: **Promise** 
 - fulfilled: String - Created event's ID.
 - rejected: Error

<br/>

## Update Event
Give the unique calendar event **id** to update an existing calendar event.

```
RNCalendarEvents.saveEvent(title, {id: 'FE6B128F-C0D8-4FB8-8FC6-D1D6BA015CDE'})
```

Arguments: 
 - title: String - The title of the event.
 - [details](#event-properties): Object - The event's details.
 - [options](#options): Object - Options specific to the saved event.

Returns: **Promise** 
 - fulfilled: String - Updated event's ID.
 - rejected: Error

<br/>

## Create calendar event with alarms

### Alarm options:

| Property        | Type            | Description | iOS | Android |
| :--------------- | :------------------| :----------- | :-----------: | :-----------: | 
| **date**           | Date or Number    | If a Date is given, an alarm will be set with an absolute date. If a Number is given, an alarm will be set with a relative offset (in minutes) from the start date. | ✓ | ✓ |
| **structuredLocation** | Object             | The location to trigger an alarm. | ✓ |  |

### Alarm structuredLocation properties:

| Property        | Type            | Description | iOS | Android |
| :--------------- | :------------------| :----------- | :-----------: | :-----------: |
| **title**           | String  | The title of the location.| ✓ |  |
| **proximity** | String             | A value indicating how a location-based alarm is triggered. Possible values: `enter`, `leave`, `none`. | ✓ |  |
| **radius** | Number             | A minimum distance from the core location that would trigger the calendar event's alarm. | ✓ |  |
| **coords** | Object             | The geolocation coordinates, as an object with latitude and longitude properties | ✓ |  |

Example with date:

```javascript
RNCalendarEvents.saveEvent('title', {
  location: 'location',
  notes: 'notes',
  startDate: '2016-08-19T19:26:00.000Z',
  endDate: '2017-08-19T19:26:00.000Z',
  alarms: [{
    date: -1 // or absolute date - iOS Only
  }]
})

```
Example with structuredLocation (iOS Only):

```javascript
RNCalendarEvents.saveEvent('title', {
  location: 'location',
  notes: 'notes',
  startDate: '2016-08-19T19:26:00.000Z',
  endDate: '2017-08-19T19:26:00.000Z',
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
})
```

Example with recurrence:

```javascript
RNCalendarEvents.saveEvent('title', {
  location: 'location',
  notes: 'notes',
  startDate: '2016-08-19T19:26:00.000Z',
  endDate: '2017-08-29T19:26:00.000Z',
  alarms: [{
    date: -1 // or absolute date - iOS Only
  }],
  recurrenceRule: {
    frequency: 'daily'
    interval: 2,
    endDate: '2017-08-29T19:26:00.000Z'
  }
})
```

<br/>

## removeEvent
Removes calendar event.

```javascript
RNCalendarEvents.removeEvent(id, options)
```

Arguments:
 - id: String - The id of the event to remove.
 - [options](#options): Object - Options specific to event removal.

Returns: **Promise** 
 - fulfilled: Bool - Successful
 - rejected: Error


<br/>

## removeFutureEvents (iOS Only)
Removes future (recurring) calendar events.

```javascript
RNCalendarEvents.removeFutureEvents(id)
```

Arguments:
 - id: String - The id of the event to remove.

Returns: **Promise** 
 - fulfilled: Bool - Successful
 - rejected: Error


<br/>

## findCalendars
Finds all the calendars on the device.

```javascript
RNCalendarEvents.findCalendars()
```

Returns: **Promise**
 - fulfilled: Array - A list of known calendars on the device
 - rejected: Error

