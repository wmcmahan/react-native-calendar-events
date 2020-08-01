import { NativeModules, processColor } from 'react-native'

const RNCalendarEvents = NativeModules.RNCalendarEvents

export default {

  async checkPermissions () {
    return RNCalendarEvents.checkPermissions()
  },
  async requestPermissions () {
    return RNCalendarEvents.requestPermissions()
  },

  async fetchAllEvents (startDate, endDate, calendars = []) {
    return RNCalendarEvents.findAllEvents(startDate, endDate, calendars)
  },

  async findCalendars () {
    return RNCalendarEvents.findCalendars()
  },

  async saveCalendar (options = {}) {
    return RNCalendarEvents.saveCalendar({
      ...options,
      color: options.color ? processColor(options.color) : undefined,
    });
  },

  async removeCalendar (id) {
    return RNCalendarEvents.removeCalendar(id)
  },

  async findEventById (id) {
    return RNCalendarEvents.findById(id)
  },

  async saveEvent (title, details, options = {sync: false}) {
    return RNCalendarEvents.saveEvent(title, details, options)
  },

  async removeEvent (id, options = {sync: false}) {
    return RNCalendarEvents.removeEvent(id, options)
  },

  async uriForCalendar () {
    return RNCalendarEvents.uriForCalendar()
  },

  openEventInCalendar (eventID) {
    RNCalendarEvents.openEventInCalendar(eventID)
  }
}
