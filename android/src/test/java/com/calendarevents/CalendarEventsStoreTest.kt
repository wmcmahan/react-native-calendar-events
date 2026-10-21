package com.calendarevents

import android.content.Context
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class CalendarEventsStoreTest {
  private lateinit var context: Context
  private lateinit var store: CalendarEventsStore
  private var calendarId: Long = 0

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    Robolectric.buildContentProvider(FakeCalendarProvider::class.java).create(CalendarContract.AUTHORITY)
    store = CalendarEventsStore(context)
    calendarId = FakeCalendarProvider.seedCalendar(context, "Work", primary = true)
  }

  // region Pure logic

  @Test
  fun parsesIsoDatesWithAndWithoutMillisAndEpochNumbers() {
    val expected = 1_767_261_600_000L // 2026-01-01T10:00:00Z
    assertEquals(expected, CalendarEventsStore.parseDate("2026-01-01T10:00:00.000Z"))
    assertEquals(expected, CalendarEventsStore.parseDate("2026-01-01T10:00:00Z"))
    assertEquals(expected, CalendarEventsStore.parseDate(1_767_261_600_000.0))
    assertNull(CalendarEventsStore.parseDate("not a date"))
    assertNull(CalendarEventsStore.parseDate(null))
  }

  @Test
  fun formatsDatesAsUtcWithMillis() {
    assertEquals("2026-01-01T10:00:00.500Z", CalendarEventsStore.formatDate(1_767_261_600_500L))
  }

  @Test
  fun buildsRecurrenceRules() {
    assertEquals("FREQ=DAILY", CalendarEventsStore.buildRecurrenceRule("daily"))
    assertEquals(
      "FREQ=WEEKLY;BYDAY=MO,WE;INTERVAL=2;COUNT=5",
      CalendarEventsStore.buildRecurrenceRule("weekly", interval = 2, occurrence = 5, daysOfWeek = listOf("MO", "WE")),
    )
    assertEquals(
      "FREQ=MONTHLY;BYSETPOS=2;BYDAY=TU;UNTIL=20260201T000000Z",
      CalendarEventsStore.buildRecurrenceRule(
        "monthly", untilMillis = 1_769_904_000_000L, occurrence = 9, daysOfWeek = listOf("TU"), weekPositionInMonth = 2,
      ),
    )
    assertNull(CalendarEventsStore.buildRecurrenceRule("hourly"))
  }

  @Test
  fun parsesRecurrenceRulesByKeyRegardlessOfOrder() {
    val rule = CalendarEventsStore.parseRecurrenceRule("COUNT=3;FREQ=WEEKLY;BYDAY=MO,FR;INTERVAL=2")
    assertEquals("weekly", rule["frequency"])
    assertEquals(2, rule["interval"])
    assertEquals(3, rule["occurrence"])
    assertEquals(listOf("MO", "FR"), rule["daysOfWeek"])

    val until = CalendarEventsStore.parseRecurrenceRule("FREQ=DAILY;UNTIL=20260201T000000Z")
    assertEquals("2026-02-01T00:00:00.000Z", until["endDate"])
  }

  @Test
  fun convertsColors() {
    assertEquals("#FF0000", CalendarEventsStore.hexColor(0xFFFF0000.toInt()))
    assertEquals("#FFFFFF", CalendarEventsStore.hexColor(null))
    // processColor on Android hands over a signed int, often boxed as a Double.
    assertEquals(0xFFFF0000.toInt(), CalendarEventsStore.colorInt(-65536.0))
    assertEquals(0xFFFF0000.toInt(), CalendarEventsStore.colorInt(4294901760.0))
  }

  @Test
  fun mapsAvailabilityAccessLevelsAndStatus() {
    assertEquals(CalendarContract.Events.AVAILABILITY_FREE, CalendarEventsStore.availabilityConstant("free"))
    assertEquals(CalendarContract.Events.AVAILABILITY_BUSY, CalendarEventsStore.availabilityConstant("bogus"))
    assertEquals("tentative", CalendarEventsStore.availabilityName(CalendarContract.Events.AVAILABILITY_TENTATIVE))
    assertEquals(listOf("busy", "free"), CalendarEventsStore.allowedAvailabilities("0,1"))
    assertEquals(listOf("tentative"), CalendarEventsStore.allowedAvailabilities("AVAILABILITY_TENTATIVE"))
    assertEquals(CalendarContract.Calendars.CAL_ACCESS_OWNER, CalendarEventsStore.accessLevel("owner"))
    assertEquals(CalendarContract.Calendars.CAL_ACCESS_NONE, CalendarEventsStore.accessLevel(null))
    assertEquals("canceled", CalendarEventsStore.statusName(CalendarContract.Events.STATUS_CANCELED))
    assertEquals("none", CalendarEventsStore.statusName(null))
  }

  // endregion

  // region Provider-backed

  @Test
  fun findCalendarsSerializesRows() {
    val calendars = store.findCalendars()
    assertEquals(1, calendars.size)
    val calendar = calendars.first()
    assertEquals(calendarId.toString(), calendar["id"])
    assertEquals("Work", calendar["title"])
    assertEquals("tester@example.com", calendar["source"])
    assertEquals("com.example", calendar["type"])
    assertEquals(true, calendar["isPrimary"])
    assertEquals(true, calendar["allowsModifications"])
    assertEquals("#2196F3", calendar["color"])
    assertEquals(listOf("busy", "free"), calendar["allowedAvailabilities"])
  }

  @Test
  fun readOnlyCalendarsDoNotAllowModifications() {
    val id = FakeCalendarProvider.seedCalendar(context, "Holidays", accessLevel = CalendarContract.Calendars.CAL_ACCESS_READ)
    assertEquals(false, store.findCalendarById(id.toString())!!["allowsModifications"])
    assertNull(store.findCalendarById("not-a-number"))
  }

  @Test
  fun saveCalendarValidatesRequiredFieldsAndInserts() {
    try {
      store.saveCalendar(mapOf("title" to "Missing"))
      throw AssertionError("expected failure")
    } catch (e: CalendarEventsStore.StoreException) {
      assertEquals("new calendars require `source`", e.message)
    }

    val id = store.saveCalendar(
      mapOf(
        "title" to "Personal",
        "name" to "personal",
        "color" to -65536.0,
        "accessLevel" to "owner",
        "ownerAccount" to "me@example.com",
        "source" to mapOf("name" to "me@example.com", "isLocalAccount" to true),
      )
    )
    val saved = store.findCalendarById(id.toString())!!
    assertEquals("Personal", saved["title"])
    assertEquals(CalendarContract.ACCOUNT_TYPE_LOCAL, saved["type"])
    assertEquals("#FF0000", saved["color"])
    context.contentResolver.query(
      android.content.ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, id),
      arrayOf(CalendarContract.Calendars.SYNC_EVENTS, CalendarContract.Calendars.VISIBLE), null, null, null,
    )!!.use { c ->
      assertTrue(c.moveToFirst())
      assertEquals(1, c.getInt(0))
      assertEquals(1, c.getInt(1))
    }
    assertTrue(store.removeCalendar(id.toString()))
    assertNull(store.findCalendarById(id.toString()))
  }

  @Test
  fun saveFindUpdateAndRemoveEvent() {
    val id = store.saveEvent(
      "Lunch",
      mapOf(
        "calendarId" to calendarId.toString(),
        "startDate" to "2026-03-02T10:00:00.000Z",
        "endDate" to "2026-03-02T11:00:00.000Z",
        "description" to "bring sunglasses",
        "location" to "Los Angeles, CA",
        "availability" to "free",
        "alarms" to listOf(mapOf("date" to 10.0)),
        "attendees" to listOf(mapOf("name" to "Sam", "email" to "sam@example.com")),
      ),
      emptyMap(),
    )

    val event = store.findEventById(id.toString())!!
    assertEquals(id.toString(), event["id"])
    assertEquals("Lunch", event["title"])
    assertEquals("2026-03-02T10:00:00.000Z", event["startDate"])
    assertEquals("2026-03-02T11:00:00.000Z", event["endDate"])
    assertEquals("bring sunglasses", event["description"])
    assertEquals("Los Angeles, CA", event["location"])
    assertEquals("free", event["availability"])
    assertEquals(false, event["allDay"])
    assertEquals(calendarId.toString(), (event["calendar"] as Map<*, *>)["id"])

    @Suppress("UNCHECKED_CAST")
    val alarms = event["alarms"] as List<Map<String, Any?>>
    assertEquals("2026-03-02T09:50:00.000Z", alarms.single()["date"])

    @Suppress("UNCHECKED_CAST")
    val attendees = event["attendees"] as List<Map<String, Any?>>
    assertEquals("sam@example.com", attendees.single()["email"])
    assertEquals("Sam", attendees.single()["name"])

    val updatedId = store.saveEvent("Dinner", mapOf("id" to id.toString(), "allDay" to true), emptyMap())
    assertEquals(id, updatedId)
    val updated = store.findEventById(id.toString())!!
    assertEquals("Dinner", updated["title"])
    assertEquals(true, updated["allDay"])

    assertTrue(store.removeEvent(id.toString(), emptyMap()))
    assertNull(store.findEventById(id.toString()))
    assertFalse(store.removeEvent(id.toString(), emptyMap()))
  }

  @Test
  fun fetchAllEventsFiltersByRangeAndCalendar() {
    val other = FakeCalendarProvider.seedCalendar(context, "Other")
    val inRange = store.saveEvent("A", event("2026-03-02T10:00:00.000Z", "2026-03-02T11:00:00.000Z"), emptyMap())
    store.saveEvent("B", event("2026-04-02T10:00:00.000Z", "2026-04-02T11:00:00.000Z"), emptyMap())
    val otherCalendar = store.saveEvent(
      "C", event("2026-03-02T12:00:00.000Z", "2026-03-02T13:00:00.000Z", calendar = other), emptyMap(),
    )

    val all = store.fetchAllEvents("2026-03-01T00:00:00.000Z", "2026-03-03T00:00:00.000Z", emptyList())
    assertEquals(listOf(inRange.toString(), otherCalendar.toString()), all.map { it["id"] })

    val filtered = store.fetchAllEvents("2026-03-01T00:00:00.000Z", "2026-03-03T00:00:00.000Z", listOf(calendarId.toString(), "", "junk"))
    assertEquals(listOf(inRange.toString()), filtered.map { it["id"] })
  }

  @Test
  fun recurrenceRuleRoundTrips() {
    val id = store.saveEvent(
      "Standup",
      event("2026-03-02T10:00:00.000Z", "2026-03-02T10:15:00.000Z") +
        mapOf("recurrenceRule" to mapOf("frequency" to "weekly", "interval" to 1.0, "occurrence" to 4.0, "daysOfWeek" to listOf("MO"))),
      emptyMap(),
    )
    val event = store.findEventById(id.toString())!!
    assertEquals("weekly", event["recurrence"])
    val rule = event["recurrenceRule"] as Map<*, *>
    assertEquals("weekly", rule["frequency"])
    assertEquals(1, rule["interval"])
    assertEquals(4, rule["occurrence"])
    assertEquals(listOf("MO"), rule["daysOfWeek"])
    // The provider wants DURATION rather than DTEND for recurring events; derived from the dates.
    assertEquals("P900S", rule["duration"])
    assertEquals("2026-03-02T10:15:00.000Z", event["endDate"])
  }

  @Test
  fun parsesDurations() {
    assertEquals(3_600_000L, CalendarEventsStore.parseDuration("PT1H"))
    assertEquals(5_400_000L, CalendarEventsStore.parseDuration("PT1H30M"))
    assertEquals(900_000L, CalendarEventsStore.parseDuration("P900S"))
    assertEquals(8 * 86_400_000L, CalendarEventsStore.parseDuration("P1W1D"))
    assertNull(CalendarEventsStore.parseDuration("1 hour"))
  }

  @Test
  fun removingASingleOccurrenceInsertsACancelledException() {
    val id = store.saveEvent("Standup", event("2026-03-02T10:00:00.000Z", "2026-03-02T10:15:00.000Z") + mapOf("recurrence" to "daily"), emptyMap())
    assertTrue(store.removeEvent(id.toString(), mapOf("exceptionDate" to "2026-03-04T10:00:00.000Z")))

    // The original still exists; the exception row is cancelled and hidden from range queries.
    assertNotNull(store.findEventById(id.toString()))
    val visible = store.fetchAllEvents("2026-03-01T00:00:00.000Z", "2026-03-10T00:00:00.000Z", emptyList())
    assertEquals(listOf(id.toString()), visible.map { it["id"] })
  }

  @Test
  fun eventsDefaultToThePrimaryWritableCalendar() {
    val id = store.saveEvent("No calendar given", event("2026-03-02T10:00:00.000Z", "2026-03-02T11:00:00.000Z", calendar = null), emptyMap())
    assertEquals(calendarId.toString(), (store.findEventById(id.toString())!!["calendar"] as Map<*, *>)["id"])
  }

  @Test
  fun absoluteAlarmDatesBecomeMinutesBeforeStart() {
    val id = store.saveEvent(
      "Lunch",
      event("2026-03-02T10:00:00.000Z", "2026-03-02T11:00:00.000Z") +
        mapOf("alarms" to listOf(mapOf("date" to "2026-03-02T09:30:00.000Z"), mapOf("date" to "2026-03-02T12:00:00.000Z"))),
      emptyMap(),
    )
    @Suppress("UNCHECKED_CAST")
    val alarms = store.findEventById(id.toString())!!["alarms"] as List<Map<String, Any?>>
    // 30 minutes before, and an "after" date clamped to the start.
    assertEquals(listOf("2026-03-02T09:30:00.000Z", "2026-03-02T10:00:00.000Z"), alarms.map { it["date"] })
  }

  @Test
  fun updatingAMissingEventThrows() {
    try {
      store.saveEvent("Ghost", mapOf("id" to "9999", "alarms" to listOf(mapOf("date" to 5.0))), emptyMap())
      throw AssertionError("expected failure")
    } catch (e: CalendarEventsStore.StoreException) {
      assertEquals("No event found with id 9999", e.message)
    }
    context.contentResolver.query(CalendarContract.Reminders.CONTENT_URI, arrayOf("_id"), "event_id = 9999", null, null)!!.use {
      assertEquals(0, it.count)
    }
  }

  @Test
  fun editingASingleOccurrenceAttachesAlarmsToTheException() {
    val masterId = store.saveEvent(
      "Standup",
      event("2026-03-02T10:00:00.000Z", "2026-03-02T10:15:00.000Z") + mapOf("recurrence" to "daily", "alarms" to listOf(mapOf("date" to 10.0))),
      emptyMap(),
    )
    val exceptionId = store.saveEvent(
      "Standup (moved)",
      mapOf("id" to masterId.toString(), "alarms" to listOf(mapOf("date" to 30.0))),
      mapOf("exceptionDate" to "2026-03-04T10:00:00.000Z"),
    )
    assertTrue(exceptionId != masterId)
    fun minutesFor(eventId: Long): List<Int> =
      context.contentResolver.query(CalendarContract.Reminders.CONTENT_URI, arrayOf("minutes"), "event_id = $eventId", null, null)!!.use { c ->
        buildList { while (c.moveToNext()) add(c.getInt(0)) }
      }
    assertEquals(listOf(10), minutesFor(masterId))
    assertEquals(listOf(30), minutesFor(exceptionId))
  }

  @Test
  fun nonNumericIdsNeverCreateADuplicate() {
    try {
      store.saveEvent("Ghost", mapOf("id" to "evt-abc", "startDate" to "2026-03-02T10:00:00.000Z"), emptyMap())
      throw AssertionError("expected failure")
    } catch (e: CalendarEventsStore.StoreException) {
      assertEquals("No event found with id evt-abc", e.message)
    }
    assertEquals(0, store.fetchAllEvents("2026-03-01T00:00:00.000Z", "2026-03-03T00:00:00.000Z", emptyList()).size)
  }

  @Test
  fun unknownCalendarIdsReturnNothingRatherThanEverything() {
    store.saveEvent("A", event("2026-03-02T10:00:00.000Z", "2026-03-02T11:00:00.000Z"), emptyMap())
    assertEquals(0, store.fetchAllEvents("2026-03-01T00:00:00.000Z", "2026-03-03T00:00:00.000Z", listOf("abc", "")).size)
    assertEquals(1, store.fetchAllEvents("2026-03-01T00:00:00.000Z", "2026-03-03T00:00:00.000Z", emptyList()).size)
  }

  @Test
  fun clearingAlarmsResetsHasAlarm() {
    val id = store.saveEvent("Lunch", event("2026-03-02T10:00:00.000Z", "2026-03-02T11:00:00.000Z") + mapOf("alarms" to listOf(mapOf("date" to 10.0))), emptyMap())
    fun hasAlarm(): Int = context.contentResolver.query(
      android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), arrayOf(CalendarContract.Events.HAS_ALARM), null, null, null,
    )!!.use { c -> c.moveToFirst(); c.getInt(0) }
    assertEquals(1, hasAlarm())
    store.saveEvent("Lunch", mapOf("id" to id.toString(), "alarms" to emptyList<Any>()), emptyMap())
    assertEquals(0, hasAlarm())
    assertEquals(emptyList<Any>(), store.findEventById(id.toString())!!["alarms"])
  }

  @Test
  fun recurringUpdateWithOnlyAnEndDateKeepsTheRealDuration() {
    val id = store.saveEvent(
      "Standup",
      event("2026-03-02T10:00:00.000Z", "2026-03-02T10:15:00.000Z") + mapOf("recurrenceRule" to mapOf("frequency" to "weekly")),
      emptyMap(),
    )
    store.saveEvent("Standup", mapOf("id" to id.toString(), "endDate" to "2026-03-02T13:00:00.000Z", "recurrenceRule" to mapOf("frequency" to "weekly")), emptyMap())
    val rule = store.findEventById(id.toString())!!["recurrenceRule"] as Map<*, *>
    assertEquals("P10800S", rule["duration"])
  }

  @Test
  fun invalidDatesAreRejected() {
    try {
      store.saveEvent("Bad", mapOf("startDate" to "yesterday"), emptyMap())
      throw AssertionError("expected failure")
    } catch (e: CalendarEventsStore.StoreException) {
      assertEquals("startDate must be an ISO-8601 string or epoch milliseconds", e.message)
    }
  }

  private fun event(start: String, end: String, calendar: Long? = calendarId): Map<String, Any?> =
    buildMap {
      put("startDate", start)
      put("endDate", end)
      calendar?.let { put("calendarId", it.toString()) }
    }

  // endregion
}
