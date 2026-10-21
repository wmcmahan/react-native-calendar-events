import {
  TurboModuleRegistry,
  type CodegenTypes,
  type TurboModule,
} from 'react-native';

/**
 * Native contract shared by iOS and Android, plus the public types.
 *
 * React Native's codegen parses this file and only understands types declared
 * in it, which is why everything lives here. It resolves only the types the
 * `Spec` interface reaches, so the "write" types further down may use `Date`
 * and unions freely as long as `Spec` never references them.
 *
 * Every date on the wire is an ISO-8601 string in UTC.
 */

// ---------------------------------------------------------------------------
// Read types. Reached by `Spec`, so codegen-safe: object literals, arrays,
// primitives and string-literal unions only.
// ---------------------------------------------------------------------------

export type AuthorizationStatus =
  | 'authorized'
  | 'denied'
  | 'restricted'
  | 'undetermined'
  /** iOS 17+ only: the app may add events but not read them. */
  | 'writeOnly';

export type Availability =
  'busy' | 'free' | 'tentative' | 'unavailable' | 'notSupported';

export type EventStatus = 'none' | 'confirmed' | 'tentative' | 'canceled';

export type RecurrenceFrequency = 'daily' | 'weekly' | 'monthly' | 'yearly';

export type Weekday = 'MO' | 'TU' | 'WE' | 'TH' | 'FR' | 'SA' | 'SU';

export type Calendar = {
  /** Unique calendar id. */
  id: string;
  /** Display name. */
  title: string;
  /** iOS: `local`, `calDAV`, `exchange`, `subscription`, `birthday`. Android: the account type. */
  type: string;
  /** The account the calendar belongs to. */
  source: string;
  /** The device's default calendar for new events. */
  isPrimary: boolean;
  /** Whether events can be written, edited or removed. */
  allowsModifications: boolean;
  /** Hex color, e.g. `#FF0000`. */
  color: string;
  /** Availability values the calendar supports. */
  allowedAvailabilities: string[];
};

export type Coordinates = { latitude: number; longitude: number };

/** iOS ONLY - A location, optionally geofenced when used on an alarm. */
export type StructuredLocation = {
  title: string;
  /** How a location-based alarm triggers. */
  proximity?: 'enter' | 'leave' | 'none';
  /** Minimum distance from `coords` that triggers the alarm, in meters. */
  radius?: number;
  coords?: Coordinates;
};

export type Attendee = {
  name: string;
  email: string;
  /** iOS ONLY */
  phone?: string;
  /** ANDROID ONLY - Raw `CalendarContract.Attendees` values. */
  type?: string;
  relationship?: string;
  status?: string;
  identity?: string;
  id_namespace?: string;
};

/** Alarm as read back: always an absolute date. */
export type AlarmReadable = {
  date: string;
  structuredLocation?: StructuredLocation;
};

/** Recurrence rule as read back. `RecurrenceRule` is the form to write. */
export type RecurrenceRuleReadable = {
  frequency: RecurrenceFrequency;
  /** Stop at this date. Overrides `occurrence`. */
  endDate?: string;
  /** Stop after this many occurrences. */
  occurrence?: number;
  /** Every n periods. */
  interval?: number;
  /** Weekly: which days. Monthly: combined with `weekPositionInMonth`. */
  daysOfWeek?: Weekday[];
  /** Monthly: which week (1 = first, -1 = last). */
  weekPositionInMonth?: number;
  /** ANDROID ONLY - RFC 5545 duration of each occurrence, default `PT1H`. */
  duration?: string;
  /** ANDROID ONLY - First day of the week for the rule. */
  weekStart?: Weekday;
};

export type CalendarEventReadable = {
  id: string;
  title: string;
  startDate: string;
  endDate: string;
  allDay: boolean;
  location?: string;
  calendarId?: string;
  /** The containing calendar. */
  calendar?: Calendar;
  attendees?: Attendee[];
  alarms?: AlarmReadable[];
  /** Simple frequency when recurring; absent otherwise. */
  recurrence?: RecurrenceFrequency;
  /** Full rule when recurring; absent otherwise. */
  recurrenceRule?: RecurrenceRuleReadable;
  availability?: Availability;
  status?: EventStatus;
  timeZone?: string;
  /** iOS ONLY */
  notes?: string;
  url?: string;
  structuredLocation?: StructuredLocation;
  /** iOS ONLY - Original date of an occurrence in a recurring series. */
  occurrenceDate?: string;
  /** iOS ONLY - Whether this is a detached instance of a repeating event. */
  isDetached?: boolean;
  /** ANDROID ONLY */
  description?: string;
  /** ANDROID ONLY - Id of the series when this is an exception. */
  originalId?: string;
  /** ANDROID ONLY - Sync id of the series. */
  syncId?: string;
};

// ---------------------------------------------------------------------------
// Write types. Public input shapes; `src/index.ts` converts them to the wire
// payloads below. Not reached by `Spec`, so `Date` and unions are fine here.
// ---------------------------------------------------------------------------

/** An ISO-8601 date string, always UTC with milliseconds when read back, e.g. `2026-01-01T10:00:00.000Z`. */
export type ISODateString = string;

