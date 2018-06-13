'use strict';

import { NativeModules } from 'react-native';

const RNCalendarEvents = NativeModules.RNCalendarEvents

export default {

  authorizationStatus () {
    return RNCalendarEvents.authorizationStatus()
  },

  authorizeEventStore () {
    return RNCalendarEvents.authorizeEventStore()
  },

  fetchAllEvents (startDate, endDate, calendars = []) {
    return RNCalendarEvents.fetchAllEvents(startDate, endDate, calendars)
  },

  findCalendars () {
    return RNCalendarEvents.findCalendars();
  },

  findEventById (id) {
    return RNCalendarEvents.findEventById(id);
  },

  saveEvent (title, details, options = {}) {
    return RNCalendarEvents.saveEvent(title, details, options)
  },

  removeEvent (id, options = {futureEvents: false}) {
    return RNCalendarEvents.removeEvent(id, options)
  },

  removeFutureEvents (id, options = {futureEvents: true}) {
    return RNCalendarEvents.removeEvent(id, options)
  }

}
