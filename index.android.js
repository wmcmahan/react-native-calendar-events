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

  async saveEvent (title, details, options = {exception: false, sync: false}) {
    return CalendarEvents.saveEvent(title, details, options)
  },

  async removeEvent (id, options = {exception: false, sync: false}) {
    return CalendarEvents.removeEvent(id, options)
  },

  async uriForCalendar () {
    return CalendarEvents.uriForCalendar()
  },

  openEventInCalendar (eventID) {
    CalendarEvents.openEventInCalendar(eventID)
  }
}
