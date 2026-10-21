package com.calendarevents

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Pure CalendarContract layer with no React Native dependency.
 *
 * Works with plain Kotlin maps and lists so it can be exercised with Robolectric
 * against an in-memory provider. The Turbo Module adapter converts to and from
 * React's ReadableMap / WritableMap at the boundary.
 */
class CalendarEventsStore(private val resolver: ContentResolver) {
  constructor(context: Context) : this(context.contentResolver)

  class StoreException(message: String) : Exception(message)

  // region Calendars

  fun findCalendars(): List<Map<String, Any?>> {
    val cursor = resolver.query(Calendars.CONTENT_URI, CALENDAR_PROJECTION, null, null, null) ?: return emptyList()
    return cursor.use { c -> buildList { while (c.moveToNext()) add(serializeCalendar(c)) } }
  }

  fun findCalendarById(id: String?): Map<String, Any?>? {
    val calendarId = id?.toLongOrNull() ?: return null
    val uri = ContentUris.withAppendedId(Calendars.CONTENT_URI, calendarId)
    resolver.query(uri, CALENDAR_PROJECTION, null, null, null)?.use { c ->
      if (c.moveToFirst()) return serializeCalendar(c)
    }
    return null
  }

  fun saveCalendar(options: Map<String, Any?>): Long {
    for (key in listOf("source", "name", "title", "color", "accessLevel", "ownerAccount")) {
      if (options[key] == null) throw StoreException("new calendars require `$key`")
    }
    val source = options["source"] as? Map<*, *> ?: throw StoreException("new calendars require `source` object")
    val sourceName = source["name"] as? String ?: throw StoreException("new calendars require a `source` object with a `name`")
    val isLocalAccount = source["isLocalAccount"] as? Boolean ?: false
    val sourceType = source["type"] as? String
    if (sourceType == null && !isLocalAccount) {
      throw StoreException("new calendars require a `source` object with a `type`, or `isLocalAccount`: true")
    }
    val accountType = if (isLocalAccount) CalendarContract.ACCOUNT_TYPE_LOCAL else sourceType!!

    val values = ContentValues().apply {
      put(Calendars.ACCOUNT_NAME, sourceName)
      put(Calendars.ACCOUNT_TYPE, accountType)
      put(Calendars.CALENDAR_COLOR, colorInt(options["color"]))
      put(Calendars.CALENDAR_ACCESS_LEVEL, accessLevel(options["accessLevel"] as? String))
      put(Calendars.OWNER_ACCOUNT, options["ownerAccount"] as? String)
      put(Calendars.NAME, options["name"] as? String)
      put(Calendars.CALENDAR_DISPLAY_NAME, options["title"] as? String)
      // The provider only expands events of calendars with SYNC_EVENTS set; without
      // these, saved events never show up in range queries or other apps (#438, #442, #450).
      put(Calendars.SYNC_EVENTS, 1)
      put(Calendars.VISIBLE, 1)
    }
    val uri = asSyncAdapter(Calendars.CONTENT_URI, sourceName, accountType)
    val inserted = resolver.insert(uri, values) ?: throw StoreException("Calendar could not be saved")
    return inserted.lastPathSegment?.toLongOrNull() ?: throw StoreException("Calendar could not be saved")
  }

  fun removeCalendar(id: String): Boolean {
    val calendarId = id.toLongOrNull() ?: return false
    val calendar = findCalendarById(id) ?: return false
    val uri = asSyncAdapter(
      ContentUris.withAppendedId(Calendars.CONTENT_URI, calendarId),
      calendar["source"] as? String,
      calendar["type"] as? String,
    )
    return resolver.delete(uri, null, null) > 0
  }

  /** The primary writable calendar, else the first writable one, else the legacy fallback of id 1. */
  fun defaultCalendarId(): Long {
    val calendars = findCalendars()
    val chosen = calendars.firstOrNull { it["isPrimary"] == true && it["allowsModifications"] == true }
      ?: calendars.firstOrNull { it["allowsModifications"] == true }
    return chosen?.get("id")?.toString()?.toLongOrNull() ?: 1L
  }

  // endregion

  // region Events

