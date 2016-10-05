'use strict'

import { NativeModules } from 'react-native'

var CalendarEvents = NativeModules.CalendarEvents

export default {
  authorizationStatus () {
  },

  async authorizeEventStore () {
    return CalendarEvents.requestCalendarPermissions()
  },

  async saveEvent (title, details) {
    return CalendarEvents.saveEvent(title, details)
  },

  async uriForCalendar () {
    return CalendarEvents.uriForCalendar()
  },

  openEventInCalendar (eventID) {
    CalendarEvents.openEventInCalendar(eventID)
  }
}
