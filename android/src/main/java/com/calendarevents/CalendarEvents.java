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
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.database.Cursor;

import com.facebook.react.bridge.Arguments;
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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.TimeZone;

public class CalendarEvents extends ReactContextBaseJavaModule {

    private static int PERMISSION_REQUEST_CODE = 37;
    private final ReactContext reactContext;
    private static final String RNC_PREFS = "REACT_NATIVE_CALENDAR_PREFERENCES";
    private static final HashMap<Integer, Promise> permissionsPromises = new HashMap<>();

    public CalendarEvents(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "CalendarEvents";
    }

    //region Calendar Permissions
    private void requestCalendarReadWritePermission(final Promise promise)
    {
        Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
            promise.reject("E_ACTIVITY_DOES_NOT_EXIST", "Activity doesn't exist");
            return;
        }
        PERMISSION_REQUEST_CODE++;
        permissionsPromises.put(PERMISSION_REQUEST_CODE, promise);
        ActivityCompat.requestPermissions(currentActivity, new String[]{
                Manifest.permission.WRITE_CALENDAR,
                Manifest.permission.READ_CALENDAR
        }, PERMISSION_REQUEST_CODE);
    }

    public static void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
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
    }

    private boolean haveCalendarReadWritePermissions() {
        int permissionCheck = ContextCompat.checkSelfPermission(reactContext, Manifest.permission.WRITE_CALENDAR);

        return permissionCheck == PackageManager.PERMISSION_GRANTED;
    }
    //endregion

    private WritableNativeArray findEventCalendars() {

        Cursor cursor;
        ContentResolver cr = reactContext.getContentResolver();

        Uri uri = CalendarContract.Calendars.CONTENT_URI;

        cursor = cr.query(uri, new String[]{
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.IS_PRIMARY,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.ALLOWED_AVAILABILITY
        }, null, null, null);

        return serializeEventCalendars(cursor);
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

            if (startDate.getType() == ReadableType.String) {
                eEndDate.setTime(sdf.parse(endDate.asString()));
            } else if (startDate.getType() == ReadableType.Number) {
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

        String selection = "((" + CalendarContract.Instances.BEGIN + " >= " + eStartDate.getTimeInMillis() + ") " +
                "AND (" + CalendarContract.Instances.END + " <= " + eEndDate.getTimeInMillis() + ") " +
                "AND (" + CalendarContract.Instances.VISIBLE + " = 1) ";

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
                CalendarContract.Instances.HAS_ALARM
        }, selection, null, null);

        return serializeEvents(cursor);
    }

    private WritableNativeMap findEventsById(String eventID) {

        WritableNativeMap result;
        Cursor cursor;
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
                CalendarContract.Events.HAS_ALARM
        }, selection, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            result = serializeEvent(cursor);
            cursor.close();
        } else {
            result = null;
        }

        return result;
    }

    private WritableNativeMap findCalendarById(String calendarID) {

        WritableNativeMap result;
        Cursor cursor;
        ContentResolver cr = reactContext.getContentResolver();
        Uri uri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, Integer.parseInt(calendarID));

        cursor = cr.query(uri, new String[]{
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.IS_PRIMARY,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.ALLOWED_AVAILABILITY
        }, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            result = serializeEventCalendar(cursor);
            cursor.close();
        } else {
            result = null;
        }

        return result;
    }

    private WritableMap addEvent(String title, ReadableMap details) throws ParseException {
        String dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));

        ContentResolver cr = reactContext.getContentResolver();
        ContentValues eventValues = new ContentValues();

        WritableMap event = Arguments.createMap();

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
            String rule = createRecurrenceRule(details.getString("recurrence"), null, null, null);
            if (rule != null) {
                eventValues.put(CalendarContract.Events.RRULE, rule);
            }
        }

        if (details.hasKey("recurrenceRule")) {
            ReadableMap recurrenceRule = details.getMap("recurrenceRule");

            if (recurrenceRule.hasKey("frequency")) {
                String frequency = recurrenceRule.getString("frequency");
                Integer interval = null;
                Integer occurrence = null;
                String endDate = null;

                if (recurrenceRule.hasKey("interval")) {
                    interval = recurrenceRule.getInt("interval");
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

                String rule = createRecurrenceRule(frequency, interval, endDate, occurrence);
                if (rule != null) {
                    eventValues.put(CalendarContract.Events.RRULE, rule);
                }
            }
        }

        if (details.hasKey("allDay")) {
            eventValues.put(CalendarContract.Events.ALL_DAY, details.getBoolean("allDay"));
        }

        eventValues.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());

        if (details.hasKey("alarms")) {
            eventValues.put(CalendarContract.Events.HAS_ALARM, true);
        }

        if (details.hasKey("availability")) {
            eventValues.put(CalendarContract.Events.AVAILABILITY, availabilityConstantMatchingString(details.getString("availability")));
        }

        if (details.hasKey("id")) {
            Uri updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, Integer.parseInt(details.getString("id")));
            cr.update(updateUri, eventValues, null, null);
            event.putInt("eventID", Integer.parseInt(details.getString("id")));

            if (details.hasKey("alarms")) {
                createRemindersForEvent(cr, Integer.parseInt(details.getString("id")), details.getArray("alarms"));
            }

        } else {

            if (details.hasKey("calendarId")) {
                WritableNativeMap calendar = findCalendarById(details.getString("calendarId"));

                if (calendar != null) {
                    eventValues.put(CalendarContract.Events.CALENDAR_ID, Integer.parseInt(calendar.getString("id")));
                } else {
                    eventValues.put(CalendarContract.Events.CALENDAR_ID, 1);
                }

            } else {
                eventValues.put(CalendarContract.Events.CALENDAR_ID, 1);
            }

            Uri eventUri = cr.insert(CalendarContract.Events.CONTENT_URI, eventValues);
            if (eventUri != null) {
                int eventID = Integer.parseInt(eventUri.getLastPathSegment());

                if (details.hasKey("alarms")) {
                    createRemindersForEvent(cr, eventID, details.getArray("alarms"));
                }
                event.putInt("eventID", eventID);
            }
        }

        return event;
    }

    private boolean removeEvent(String eventID) {
        int rows = 0;

        try {
            ContentResolver cr = reactContext.getContentResolver();
            Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, Integer.parseInt(eventID));

            rows = cr.delete(uri, null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return rows > 0;
    }
    //endregion


    //region Reminders
    private void createRemindersForEvent(ContentResolver resolver, int eventID, ReadableArray reminders) {

        Cursor cursor = CalendarContract.Reminders.query(resolver, eventID, new String[] {
                CalendarContract.Reminders._ID
        });

        while (cursor.moveToNext()) {
            long reminderId = cursor.getLong(0);
            Uri reminderUri = ContentUris.withAppendedId(CalendarContract.Reminders.CONTENT_URI, reminderId);
            resolver.delete(reminderUri, null, null);
        }
        cursor.close();

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
            cal.add(Calendar.MINUTE, Integer.parseInt(cursor.getString(0)));

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
        for(String availabilityId: dbString.split(",")) {
            switch(Integer.parseInt(availabilityId)) {
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

    //region Recurrence Rule
    private String createRecurrenceRule(String recurrence, Integer interval, String endDate, Integer occurrence) {
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

        while (cursor.moveToNext()) {
            results.pushMap(serializeEvent(cursor));
        }

        cursor.close();

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

            event.putString("recurrence", recurrenceRules[0].split("=")[1].toLowerCase());
            recurrenceRule.putString("frequency", recurrenceRules[0].split("=")[1].toLowerCase());

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

        if (cursor.getInt(10) > 0) {
            event.putArray("alarms", findReminderByEventId(cursor.getString(0), Long.parseLong(cursor.getString(3))));
        } else {
            WritableNativeArray emptyAlarms = new WritableNativeArray();
            event.putArray("alarms", emptyAlarms);
        }

        return event;
    }

    private WritableNativeArray serializeEventCalendars(Cursor cursor) {
        WritableNativeArray results = new WritableNativeArray();

        while (cursor.moveToNext()) {
            results.pushMap(serializeEventCalendar(cursor));
        }

        cursor.close();

        return results;
    }

    private WritableNativeMap serializeEventCalendar(Cursor cursor) {

        WritableNativeMap calendar = new WritableNativeMap();

        calendar.putString("id", cursor.getString(0));
        calendar.putString("title", cursor.getString(1));
        calendar.putString("source", cursor.getString(2));
        calendar.putBoolean("isPrimary", cursor.getString(3).equals("1"));
        calendar.putArray("allowedAvailabilities", calendarAllowedAvailabilitiesFromDBString(cursor.getString(5)));

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
    // endregion

    //region React Native Methods
    @ReactMethod
    public void getCalendarPermissions(Promise promise) {
        SharedPreferences sharedPreferences = reactContext.getSharedPreferences(RNC_PREFS, ReactContext.MODE_PRIVATE);
        boolean permissionRequested = sharedPreferences.getBoolean("permissionRequested", false);


        if (this.haveCalendarReadWritePermissions()) {
            promise.resolve("authorized");
        } else if (!permissionRequested) {
            promise.resolve("undetermined");
        } else {
            promise.resolve("denied");
        }
    }

    @ReactMethod
    public void requestCalendarPermissions(Promise promise) {
        SharedPreferences sharedPreferences = reactContext.getSharedPreferences(RNC_PREFS, ReactContext.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("permissionRequested", true);
        editor.apply();

        if (this.haveCalendarReadWritePermissions()) {
            promise.resolve("authorized");
        } else {
            this.requestCalendarReadWritePermission(promise);
        }
    }

    @ReactMethod
    public void findCalendars(final Promise promise) {
        if (this.haveCalendarReadWritePermissions()) {
            try {
                Thread thread = new Thread(new Runnable(){
                    @Override
                    public void run() {
                        WritableArray calendars = findEventCalendars();
                        promise.resolve(calendars);
                    }
                });
                thread.start();
            } catch (Exception e) {
                promise.reject("calendar request error", e.getMessage());
            }
        } else {
            promise.reject("add event error", "you don't have permissions to retrieve an event to the users calendar");
        }
    }

    @ReactMethod
    public void saveEvent(final String title, final ReadableMap details, final Promise promise) {
        if (this.haveCalendarReadWritePermissions()) {
            try {
                Thread thread = new Thread(new Runnable(){
                    @Override
                    public void run() {
                        WritableMap event;
                        try {
                            event = addEvent(title, details);
                            promise.resolve(event.getInt("eventID"));
                        } catch (ParseException e) {
                            promise.reject("add event error", e.getMessage());
                        }
                    }
                });
                thread.start();
            } catch (Exception e) {
                promise.reject("add event error", e.getMessage());
            }
        } else {
            promise.reject("add event error", "you don't have permissions to add an event to the users calendar");
        }
    }

    @ReactMethod
    public void findAllEvents(final Dynamic startDate, final Dynamic endDate, final ReadableArray calendars, final Promise promise) {

        if (this.haveCalendarReadWritePermissions()) {
            try {
                Thread thread = new Thread(new Runnable(){
                    @Override
                    public void run() {
                        WritableNativeArray results = findEvents(startDate, endDate, calendars);
                        promise.resolve(results);
                    }
                });
                thread.start();

            } catch (Exception e) {
                promise.reject("find event error", e.getMessage());
            }
        } else {
            promise.reject("find event error", "you don't have permissions to read an event from the users calendar");
        }

    }

    @ReactMethod
    public void findById(final String eventID, final Promise promise) {
        if (this.haveCalendarReadWritePermissions()) {
            try {
                Thread thread = new Thread(new Runnable(){
                    @Override
                    public void run() {
                        WritableMap results = findEventsById(eventID);
                        promise.resolve(results);
                    }
                });
                thread.start();

            } catch (Exception e) {
                promise.reject("find event error", e.getMessage());
            }
        } else {
            promise.reject("find event error", "you don't have permissions to read an event from the users calendar");
        }

    }

    @ReactMethod
    public void removeEvent(final String eventID, final Promise promise) {
        if (this.haveCalendarReadWritePermissions()) {
            try {
                Thread thread = new Thread(new Runnable(){
                    @Override
                    public void run() {
                        boolean successful = removeEvent(eventID);
                        promise.resolve(successful);
                    }
                });
                thread.start();

            } catch (Exception e) {
                promise.reject("error removing event", e.getMessage());
            }
        } else {
            promise.reject("remove event error", "you don't have permissions to remove an event from the users calendar");
        }

    }

    @ReactMethod
    public void openEventInCalendar(int eventID) {
        Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventID);
        Intent sendIntent = new Intent(Intent.ACTION_VIEW).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).setData(uri);

        if (sendIntent.resolveActivity(reactContext.getPackageManager()) != null) {
            reactContext.startActivity(sendIntent);
        }
    }

    @ReactMethod
    public void uriForCalendar(Promise promise) {
        promise.resolve(CalendarContract.Events.CONTENT_URI.toString());
    }
    //endregion
}
