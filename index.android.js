'use strict'

import { NativeModules, processColor } from 'react-native'

var CalendarEvents = NativeModules.CalendarEvents

export default {

  async authorizationStatus () {
    return CalendarEvents.getCalendarPermissions()
  },

  async authorizeEventStore () {
    return CalendarEvents.requestCalendarPermissions()
  },

  async fetchAllEvents (startDate, endDate, calendars = []) {
    return CalendarEvents.findAllEvents(startDate, endDate, calendars)
  },

  async findCalendars () {
    return CalendarEvents.findCalendars()
  },

  async saveCalendar (options = {}) {
    return CalendarEvents.saveCalendar({
      ...options,
      color: options.color ? processColor(options.color) : undefined,
    });
  },

  async findEventById (id) {
    return CalendarEvents.findById(id)
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
