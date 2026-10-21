type Mod<T> = { modResults: T };

const mockEnsurePermissions = jest.fn(
  (manifest: object, permissions: string[]) => ({
    ...manifest,
    permissions,
  })
);

jest.mock(
  'expo/config-plugins',
  () => ({
    AndroidConfig: {
      Permissions: { ensurePermissions: mockEnsurePermissions },
    },
    withInfoPlist: (
      config: object,
      action: (mod: Mod<object>) => Mod<object>
    ) => {
      const mod = action({ modResults: {} });
      return { ...config, infoPlist: mod.modResults };
    },
    withAndroidManifest: (
      config: object,
      action: (mod: Mod<object>) => Mod<object>
    ) => {
      const mod = action({ modResults: {} });
      return { ...config, manifest: mod.modResults };
    },
  }),
  { virtual: true }
);

const withCalendarEvents = require('../../app.plugin') as (
  config: object,
  props?: object
) => Record<string, Record<string, unknown>>;

describe('expo config plugin', () => {
  it('writes default iOS usage strings and Android permissions', () => {
    const result = withCalendarEvents({ name: 'app' });
    expect(result.infoPlist).toEqual({
      NSCalendarsFullAccessUsageDescription:
        'Allow $(PRODUCT_NAME) to access your calendar',
      NSCalendarsWriteOnlyAccessUsageDescription:
        'Allow $(PRODUCT_NAME) to add events to your calendar',
      NSCalendarsUsageDescription:
        'Allow $(PRODUCT_NAME) to access your calendar',
    });
    expect(mockEnsurePermissions).toHaveBeenCalledWith({}, [
      'android.permission.READ_CALENDAR',
      'android.permission.WRITE_CALENDAR',
    ]);
  });

  it('leaves a key alone when its string is false', () => {
    const result = withCalendarEvents(
      { name: 'app' },
      { writeOnlyCalendarPermission: false }
    );
    expect(result.infoPlist).toEqual({
      NSCalendarsFullAccessUsageDescription:
        'Allow $(PRODUCT_NAME) to access your calendar',
      NSCalendarsUsageDescription:
        'Allow $(PRODUCT_NAME) to access your calendar',
    });
  });

  it('uses custom strings and adds reminders keys only when asked', () => {
    const result = withCalendarEvents(
      { name: 'app' },
      {
        calendarPermission: 'Cal',
        writeOnlyCalendarPermission: 'Write',
        remindersPermission: 'Rem',
      }
    );
    expect(result.infoPlist).toEqual({
      NSCalendarsFullAccessUsageDescription: 'Cal',
      NSCalendarsWriteOnlyAccessUsageDescription: 'Write',
      NSCalendarsUsageDescription: 'Cal',
      NSRemindersFullAccessUsageDescription: 'Rem',
      NSRemindersUsageDescription: 'Rem',
    });
  });
});
