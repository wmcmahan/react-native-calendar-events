package com.calendarevents;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;

public class CalendarEvents extends ReactContextBaseJavaModule {

    public static int PERMISSION_REQUEST_CODE = 37;
    private ReactContext reactContext;
    private static HashMap<Integer, Promise> permissionsPromises = new HashMap<>();

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
        ActivityCompat.requestPermissions(currentActivity,
                new String[]{ Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CALENDAR },
                PERMISSION_REQUEST_CODE);
    }

    public static void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (permissionsPromises.containsKey(requestCode)) {
            // If request is cancelled, the result arrays are empty.
            Promise permissionsPromise = permissionsPromises.get(requestCode);
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                permissionsPromise.resolve("authorized");
            } else if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                permissionsPromise.resolve("denied");
            } else if (permissionsPromises.size() == 1) { // there should only be one
                permissionsPromise.reject("permissions - unknown error", grantResults.length > 0 ? String.valueOf(grantResults[0]) : "Request was cancelled");
            }
            permissionsPromises.remove(requestCode);
        }
    }

    private boolean haveCalendarReadWritePermissions()
    {
        int permissionCheck = ContextCompat.checkSelfPermission(reactContext,
                Manifest.permission.WRITE_CALENDAR);

        return permissionCheck == PackageManager.PERMISSION_GRANTED;
    }
    //endregion

    public WritableNativeArray findEventCalendars() {

        Cursor cursor = null;
        ContentResolver cr = reactContext.getContentResolver();

        Uri uri = CalendarContract.Calendars.CONTENT_URI;

        cursor = cr.query(uri, new String[]{
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME
        }, null, null, null);

        return serializeEventCalendars(cursor);
    }

    //region Event Accessors
    public WritableNativeArray findEvents(String startDate, String endDate, ReadableArray calendars) {
        String dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));

        Calendar eStartDate = Calendar.getInstance();
        Calendar eEndDate = Calendar.getInstance();

        try {
            eStartDate.setTime(sdf.parse(startDate));
            eEndDate.setTime(sdf.parse(endDate));
        } catch (ParseException e) {
            e.printStackTrace();
        }

        Cursor cursor = null;
        ContentResolver cr = reactContext.getContentResolver();

        Uri uri = CalendarContract.Events.CONTENT_URI;

        String selection = "((" + CalendarContract.Events.DTSTART + " >= " + eStartDate.getTimeInMillis() + ") " +
                "AND (" + CalendarContract.Events.DTEND + " <= " + eEndDate.getTimeInMillis() + ") " +
                "AND (" + CalendarContract.Events.DELETED + " != 1) ";

        if (calendars.size() > 0) {
            String calendarQuery = "AND (";
            for (int i = 0; i < calendars.size(); i++) {
                calendarQuery += CalendarContract.Events.CALENDAR_ID + " = " + calendars.getString(i);
                if (i != calendars.size() - 1) {
                    calendarQuery += " OR ";
                }
            }
            calendarQuery += ")";
            selection += calendarQuery;
        }

        selection += ")";

        cursor = cr.query(uri, new String[]{
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.RRULE,
                CalendarContract.Events.CALENDAR_ID
        }, selection, null, null);

        return serializeEvents(cursor);
    }

    public WritableNativeMap findEventsById(String eventID) {

        WritableNativeMap result = new WritableNativeMap();
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
                CalendarContract.Events.CALENDAR_ID
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

    public WritableNativeMap findCalendarById(String calendarID) {

        WritableNativeMap result = new WritableNativeMap();
        Cursor cursor = null;
        ContentResolver cr = reactContext.getContentResolver();
        Uri uri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, Integer.parseInt(calendarID));

        cursor = cr.query(uri, new String[]{
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME
        }, null, null, null);

        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            result = serializeEventCalendar(cursor);
        } else {
            result = null;
        }

        cursor.close();

        return result;
    }

    public WritableMap addEvent(String title, ReadableMap details) throws ParseException {
        String dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));

        ContentResolver cr = reactContext.getContentResolver();
        ContentValues eventValues = new ContentValues();

        WritableMap event = Arguments.createMap();

        if (title != null) {
            eventValues.put(CalendarContract.Events.TITLE, title);
        }
        if (details.hasKey("notes")) {
            eventValues.put(CalendarContract.Events.DESCRIPTION, details.getString("notes"));
        }
        if (details.hasKey("location")) {
            eventValues.put(CalendarContract.Events.EVENT_LOCATION, details.getString("location"));
        }

        if (details.hasKey("startDate")) {
            java.util.Calendar startCal = java.util.Calendar.getInstance();
            try {
                startCal.setTime(sdf.parse(details.getString("startDate")));
            } catch (ParseException e) {
                e.printStackTrace();
                throw e;
            }
            eventValues.put(CalendarContract.Events.DTSTART, startCal.getTimeInMillis());
        }

        if (details.hasKey("endDate")) {
            java.util.Calendar endCal = java.util.Calendar.getInstance();
            try {
                endCal.setTime(sdf.parse(details.getString("endDate")));
            } catch (ParseException e) {
                e.printStackTrace();
                throw e;
            }
            eventValues.put(CalendarContract.Events.DTEND, endCal.getTimeInMillis());
        }

        if (details.hasKey("recurrence")) {
            String rule = createRecurrenceRule(details.getString("recurrence"));
            if (rule != null) {
                eventValues.put(CalendarContract.Events.RRULE, rule);
            }
        }
        if (details.hasKey("allDay")) {
            eventValues.put(CalendarContract.Events.ALL_DAY, details.getBoolean("allDay"));
        }
        eventValues.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
        if (details.hasKey("alarms")) {
            eventValues.put(CalendarContract.Events.HAS_ALARM, true);
        }

        if (details.hasKey("id")) {
            Uri updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, Integer.parseInt(details.getString("id")));
            cr.update(updateUri, eventValues, null, null);
            event.putInt("eventID", Integer.parseInt(details.getString("id")));
        } else {

            if (details.hasKey("calendarId")) {
                WritableNativeMap calendar = findCalendarById(details.getString("calendarId"));

                if (calendar != null) {
                    eventValues.put(CalendarContract.Events.CALENDAR_ID, Integer.parseInt(calendar.getString("id")));
                } else {
                    eventValues.put(CalendarContract.Events.CALENDAR_ID, 1);
                }
            }

            Uri eventsUri = CalendarContract.Events.CONTENT_URI;
            Uri eventUri = cr.insert(eventsUri, eventValues);
            int eventID = Integer.parseInt(eventUri.getLastPathSegment());

            if (details.hasKey("alarms")) {
                createRemindersForEvent(cr, eventID, details.getArray("alarms"));
            }
            event.putInt("eventID", eventID);
        }

        return event;
    }

    public boolean removeEvent(String eventID) {

        ContentResolver cr = reactContext.getContentResolver();
        Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, Integer.parseInt(eventID));
        String selection = "((" + CalendarContract.Events.DELETED + " != 1))";

        int rows = cr.delete(uri, selection, null);

        return rows > 0;
    }
    //endregion


    //region Reminders
    private void createRemindersForEvent(ContentResolver resolver, int eventID, ReadableArray reminders) {
        for (int i = 0; i < reminders.size(); i++) {
            ReadableMap reminder = reminders.getMap(i);
            ReadableType type = reminder.getType("date");
            if (type == ReadableType.Number) {
                int minutes = -reminder.getInt("date");
                ContentValues reminderValues = new ContentValues();

                reminderValues.put(CalendarContract.Reminders.EVENT_ID, eventID);
                reminderValues.put(CalendarContract.Reminders.MINUTES, minutes);
                reminderValues.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_DEFAULT);

                resolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues);
            }
        }
    }
    //endregion

    //region Recurrence Rule
    private String createRecurrenceRule(String recurrence) {
        if (recurrence.equals("daily")) {
            return "FREQ=DAILY";
        } else if (recurrence.equals("weekly")) {
            return "FREQ=WEEKLY";
        }  else if (recurrence.equals("monthly")) {
            return "FREQ=MONTHLY";
        } else if (recurrence.equals("yearly")) {
            return "FREQ=YEARLY";
        } else {
            return null;
        }
    }
    //endregion

    // region Serialize Events
    public WritableNativeArray serializeEvents(Cursor cursor) {
        WritableNativeArray results = new WritableNativeArray();

        while (cursor.moveToNext()) {
            results.pushMap(serializeEvent(cursor));
        }

        cursor.close();

        return results;
    }

    public WritableNativeMap serializeEvent(Cursor cursor) {

        WritableNativeMap event = new WritableNativeMap();

        String dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));

        Calendar foundStartDate = Calendar.getInstance();
        Calendar foundEndDate = Calendar.getInstance();

        boolean allDay = false;
        String recurrenceRole = "";
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
            allDay = Integer.parseInt(cursor.getString(5)) != 0;
        }

        if (cursor.getString(7) != null) {
            recurrenceRole = cursor.getString(7).split("=")[1].toLowerCase();
        }

        event.putString("id", cursor.getString(0));
        event.putMap("calendarId", findCalendarById(cursor.getString(cursor.getColumnIndex("calendar_id"))));
        event.putString("title", cursor.getString(cursor.getColumnIndex("title")));
        event.putString("description", cursor.getString(2));
        event.putString("startDate", startDateUTC);
        event.putString("endDate", endDateUTC);
        event.putBoolean("allDay", allDay);
        event.putString("location", cursor.getString(6));
        event.putString("recurrence", recurrenceRole);

        return event;
    }

    public WritableNativeArray serializeEventCalendars(Cursor cursor) {
        WritableNativeArray results = new WritableNativeArray();

        while (cursor.moveToNext()) {
            results.pushMap(serializeEventCalendar(cursor));
        }

        cursor.close();

        return results;
    }

    public WritableNativeMap serializeEventCalendar(Cursor cursor) {

        WritableNativeMap calendar = new WritableNativeMap();

        calendar.putString("id", cursor.getString(0));
        calendar.putString("title", cursor.getString(1));
        calendar.putString("source", cursor.getString(2));

        return calendar;
    }
    // endregion

    //region React Native Methods
    @ReactMethod
    public void getCalendarPermissions(Promise promise) {
        if (this.haveCalendarReadWritePermissions()) {
            promise.resolve("authorized");
        } else {
            promise.resolve("denied");
        }
    }

    @ReactMethod
    public void requestCalendarPermissions(Promise promise) {
        if (this.haveCalendarReadWritePermissions()) {
            promise.resolve("authorized");
        } else {
            this.requestCalendarReadWritePermission(promise);
        }
    }

    @ReactMethod
    public void findCalendars(Promise promise) {
        if (this.haveCalendarReadWritePermissions()) {
            try {
                WritableArray calendars = this.findEventCalendars();
                promise.resolve(calendars);
            } catch (Exception e) {
                promise.reject("calendar request error", e.getMessage());
            }
        } else {
            promise.reject("add event error", "you don't have permissions to add an event to the users calendar");
        }
    }

    @ReactMethod
    public void saveEvent(String title, ReadableMap details, Promise promise) {
        if (this.haveCalendarReadWritePermissions()) {
            try {
                WritableMap event = this.addEvent(title, details);
                promise.resolve(event.getInt("eventID"));
            } catch (Exception e) {
                promise.reject("add event error", e.getMessage());
            }
        } else {
            promise.reject("add event error", "you don't have permissions to add an event to the users calendar");
        }
    }

    @ReactMethod
    public void findAllEvents(String startDate, String endDate, ReadableArray calendars, Promise promise) {

        if (this.haveCalendarReadWritePermissions()) {
            try {
                WritableNativeArray results = this.findEvents(startDate, endDate, calendars);
                promise.resolve(results);

            } catch (Exception e) {
                promise.reject("find event error", e.getMessage());
            }
        } else {
            promise.reject("find event error", "you don't have permissions to read an event from the users calendar");
        }

    }

    @ReactMethod
    public void findById(String eventID, Promise promise) {
        if (this.haveCalendarReadWritePermissions()) {
            try {
                WritableNativeMap results = this.findEventsById(eventID);
                promise.resolve(results);

            } catch (Exception e) {
                promise.reject("find event error", e.getMessage());
            }
        } else {
            promise.reject("find event error", "you don't have permissions to read an event from the users calendar");
        }

    }

    @ReactMethod
    public void removeEvent(String eventID, Promise promise) {
        if (this.haveCalendarReadWritePermissions()) {
            try {
                boolean successful = this.removeEvent(eventID);
                promise.resolve(successful);

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
