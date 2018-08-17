'use strict'

import { NativeModules } from 'react-native'

var CalendarEvents = NativeModules.CalendarEvents

export default {

  async authorizationStatus () {
    return CalendarEvents.getCalendarPermissions()
  },

  async authorizeEventStore () {
    return CalendarEvents.requestCalendarPermissions()
  },

  async fetchAllEvents (startDate, endDate, calendars = []) {
    return CalendarEvents.findAllEvents(startDate, endDate, calendars).then(events => {
      return events.map(e => {
        if (e.calendar && e.calendar.color) {
          let color = `#${(0xFFFFFF + parseInt(e.calendar.color)).toString(16)}`;
          e.calendar.color = color;
        }
        return e;
      });
    })
  },

  async findCalendars () {
    return CalendarEvents.findCalendars().then(calendars => {
      return calendars.map(c => {
        if (c.color) {
          let color = `#${(0xFFFFFF + parseInt(c.color)).toString(16)}`;
          c.color = color;
        }
        return c; 
      });
    });
  },

  async findEventById (id) {
    return CalendarEvents.findById(id).then(event => {
      if (event.calendar && event.calendar.color) {
        let color = `#${(0xFFFFFF + parseInt(c.color)).toString(16)}`;
        event.calendar.color = color;
      }
      return event;
    })
  },

  async saveEvent (title, details, options = {sync: false}) {
    return CalendarEvents.saveEvent(title, details, options)
  },

  async removeEvent (id, options = {sync: false}) {
    return CalendarEvents.removeEvent(id, options)
  },

  async uriForCalendar () {
    return CalendarEvents.uriForCalendar()
  },

  openEventInCalendar (eventID) {
    CalendarEvents.openEventInCalendar(eventID)
  }
}
