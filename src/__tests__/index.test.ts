import { Platform } from 'react-native';
import RNCalendarEvents from '../index';
import NativeCalendarEvents from '../NativeCalendarEvents';

jest.mock('../NativeCalendarEvents', () => ({
  __esModule: true,
  default: {
    checkPermissions: jest.fn(async () => 'authorized'),
    requestPermissions: jest.fn(async () => 'authorized'),
    findCalendars: jest.fn(async () => []),
    saveCalendar: jest.fn(async () => 'cal-1'),
    removeCalendar: jest.fn(async () => true),
    fetchAllEvents: jest.fn(async () => []),
    findEventById: jest.fn(async () => null),
    saveEvent: jest.fn(async () => 'evt-1'),
    removeEvent: jest.fn(async () => true),
    uriForCalendar: jest.fn(
      async () => 'content://com.android.calendar/events'
    ),
    openEventInCalendar: jest.fn(),
  },
}));

// Conversion details are covered in wire.test.ts; these check that each
// method forwards the converted arguments to the right native call.

const native = NativeCalendarEvents as jest.Mocked<typeof NativeCalendarEvents>;
const START = new Date('2026-01-01T10:00:00.000Z');
const END = new Date('2026-01-01T11:00:00.000Z');

function setPlatform(os: 'ios' | 'android') {
  Object.defineProperty(Platform, 'OS', { value: os, configurable: true });
}

beforeEach(() => {
  jest.clearAllMocks();
  setPlatform('ios');
});

describe('permissions', () => {
  it('defaults readOnly to false and forwards it when given', async () => {
    await RNCalendarEvents.checkPermissions();
    await RNCalendarEvents.requestPermissions(true);
    expect(native.checkPermissions).toHaveBeenCalledWith(false);
    expect(native.requestPermissions).toHaveBeenCalledWith(true);
  });
});

describe('calendars', () => {
  it('forwards findCalendars and removeCalendar', async () => {
    await RNCalendarEvents.findCalendars();
    await RNCalendarEvents.removeCalendar('cal-1');
    expect(native.findCalendars).toHaveBeenCalledTimes(1);
    expect(native.removeCalendar).toHaveBeenCalledWith('cal-1');
  });

  it('saveCalendar sends the converted options', async () => {
    await RNCalendarEvents.saveCalendar({ title: 'Work', color: '#ff0000' });
    expect(native.saveCalendar).toHaveBeenCalledWith({
      title: 'Work',
      color: 0xffff0000,
    });
  });
});

describe('events', () => {
  it('fetchAllEvents sends ISO dates and defaults calendars to []', async () => {
    await RNCalendarEvents.fetchAllEvents(START, END);
    expect(native.fetchAllEvents).toHaveBeenCalledWith(
      '2026-01-01T10:00:00.000Z',
      '2026-01-01T11:00:00.000Z',
      []
    );
  });

  it('rejects instead of throwing when a date is invalid', async () => {
    await expect(
      RNCalendarEvents.fetchAllEvents('yesterday', END)
    ).rejects.toThrow('Invalid date: yesterday');
    expect(native.fetchAllEvents).not.toHaveBeenCalled();
  });

  it('findEventById forwards the id', async () => {
    await RNCalendarEvents.findEventById('evt-1');
    expect(native.findEventById).toHaveBeenCalledWith('evt-1');
  });

  it('saveEvent sends converted details and options, defaulting options', async () => {
    await RNCalendarEvents.saveEvent('Lunch', {
      startDate: START,
      alarms: [{ date: -5 }],
    });
    expect(native.saveEvent).toHaveBeenCalledWith(
      'Lunch',
      { startDate: '2026-01-01T10:00:00.000Z', alarms: [{ date: -5 }] },
      { exceptionDate: undefined }
    );
  });

  it('removeEvent sends converted options', async () => {
    await RNCalendarEvents.removeEvent('evt-1', { exceptionDate: START });
    expect(native.removeEvent).toHaveBeenCalledWith('evt-1', {
      exceptionDate: '2026-01-01T10:00:00.000Z',
    });
  });

  it('removeFutureEvents forces futureEvents: true', async () => {
    await RNCalendarEvents.removeFutureEvents('evt-1', {
      exceptionDate: START,
    });
    expect(native.removeEvent).toHaveBeenCalledWith('evt-1', {
      exceptionDate: '2026-01-01T10:00:00.000Z',
      futureEvents: true,
    });
  });
});

describe('android-only helpers', () => {
  it('uriForCalendar forwards', async () => {
    await expect(RNCalendarEvents.uriForCalendar()).resolves.toBe(
      'content://com.android.calendar/events'
    );
  });

  it('openEventInCalendar is a no-op on iOS and forwards on Android', () => {
    RNCalendarEvents.openEventInCalendar('1');
    expect(native.openEventInCalendar).not.toHaveBeenCalled();
    setPlatform('android');
    RNCalendarEvents.openEventInCalendar('1');
    expect(native.openEventInCalendar).toHaveBeenCalledWith('1');
  });
});
