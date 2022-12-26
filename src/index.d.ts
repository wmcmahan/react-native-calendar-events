// Type definitions for react-native-calendar v1.7.0
// Typescript version: 3.0

type ISODateString = string;
export type AuthorizationStatus =
  | "denied"
  | "restricted"
  | "authorized"
  | "undetermined";
export type RecurrenceFrequency = "daily" | "weekly" | "monthly" | "yearly";

/** iOS ONLY - GeoFenced alarm location */
interface AlarmStructuredLocation {
  /** The title of the location. */
  title: string;
  /** A value indicating how a location-based alarm is triggered. */
  proximity: "enter" | "leave" | "none";
  /** A minimum distance from the core location that would trigger the calendar event's alarm. */
  radius: number;
  /** The geolocation coordinates, as an object with latitude and longitude properties. */
  coords: { latitude: number; longitude: number };
}

export interface Options {
  /** The start date of a recurring event's exception instance. Used for updating single event in a recurring series. */
  exceptionDate?: ISODateString;
  /** iOS ONLY - If true the update will span all future events. If false it only update the single instance. */
  futureEvents?: boolean;
  /** ANDROID ONLY - If true, can help avoid syncing issues */
  sync?: boolean;
}

interface Alarm<D = ISODateString | number> {
  /** When saving an event, if a Date is given, an alarm will be set with an absolute date. If a Number is given, an alarm will be set with a relative offset (in minutes) from the start date. When reading an event this will always be an ISO Date string */
  date: D;
  /** iOS ONLY - The location to trigger an alarm. */
  structuredLocation?: AlarmStructuredLocation;
}

interface RecurrenceRule {
  /** Event recurring frequency. */
  frequency: RecurrenceFrequency;
  /** Event recurring end date. This overrides occurrence. */
  endDate: ISODateString;
  /** Number of event occurrences */
  occurrence: number;
  /** The interval between events of this recurrence. */
  interval: number;
}

interface Attendee {
  /** The name of the attendee. */
  name: string;
  /** The email address of the attendee. */
  email: string;
  /** iOS ONLY - The The phone number of the attendee. */
  phone?: string;
}

interface Calendar {
  /** Unique calendar ID. */
  id: string;
  /** The calendar’s title. */
  title: string;
  /** The calendar’s type. */
  type: string;
  /** The source object representing the account to which this calendar belongs. */
  source: string;
  /** Indicates if the calendar is assigned as primary. */
  isPrimary: boolean;
  /** Indicates if the calendar allows events to be written, edited or removed. */
  allowsModifications: boolean;
  /** The color assigned to the calendar represented as a hex value. */
  color: string;
  /** The event availability settings supported by the calendar. */
  allowedAvailabilities: string[];
}

interface CalendarEventBase {
  /** The start date of the calendar event in ISO format */
  startDate: ISODateString;
  /** The end date of the calendar event in ISO format. */
  endDate?: ISODateString;
  /** Unique id for the calendar where the event will be saved. Defaults to the device's default  calendar. */
  calendarId?: string;
  /** Indicates whether the event is an all-day event. */
  allDay?: boolean;
  /** The simple recurrence frequency of the calendar event. */
  recurrence?: RecurrenceFrequency;
  /** The location associated with the calendar event. */
  location?: string;
  /** iOS ONLY - The location with coordinates. */
  structuredLocation?: AlarmStructuredLocation;
  /** iOS ONLY - Indicates whether an event is a detached instance of a repeating event. */
  isDetached?: boolean;
  /** iOS ONLY - The url associated with the calendar event. */
  url?: string;
  /** iOS ONLY - The notes associated with the calendar event. */
  notes?: string;
  /** ANDROID ONLY - The description associated with the calendar event. */
  description?: string;
  /** iOS ONLY - The time zone associated with the event */
  timeZone?: string;
  /** iOS ONLY – The availability setting for the event. */
  availability: "busy" | "free" | "tentative" | "unavailable" | "notSupported";  
  /** iOS ONLY – The status of the event. */
  status: "none" | "tentative" | "confirmed" | "canceled";
}

