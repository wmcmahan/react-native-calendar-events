package com.calendarevents;

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

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableMap;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.TimeZone;

public class CalendarEvents extends ReactContextBaseJavaModule {

    public static final int PERMISSION_REQUEST_CODE = 37;
    private ReactContext reactContext;
    private static Promise permissionsPromise;

    public CalendarEvents(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "CalendarEvents";
    }

    //region Calendar Permissions
    private void requestCalendarReadWritePermission()
    {
        if  (ContextCompat.checkSelfPermission(reactContext, Manifest.permission.WRITE_CALENDAR)!= PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this.getCurrentActivity(),
                    new String[]{ Manifest.permission.WRITE_CALENDAR },
                    PERMISSION_REQUEST_CODE);
        }
    }

    public static void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_CODE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    permissionsPromise.resolve("authorized");
                } else if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    permissionsPromise.resolve("denied");
                } else {
                    permissionsPromise.reject("permissions - unknown error", String.valueOf(grantResults[0]));
                }
                permissionsPromise = null;
                return;
            }

        }
    }

    private boolean haveCalendarReadWritePermissions()
    {
        int permissionCheck = ContextCompat.checkSelfPermission(reactContext,
                Manifest.permission.WRITE_CALENDAR);

        return permissionCheck == PackageManager.PERMISSION_GRANTED;
    }
    //endregion

    //region Event Accessors
    public WritableMap addEvent(String title, ReadableMap details) {
        String dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));

        ContentResolver cr = reactContext.getContentResolver();
        ContentValues eventValues = new ContentValues();

        WritableMap event = Arguments.createMap();
        eventValues.put(CalendarContract.Events.CALENDAR_ID, 1);

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
                eventValues.put(CalendarContract.Events.DTSTART, startCal.getTimeInMillis());
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

        if (details.hasKey("endDate")) {
            java.util.Calendar endCal = java.util.Calendar.getInstance();
            try {
                endCal.setTime(sdf.parse(details.getString("endDate")));
            } catch (ParseException e) {
                e.printStackTrace();
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

        Uri eventsUri = CalendarContract.Events.CONTENT_URI;
        Uri eventUri = cr.insert(eventsUri, eventValues);
        int eventID = Integer.parseInt(eventUri.getLastPathSegment());

        if (details.hasKey("alarms")) {
            createRemindersForEvent(cr, eventID, details.getArray("alarms"));
        }

        event.putInt("eventID", eventID);
        return event;
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
        String recurrenceRule = null;
        String[] validValues = {"daily", "weekly", "monthly", "yearly"};

        if (Arrays.asList(validValues).contains(recurrence)) {
            if (recurrence == "daily") {
                recurrenceRule = "FREQ=DAILY";
            } else if (recurrence == "weekly") {
                recurrenceRule = "FREQ=WEEKLY";
            } else if (recurrence == "monthly") {
                recurrenceRule = "FREQ=MONTHLY";
            } else if (recurrence == "yearly") {
                recurrenceRule = "FREQ=YEARLY";
            }
        }

        return recurrenceRule;
    }
    //endregion

    //region React Native Methods
    @ReactMethod
    public void requestCalendarPermissions(Promise promise) {
        this.permissionsPromise = promise;
        if (this.haveCalendarReadWritePermissions()) {
            this.permissionsPromise.resolve("authorized");
        } else {
            this.requestCalendarReadWritePermission();
        }
    }

    @ReactMethod
    public void saveEvent(String title, ReadableMap details, Promise promise) {
        if (this.haveCalendarReadWritePermissions()) {
            try {
                WritableMap event = this.addEvent(title, details);
                promise.resolve(event);
            } catch (Exception e) {
                promise.reject("add event error", e.getMessage());
            }
        } else {
            promise.reject("add event error", "you don't have permissions to add an event to the users calendar");
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
