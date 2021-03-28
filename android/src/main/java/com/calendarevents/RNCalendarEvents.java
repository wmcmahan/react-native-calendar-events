package com.calendarevents;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.Manifest;
import android.net.Uri;
import android.provider.CalendarContract;
import androidx.core.content.ContextCompat;
import android.database.Cursor;
import android.accounts.Account;
import android.accounts.AccountManager;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.bridge.Dynamic;
import com.facebook.react.modules.core.PermissionAwareActivity;
import com.facebook.react.modules.core.PermissionListener;

import java.sql.Array;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.TimeZone;
import android.util.Log;

public class RNCalendarEvents extends ReactContextBaseJavaModule implements PermissionListener {

    private static int PERMISSION_REQUEST_CODE = 37;
    private final ReactContext reactContext;
    private static final String RNC_PREFS = "REACT_NATIVE_CALENDAR_PREFERENCES";
    private static final HashMap<Integer, Promise> permissionsPromises = new HashMap<>();

    public RNCalendarEvents(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "RNCalendarEvents";
    }

    //region Calendar Permissions
    private void requestCalendarPermission(boolean readOnly, final Promise promise)
    {
        Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
            promise.reject("E_ACTIVITY_DOES_NOT_EXIST", "Activity doesn't exist");
            return;
        }
        if (!(currentActivity instanceof PermissionAwareActivity)) {
            promise.reject("E_ACTIVITY_NOT_PERMISSION_AWARE", "Activity does not implement the PermissionAwareActivity interface");
            return;
        }
        PermissionAwareActivity activity = (PermissionAwareActivity)currentActivity;
        PERMISSION_REQUEST_CODE++;
        permissionsPromises.put(PERMISSION_REQUEST_CODE, promise);
        String[] permissions = new String[]{Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CALENDAR};
        if (readOnly == true) {
            permissions = new String[]{Manifest.permission.READ_CALENDAR};
        }
        activity.requestPermissions(permissions, PERMISSION_REQUEST_CODE, this);
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (permissionsPromises.containsKey(requestCode)) {

            // If request is cancelled, the result arrays are empty.
            Promise permissionsPromise = permissionsPromises.get(requestCode);

            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                permissionsPromise.resolve("authorized");
            } else if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                permissionsPromise.resolve("denied");
            } else if (permissionsPromises.size() == 1) {
                permissionsPromise.reject("permissions - unknown error", grantResults.length > 0 ? String.valueOf(grantResults[0]) : "Request was cancelled");
            }
            permissionsPromises.remove(requestCode);
        }

        return permissionsPromises.size() == 0;
    }

    private boolean haveCalendarPermissions(boolean readOnly) {
        int writePermission = ContextCompat.checkSelfPermission(reactContext, Manifest.permission.WRITE_CALENDAR);
        int readPermission = ContextCompat.checkSelfPermission(reactContext, Manifest.permission.READ_CALENDAR);

        if (readOnly) {
            return readPermission == PackageManager.PERMISSION_GRANTED;
        }

        return writePermission == PackageManager.PERMISSION_GRANTED &&
                readPermission == PackageManager.PERMISSION_GRANTED;
    }
    
    private boolean shouldShowRequestPermissionRationale(boolean readOnly) {
        Activity currentActivity = getCurrentActivity();

        if (currentActivity == null) {
            Log.w(this.getName(), "Activity doesn't exist");
            return false;
        }
        if (!(currentActivity instanceof PermissionAwareActivity)) {
            Log.w(this.getName(), "Activity does not implement the PermissionAwareActivity interface");
            return false;
        }

        PermissionAwareActivity activity = (PermissionAwareActivity)currentActivity;

        if (readOnly) {
            return activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR);
        }
        return activity.shouldShowRequestPermissionRationale(Manifest.permission.WRITE_CALENDAR);
    }

    //endregion

    private WritableNativeArray findEventCalendars() {

        Cursor cursor;
        ContentResolver cr = reactContext.getContentResolver();

        Uri uri = CalendarContract.Calendars.CONTENT_URI;

        String IS_PRIMARY = CalendarContract.Calendars.IS_PRIMARY == null ? "0" : CalendarContract.Calendars.IS_PRIMARY;

        cursor = cr.query(uri, new String[]{
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                IS_PRIMARY,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.ALLOWED_AVAILABILITY,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.CALENDAR_COLOR
        }, null, null, null);

        return serializeEventCalendars(cursor);
    }

    private WritableNativeMap findCalendarById(String calendarID) {

        WritableNativeMap result;
        Cursor cursor;
        ContentResolver cr = reactContext.getContentResolver();
        Uri uri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, Integer.parseInt(calendarID));

        String IS_PRIMARY = CalendarContract.Calendars.IS_PRIMARY == null ? "0" : CalendarContract.Calendars.IS_PRIMARY;

        cursor = cr.query(uri, new String[]{
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                IS_PRIMARY,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.ALLOWED_AVAILABILITY,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.CALENDAR_COLOR
        }, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            result = serializeEventCalendar(cursor);
            cursor.close();
        } else {
            result = null;
        }

        return result;
    }

    private Integer calAccessConstantMatchingString(String string) {
        if (string.equals("contributor")) {
            return CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR;
        }
        if (string.equals("editor")) {
            return CalendarContract.Calendars.CAL_ACCESS_EDITOR;
        }
        if (string.equals("freebusy")) {
            return CalendarContract.Calendars.CAL_ACCESS_FREEBUSY;
        }
        if (string.equals("override")) {
            return CalendarContract.Calendars.CAL_ACCESS_OVERRIDE;
        }
        if (string.equals("owner")) {
            return CalendarContract.Calendars.CAL_ACCESS_OWNER;
        }
        if (string.equals("read")) {
            return CalendarContract.Calendars.CAL_ACCESS_READ;
        }
        if (string.equals("respond")) {
            return CalendarContract.Calendars.CAL_ACCESS_RESPOND;
        }
        if (string.equals("root")) {
            return CalendarContract.Calendars.CAL_ACCESS_ROOT;
        }
        return CalendarContract.Calendars.CAL_ACCESS_NONE;
    }

    private int addCalendar(ReadableMap details) throws Exception, SecurityException {

        ContentResolver cr = reactContext.getContentResolver();
        ContentValues calendarValues = new ContentValues();

        // required fields for new calendars
        if (!details.hasKey("source")) {
            throw new Exception("new calendars require `source` object");
        }
        if (!details.hasKey("name")) {
            throw new Exception("new calendars require `name`");
        }
        if (!details.hasKey("title")) {
            throw new Exception("new calendars require `title`");
        }
        if (!details.hasKey("color")) {
            throw new Exception("new calendars require `color`");
        }
        if (!details.hasKey("accessLevel")) {
            throw new Exception("new calendars require `accessLevel`");
        }
        if (!details.hasKey("ownerAccount")) {
            throw new Exception("new calendars require `ownerAccount`");
        }

        ReadableMap source = details.getMap("source");

        if (!source.hasKey("name")) {
            throw new Exception("new calendars require a `source` object with a `name`");
        }

        Boolean isLocalAccount = false;
        if (source.hasKey("isLocalAccount")) {
            isLocalAccount = source.getBoolean("isLocalAccount");
        }

        if (!source.hasKey("type") && isLocalAccount == false) {
            throw new Exception("new calendars require a `source` object with a `type`, or `isLocalAccount`: true");
        }

        calendarValues.put(CalendarContract.Calendars.ACCOUNT_NAME, source.getString("name"));
        calendarValues.put(CalendarContract.Calendars.ACCOUNT_TYPE, isLocalAccount ? CalendarContract.ACCOUNT_TYPE_LOCAL : source.getString("type"));
        calendarValues.put(CalendarContract.Calendars.CALENDAR_COLOR, details.getInt("color"));
        calendarValues.put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, calAccessConstantMatchingString(details.getString("accessLevel")));
        calendarValues.put(CalendarContract.Calendars.OWNER_ACCOUNT, details.getString("ownerAccount"));
        calendarValues.put(CalendarContract.Calendars.NAME, details.getString("name"));
        calendarValues.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, details.getString("title"));
        // end required fields

        Uri.Builder uriBuilder = CalendarContract.Calendars.CONTENT_URI.buildUpon();
        uriBuilder.appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true");
        uriBuilder.appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, source.getString("name"));
        uriBuilder.appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, isLocalAccount ? CalendarContract.ACCOUNT_TYPE_LOCAL : source.getString("type"));

        Uri calendarsUri = uriBuilder.build();

        Uri calendarUri = cr.insert(calendarsUri, calendarValues);
        return Integer.parseInt(calendarUri.getLastPathSegment());
    }

    private boolean removeCalendar(String calendarID) {
        int rows = 0;

        try {
            ContentResolver cr = reactContext.getContentResolver();

            Uri uri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, (long) Integer.parseInt(calendarID));
            rows = cr.delete(uri, null, null);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return rows > 0;
    }

    private WritableNativeArray findAttendeesByEventId(String eventID) {
        WritableNativeArray result;
        Cursor cursor;
        ContentResolver cr = reactContext.getContentResolver();
        String query = "(" + CalendarContract.Attendees.EVENT_ID + " = ?)";
        String[] args = new String[]{eventID};

        cursor = cr.query(CalendarContract.Attendees.CONTENT_URI, new String[]{
                CalendarContract.Attendees._ID,
                CalendarContract.Attendees.EVENT_ID,
                CalendarContract.Attendees.ATTENDEE_NAME,
                CalendarContract.Attendees.ATTENDEE_EMAIL,
                CalendarContract.Attendees.ATTENDEE_TYPE,
                CalendarContract.Attendees.ATTENDEE_RELATIONSHIP,
                CalendarContract.Attendees.ATTENDEE_STATUS,
                CalendarContract.Attendees.ATTENDEE_IDENTITY,
                CalendarContract.Attendees.ATTENDEE_ID_NAMESPACE
        }, query, args, null);

        if (cursor != null && cursor.moveToFirst()) {
            result = serializeAttendeeCalendar(cursor);
            cursor.close();
        } else {
            WritableNativeArray emptyAttendees = new WritableNativeArray();
            result = emptyAttendees;
        }

        return result;
    }

    //region Event Accessors
    private WritableNativeArray findEvents(Dynamic startDate, Dynamic endDate, ReadableArray calendars) {
        String dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));

        Calendar eStartDate = Calendar.getInstance();
        Calendar eEndDate = Calendar.getInstance();

        try {
            if (startDate.getType() == ReadableType.String) {
                eStartDate.setTime(sdf.parse(startDate.asString()));
            } else if (startDate.getType() == ReadableType.Number) {
                eStartDate.setTimeInMillis((long)startDate.asDouble());
            }

            if (endDate.getType() == ReadableType.String) {
                eEndDate.setTime(sdf.parse(endDate.asString()));
            } else if (endDate.getType() == ReadableType.Number) {
                eEndDate.setTimeInMillis((long)endDate.asDouble());
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }

        Cursor cursor;
        ContentResolver cr = reactContext.getContentResolver();

        Uri.Builder uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(uriBuilder, eStartDate.getTimeInMillis());
        ContentUris.appendId(uriBuilder, eEndDate.getTimeInMillis());

        Uri uri = uriBuilder.build();

        String selection = "((" + CalendarContract.Instances.BEGIN + " < " + eEndDate.getTimeInMillis() + ") " +
                "AND (" + CalendarContract.Instances.END + " >= " + eStartDate.getTimeInMillis() + ") " +
                "AND (" + CalendarContract.Instances.VISIBLE + " = 1) " +
                "AND (" + CalendarContract.Instances.STATUS + " IS NOT " + CalendarContract.Events.STATUS_CANCELED + ") ";

        if (calendars.size() > 0) {
            String calendarQuery = "AND (";
            for (int i = 0; i < calendars.size(); i++) {
                calendarQuery += CalendarContract.Instances.CALENDAR_ID + " = " + calendars.getString(i);
                if (i != calendars.size() - 1) {
                    calendarQuery += " OR ";
                }
            }
            calendarQuery += ")";
            selection += calendarQuery;
        }

        selection += ")";

        cursor = cr.query(uri, new String[]{
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.DESCRIPTION,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.RRULE,
                CalendarContract.Instances.CALENDAR_ID,
                CalendarContract.Instances.AVAILABILITY,
                CalendarContract.Instances.HAS_ALARM,
                CalendarContract.Instances.ORIGINAL_ID,
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.DURATION,
                CalendarContract.Instances.ORIGINAL_SYNC_ID,
        }, selection, null, null);


        return serializeEvents(cursor);
    }

    private WritableNativeMap findEventById(String eventID) {

        WritableNativeMap result;
        Cursor cursor = null;
        ContentResolver cr = reactContext.getContentResolver();
        Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, Integer.parseInt(eventID));

        String selection = "((" + CalendarContract.Events.DELETED + " != 1))";

        cursor = cr.query(uri, new String[]{
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.RRULE,
                CalendarContract.Events.CALENDAR_ID,
                CalendarContract.Events.AVAILABILITY,
                CalendarContract.Events.HAS_ALARM,
                CalendarContract.Instances.DURATION
        }, selection, null, null);

        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            result = serializeEvent(cursor);
        } else {
            result = null;
        }

        cursor.close();

        return result;
    }

    private WritableNativeMap findEventInstanceById(String eventID) {

        WritableNativeMap result;
        Cursor cursor;
        ContentResolver cr = reactContext.getContentResolver();

        Uri.Builder uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(uriBuilder, Long.MIN_VALUE);
        ContentUris.appendId(uriBuilder, Long.MAX_VALUE);
        Uri uri = uriBuilder.build();

        String selection = "(Instances._ID = " + eventID + ")";

        cursor = cr.query(uri, new String[]{
                CalendarContract.Instances._ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.DESCRIPTION,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.RRULE,
                CalendarContract.Instances.CALENDAR_ID,
                CalendarContract.Instances.AVAILABILITY,
                CalendarContract.Instances.HAS_ALARM,
                CalendarContract.Instances.ORIGINAL_ID,
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.DURATION
        }, selection, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            result = serializeEvent(cursor);
            cursor.close();
        } else {
            result = null;
        }

        return result;
    }

    private int addEvent(String title, ReadableMap details, ReadableMap options) throws ParseException {
        String dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
        boolean skipTimezone = false;
        if(details.hasKey("skipAndroidTimezone") && details.getBoolean("skipAndroidTimezone")){
            skipTimezone = true;
        }
        if(!skipTimezone){
            sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        }
        ContentResolver cr = reactContext.getContentResolver();
        ContentValues eventValues = new ContentValues();

        if (title != null) {
            eventValues.put(CalendarContract.Events.TITLE, title);
        }

        if (details.hasKey("description")) {
            eventValues.put(CalendarContract.Events.DESCRIPTION, details.getString("description"));
        }

        if (details.hasKey("location")) {
            eventValues.put(CalendarContract.Events.EVENT_LOCATION, details.getString("location"));
        }

        if (details.hasKey("startDate")) {
            Calendar startCal = Calendar.getInstance();
            ReadableType type = details.getType("startDate");

            try {
                if (type == ReadableType.String) {
                    startCal.setTime(sdf.parse(details.getString("startDate")));
                    eventValues.put(CalendarContract.Events.DTSTART, startCal.getTimeInMillis());
                } else if (type == ReadableType.Number) {
                    eventValues.put(CalendarContract.Events.DTSTART, (long)details.getDouble("startDate"));
                }
            } catch (ParseException e) {
                e.printStackTrace();
                throw e;
            }
        }

        if (details.hasKey("endDate")) {
            Calendar endCal = Calendar.getInstance();
            ReadableType type = details.getType("endDate");

            try {
                if (type == ReadableType.String) {
                    endCal.setTime(sdf.parse(details.getString("endDate")));
                    eventValues.put(CalendarContract.Events.DTEND, endCal.getTimeInMillis());
                } else if (type == ReadableType.Number) {
                    eventValues.put(CalendarContract.Events.DTEND, (long)details.getDouble("endDate"));
                }
            } catch (ParseException e) {
                e.printStackTrace();
                throw e;
            }
        }

        if (details.hasKey("recurrence")) {
            String rule = createRecurrenceRule(details.getString("recurrence"), null, null, null, null, null, null);
            if (rule != null) {
                eventValues.put(CalendarContract.Events.RRULE, rule);
            }
        }

        if (details.hasKey("recurrenceRule")) {
            ReadableMap recurrenceRule = details.getMap("recurrenceRule");

            if (recurrenceRule.hasKey("frequency")) {
                String frequency = recurrenceRule.getString("frequency");
                String duration = "PT1H";
                Integer interval = null;
                Integer occurrence = null;
                String endDate = null;
                ReadableArray daysOfWeek = null;
                String weekStart = null;
                Integer weekPositionInMonth = null;

                if (recurrenceRule.hasKey("interval")) {
                    interval = recurrenceRule.getInt("interval");
                }

                if (recurrenceRule.hasKey("duration")) {
                    duration = recurrenceRule.getString("duration");
                }

                if (recurrenceRule.hasKey("occurrence")) {
                    occurrence = recurrenceRule.getInt("occurrence");
                }

                if (recurrenceRule.hasKey("endDate")) {
                    ReadableType type = recurrenceRule.getType("endDate");
                    SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");

                    if (type == ReadableType.String) {
                        endDate = format.format(sdf.parse(recurrenceRule.getString("endDate")));
                    } else if (type == ReadableType.Number) {
                        Calendar calendar = Calendar.getInstance();
                        calendar.setTimeInMillis((long)recurrenceRule.getDouble("endDate"));
                        endDate = format.format(calendar.getTime());
                    }
                }

                if (recurrenceRule.hasKey("daysOfWeek")) {
                    daysOfWeek = recurrenceRule.getArray("daysOfWeek");
                }

                if (recurrenceRule.hasKey("weekStart")) {
                    weekStart = recurrenceRule.getString("weekStart");
                }

                if (recurrenceRule.hasKey("weekPositionInMonth")) {
                    weekPositionInMonth = recurrenceRule.getInt("weekPositionInMonth");
                }

                String rule = createRecurrenceRule(frequency, interval, endDate, occurrence, daysOfWeek, weekStart, weekPositionInMonth);
                if (duration != null) {
                    eventValues.put(CalendarContract.Events.DURATION, duration);
                }
                if (rule != null) {
                    eventValues.put(CalendarContract.Events.RRULE, rule);
                }
            }
        }

        if (details.hasKey("allDay")) {
            eventValues.put(CalendarContract.Events.ALL_DAY, details.getBoolean("allDay") ? 1 : 0);
        }

        if (details.hasKey("timeZone")) {
            eventValues.put(CalendarContract.Events.EVENT_TIMEZONE, details.getString("timeZone"));
        } else {
            eventValues.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
        }

        if (details.hasKey("endTimeZone")) {
            eventValues.put(CalendarContract.Events.EVENT_END_TIMEZONE, details.getString("endTimeZone"));
        } else {
            eventValues.put(CalendarContract.Events.EVENT_END_TIMEZONE, TimeZone.getDefault().getID());
        }

        if (details.hasKey("alarms")) {
            eventValues.put(CalendarContract.Events.HAS_ALARM, true);
        }

        if (details.hasKey("availability")) {
            eventValues.put(CalendarContract.Events.AVAILABILITY, availabilityConstantMatchingString(details.getString("availability")));
        }

        if (details.hasKey("id")) {
            int eventID = Integer.parseInt(details.getString("id"));
            WritableMap eventInstance = findEventById(details.getString("id"));

            if (eventInstance != null) {
                ReadableMap eventCalendar = eventInstance.getMap("calendar");

                if (!options.hasKey("exceptionDate")) {
                    Uri updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventID);

                    if (options.hasKey("sync") && options.getBoolean("sync")) {
                        syncCalendar(cr, eventInstance.getMap("calendar").getString("id"));
                        updateUri = eventUriAsSyncAdapter(updateUri, eventCalendar.getString("source"), eventCalendar.getString("type"));
                    }
                    cr.update(updateUri, eventValues, null, null);

                } else {
                    Calendar exceptionStart = Calendar.getInstance();
                    ReadableType type = options.getType("exceptionDate");

                    try {
                        if (type == ReadableType.String) {
                            exceptionStart.setTime(sdf.parse(options.getString("exceptionDate")));
                            eventValues.put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, exceptionStart.getTimeInMillis());
                        } else if (type == ReadableType.Number) {
                            eventValues.put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, (long) options.getDouble("exceptionDate"));
                        }
                    } catch (ParseException e) {
                        e.printStackTrace();
                        throw e;
                    }

                    Uri exceptionUri = Uri.withAppendedPath(CalendarContract.Events.CONTENT_EXCEPTION_URI, Integer.toString(eventID));

                    if (options.hasKey("sync") && options.getBoolean("sync")) {
                        syncCalendar(cr, eventInstance.getMap("calendar").getString("id"));
                        eventUriAsSyncAdapter(exceptionUri, eventCalendar.getString("source"), eventCalendar.getString("type"));
                    }

                    try {
                        Uri eventUri = cr.insert(exceptionUri, eventValues);
                        if (eventUri != null) {
                            eventID = Integer.parseInt(eventUri.getLastPathSegment());
                        }
                    } catch (Exception e) {
                        Log.d(this.getName(), "Event exception error", e);
                    }
                }
            }

            if (details.hasKey("alarms")) {
                createRemindersForEvent(cr, Integer.parseInt(details.getString("id")), details.getArray("alarms"));
            }

            if (details.hasKey("attendees")) {
                createAttendeesForEvent(cr, Integer.parseInt(details.getString("id")), details.getArray("attendees"));
            }

            return eventID;

        } else {
            WritableNativeMap calendar;
            int eventID = -1;

            if (details.hasKey("calendarId")) {
                calendar = findCalendarById(details.getString("calendarId"));

                if (calendar != null) {
                    eventValues.put(CalendarContract.Events.CALENDAR_ID, Integer.parseInt(calendar.getString("id")));
                } else {
                    eventValues.put(CalendarContract.Events.CALENDAR_ID, 1);
                }

            } else {
                calendar = findCalendarById("1");
                eventValues.put(CalendarContract.Events.CALENDAR_ID, 1);
            }

            Uri createEventUri = CalendarContract.Events.CONTENT_URI;

            if (options.hasKey("sync") && options.getBoolean("sync")) {
                syncCalendar(cr, calendar.getString("id"));
                createEventUri = eventUriAsSyncAdapter(CalendarContract.Events.CONTENT_URI, calendar.getString("source"), calendar.getString("type"));
            }

            Uri eventUri = cr.insert(createEventUri, eventValues);

            if (eventUri != null) {
                String rowId = eventUri.getLastPathSegment();
                if (rowId != null) {
                    eventID = Integer.parseInt(rowId);

                    if (details.hasKey("alarms")) {
                        createRemindersForEvent(cr, eventID, details.getArray("alarms"));
                    }

                    if (details.hasKey("attendees")) {
                        createAttendeesForEvent(cr, eventID, details.getArray("attendees"));
                    }
                    return eventID;
                }

            }
            return eventID;
        }

    }

    private boolean removeEvent(String eventID, ReadableMap options) {
        int rows = 0;

        try {
            ContentResolver cr = reactContext.getContentResolver();
            WritableMap eventInstance = findEventById(eventID);
            ReadableMap eventCalendar = eventInstance.getMap("calendar");

            if (!options.hasKey("exceptionDate")) {
                Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, (long) Integer.parseInt(eventID));

                if (options.hasKey("sync") && options.getBoolean("sync")) {
                    syncCalendar(cr, eventCalendar.getString("id"));
                    uri = eventUriAsSyncAdapter(uri, eventCalendar.getString("source"), eventCalendar.getString("type"));
                }
                rows = cr.delete(uri, null, null);

            } else {
                ContentValues eventValues = new ContentValues();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                sdf.setTimeZone(TimeZone.getTimeZone("GMT"));

                Calendar exceptionStart = Calendar.getInstance();
                ReadableType type = options.getType("exceptionDate");

                try {
                    if (type == ReadableType.String) {
                        exceptionStart.setTime(sdf.parse(options.getString("exceptionDate")));
                        eventValues.put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, exceptionStart.getTimeInMillis());
                    } else if (type == ReadableType.Number) {
                        eventValues.put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, (long) options.getDouble("exceptionDate"));
                    }
                } catch (ParseException e) {
                    e.printStackTrace();
                    throw e;
                }

                eventValues.put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED);

                Uri uri = Uri.withAppendedPath(CalendarContract.Events.CONTENT_EXCEPTION_URI, eventID);

                if (options.hasKey("sync") && options.getBoolean("sync")) {
                    uri = eventUriAsSyncAdapter(uri, eventCalendar.getString("source"), eventCalendar.getString("type"));
                }

                Uri exceptionUri = cr.insert(uri, eventValues);
                if (exceptionUri != null) {
                    rows = 1;
                }

            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return rows > 0;
    }

    //sync adaptors
    private Uri eventUriAsSyncAdapter (Uri uri, String accountName, String accountType) {
        uri = uri.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, accountType)
                .build();

        return uri;
    }

    public static void syncCalendar(ContentResolver cr, String calendarId) {
        ContentValues values = new ContentValues();
        values.put(CalendarContract.Calendars.SYNC_EVENTS, 1);
        values.put(CalendarContract.Calendars.VISIBLE, 1);

        cr.update(ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, Long.parseLong(calendarId)), values, null, null);
    }
    //endregion

    //region Attendees
    private void createAttendeesForEvent(ContentResolver resolver, int eventID, ReadableArray attendees) {
        Cursor cursor = CalendarContract.Attendees.query(resolver, eventID, new String[] {
                CalendarContract.Attendees._ID
        });
        
        while (cursor.moveToNext()) {
            long attendeeId = cursor.getLong(0);
            Uri attendeeUri = ContentUris.withAppendedId(CalendarContract.Attendees.CONTENT_URI, attendeeId);
            resolver.delete(attendeeUri, null, null);
        }
        cursor.close();

        for (int i = 0; i < attendees.size(); i++) {
            ReadableMap attendee = attendees.getMap(i);
            ReadableType type = attendee.getType("url");
            ReadableType fNameType = attendee.getType("firstName");
            if (type == ReadableType.String) {
                ContentValues attendeeValues = new ContentValues();
                attendeeValues.put(CalendarContract.Attendees.EVENT_ID, eventID);
                attendeeValues.put(CalendarContract.Attendees.ATTENDEE_EMAIL, attendee.getString("url"));
                attendeeValues.put(CalendarContract.Attendees.ATTENDEE_RELATIONSHIP, CalendarContract.Attendees.RELATIONSHIP_ATTENDEE);

                if (fNameType == ReadableType.String) {
                    attendeeValues.put(CalendarContract.Attendees.ATTENDEE_NAME, attendee.getString("firstName"));
                }
                resolver.insert(CalendarContract.Attendees.CONTENT_URI, attendeeValues);
            }
        }
    }
    //endregion

    //region Reminders
    private void createRemindersForEvent(ContentResolver resolver, int eventID, ReadableArray reminders) {
        Cursor cursor = null;

        if (resolver != null) {
            cursor = CalendarContract.Reminders.query(resolver, eventID, new String[] {
                CalendarContract.Reminders._ID
            });
        }

        while (cursor != null && cursor.moveToNext()) {
            Uri reminderUri = null;
            long reminderId = cursor.getLong(0);
            if (reminderId > 0) {
                reminderUri = ContentUris.withAppendedId(CalendarContract.Reminders.CONTENT_URI, reminderId);
            }
            if (reminderUri != null) {
                resolver.delete(reminderUri, null, null);
            }
        }

        if (cursor != null) {
            cursor.close();
        }

        for (int i = 0; i < reminders.size(); i++) {
            ReadableMap reminder = reminders.getMap(i);
            ReadableType type = reminder.getType("date");
            if (type == ReadableType.Number) {
                int minutes = reminder.getInt("date");
                ContentValues reminderValues = new ContentValues();

                reminderValues.put(CalendarContract.Reminders.EVENT_ID, eventID);
                reminderValues.put(CalendarContract.Reminders.MINUTES, minutes);
                reminderValues.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);

                resolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues);
            }
        }
    }

    private WritableNativeArray findReminderByEventId(String eventID, long startDate) {

        WritableNativeArray results = new WritableNativeArray();
        ContentResolver cr = reactContext.getContentResolver();
        String selection = "(" + CalendarContract.Reminders.EVENT_ID + " = ?)";

        Cursor cursor = cr.query(CalendarContract.Reminders.CONTENT_URI, new String[]{
                CalendarContract.Reminders.MINUTES
        }, selection, new String[] {eventID}, null);

        while (cursor != null && cursor.moveToNext()) {
            WritableNativeMap alarm = new WritableNativeMap();

            Calendar cal = Calendar.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
            cal.setTimeInMillis(startDate);
            int minutes;
            try {
                minutes = cursor.getInt(0);
            } catch (Exception e) {
                Log.d(this.getName(), "Error parsing event minutes", e);
                continue;
            }

            cal.add(Calendar.MINUTE, minutes);
            alarm.putString("date", sdf.format(cal.getTime()));
            results.pushMap(alarm);
        }

        if (cursor != null) {
            cursor.close();
        }

        return results;
    }
    //endregion

    //region Availability
    private WritableNativeArray calendarAllowedAvailabilitiesFromDBString(String dbString) {
        WritableNativeArray availabilitiesStrings = new WritableNativeArray();
        for(String availabilityStr: dbString.split(",")) {
            int availabilityId = -1;

            try {
                availabilityId = Integer.parseInt(availabilityStr);
            } catch(NumberFormatException e) {
                // Some devices seem to just use strings.
                if (availabilityStr.equals("AVAILABILITY_BUSY")) {
                    availabilityId = CalendarContract.Events.AVAILABILITY_BUSY;
                } else if (availabilityStr.equals("AVAILABILITY_FREE")) {
                    availabilityId = CalendarContract.Events.AVAILABILITY_FREE;
                } else if (availabilityStr.equals("AVAILABILITY_TENTATIVE")) {
                    availabilityId = CalendarContract.Events.AVAILABILITY_TENTATIVE;
                }
            }

            switch(availabilityId) {
                case CalendarContract.Events.AVAILABILITY_BUSY:
                    availabilitiesStrings.pushString("busy");
                    break;
                case CalendarContract.Events.AVAILABILITY_FREE:
                    availabilitiesStrings.pushString("free");
                    break;
                case CalendarContract.Events.AVAILABILITY_TENTATIVE:
                    availabilitiesStrings.pushString("tentative");
                    break;
            }
        }

        return availabilitiesStrings;
    }

    private String availabilityStringMatchingConstant(Integer constant)
    {
        switch(constant) {
            case CalendarContract.Events.AVAILABILITY_BUSY:
            default:
                return "busy";
            case CalendarContract.Events.AVAILABILITY_FREE:
                return "free";
            case CalendarContract.Events.AVAILABILITY_TENTATIVE:
                return "tentative";
        }
    }

    private Integer availabilityConstantMatchingString(String string) throws IllegalArgumentException {
        if (string.equals("free")){
            return CalendarContract.Events.AVAILABILITY_FREE;
        }

        if (string.equals("tentative")){
            return CalendarContract.Events.AVAILABILITY_TENTATIVE;
        }

        return CalendarContract.Events.AVAILABILITY_BUSY;
    }
    //endregion

    private String ReadableArrayToString (ReadableArray strArr) {
        ArrayList<Object> array = strArr.toArrayList();
        StringBuilder strBuilder = new StringBuilder();
        for (int i = 0; i < array.size(); i++) {
            strBuilder.append(array.get(i).toString() + ',');
        }
        String newString = strBuilder.toString();
        newString = newString.substring(0, newString.length() - 1);
        return newString;
    }

    //region Recurrence Rule
    private String createRecurrenceRule(String recurrence, Integer interval, String endDate, Integer occurrence, ReadableArray daysOfWeek, String weekStart, Integer weekPositionInMonth) {
        String rrule;

        if (recurrence.equals("daily")) {
            rrule=  "FREQ=DAILY";
        } else if (recurrence.equals("weekly")) {
            rrule = "FREQ=WEEKLY";
        }  else if (recurrence.equals("monthly")) {
            rrule = "FREQ=MONTHLY";
        } else if (recurrence.equals("yearly")) {
            rrule = "FREQ=YEARLY";
        } else {
            return null;
        }

        if (daysOfWeek != null && recurrence.equals("weekly")) {
            rrule += ";BYDAY=" + ReadableArrayToString(daysOfWeek);
        }

        if (recurrence.equals("monthly") && daysOfWeek != null && weekPositionInMonth != null) {
            rrule += ";BYSETPOS=" + weekPositionInMonth;
            rrule += ";BYDAY=" + ReadableArrayToString(daysOfWeek);
        }

        if (weekStart != null) {
            rrule += ";WKST=" + weekStart;
        }

        if (interval != null) {
            rrule += ";INTERVAL=" + interval;
        }

        if (endDate != null) {
            rrule += ";UNTIL=" + endDate;
        } else if (occurrence != null) {
            rrule += ";COUNT=" + occurrence;
        }

        return rrule;
    }
    //endregion

    // region Serialize Events
    private WritableNativeArray serializeEvents(Cursor cursor) {
        WritableNativeArray results = new WritableNativeArray();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                results.pushMap(serializeEvent(cursor));
            }

            cursor.close();
        }

        return results;
    }

    private WritableNativeMap serializeEvent(Cursor cursor) {
        WritableNativeMap event = new WritableNativeMap();

        String dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));

        Calendar foundStartDate = Calendar.getInstance();
        Calendar foundEndDate = Calendar.getInstance();

        boolean allDay = false;
        String startDateUTC = "";
        String endDateUTC = "";

        if (cursor.getString(3) != null) {
            foundStartDate.setTimeInMillis(Long.parseLong(cursor.getString(3)));
            startDateUTC = sdf.format(foundStartDate.getTime());
        }

        if (cursor.getString(4) != null) {
            foundEndDate.setTimeInMillis(Long.parseLong(cursor.getString(4)));
            endDateUTC = sdf.format(foundEndDate.getTime());
        }

        if (cursor.getString(5) != null) {
            allDay = cursor.getInt(5) != 0;
        }

        if (cursor.getString(7) != null) {
            WritableNativeMap recurrenceRule = new WritableNativeMap();
            String[] recurrenceRules = cursor.getString(7).split(";");
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");

            if (recurrenceRules.length > 0 && recurrenceRules[0].split("=").length > 1) {
                event.putString("recurrence", recurrenceRules[0].split("=")[1].toLowerCase());
                recurrenceRule.putString("frequency", recurrenceRules[0].split("=")[1].toLowerCase());
            }

            if (cursor.getColumnIndex(CalendarContract.Events.DURATION) != -1 && cursor.getString(cursor.getColumnIndex(CalendarContract.Events.DURATION)) != null) {
                recurrenceRule.putString("duration", cursor.getString(cursor.getColumnIndex(CalendarContract.Events.DURATION)));
            }

            if (recurrenceRules.length >= 2 && recurrenceRules[1].split("=")[0].equals("INTERVAL")) {
                recurrenceRule.putInt("interval", Integer.parseInt(recurrenceRules[1].split("=")[1]));
            }

            if (recurrenceRules.length >= 3) {
                if (recurrenceRules[2].split("=")[0].equals("UNTIL")) {
                    try {
                        recurrenceRule.putString("endDate", sdf.format(format.parse(recurrenceRules[2].split("=")[1])));
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                } else if (recurrenceRules[2].split("=")[0].equals("COUNT")) {
                    recurrenceRule.putInt("occurrence", Integer.parseInt(recurrenceRules[2].split("=")[1]));
                }

            }

            event.putMap("recurrenceRule", recurrenceRule);
        }

        event.putString("id", cursor.getString(0));
        event.putMap("calendar", findCalendarById(cursor.getString(cursor.getColumnIndex("calendar_id"))));
        event.putString("title", cursor.getString(cursor.getColumnIndex("title")));
        event.putString("description", cursor.getString(2));
        event.putString("startDate", startDateUTC);
        event.putString("endDate", endDateUTC);
        event.putBoolean("allDay", allDay);
        event.putString("location", cursor.getString(6));
        event.putString("availability", availabilityStringMatchingConstant(cursor.getInt(9)));
        event.putArray("attendees", (WritableArray) findAttendeesByEventId(cursor.getString(0)));

        if (cursor.getInt(10) > 0) {
            event.putArray("alarms", findReminderByEventId(cursor.getString(0), Long.parseLong(cursor.getString(3))));
        } else {
            WritableNativeArray emptyAlarms = new WritableNativeArray();
            event.putArray("alarms", emptyAlarms);
        }

        if (cursor.getColumnIndex(CalendarContract.Events.ORIGINAL_ID) != -1 && cursor.getString(cursor.getColumnIndex(CalendarContract.Events.ORIGINAL_ID)) != null) {
            event.putString("originalId", cursor.getString(cursor.getColumnIndex(CalendarContract.Events.ORIGINAL_ID)));
        }

        if (cursor.getColumnIndex(CalendarContract.Instances.ORIGINAL_SYNC_ID) != -1 && cursor.getString(cursor.getColumnIndex(CalendarContract.Instances.ORIGINAL_SYNC_ID)) != null) {
            event.putString("syncId", cursor.getString(cursor.getColumnIndex(CalendarContract.Instances.ORIGINAL_SYNC_ID)));
        }

        return event;
    }

    private WritableNativeArray serializeEventCalendars(Cursor cursor) {
        WritableNativeArray results = new WritableNativeArray();

        if (cursor != null) {
            while (cursor.moveToNext()) {
                results.pushMap(serializeEventCalendar(cursor));
            }
            cursor.close();
        }

        return results;
    }

    private WritableNativeMap serializeEventCalendar(Cursor cursor) {

        WritableNativeMap calendar = new WritableNativeMap();

        calendar.putString("id", cursor.getString(0));
        calendar.putString("title", cursor.getString(1));
        calendar.putString("source", cursor.getString(2));
        calendar.putArray("allowedAvailabilities", calendarAllowedAvailabilitiesFromDBString(cursor.getString(5)));
        calendar.putString("type", cursor.getString(6));

        String colorHex = "#FFFFFF";
        try {
            colorHex = String.format("#%06X", (0xFFFFFF & cursor.getInt(7)));
        } catch (Exception e) {
            Log.d(this.getName(), "Error parsing calendar color", e);
        }
        calendar.putString("color", colorHex);

        if (cursor.getString(3) != null) {
            calendar.putBoolean("isPrimary", cursor.getString(3).equals("1"));
        }

        int accesslevel = cursor.getInt(4);

        if (accesslevel == CalendarContract.Calendars.CAL_ACCESS_ROOT ||
                accesslevel == CalendarContract.Calendars.CAL_ACCESS_OWNER ||
                accesslevel == CalendarContract.Calendars.CAL_ACCESS_EDITOR ||
                accesslevel == CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) {
            calendar.putBoolean("allowsModifications", true);
        } else {
            calendar.putBoolean("allowsModifications", false);
        }

        return calendar;
    }

    private WritableNativeArray serializeAttendeeCalendar(Cursor cursor) {

        WritableNativeArray results = new WritableNativeArray();

        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {

            WritableNativeMap attendee = new WritableNativeMap();

            attendee.putString("name", cursor.getString( 2));
            attendee.putString("email", cursor.getString(3));
            attendee.putString("type", cursor.getString(4));
            attendee.putString("relationship", cursor.getString(5));
            attendee.putString("status", cursor.getString(6));
            attendee.putString("identity", cursor.getString(7));
            attendee.putString("id_namespace", cursor.getString(8));
            results.pushMap(attendee);
        }

        return results;
    }
    // endregion

    private String getPermissionKey(boolean readOnly) {
        String permissionKey = "permissionRequested"; // default to previous key for read/write, backwards-compatible
        if (readOnly) {
            permissionKey = "permissionRequestedRead"; // new key for read-only permission requests
        }
        return permissionKey;
    }

    //region React Native Methods
    @ReactMethod
    public void checkPermissions(boolean readOnly, Promise promise) {
        try {
            SharedPreferences sharedPreferences = reactContext.getSharedPreferences(RNC_PREFS, ReactContext.MODE_PRIVATE);
            boolean permissionRequested = sharedPreferences.getBoolean(getPermissionKey(readOnly), false);

            if (this.haveCalendarPermissions(readOnly)) {
                promise.resolve("authorized");
            } else if (!permissionRequested) {
                promise.resolve("undetermined");
            } else if (this.shouldShowRequestPermissionRationale(readOnly)) {
                promise.resolve("denied");
            } else {
                promise.resolve("restricted");
            }
        }
        catch(Throwable t) {
            Log.e("RNCalendarEvents error checking permissions", t.getMessage(), t);
            promise.reject("error checking permissions", t.getMessage(), t);
        }
    }

    @ReactMethod
    public void requestPermissions(boolean readOnly, Promise promise) {
        try {
            SharedPreferences sharedPreferences = reactContext.getSharedPreferences(RNC_PREFS, ReactContext.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(getPermissionKey(readOnly), true);
            editor.apply();

            if (this.haveCalendarPermissions(readOnly)) {
                promise.resolve("authorized");
            } else {
                this.requestCalendarPermission(readOnly, promise);
            }
        }
        catch(Throwable t) {
            Log.e("RNCalendarEvents error requesting permissions", t.getMessage(), t);
            promise.reject("error requesting permissions", t.getMessage(), t);
        }
    }

    @ReactMethod
    public void findCalendars(final Promise promise) {
        if (this.haveCalendarPermissions(true)) {
            try {
                Thread thread = new Thread(new Runnable(){
                    @Override
                    public void run() {
                        try {
                            WritableArray calendars = findEventCalendars();
                            promise.resolve(calendars);
                        }
                        catch(Throwable t) {
                            Log.e("RNCalendarEvents calendar request error", t.getMessage(), t);
                            promise.reject("calendar request error", t.getMessage(), t);
                        }
                    }
                });
                thread.start();
            } catch (Throwable t) {
                promise.reject("calendar request error", t.getMessage(), t);
            }
        } else {
            promise.reject("add event error", "you don't have permissions to retrieve an event to the users calendar");
        }
    }

    @ReactMethod
    public void saveCalendar(final ReadableMap options, final Promise promise) {
        if (!this.haveCalendarPermissions(false)) {
            promise.reject("save calendar error", "unauthorized to access calendar");
            return;
        }
        try {
            Thread thread = new Thread(new Runnable(){
                @Override
                public void run() {
                    try {
                        Integer calendarID = addCalendar(options);
                        promise.resolve(calendarID.toString());
                    } catch (Throwable t) {
                        Log.e("RNCalendarEvents save calendar error", t.getMessage(), t);
                        promise.reject("save calendar error", t.getMessage(), t);
                    }
                }
            });
            thread.start();
        } catch (Throwable t) {
            promise.reject("save calendar error", "Calendar could not be saved", t);
        }
    }

    @ReactMethod
    public void removeCalendar(final String CalendarID, final Promise promise) {
        if (this.haveCalendarPermissions(false)) {
            try {
                Thread thread = new Thread(new Runnable(){
                    @Override
                    public void run() {
                        try {
                            boolean successful = removeCalendar(CalendarID);
                            promise.resolve(successful);
                        }
                        catch(Throwable t) {
                            Log.e("RNCalendarEvents error removing calendar", t.getMessage(), t);
                            promise.reject("error removing calendar", t.getMessage(), t);
                        }
                    }
                });
                thread.start();

            } catch (Throwable t) {
                promise.reject("error removing calendar", t.getMessage(), t);
            }
        } else {
            promise.reject("remove calendar error", "you don't have permissions to remove a calendar");
        }

    }
    @ReactMethod
    public void saveEvent(final String title, final ReadableMap details, final ReadableMap options, final Promise promise) {
        if (this.haveCalendarPermissions(false)) {
            try {
                Thread thread = new Thread(new Runnable(){
                    @Override
                    public void run() {
                        int eventId;
                        try {
                            eventId = addEvent(title, details, options);
                            if (eventId > -1) {
                                promise.resolve(Integer.toString(eventId));
                            } else {
                                promise.reject("add event error", "Unable to save event");
                            }
                        } catch (Throwable t) {
                            Log.e("RNCalendarEvents add event error", t.getMessage(), t);
                            promise.reject("add event error", t.getMessage(), t);
                        }
                    }
                });
                thread.start();
            } catch (Throwable t) {
                promise.reject("add event error", t.getMessage(), t);
            }
        } else {
            promise.reject("add event error", "you don't have permissions to add an event to the users calendar");
        }
    }

    @ReactMethod
    public void findAllEvents(final Dynamic startDate, final Dynamic endDate, final ReadableArray calendars, final Promise promise) {

        if (this.haveCalendarPermissions(true)) {
            try {
                Thread thread = new Thread(new Runnable(){
                    @Override
                    public void run() {
                        try {
                            WritableNativeArray results = findEvents(startDate, endDate, calendars);
                            promise.resolve(results);
                        }
                        catch(Throwable t) {
                            Log.e("RNCalendarEvents find event error", t.getMessage(), t);
                            promise.reject("find event error", t.getMessage(), t);
                        }
                    }
                });
                thread.start();

            } catch (Throwable t) {
                promise.reject("find event error", t.getMessage(), t);
            }
        } else {
            promise.reject("find event error", "you don't have permissions to read an event from the users calendar");
        }

    }

    @ReactMethod
    public void findById(final String eventID, final Promise promise) {
        if (this.haveCalendarPermissions(true)) {
            try {
                Thread thread = new Thread(new Runnable(){
                    @Override
                    public void run() {
                        try {
                            WritableMap results = findEventById(eventID);
                            promise.resolve(results);
                        }
                        catch(Throwable t) {
                            Log.e("RNCalendarEvents find event error", t.getMessage(), t);
                            promise.reject("find event error", t.getMessage(), t);
                        }
                    }
                });
                thread.start();

            } catch (Throwable t) {
                promise.reject("find event error", t.getMessage(), t);
            }
        } else {
            promise.reject("find event error", "you don't have permissions to read an event from the users calendar");
        }

    }

    @ReactMethod
    public void removeEvent(final String eventID, final ReadableMap options, final Promise promise) {
        if (this.haveCalendarPermissions(false)) {
            try {
                Thread thread = new Thread(new Runnable(){
                    @Override
                    public void run() {
                        try {
                            boolean successful = removeEvent(eventID, options);
                            promise.resolve(successful);
                        }
                        catch(Throwable t) {
                            Log.e("RNCalendarEvents error removing event", t.getMessage(), t);
                            promise.reject("error removing event", t.getMessage(), t);
                        }
                    }
                });
                thread.start();

            } catch (Throwable t) {
                promise.reject("error removing event", t.getMessage(), t);
            }
        } else {
            promise.reject("remove event error", "you don't have permissions to remove an event from the users calendar");
        }

    }

    @ReactMethod
    public void openEventInCalendar(int eventID) {
        try {
            Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventID);
            Intent sendIntent = new Intent(Intent.ACTION_VIEW).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).setData(uri);

            if (sendIntent.resolveActivity(reactContext.getPackageManager()) != null) {
                reactContext.startActivity(sendIntent);
            }
        }
        catch(Throwable t) {
            Log.e("RNCalendarEvents error opening event in calendar", t.getMessage(), t);
        }
    }

    @ReactMethod
    public void uriForCalendar(Promise promise) {
        promise.resolve(CalendarContract.Events.CONTENT_URI.toString());
    }
    //endregion
}