  fun fetchAllEvents(startDate: Any?, endDate: Any?, calendarIds: List<String>): List<Map<String, Any?>> {
    val start = parseDate(startDate) ?: throw StoreException("startDate must be an ISO-8601 string or epoch milliseconds")
    val end = parseDate(endDate) ?: throw StoreException("endDate must be an ISO-8601 string or epoch milliseconds")

    val uri = Instances.CONTENT_URI.buildUpon()
      .also { ContentUris.appendId(it, start); ContentUris.appendId(it, end) }
      .build()

    // Numeric values are validated longs, so they are inlined rather than bound (#342).
    val selection = StringBuilder()
      .append("(${Instances.BEGIN} < $end) AND (${Instances.END} >= $start)")
      .append(" AND (${Instances.VISIBLE} = 1)")
      .append(" AND (${Instances.STATUS} IS NOT ${Events.STATUS_CANCELED})")
    val ids = calendarIds.mapNotNull { it.toLongOrNull() }
    if (calendarIds.isNotEmpty() && ids.isEmpty()) return emptyList() // filtered to calendars that cannot exist
    if (ids.isNotEmpty()) {
      selection.append(" AND (${Instances.CALENDAR_ID} IN (${ids.joinToString(",")}))")
    }

    val cursor = resolver.query(uri, INSTANCE_PROJECTION, selection.toString(), null, "${Instances.BEGIN} ASC")
      ?: return emptyList()
    // One calendar lookup per distinct calendar for the whole result set, not per row.
    val calendars = HashMap<String?, Map<String, Any?>?>()
    return cursor.use { c -> buildList { while (c.moveToNext()) add(serializeEvent(c, calendars)) } }
  }