/** Anywhere a date is written you may pass a `Date` or any ISO-8601 string. */
export type DateInput = Date | ISODateString;

export type RecurrenceRule = Omit<RecurrenceRuleReadable, 'endDate'> & {
  /** Stop at this date. Overrides `occurrence`. */
  endDate?: DateInput;
};

/**
 * Alarm to write. `date` is an absolute date, or a number of minutes relative
 * to the event start where a negative value means "before". Android cannot
 * ring after the start, so positive values are treated as "before" there too.
 */
export type AlarmWritable = {
  date: DateInput | number;
  /** iOS ONLY */
  structuredLocation?: StructuredLocation;
};

export type CalendarEventWritable = {
  /** Set to update an existing event. */
  id?: string;
  /** Defaults to the device's primary writable calendar. */
  calendarId?: string;
  /** Required when creating. */
  startDate?: DateInput;
  /** Required when creating. */
  endDate?: DateInput;
  allDay?: boolean;
  location?: string;
  /** iOS ONLY */
  structuredLocation?: StructuredLocation;
  /** iOS ONLY */
  url?: string;
  /** iOS ONLY */
  notes?: string;
  /** ANDROID ONLY */
  description?: string;
  /** e.g. `Europe/Paris` */
  timeZone?: string;
  /** ANDROID ONLY */
  endTimeZone?: string;
  availability?: Availability;
  /** Shortcut for a simple rule. */
  recurrence?: RecurrenceFrequency;
  recurrenceRule?: RecurrenceRule;
  alarms?: AlarmWritable[];
  /** Replaces the attendees on update. */
  attendees?: Array<Pick<Attendee, 'name' | 'email'>>;
};

export type Options = {
  /** Target a single occurrence of a recurring event. */
  exceptionDate?: DateInput;
  /** iOS ONLY - With `exceptionDate`: also apply to later occurrences. */
  futureEvents?: boolean;
  /** ANDROID ONLY - Write through the sync adapter and force a sync. */
  sync?: boolean;
};

export type CalendarEntityTypeiOS = 'event' | 'reminder';

export type CalendarAccessLevelAndroid =
  | 'contributor'
  | 'editor'
  | 'freebusy'
  | 'override'
  | 'owner'
  | 'read'
  | 'respond'
  | 'root';

export type CalendarAccountSourceAndroid =
  { name: string; type: string } | { name: string; isLocalAccount: boolean };

export type CalendarOptions = {
  title: string;
  /** Any color React Native understands. Required on Android. */
  color?: string;
  /** iOS ONLY - Defaults to `event`. */
  entityType?: CalendarEntityTypeiOS;
  /** ANDROID ONLY (required) */
  name?: string;
  /** ANDROID ONLY (required) */
  accessLevel?: CalendarAccessLevelAndroid;
  /** ANDROID ONLY (required) */
  ownerAccount?: string;
  /** ANDROID ONLY (required) */
  source?: CalendarAccountSourceAndroid;
};

// ---------------------------------------------------------------------------
// Wire payloads built by `src/index.ts`. The three object parameters are typed
// `UnsafeObject` on purpose: typed parameters make codegen generate opaque
// ObjC++ structs with no way to hand the dictionary to the Swift core, and
// their accessors validate only on access, so the typing bought nothing.
// ---------------------------------------------------------------------------

export type NativeCalendarOptions = Omit<CalendarOptions, 'color'> & {
  /** `processColor` result. */
  color?: number;
};

export type NativeAlarm = Omit<AlarmWritable, 'date'> & {
  /** ISO date for an absolute alarm, or minutes relative to the start. */
  date: string | number;
};

export type NativeEventDetails = Omit<
  CalendarEventWritable,
  'startDate' | 'endDate' | 'recurrenceRule' | 'alarms'
> & {
  startDate?: string;
  endDate?: string;
  recurrenceRule?: RecurrenceRuleReadable;
  alarms?: NativeAlarm[];
};

export type NativeOptions = Omit<Options, 'exceptionDate'> & {
  exceptionDate?: string;
};

export interface Spec extends TurboModule {
  checkPermissions(readOnly: boolean): Promise<AuthorizationStatus>;
  requestPermissions(readOnly: boolean): Promise<AuthorizationStatus>;
  findCalendars(): Promise<Calendar[]>;
  saveCalendar(options: CodegenTypes.UnsafeObject): Promise<string>;
  removeCalendar(id: string): Promise<boolean>;
  fetchAllEvents(
    startDate: string,
    endDate: string,
    calendarIds: string[]
  ): Promise<CalendarEventReadable[]>;
  findEventById(id: string): Promise<CalendarEventReadable | null>;
  saveEvent(
    title: string,
    details: CodegenTypes.UnsafeObject,
    options: CodegenTypes.UnsafeObject
  ): Promise<string>;
  removeEvent(id: string, options: CodegenTypes.UnsafeObject): Promise<boolean>;
  // Android only. iOS rejects / no-ops.
  uriForCalendar(): Promise<string>;
  openEventInCalendar(eventId: string): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('RNCalendarEvents');
