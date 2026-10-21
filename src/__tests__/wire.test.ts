import { Platform } from 'react-native';
import {
  toISO,
  toNativeAlarms,
  toNativeCalendarOptions,
  toNativeColor,
  toNativeDetails,
  toNativeOptions,
} from '../wire';

const START = new Date('2026-01-01T10:00:00.000Z');

function setPlatform(os: 'ios' | 'android') {
  Object.defineProperty(Platform, 'OS', { value: os, configurable: true });
}

beforeEach(() => setPlatform('ios'));

describe('toISO', () => {
  it('serializes Date objects', () => {
    expect(toISO(START)).toBe('2026-01-01T10:00:00.000Z');
  });

  it('normalizes any ISO-8601 string to the canonical UTC form', () => {
    expect(toISO('2026-01-01')).toBe('2026-01-01T00:00:00.000Z');
    expect(toISO('2026-01-02T12:00:00+02:00')).toBe('2026-01-02T10:00:00.000Z');
    expect(toISO('2026-01-01T10:00:00.000Z')).toBe('2026-01-01T10:00:00.000Z');
  });

  it('passes undefined through', () => {
    expect(toISO(undefined)).toBeUndefined();
  });

  it('throws on anything it cannot parse', () => {
    expect(() => toISO('yesterday')).toThrow('Invalid date: yesterday');
    expect(() => toISO('')).toThrow('Invalid date');
  });
});

describe('toNativeColor', () => {
  it('converts to the processColor integer', () => {
    expect(toNativeColor('#ff0000')).toBe(0xffff0000);
    expect(toNativeColor('red')).toBe(0xffff0000);
  });

  it('is undefined when no color is given', () => {
    expect(toNativeColor(undefined)).toBeUndefined();
    expect(toNativeColor('')).toBeUndefined();
  });
});

describe('toNativeCalendarOptions', () => {
  it('keeps every field and replaces the color', () => {
    expect(
      toNativeCalendarOptions({
        title: 'Work',
        color: '#00ff00',
        name: 'work',
        accessLevel: 'owner',
      })
    ).toEqual({
      title: 'Work',
      color: 0xff00ff00,
      name: 'work',
      accessLevel: 'owner',
    });
  });
});

describe('toNativeAlarms', () => {
  it('keeps negative offsets on iOS', () => {
    expect(toNativeAlarms([{ date: -15 }, { date: 30 }])).toEqual([
      { date: -15 },
      { date: 30 },
    ]);
  });

  it('drops the offset sign on Android, where alarms only ring before the start', () => {
    setPlatform('android');
    expect(toNativeAlarms([{ date: -15 }, { date: 30 }])).toEqual([
      { date: 15 },
      { date: 30 },
    ]);
  });

  it('normalizes absolute dates and keeps the location', () => {
    const structuredLocation = {
      title: 'HQ',
      proximity: 'enter' as const,
      radius: 10,
      coords: { latitude: 1, longitude: 2 },
    };
    expect(
      toNativeAlarms([
        { date: START, structuredLocation },
        { date: '2026-01-01T09:00:00+02:00' },
      ])
    ).toEqual([
      { date: '2026-01-01T10:00:00.000Z', structuredLocation },
      { date: '2026-01-01T07:00:00.000Z' },
    ]);
  });

  it('rejects unparseable alarm dates and passes undefined through', () => {
    expect(() => toNativeAlarms([{ date: 'tomorrow' }])).toThrow(
      'Invalid date: tomorrow'
    );
    expect(toNativeAlarms(undefined)).toBeUndefined();
  });
});

describe('toNativeDetails', () => {
  it('normalizes every date-bearing field', () => {
    expect(
      toNativeDetails({
        startDate: START,
        endDate: '2026-01-01T11:00:00Z',
        recurrenceRule: { frequency: 'weekly', endDate: START, interval: 2 },
        alarms: [{ date: -5 }],
      })
    ).toEqual({
      startDate: '2026-01-01T10:00:00.000Z',
      endDate: '2026-01-01T11:00:00.000Z',
      recurrenceRule: {
        frequency: 'weekly',
        endDate: '2026-01-01T10:00:00.000Z',
        interval: 2,
      },
      alarms: [{ date: -5 }],
    });
  });

  it('omits fields that were not given instead of adding undefined keys', () => {
    expect(toNativeDetails({ id: 'evt-1', notes: 'n' })).toEqual({
      id: 'evt-1',
      notes: 'n',
    });
  });

  it('leaves non-date fields untouched', () => {
    const structuredLocation = {
      title: 'HQ',
      proximity: 'none' as const,
      radius: 0,
      coords: { latitude: 0, longitude: 0 },
    };
    expect(
      toNativeDetails({
        allDay: true,
        availability: 'busy',
        attendees: [{ name: 'Sam', email: 'sam@example.com' }],
        structuredLocation,
      })
    ).toEqual({
      allDay: true,
      availability: 'busy',
      attendees: [{ name: 'Sam', email: 'sam@example.com' }],
      structuredLocation,
    });
  });
});

describe('toNativeOptions', () => {
  it('normalizes exceptionDate and keeps the flags', () => {
    expect(
      toNativeOptions({ exceptionDate: START, futureEvents: true, sync: true })
    ).toEqual({
      exceptionDate: '2026-01-01T10:00:00.000Z',
      futureEvents: true,
      sync: true,
    });
  });

  it('yields an undefined exceptionDate when none is given', () => {
    expect(toNativeOptions({})).toEqual({ exceptionDate: undefined });
  });
});
