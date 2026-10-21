package com.calendarevents

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs against the real CalendarProvider on a device or emulator, which is the
 * only place recurrence expansion and the provider's validation rules exist.
 */
@RunWith(AndroidJUnit4::class)
class CalendarEventsStoreInstrumentedTest {
  @get:Rule
  val permissions: GrantPermissionRule =
    GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

  private lateinit var store: CalendarEventsStore
  private var calendarId: Long = 0

  @Before
  fun setUp() {
    store = CalendarEventsStore(InstrumentationRegistry.getInstrumentation().targetContext)
    calendarId = store.saveCalendar(
      mapOf(
        "title" to "RNCalendarEvents Tests",
        "name" to "rncalendarevents-tests",
        "color" to -65536.0,
        "accessLevel" to "owner",
        "ownerAccount" to "tests@example.com",
        "source" to mapOf("name" to "tests@example.com", "isLocalAccount" to true),
      )
    )
  }

  @After
  fun tearDown() {
    store.removeCalendar(calendarId.toString())
  }

  @Test
  fun createdCalendarIsListedAndWritable() {
    val calendar = store.findCalendars().first { it["id"] == calendarId.toString() }
    assertEquals("RNCalendarEvents Tests", calendar["title"])
    assertEquals(true, calendar["allowsModifications"])
    assertEquals("#FF0000", calendar["color"])
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
        "alarms" to listOf(mapOf("date" to 10.0)),
        "attendees" to listOf(mapOf("name" to "Sam", "email" to "sam@example.com")),
      ),
      emptyMap(),
    )
    val event = store.findEventById(id.toString())!!
    assertEquals("Lunch", event["title"])
    assertEquals("2026-03-02T10:00:00.000Z", event["startDate"])
    assertEquals("2026-03-02T11:00:00.000Z", event["endDate"])
    @Suppress("UNCHECKED_CAST")
    assertEquals("2026-03-02T09:50:00.000Z", (event["alarms"] as List<Map<String, Any?>>).single()["date"])
    @Suppress("UNCHECKED_CAST")
    assertEquals("sam@example.com", (event["attendees"] as List<Map<String, Any?>>).single()["email"])

    store.saveEvent("Dinner", mapOf("id" to id.toString()), emptyMap())
    assertEquals("Dinner", store.findEventById(id.toString())!!["title"])

    val listed = store.fetchAllEvents("2026-03-01T00:00:00.000Z", "2026-03-03T00:00:00.000Z", listOf(calendarId.toString()))
    assertEquals(listOf(id.toString()), listed.map { it["id"] })

    assertTrue(store.removeEvent(id.toString(), emptyMap()))
    assertNull(store.findEventById(id.toString()))
    assertFalse(store.removeEvent(id.toString(), emptyMap()))
  }

  @Test
  fun recurringEventsAreExpandedAndSingleOccurrencesCanBeRemoved() {
    val id = store.saveEvent(
      "Standup",
      mapOf(
        "calendarId" to calendarId.toString(),
        "startDate" to "2026-03-02T10:00:00.000Z",
        "endDate" to "2026-03-02T10:15:00.000Z",
        "recurrenceRule" to mapOf("frequency" to "daily", "occurrence" to 5.0),
      ),
      emptyMap(),
    )
    val master = store.findEventById(id.toString())!!
    assertEquals("daily", master["recurrence"])
    assertEquals("2026-03-02T10:15:00.000Z", master["endDate"])

    val range = listOf("2026-03-01T00:00:00.000Z", "2026-03-10T00:00:00.000Z")
    val occurrences = store.fetchAllEvents(range[0], range[1], listOf(calendarId.toString()))
    assertEquals(5, occurrences.size)
    assertEquals("2026-03-04T10:00:00.000Z", occurrences[2]["startDate"])
    assertEquals("2026-03-04T10:15:00.000Z", occurrences[2]["endDate"])

    assertTrue(store.removeEvent(id.toString(), mapOf("exceptionDate" to "2026-03-04T10:00:00.000Z")))
    val remaining = store.fetchAllEvents(range[0], range[1], listOf(calendarId.toString()))
    assertEquals(4, remaining.size)
    assertFalse(remaining.any { it["startDate"] == "2026-03-04T10:00:00.000Z" })
  }

  @Test
  fun simpleRecurrenceShortcutWorks() {
    val id = store.saveEvent(
      "Weekly",
      mapOf(
        "calendarId" to calendarId.toString(),
        "startDate" to "2026-03-02T10:00:00.000Z",
        "endDate" to "2026-03-02T11:00:00.000Z",
        "recurrence" to "weekly",
      ),
      emptyMap(),
    )
    assertNotNull(store.findEventById(id.toString()))
    val occurrences = store.fetchAllEvents("2026-03-01T00:00:00.000Z", "2026-03-23T00:00:00.000Z", listOf(calendarId.toString()))
    assertEquals(3, occurrences.size)
  }
}
