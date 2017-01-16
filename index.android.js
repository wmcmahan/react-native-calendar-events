'use strict'

import { NativeModules } from 'react-native'

var CalendarEvents = NativeModules.CalendarEvents

export default {
  async authorizationStatus () {
    return CalendarEvents.getCalendarPermissions()
  },

  async fetchAllEvents (startDate, endDate, calendars = []) {
    return CalendarEvents.findAllEvents(startDate, endDate, calendars)
  },

  async findCalendars () {
    return CalendarEvents.findCalendars();
  },

  async findEventById (id) {
    return CalendarEvents.findById(id);
  },

  async authorizeEventStore () {
    return CalendarEvents.requestCalendarPermissions()
  },

  async saveEvent (title, details) {
    return CalendarEvents.saveEvent(title, details)
  },

  async removeEvent (id) {
    return CalendarEvents.removeEvent(id)
  },

  async uriForCalendar () {
    return CalendarEvents.uriForCalendar()
  },

  openEventInCalendar (eventID) {
    CalendarEvents.openEventInCalendar(eventID)
  }
}
