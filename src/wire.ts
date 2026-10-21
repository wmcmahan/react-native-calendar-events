import { Platform, processColor } from 'react-native';
import type {
  CalendarEventWritable,
  CalendarOptions,
  DateInput,
  ISODateString,
  NativeAlarm,
  NativeCalendarOptions,
  NativeEventDetails,
  NativeOptions,
  Options,
} from './NativeCalendarEvents';

/**
 * Converters from the public write types to the wire payloads the native
 * modules read. Native code parses one canonical date form only
 * (`2026-01-01T10:00:00.000Z`), so every accepted input is normalized here.
 */

export function toISO(date: DateInput): ISODateString;
export function toISO(date: DateInput | undefined): ISODateString | undefined;
export function toISO(date: DateInput | undefined) {
  if (date == null) return undefined;
  const parsed = date instanceof Date ? date : new Date(date);
  if (Number.isNaN(parsed.getTime())) {
    throw new TypeError(
      `Invalid date: ${String(date)}. Pass a Date or an ISO-8601 string.`
    );
  }
  return parsed.toISOString();
}

/** `processColor` may yield a platform color object; native only takes the integer form. */
export function toNativeColor(color: string | undefined) {
  const processed = color ? processColor(color) : undefined;
  return typeof processed === 'number' ? processed : undefined;
}

export function toNativeCalendarOptions(
  options: CalendarOptions
): NativeCalendarOptions {
  return { ...options, color: toNativeColor(options.color) };
}

/**
 * Alarm offsets are minutes relative to the start, negative meaning "before".
 * Android's calendar provider only supports "minutes before", so the sign is
 * dropped there and both `-15` and `15` ring fifteen minutes early.
 */
export function toNativeAlarms(alarms: CalendarEventWritable['alarms']) {
  return alarms?.map(({ date, ...alarm }): NativeAlarm => ({
    ...alarm,
    date:
      typeof date === 'number'
        ? Platform.OS === 'android'
          ? Math.abs(date)
          : date
        : toISO(date),
  }));
}

export function toNativeDetails({
  startDate,
  endDate,
  recurrenceRule,
  alarms,
  ...rest
}: CalendarEventWritable): NativeEventDetails {
  return {
    ...rest,
    ...(startDate !== undefined && { startDate: toISO(startDate) }),
    ...(endDate !== undefined && { endDate: toISO(endDate) }),
    ...(recurrenceRule && {
      recurrenceRule: {
        ...recurrenceRule,
        endDate: toISO(recurrenceRule.endDate),
      },
    }),
    ...(alarms && { alarms: toNativeAlarms(alarms) }),
  };
}

export function toNativeOptions(options: Options): NativeOptions {
  return { ...options, exceptionDate: toISO(options.exceptionDate) };
}