export interface CalendarEventReadable extends CalendarEventBase {
  /** Unique id for the calendar event */
  id: string;
  /** The title for the calendar event. */
  title: string;
  /** The attendees of the event, including the organizer. */
  attendees?: Attendee[];
  /** The calendar containing the event */
  calendar?: Calendar;
  /** iOS ONLY - The original occurrence date of an event if it is part of a recurring series. */
  occurrenceDate?: ISODateString;
  /** The alarms associated with the calendar event, as an array of alarm objects. */
  alarms?: Array<Alarm<ISODateString>>;
}

export interface CalendarEventWritable extends CalendarEventBase {
  /** Unique id for the calendar event, used for updating existing events */
  id?: string;
  /** The event's recurrence settings */
  recurrenceRule?: RecurrenceRule;
  /** The alarms associated with the calendar event, as an array of alarm objects. */
  alarms?: Array<Alarm<ISODateString | number>>;
}

export interface CalendarOptions {
  /** The calendar title */
  title: string;
  /** The calendar color */
  color: string;
  /** iOS ONLY - Entity type for the calendar */
  entityType: CalendarEntityTypeiOS;
  /** Android ONLY - The calendar name */
  name: string;
  /** Android ONLY - Defines how the event shows up for others when the calendar is shared */
  accessLevel: CalendarAccessLevelAndroid;
  /** Android ONLY - The owner account for this calendar, based on the calendar feed */
  ownerAccount: string;
  /** Android ONLY - The calendar Account source */
  source: CalendarAccountSourceAndroid;
}

export type CalendarEntityTypeiOS = "event" | "reminder";

export type CalendarAccessLevelAndroid =
  | "contributor"
  | "editor"
  | "freebusy"
  | "override"
  | "owner"
  | "read"
  | "respond"
  | "root";

export type CalendarAccountSourceAndroid =
  | {
      /** The Account name */
      name: string;
      /** The Account type */
      type: string;
    }
  | {
      /** The Account name */
      name: string;
      /** The source (required if source.type is not used) */
      isLocalAccount: boolean;
    };

export default class ReactNativeCalendarEvents {
  /**
   * Get calendar authorization status.
   * @param readOnly - optional, default false, use true to check for calendar read-only vs calendar read/write. Android-specific, iOS is always read/write
   */
  static checkPermissions(readOnly?: boolean): Promise<AuthorizationStatus>;
  /**
   * Request calendar authorization. Authorization must be granted before accessing calendar events.
   * @param readOnly - optional, default false, use true to request for calendar read-only vs calendar read/write. Android-specific, iOS is always read/write
   */
  static requestPermissions(readOnly?: boolean): Promise<AuthorizationStatus>;

  /** Finds all the calendars on the device. */
  static findCalendars(): Promise<Calendar[]>;
  /** Create a calendar.
   * @param calendar - Calendar to create
   */
  static saveCalendar(calendar: CalendarOptions): Promise<string>;
  /**
   * Removes a calendar.
   * @param id - The calendar id
   * @returns - Promise resolving to boolean to indicate if removal succeeded.
   */
  static removeCalendar(id: string): Promise<boolean>;
  /**
   * Find calendar  by id.
   * @param id - Calendar ID
   */
  static findEventById(id: string): Promise<CalendarEventReadable | null>;
  /**
   * Fetch all calendar events.
   * @param startDate - Date string in ISO format
   * @param endDate - Date string in ISO format
   * @param [calendarIds] - List of calendar id strings to specify calendar events. Defaults to all calendars if empty.
   */
  static fetchAllEvents(
    startDate: ISODateString,
    endDate: ISODateString,
    calendarIds?: string[]
  ): Promise<CalendarEventReadable[]>;
  /**
   * Creates or updates a calendar event. To update an event, the event id must be defined.
   * @param title - The title of the event
   * @param details - Event details
   * @param [options] - Options specific to the saved event.
   * @returns - Promise resolving to saved event's ID.
   */
  static saveEvent(
    title: string,
    details: CalendarEventWritable,
    options?: Options
  ): Promise<string>;
  /**
   * Removes calendar event.
   * @param id - The event id
   * @param [options] - Options specific to the saved event.
   * @returns - Promise resolving to boolean to indicate if removal succeeded.
   */
  static removeEvent(id: string, options?: Options): Promise<boolean>;
}