  fun findEventById(id: String?): Map<String, Any?>? {
    val eventId = id?.toLongOrNull() ?: return null // non-numeric ids used to crash (#332)
    val uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)
    resolver.query(uri, EVENT_PROJECTION, "(${Events.DELETED} != 1)", null, null)?.use { c ->
      if (c.moveToFirst()) return serializeEvent(c)
    }
    return null
  }

  fun saveEvent(title: String?, details: Map<String, Any?>, options: Map<String, Any?>): Long {
    val values = ContentValues()

    // 3. An id that is present but not numeric can never match an event; do not fall through to create.
    val idValue = details["id"]
    val existingId = (idValue as? String)?.toLongOrNull()
    if (idValue != null && existingId == null) throw StoreException("No event found with id $idValue")

    title?.let { values.put(Events.TITLE, it) }
    (details["description"] as? String)?.let { values.put(Events.DESCRIPTION, it) }
    (details["location"] as? String)?.let { values.put(Events.EVENT_LOCATION, it) }
    details["startDate"]?.let { values.put(Events.DTSTART, parseDateOrThrow(it, "startDate")) }
    details["endDate"]?.let { values.put(Events.DTEND, parseDateOrThrow(it, "endDate")) }

    (details["recurrence"] as? String)?.let { frequency ->
      buildRecurrenceRule(frequency)?.let { values.put(Events.RRULE, it) }
    }
    (details["recurrenceRule"] as? Map<*, *>)?.let { rule ->
      val frequency = rule["frequency"] as? String
      if (frequency != null) {
        val endDate = rule["endDate"]?.let { parseDateOrThrow(it, "recurrenceRule.endDate") }
        @Suppress("UNCHECKED_CAST")
        val rrule = buildRecurrenceRule(
          frequency = frequency,
          interval = (rule["interval"] as? Number)?.toInt(),
          untilMillis = endDate,
          occurrence = (rule["occurrence"] as? Number)?.toInt(),
          daysOfWeek = (rule["daysOfWeek"] as? List<*>)?.map { it.toString() },
          weekStart = rule["weekStart"] as? String,
          weekPositionInMonth = (rule["weekPositionInMonth"] as? Number)?.toInt(),
        )
        values.put(Events.DURATION, rule["duration"] as? String ?: "PT1H")
        rrule?.let { values.put(Events.RRULE, it) }
      }
    }

    // The provider requires recurring events to carry DURATION instead of DTEND (#312).
    if (values.containsKey(Events.RRULE)) {
      val start = values.getAsLong(Events.DTSTART) ?: existingId?.let { eventStart(it) }
      val end = values.getAsLong(Events.DTEND)
      val explicitDuration = (details["recurrenceRule"] as? Map<*, *>)?.get("duration") as? String
      if (explicitDuration == null) {
        if (start != null && end != null && end > start) {
          values.put(Events.DURATION, "P${(end - start) / 1000}S")
        } else if (!values.containsKey(Events.DURATION)) {
          values.put(Events.DURATION, "PT1H")
        }
      }
      values.remove(Events.DTEND)
    }

    (details["allDay"] as? Boolean)?.let { values.put(Events.ALL_DAY, if (it) 1 else 0) }
    values.put(Events.EVENT_TIMEZONE, details["timeZone"] as? String ?: TimeZone.getDefault().id)
    values.put(Events.EVENT_END_TIMEZONE, details["endTimeZone"] as? String ?: TimeZone.getDefault().id)
    (details["alarms"] as? List<*>)?.let { values.put(Events.HAS_ALARM, if (it.isNotEmpty()) 1 else 0) }
    (details["availability"] as? String)?.let { values.put(Events.AVAILABILITY, availabilityConstant(it)) }

    val sync = options["sync"] == true
    val alarms = details["alarms"] as? List<*>
    val attendees = details["attendees"] as? List<*>

    if (existingId != null) {
      var eventId: Long = existingId
      val existing = findEventById(existingId.toString())
        ?: throw StoreException("No event found with id $existingId")
      val calendar = existing["calendar"] as? Map<*, *>
      val exceptionDate = options["exceptionDate"]
      if (exceptionDate == null) {
        var updateUri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)
        if (sync && calendar != null) {
          syncCalendar(calendar["id"].toString())
          updateUri = asSyncAdapter(updateUri, calendar["source"] as? String, calendar["type"] as? String)
        }
        resolver.update(updateUri, values, null, null)
      } else {
        values.put(Events.ORIGINAL_INSTANCE_TIME, parseDateOrThrow(exceptionDate, "exceptionDate"))
        var exceptionUri = Uri.withAppendedPath(Events.CONTENT_EXCEPTION_URI, eventId.toString())
        if (sync && calendar != null) {
          syncCalendar(calendar["id"].toString())
          exceptionUri = asSyncAdapter(exceptionUri, calendar["source"] as? String, calendar["type"] as? String)
        }
        val hasSyncId = ensureSyncId(existingId, calendar)
        resolver.insert(exceptionUri, values)?.lastPathSegment?.toLongOrNull()?.let { eventId = it }
        if (!hasSyncId) refreshRecurrenceInstances(existingId)
      }
      // For a single-occurrence edit these belong to the new exception row, not the series.
      alarms?.let { replaceReminders(eventId, it) }
      attendees?.let { replaceAttendees(eventId, it) }
      return eventId
    }

    val calendar = findCalendarById(details["calendarId"] as? String)
    values.put(Events.CALENDAR_ID, calendar?.get("id")?.toString()?.toLongOrNull() ?: defaultCalendarId())
    var createUri = Events.CONTENT_URI
    if (sync && calendar != null) {
      syncCalendar(calendar["id"].toString())
      createUri = asSyncAdapter(createUri, calendar["source"] as? String, calendar["type"] as? String)
    }
    val eventId = resolver.insert(createUri, values)?.lastPathSegment?.toLongOrNull()
      ?: throw StoreException("Event could not be saved")
    if (values.containsKey(Events.RRULE)) ensureSyncId(eventId, calendar)
    alarms?.let { replaceReminders(eventId, it) }
    attendees?.let { replaceAttendees(eventId, it) }
    return eventId
  }

  fun removeEvent(id: String, options: Map<String, Any?>): Boolean {
    val eventId = id.toLongOrNull() ?: return false
    val existing = findEventById(id) ?: return false
    val calendar = existing["calendar"] as? Map<*, *>
    val sync = options["sync"] == true
    val exceptionDate = options["exceptionDate"]

    if (exceptionDate == null) {
      var uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)
      if (sync && calendar != null) {
        syncCalendar(calendar["id"].toString())
        uri = asSyncAdapter(uri, calendar["source"] as? String, calendar["type"] as? String)
      }
      return resolver.delete(uri, null, null) > 0
    }

    val values = ContentValues().apply {
      put(Events.ORIGINAL_INSTANCE_TIME, parseDateOrThrow(exceptionDate, "exceptionDate"))
      put(Events.STATUS, Events.STATUS_CANCELED)
    }
    var uri = Uri.withAppendedPath(Events.CONTENT_EXCEPTION_URI, id)
    if (sync && calendar != null) {
      uri = asSyncAdapter(uri, calendar["source"] as? String, calendar["type"] as? String)
    }
    val hasSyncId = ensureSyncId(eventId, calendar)
    val inserted = resolver.insert(uri, values) != null
    if (inserted && !hasSyncId) refreshRecurrenceInstances(eventId)
    return inserted
  }

  /**
   * The provider ties an exception to its recurrence through `_sync_id` /
   * `original_sync_id`, which only a sync adapter can set. Local calendars have no
   * sync adapter, so a cancelled occurrence of a local recurrence would never hide.
   * For local calendars the store acts as the sync adapter and assigns an id itself.
   * Returns whether the master now has a sync id.
   */
  private fun ensureSyncId(masterId: Long, calendar: Map<*, *>?): Boolean {
    val uri = ContentUris.withAppendedId(Events.CONTENT_URI, masterId)
    val existing = resolver.query(uri, arrayOf(Events._SYNC_ID), null, null, null)?.use { c ->
      if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
    }
    if (existing != null) return true
    if (calendar?.get("type") != CalendarContract.ACCOUNT_TYPE_LOCAL) return false
    val syncUri = asSyncAdapter(uri, calendar["source"] as? String, calendar["type"] as? String)
    val values = ContentValues().apply { put(Events._SYNC_ID, "rncalendarevents-${UUID.randomUUID()}") }
    return resolver.update(syncUri, values, null, null) > 0
  }

  /**
   * Inserting an exception for a recurrence that has never been synced makes the
   * provider drop the master's instances and only re-expand the exception
   * (CalendarInstancesHelper.updateRecurrenceInstancesLocked passes the exception's
   * row id where it needs the master's). Touching the master's start time forces a
   * correct re-expansion of master plus exceptions.
   */
  private fun refreshRecurrenceInstances(masterId: Long) {
    val uri = ContentUris.withAppendedId(Events.CONTENT_URI, masterId)
    // The provider only sees the columns passed to update(), and decides whether the
    // event is recurring from them, so the recurrence columns must be re-sent as well.
    val columns = arrayOf(Events.DTSTART, Events.RRULE, Events.RDATE, Events.DURATION, Events.EVENT_TIMEZONE)
    val values = resolver.query(uri, columns, null, null, null)?.use { c ->
      if (!c.moveToFirst() || c.isNull(0)) return@use null
      ContentValues().apply {
        put(Events.DTSTART, c.getLong(0))
        for (i in 1 until columns.size) if (!c.isNull(i)) put(columns[i], c.getString(i))
      }
    } ?: return
    resolver.update(uri, values, null, null)
  }

  private fun asSyncAdapter(uri: Uri, accountName: String?, accountType: String?): Uri =
    uri.buildUpon()
      .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
      .appendQueryParameter(Calendars.ACCOUNT_NAME, accountName ?: "")
      .appendQueryParameter(Calendars.ACCOUNT_TYPE, accountType ?: "")
      .build()

  private fun syncCalendar(calendarId: String) {
    val id = calendarId.toLongOrNull() ?: return
    val values = ContentValues().apply {
      put(Calendars.SYNC_EVENTS, 1)
      put(Calendars.VISIBLE, 1)
    }
    resolver.update(ContentUris.withAppendedId(Calendars.CONTENT_URI, id), values, null, null)
  }

  // endregion

  // region Reminders & attendees

  /**
   * Reminder minutes follow CalendarContract: a positive number means "minutes
   * before the start". Reading back converts to an absolute ISO date.
   */
  private fun eventStart(eventId: Long): Long? =
    resolver.query(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), arrayOf(Events.DTSTART), null, null, null)
      ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }

  private fun replaceReminders(eventId: Long, reminders: List<*>) {
    Reminders.query(resolver, eventId, arrayOf(Reminders._ID))?.use { c ->
      while (c.moveToNext()) {
        resolver.delete(ContentUris.withAppendedId(Reminders.CONTENT_URI, c.getLong(0)), null, null)
      }
    }
    val eventStart: Long? by lazy { eventStart(eventId) }
    for (reminder in reminders) {
      val date = (reminder as? Map<*, *>)?.get("date")
      val minutes = when (date) {
        is Number -> date.toInt()
        // Absolute date: convert to minutes before the start; the provider cannot ring after it.
        is String -> {
          val absolute = parseDate(date) ?: continue
          val start = eventStart ?: continue
          ((start - absolute) / 60_000L).toInt().coerceAtLeast(0)
        }
        else -> continue
      }
      val values = ContentValues().apply {
        put(Reminders.EVENT_ID, eventId)
        put(Reminders.MINUTES, minutes)
        put(Reminders.METHOD, Reminders.METHOD_ALERT)
      }
      resolver.insert(Reminders.CONTENT_URI, values)
    }
  }

  private fun findReminders(eventId: Long, startMillis: Long?): List<Map<String, Any?>> {
    val cursor = resolver.query(
      Reminders.CONTENT_URI, arrayOf(Reminders.MINUTES),
      "(${Reminders.EVENT_ID} = ?)", arrayOf(eventId.toString()), null,
    ) ?: return emptyList()
    return cursor.use { c ->
      buildList {
        while (c.moveToNext()) {
          val minutes = if (c.isNull(0)) continue else c.getInt(0)
          val base = startMillis ?: System.currentTimeMillis()
          add(mapOf("date" to formatDate(base - minutes * 60_000L)))
        }
      }
    }
  }

  private fun replaceAttendees(eventId: Long, attendees: List<*>) {
    Attendees.query(resolver, eventId, arrayOf(Attendees._ID))?.use { c ->
      while (c.moveToNext()) {
        resolver.delete(ContentUris.withAppendedId(Attendees.CONTENT_URI, c.getLong(0)), null, null)
      }
    }
    for (entry in attendees) {
      val attendee = entry as? Map<*, *> ?: continue
      // v2 accepted `url` / `firstName`; the documented shape is `email` / `name`.
      val email = (attendee["email"] ?: attendee["url"]) as? String ?: continue
      val name = (attendee["name"] ?: attendee["firstName"]) as? String
      val values = ContentValues().apply {
        put(Attendees.EVENT_ID, eventId)
        put(Attendees.ATTENDEE_EMAIL, email)
        put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ATTENDEE)
        name?.let { put(Attendees.ATTENDEE_NAME, it) }
      }
      resolver.insert(Attendees.CONTENT_URI, values)
    }
  }

  private fun findAttendees(eventId: Long): List<Map<String, Any?>> {
    val cursor = resolver.query(
      Attendees.CONTENT_URI, ATTENDEE_PROJECTION,
      "(${Attendees.EVENT_ID} = ?)", arrayOf(eventId.toString()), null,
    ) ?: return emptyList()
    return cursor.use { c ->
      buildList {
        while (c.moveToNext()) {
          add(
            mapOf(
              "name" to c.getStringOrNull(Attendees.ATTENDEE_NAME),
              "email" to c.getStringOrNull(Attendees.ATTENDEE_EMAIL),
              "type" to c.getStringOrNull(Attendees.ATTENDEE_TYPE),
              "relationship" to c.getStringOrNull(Attendees.ATTENDEE_RELATIONSHIP),
              "status" to c.getStringOrNull(Attendees.ATTENDEE_STATUS),
              "identity" to c.getStringOrNull(Attendees.ATTENDEE_IDENTITY),
              "id_namespace" to c.getStringOrNull(Attendees.ATTENDEE_ID_NAMESPACE),
            )
          )
        }
      }
    }
  }

  // endregion

  // region Serialization

  private fun serializeCalendar(c: Cursor): Map<String, Any?> {
    val accessLevel = c.getIntOrNull(Calendars.CALENDAR_ACCESS_LEVEL) ?: Calendars.CAL_ACCESS_NONE
    val color = c.getIntOrNull(Calendars.CALENDAR_COLOR)
    return mapOf(
      "id" to c.getStringOrNull(Calendars._ID),
      "title" to c.getStringOrNull(Calendars.CALENDAR_DISPLAY_NAME),
      "source" to c.getStringOrNull(Calendars.ACCOUNT_NAME),
      "type" to c.getStringOrNull(Calendars.ACCOUNT_TYPE),
      "isPrimary" to (c.getStringOrNull(Calendars.IS_PRIMARY) == "1"),
      "allowsModifications" to (accessLevel >= Calendars.CAL_ACCESS_CONTRIBUTOR),
      "allowedAvailabilities" to allowedAvailabilities(c.getStringOrNull(Calendars.ALLOWED_AVAILABILITY)),
      "color" to hexColor(color),
    )
  }

  /** Handles both Events rows and Instances rows (which alias the id and date columns). */
  private fun serializeEvent(c: Cursor, calendarCache: MutableMap<String?, Map<String, Any?>?> = HashMap()): Map<String, Any?> {
    val id = c.getStringOrNull(Instances.EVENT_ID) ?: c.getStringOrNull(Events._ID)
    val start = c.getLongOrNull(Instances.BEGIN) ?: c.getLongOrNull(Events.DTSTART)
    val end = c.getLongOrNull(Instances.END) ?: c.getLongOrNull(Events.DTEND)
      ?: start?.let { s -> c.getStringOrNull(Events.DURATION)?.let { d -> parseDuration(d)?.let { s + it } } }
    val eventId = id?.toLongOrNull()

    val result = mutableMapOf<String, Any?>(
      "id" to id,
      "calendar" to c.getStringOrNull(Events.CALENDAR_ID).let { calendarId ->
        calendarCache.getOrPut(calendarId) { findCalendarById(calendarId) }
      },
      "title" to c.getStringOrNull(Events.TITLE),
      "description" to c.getStringOrNull(Events.DESCRIPTION),
      "startDate" to (start?.let(::formatDate) ?: ""),
      "endDate" to (end?.let(::formatDate) ?: ""),
      "allDay" to ((c.getIntOrNull(Events.ALL_DAY) ?: 0) != 0),
      "location" to c.getStringOrNull(Events.EVENT_LOCATION),
      "availability" to availabilityName(c.getIntOrNull(Events.AVAILABILITY)),
      "status" to statusName(c.getIntOrNull(Events.STATUS)),
      "timeZone" to c.getStringOrNull(Events.EVENT_TIMEZONE),
      "attendees" to (eventId?.let(::findAttendees) ?: emptyList<Map<String, Any?>>()),
      "alarms" to (
        if ((c.getIntOrNull(Events.HAS_ALARM) ?: 0) > 0 && eventId != null) findReminders(eventId, start)
        else emptyList<Map<String, Any?>>()
      ),
    )

    c.getStringOrNull(Events.RRULE)?.let { rrule ->
      val rule = parseRecurrenceRule(rrule).toMutableMap()
      c.getStringOrNull(Events.DURATION)?.let { rule["duration"] = it }
      result["recurrence"] = rule["frequency"]
      result["recurrenceRule"] = rule
    }
    c.getStringOrNull(Events.ORIGINAL_ID)?.let { result["originalId"] = it }
    c.getStringOrNull(Events.ORIGINAL_SYNC_ID)?.let { result["syncId"] = it }
    return result
  }

  // endregion

  companion object {
    private val GMT: TimeZone = TimeZone.getTimeZone("GMT")
    private const val ISO_MILLIS = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
    private const val ISO_SECONDS = "yyyy-MM-dd'T'HH:mm:ss'Z'"
    private const val RRULE_UNTIL = "yyyyMMdd'T'HHmmss'Z'"

    val CALENDAR_PROJECTION = arrayOf(
      Calendars._ID,
      Calendars.CALENDAR_DISPLAY_NAME,
      Calendars.ACCOUNT_NAME,
      Calendars.IS_PRIMARY,
      Calendars.CALENDAR_ACCESS_LEVEL,
      Calendars.ALLOWED_AVAILABILITY,
      Calendars.ACCOUNT_TYPE,
      Calendars.CALENDAR_COLOR,
    )

    val EVENT_PROJECTION = arrayOf(
      Events._ID,
      Events.TITLE,
      Events.DESCRIPTION,
      Events.DTSTART,
      Events.DTEND,
      Events.ALL_DAY,
      Events.EVENT_LOCATION,
      Events.RRULE,
      Events.CALENDAR_ID,
      Events.AVAILABILITY,
      Events.HAS_ALARM,
      Events.ORIGINAL_ID,
      Events.DURATION,
      Events.ORIGINAL_SYNC_ID,
      Events.STATUS,
      Events.EVENT_TIMEZONE,
    )

    val INSTANCE_PROJECTION = arrayOf(
      Instances.EVENT_ID,
      Instances.TITLE,
      Instances.DESCRIPTION,
      Instances.BEGIN,
      Instances.END,
      Instances.ALL_DAY,
      Instances.EVENT_LOCATION,
      Instances.RRULE,
      Instances.CALENDAR_ID,
      Instances.AVAILABILITY,
      Instances.HAS_ALARM,
      Instances.ORIGINAL_ID,
      Instances.DURATION,
      Instances.ORIGINAL_SYNC_ID,
      Instances.STATUS,
      Instances.EVENT_TIMEZONE,
    )

    val ATTENDEE_PROJECTION = arrayOf(
      Attendees._ID,
      Attendees.EVENT_ID,
      Attendees.ATTENDEE_NAME,
      Attendees.ATTENDEE_EMAIL,
      Attendees.ATTENDEE_TYPE,
      Attendees.ATTENDEE_RELATIONSHIP,
      Attendees.ATTENDEE_STATUS,
      Attendees.ATTENDEE_IDENTITY,
      Attendees.ATTENDEE_ID_NAMESPACE,
    )

    // SimpleDateFormat is neither cheap nor thread-safe: one instance per pattern per thread.
    private val formatters = ThreadLocal.withInitial { HashMap<String, SimpleDateFormat>() }

    private fun formatter(pattern: String): SimpleDateFormat =
      formatters.get()!!.getOrPut(pattern) {
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = GMT; isLenient = false }
      }

    /** Accepts ISO-8601 strings in UTC (with or without milliseconds) and epoch-millisecond numbers. */
    fun parseDate(value: Any?): Long? = when (value) {
      is Number -> value.toLong()
      is String -> listOf(ISO_MILLIS, ISO_SECONDS).firstNotNullOfOrNull { pattern ->
        try { formatter(pattern).parse(value)?.time } catch (_: ParseException) { null }
      }
      else -> null
    }

    private fun parseDateOrThrow(value: Any, field: String): Long =
      parseDate(value) ?: throw StoreException("$field must be an ISO-8601 string or epoch milliseconds")

    /** Always UTC with milliseconds, e.g. `2026-01-01T10:00:00.000Z`. */
    fun formatDate(millis: Long): String = formatter(ISO_MILLIS).format(Date(millis))

    fun buildRecurrenceRule(
      frequency: String,
      interval: Int? = null,
      untilMillis: Long? = null,
      occurrence: Int? = null,
      daysOfWeek: List<String>? = null,
      weekStart: String? = null,
      weekPositionInMonth: Int? = null,
    ): String? {
      val freq = when (frequency) {
        "daily" -> "DAILY"
        "weekly" -> "WEEKLY"
        "monthly" -> "MONTHLY"
        "yearly" -> "YEARLY"
        else -> return null
      }
      val rule = StringBuilder("FREQ=$freq")
      if (frequency == "weekly" && !daysOfWeek.isNullOrEmpty()) {
        rule.append(";BYDAY=").append(daysOfWeek.joinToString(","))
      }
      if (frequency == "monthly" && !daysOfWeek.isNullOrEmpty() && weekPositionInMonth != null) {
        rule.append(";BYSETPOS=").append(weekPositionInMonth)
        rule.append(";BYDAY=").append(daysOfWeek.joinToString(","))
      }
      weekStart?.let { rule.append(";WKST=").append(it) }
      interval?.let { rule.append(";INTERVAL=").append(it) }
      if (untilMillis != null) {
        rule.append(";UNTIL=").append(formatter(RRULE_UNTIL).format(Date(untilMillis)))
      } else if (occurrence != null) {
        rule.append(";COUNT=").append(occurrence)
      }
      return rule.toString()
    }

    /** Parses by key rather than by position, so provider-reordered rules still work. */
    fun parseRecurrenceRule(rrule: String): Map<String, Any?> {
      val parts = rrule.split(";").mapNotNull { part ->
        val pair = part.split("=", limit = 2)
        if (pair.size == 2) pair[0].uppercase() to pair[1] else null
      }.toMap()
      val result = mutableMapOf<String, Any?>()
      parts["FREQ"]?.let { result["frequency"] = it.lowercase() }
      parts["INTERVAL"]?.toIntOrNull()?.let { result["interval"] = it }
      parts["COUNT"]?.toIntOrNull()?.let { result["occurrence"] = it }
      parts["UNTIL"]?.let { until ->
        val parsed = try { formatter(RRULE_UNTIL).parse(until) } catch (_: ParseException) { null }
          ?: try { formatter("yyyyMMdd").parse(until) } catch (_: ParseException) { null }
        parsed?.let { result["endDate"] = formatDate(it.time) }
      }
      parts["BYDAY"]?.let { result["daysOfWeek"] = it.split(",") }
      parts["BYSETPOS"]?.toIntOrNull()?.let { result["weekPositionInMonth"] = it }
      parts["WKST"]?.let { result["weekStart"] = it }
      return result
    }

    private val DURATION_PATTERN = Regex("""^P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$""")
    private val LEGACY_DURATION_PATTERN = Regex("""^P(\d+)S$""")

    /** Parses an RFC 5545 duration such as `PT1H30M` or the provider's `P3600S` into milliseconds. */
    fun parseDuration(duration: String): Long? {
      LEGACY_DURATION_PATTERN.find(duration)?.let { return it.groupValues[1].toLong() * 1000 }
      val match = DURATION_PATTERN.find(duration) ?: return null
      val (weeks, days, hours, minutes, seconds) = match.destructured
      val total = (weeks.toLongOrNull() ?: 0) * 7 * 86_400 +
        (days.toLongOrNull() ?: 0) * 86_400 +
        (hours.toLongOrNull() ?: 0) * 3_600 +
        (minutes.toLongOrNull() ?: 0) * 60 +
        (seconds.toLongOrNull() ?: 0)
      return total * 1000
    }

    fun hexColor(color: Int?): String =
      if (color == null) "#FFFFFF" else String.format(Locale.US, "#%06X", 0xFFFFFF and color)

    /** `processColor` on Android yields a signed 32-bit ARGB value, possibly as a Double. */
    fun colorInt(value: Any?): Int = (value as? Number)?.toLong()?.toInt() ?: throw StoreException("`color` must be a number")

    fun accessLevel(name: String?): Int = when (name) {
      "contributor" -> Calendars.CAL_ACCESS_CONTRIBUTOR
      "editor" -> Calendars.CAL_ACCESS_EDITOR
      "freebusy" -> Calendars.CAL_ACCESS_FREEBUSY
      "override" -> Calendars.CAL_ACCESS_OVERRIDE
      "owner" -> Calendars.CAL_ACCESS_OWNER
      "read" -> Calendars.CAL_ACCESS_READ
      "respond" -> Calendars.CAL_ACCESS_RESPOND
      "root" -> Calendars.CAL_ACCESS_ROOT
      else -> Calendars.CAL_ACCESS_NONE
    }

    fun availabilityConstant(name: String): Int = when (name) {
      "free" -> Events.AVAILABILITY_FREE
      "tentative" -> Events.AVAILABILITY_TENTATIVE
      else -> Events.AVAILABILITY_BUSY
    }

    fun availabilityName(constant: Int?): String = when (constant) {
      Events.AVAILABILITY_FREE -> "free"
      Events.AVAILABILITY_TENTATIVE -> "tentative"
      else -> "busy"
    }

    fun statusName(constant: Int?): String = when (constant) {
      Events.STATUS_TENTATIVE -> "tentative"
      Events.STATUS_CONFIRMED -> "confirmed"
      Events.STATUS_CANCELED -> "canceled"
      else -> "none"
    }

    /** The provider stores a comma list of ints, though some devices use the constant names. */
    fun allowedAvailabilities(dbString: String?): List<String> {
      if (dbString.isNullOrBlank()) return emptyList()
      return dbString.split(",").mapNotNull { raw ->
        when (raw.trim().toIntOrNull() ?: raw.trim()) {
          Events.AVAILABILITY_BUSY, "AVAILABILITY_BUSY" -> "busy"
          Events.AVAILABILITY_FREE, "AVAILABILITY_FREE" -> "free"
          Events.AVAILABILITY_TENTATIVE, "AVAILABILITY_TENTATIVE" -> "tentative"
          else -> null
        }
      }
    }

    private fun Cursor.getStringOrNull(column: String): String? {
      val index = getColumnIndex(column)
      return if (index == -1 || isNull(index)) null else getString(index)
    }

    private fun Cursor.getIntOrNull(column: String): Int? {
      val index = getColumnIndex(column)
      return if (index == -1 || isNull(index)) null else getInt(index)
    }

    private fun Cursor.getLongOrNull(column: String): Long? {
      val index = getColumnIndex(column)
      return if (index == -1 || isNull(index)) null else getLong(index)
    }
  }
}
