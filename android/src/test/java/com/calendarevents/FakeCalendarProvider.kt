package com.calendarevents

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.provider.CalendarContract

/**
 * A minimal in-memory stand-in for the system CalendarProvider, backed by a
 * real SQLite database so the store's selections run unchanged.
 *
 * Instances are modelled as one row per event (no recurrence expansion); the
 * emulator-based instrumented tests cover the real provider's expansion.
 */
class FakeCalendarProvider : ContentProvider() {
  private lateinit var helper: SQLiteOpenHelper

  override fun onCreate(): Boolean {
    helper = object : SQLiteOpenHelper(context, null, null, 1) {
      override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
          """CREATE TABLE calendars (_id INTEGER PRIMARY KEY AUTOINCREMENT, calendar_displayName TEXT, account_name TEXT,
             account_type TEXT, isPrimary INTEGER DEFAULT 0, calendar_access_level INTEGER DEFAULT 700,
             allowedAvailability TEXT DEFAULT '0,1', calendar_color INTEGER, ownerAccount TEXT, name TEXT,
             sync_events INTEGER DEFAULT 0, visible INTEGER DEFAULT 1)"""
        )
        db.execSQL(
          """CREATE TABLE events (_id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, description TEXT, dtstart INTEGER,
             dtend INTEGER, allDay INTEGER DEFAULT 0, eventLocation TEXT, rrule TEXT, rdate TEXT, _sync_id TEXT, calendar_id INTEGER,
             availability INTEGER DEFAULT 0, hasAlarm INTEGER DEFAULT 0, original_id INTEGER, duration TEXT,
             original_sync_id TEXT, eventStatus INTEGER, eventTimezone TEXT, eventEndTimezone TEXT,
             originalInstanceTime INTEGER, deleted INTEGER DEFAULT 0)"""
        )
        db.execSQL(
          """CREATE VIEW instances AS SELECT _id AS _id, _id AS event_id, title, description, dtstart AS begin,
             COALESCE(dtend, dtstart + 3600000) AS end, allDay, eventLocation, rrule, calendar_id, availability, hasAlarm, original_id, duration,
             original_sync_id, eventStatus, eventTimezone, 1 AS visible FROM events WHERE deleted != 1"""
        )
        db.execSQL(
          """CREATE TABLE reminders (_id INTEGER PRIMARY KEY AUTOINCREMENT, event_id INTEGER, minutes INTEGER, method INTEGER)"""
        )
        db.execSQL(
          """CREATE TABLE attendees (_id INTEGER PRIMARY KEY AUTOINCREMENT, event_id INTEGER, attendeeName TEXT,
             attendeeEmail TEXT, attendeeType INTEGER, attendeeRelationship INTEGER, attendeeStatus INTEGER,
             attendeeIdentity TEXT, attendeeIdNamespace TEXT)"""
        )
      }

      override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
    return true
  }

  private data class Route(val table: String, val id: Long?)

  private fun route(uri: Uri): Route {
    val segments = uri.pathSegments
    val table = when (segments.firstOrNull()) {
      "calendars" -> "calendars"
      "events" -> "events"
      "exception" -> "exception"
      "instances" -> "instances"
      "reminders" -> "reminders"
      "attendees" -> "attendees"
      else -> throw IllegalArgumentException("Unsupported uri $uri")
    }
    val id = if (table == "instances") null else segments.getOrNull(1)?.toLongOrNull()
    return Route(table, id)
  }

  private fun withId(selection: String?, id: Long?): String? = when {
    id == null -> selection
    selection.isNullOrBlank() -> "_id = $id"
    else -> "($selection) AND _id = $id"
  }

  override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor {
    val route = route(uri)
    return helper.readableDatabase.query(route.table, projection, withId(selection, route.id), selectionArgs, null, null, sortOrder)
  }

  override fun insert(uri: Uri, values: ContentValues?): Uri? {
    val route = route(uri)
    val db = helper.writableDatabase
    val rowValues = ContentValues(values)
    if (route.table == "exception") {
      // An exception is a new event row that points back at the original.
      rowValues.put(CalendarContract.Events.ORIGINAL_ID, route.id)
      val original = db.query("events", null, "_id = ${route.id}", null, null, null, null)
      original.use {
        if (it.moveToFirst()) {
          for (column in listOf("calendar_id", "title", "dtstart", "dtend")) {
            if (!rowValues.containsKey(column)) {
              val index = it.getColumnIndexOrThrow(column)
              if (!it.isNull(index)) rowValues.put(column, it.getString(index))
            }
          }
        }
      }
      val id = db.insertOrThrow("events", null, rowValues)
      return ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
    }
    val id = db.insertOrThrow(route.table, null, rowValues)
    return ContentUris.withAppendedId(uri, id)
  }

  override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
    val route = route(uri)
    return helper.writableDatabase.update(route.table, values, withId(selection, route.id), selectionArgs)
  }

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
    val route = route(uri)
    return helper.writableDatabase.delete(route.table, withId(selection, route.id), selectionArgs)
  }

  override fun getType(uri: Uri): String? = null

  companion object {
    fun seedCalendar(context: Context, title: String, primary: Boolean = false, accessLevel: Int = CalendarContract.Calendars.CAL_ACCESS_OWNER, color: Int = 0xFF2196F3.toInt()): Long {
      val values = ContentValues().apply {
        put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, title)
        put(CalendarContract.Calendars.ACCOUNT_NAME, "tester@example.com")
        put(CalendarContract.Calendars.ACCOUNT_TYPE, "com.example")
        put(CalendarContract.Calendars.IS_PRIMARY, if (primary) 1 else 0)
        put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, accessLevel)
        put(CalendarContract.Calendars.CALENDAR_COLOR, color)
      }
      val uri = context.contentResolver.insert(CalendarContract.Calendars.CONTENT_URI, values)!!
      return ContentUris.parseId(uri)
    }
  }
}
