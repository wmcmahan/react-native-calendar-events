import { Platform } from 'react-native';
import NativeCalendarEvents, {
  type CalendarEventWritable,
  type CalendarOptions,
  type DateInput,
  type Options,
} from './NativeCalendarEvents';
import {
  toISO,
  toNativeCalendarOptions,
  toNativeDetails,
  toNativeOptions,
} from './wire';

// The public types. The `Native*` shapes and `Spec` stay internal.
export type {
  AlarmReadable,
  AlarmWritable,
  Attendee,
  AuthorizationStatus,
  Availability,
  Calendar,
  CalendarAccessLevelAndroid,
  CalendarAccountSourceAndroid,
  CalendarEntityTypeiOS,
  CalendarEventReadable,
  CalendarEventWritable,
  CalendarOptions,
  Coordinates,
  DateInput,
  EventStatus,
  ISODateString,
  Options,
  RecurrenceFrequency,
  RecurrenceRule,
  RecurrenceRuleReadable,
  StructuredLocation,
  Weekday,
} from './NativeCalendarEvents';

const RNCalendarEvents = {
  /**
   * Get calendar authorization status.
   * @param readOnly - default false. Android only: check for read-only vs read/write. Ignored on iOS.
   */
  checkPermissions(readOnly = false) {
    return NativeCalendarEvents.checkPermissions(readOnly);
  },

  /**
   * Request calendar authorization. Must be granted before accessing calendar events.
   * @param readOnly - default false. Android only: request read-only vs read/write. Ignored on iOS.
   */
  requestPermissions(readOnly = false) {
    return NativeCalendarEvents.requestPermissions(readOnly);
  },

  /** Finds all the calendars on the device. */
  findCalendars() {
    return NativeCalendarEvents.findCalendars();
  },

  /** Create a calendar. Resolves with the new calendar's id. */
  saveCalendar(options: CalendarOptions) {
    return NativeCalendarEvents.saveCalendar(toNativeCalendarOptions(options));
  },

  /** Removes a calendar. Resolves with whether a calendar was removed. */
  removeCalendar(id: string) {
    return NativeCalendarEvents.removeCalendar(id);
  },

  /** Find an event by id. Resolves with null when not found. */
  findEventById(id: string) {
    return NativeCalendarEvents.findEventById(id);
  },

  /**
   * Fetch all calendar events between two dates.
   * @param calendarIds - Restrict to these calendars. Defaults to all calendars when empty.
   */
  async fetchAllEvents(
    startDate: DateInput,
    endDate: DateInput,
    calendarIds: string[] = []
  ) {
    return NativeCalendarEvents.fetchAllEvents(
      toISO(startDate),
      toISO(endDate),
      calendarIds
    );
  },

  /**
   * Creates or updates a calendar event. To update an event, `details.id` must be set.
   * Resolves with the saved event's id.
   */
  async saveEvent(
    title: string,
    details: CalendarEventWritable,
    options: Options = {}
  ) {
    return NativeCalendarEvents.saveEvent(
      title,
      toNativeDetails(details),
      toNativeOptions(options)
    );
  },

  /**
   * Removes an event, or a single occurrence when `options.exceptionDate` is set.
   * Resolves with `false` when no such event exists.
   */
  async removeEvent(id: string, options: Options = {}) {
    return NativeCalendarEvents.removeEvent(id, toNativeOptions(options));
  },

  /** iOS ONLY - Removes this and all future instances of a recurring event. */
  async removeFutureEvents(id: string, options: Options = {}) {
    return NativeCalendarEvents.removeEvent(
      id,
      toNativeOptions({ ...options, futureEvents: true })
    );
  },

  /** ANDROID ONLY - The content URI of the calendar provider. Rejects on iOS. */
  uriForCalendar() {
    return NativeCalendarEvents.uriForCalendar();
  },

  /** ANDROID ONLY - Opens the given event in the system calendar app. No-op on iOS. */
  openEventInCalendar(eventId: string) {
    if (Platform.OS === 'android') {
      NativeCalendarEvents.openEventInCalendar(eventId);
    }
  },
};

export default RNCalendarEvents;
