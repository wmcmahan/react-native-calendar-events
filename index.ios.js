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

  saveEvent (title, details, options = {exception: false}) {
    return RNCalendarEvents.saveEvent(title, details, options)
  },

  removeEvent (id) {
    return RNCalendarEvents.removeEvent(id)
  },

  removeFutureEvents (id) {
    return RNCalendarEvents.removeFutureEvents(id)
  }

}
