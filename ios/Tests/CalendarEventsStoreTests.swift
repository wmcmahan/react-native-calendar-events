import EventKit
import XCTest

@testable import RNCalendarEvents

/// Pure-logic tests: no EventKit store access needed.
final class CalendarEventsStoreLogicTests: XCTestCase {
  func testStatusStrings() {
    XCTAssertEqual(CalendarEventsStore.statusString(.denied), "denied")
    XCTAssertEqual(CalendarEventsStore.statusString(.restricted), "restricted")
    XCTAssertEqual(CalendarEventsStore.statusString(.notDetermined), "undetermined")
    XCTAssertEqual(CalendarEventsStore.statusString(.authorized), "authorized")
    if #available(iOS 17.0, *) {
      XCTAssertEqual(CalendarEventsStore.statusString(.fullAccess), "authorized")
      XCTAssertEqual(CalendarEventsStore.statusString(.writeOnly), "writeOnly")
    }
  }

  func testDateParsingAcceptsIsoWithAndWithoutFractionAndEpochMillis() {
    let expected = Date(timeIntervalSince1970: 1_767_261_600)  // 2026-01-01T10:00:00Z
    XCTAssertEqual(CalendarEventsStore.date(from: "2026-01-01T10:00:00.000Z"), expected)
    XCTAssertEqual(CalendarEventsStore.date(from: "2026-01-01T10:00:00Z"), expected)
    XCTAssertEqual(CalendarEventsStore.date(from: NSNumber(value: 1_767_261_600_000)), expected)
    XCTAssertNil(CalendarEventsStore.date(from: "not a date"))
    XCTAssertNil(CalendarEventsStore.date(from: nil))
  }

  func testIsoStringIsUtcWithMilliseconds() {
    let date = Date(timeIntervalSince1970: 1_767_261_600.5)
    XCTAssertEqual(CalendarEventsStore.isoString(date), "2026-01-01T10:00:00.500Z")
  }

  func testColorRoundTrip() {
    // processColor('#ff0000') -> 0xffff0000
    let color = CalendarEventsStore.color(from: NSNumber(value: 0xffff0000 as UInt32))
    XCTAssertEqual(CalendarEventsStore.hexString(from: color?.cgColor), "#FF0000")
    XCTAssertEqual(CalendarEventsStore.hexString(from: nil), "#FFFFFF")
    XCTAssertNil(CalendarEventsStore.color(from: "#ff0000"))
  }

  func testRecurrenceRuleFromSimpleFrequency() throws {
    let rule = try XCTUnwrap(CalendarEventsStore.recurrenceRule(["frequency": "weekly"]))
    XCTAssertEqual(rule.frequency, .weekly)
    XCTAssertEqual(rule.interval, 1)
    XCTAssertNil(rule.recurrenceEnd)
  }

  func testRecurrenceRuleWithIntervalDaysAndOccurrenceCount() throws {
    let rule = try XCTUnwrap(
      CalendarEventsStore.recurrenceRule([
        "frequency": "weekly",
        "interval": 2,
        "occurrence": 5,
        "daysOfWeek": ["MO", "WE"],
      ])
    )
    XCTAssertEqual(rule.interval, 2)
    XCTAssertEqual(rule.recurrenceEnd?.occurrenceCount, 5)
    XCTAssertEqual(rule.daysOfTheWeek?.map(\.dayOfTheWeek), [.monday, .wednesday])
  }

  func testRecurrenceRuleEndDateOverridesOccurrence() throws {
    let rule = try XCTUnwrap(
      CalendarEventsStore.recurrenceRule([
        "frequency": "daily",
        "occurrence": 5,
        "endDate": "2026-02-01T00:00:00.000Z",
      ])
    )
    XCTAssertEqual(rule.recurrenceEnd?.endDate, CalendarEventsStore.date(from: "2026-02-01T00:00:00.000Z"))
    XCTAssertEqual(rule.recurrenceEnd?.occurrenceCount, 0)
  }

  func testRecurrenceRuleRejectsUnknownFrequency() {
    XCTAssertNil(CalendarEventsStore.recurrenceRule(["frequency": "hourly"]))
    XCTAssertNil(CalendarEventsStore.recurrenceRule([:]))
  }

  func testAlarmFromRelativeMinutes() throws {
    let alarm = try XCTUnwrap(CalendarEventsStore.alarm(from: ["date": -15]))
    XCTAssertEqual(alarm.relativeOffset, -900)
    XCTAssertNil(alarm.absoluteDate)
  }

  func testAlarmFromAbsoluteDate() throws {
    let alarm = try XCTUnwrap(CalendarEventsStore.alarm(from: ["date": "2026-01-01T09:45:00.000Z"]))
    XCTAssertEqual(alarm.absoluteDate, CalendarEventsStore.date(from: "2026-01-01T09:45:00.000Z"))
  }

  func testAlarmWithGeofence() throws {
    let alarm = try XCTUnwrap(
      CalendarEventsStore.alarm(from: [
        "structuredLocation": [
          "title": "Office",
          "proximity": "enter",
          "radius": 100,
          "coords": ["latitude": 37.33, "longitude": -122.03],
        ]
      ])
    )
    XCTAssertEqual(alarm.proximity, .enter)
    XCTAssertEqual(alarm.structuredLocation?.title, "Office")
    XCTAssertEqual(alarm.structuredLocation?.radius, 100)
    XCTAssertEqual(alarm.structuredLocation?.geoLocation?.coordinate.latitude ?? 0, 37.33, accuracy: 0.0001)
  }

  func testEmptyAlarmIsIgnored() {
    XCTAssertNil(CalendarEventsStore.alarm(from: [:]))
  }

  func testUnparseableAlarmDateIsIgnored() {
    XCTAssertNil(CalendarEventsStore.alarm(from: ["date": "tomorrow"]))
  }

  func testNullValuesFromJavaScriptAreTreatedAsAbsent() {
    // JS null arrives as NSNull.
    XCTAssertNil(CalendarEventsStore.alarm(from: ["date": NSNull()]))
    XCTAssertEqual(CalendarEventsStore.span(for: ["exceptionDate": NSNull()]), .futureEvents)
    XCTAssertEqual(CalendarEventsStore.span(for: ["exceptionDate": NSNull(), "futureEvents": true]), .futureEvents)
  }

  func testColorConversionHandlesGrayscaleAndClamping() {
    XCTAssertEqual(CalendarEventsStore.hexString(from: UIColor(white: 0.5, alpha: 1).cgColor), "#808080")
    XCTAssertEqual(
      CalendarEventsStore.hexString(from: UIColor(red: 2, green: -1, blue: 0.5, alpha: 1).cgColor), "#FF0080")
  }

  func testSpanDefaults() {
    // No exceptionDate: the whole series, for saves and removals alike.
    XCTAssertEqual(CalendarEventsStore.span(for: [:]), .futureEvents)
    XCTAssertEqual(CalendarEventsStore.span(for: ["futureEvents": false]), .futureEvents)
    // With an exceptionDate, futureEvents decides (PR #385).
    let exception: [String: Any] = ["exceptionDate": "2026-01-08T10:00:00.000Z"]
    XCTAssertEqual(CalendarEventsStore.span(for: exception), .thisEvent)
    XCTAssertEqual(CalendarEventsStore.span(for: exception.merging(["futureEvents": true]) { $1 }), .futureEvents)
  }

  func testRecurrenceRuleLastWeekOfMonth() throws {
    let rule = try XCTUnwrap(
      CalendarEventsStore.recurrenceRule(["frequency": "monthly", "daysOfWeek": ["FR"], "weekPositionInMonth": -1])
    )
    XCTAssertEqual(rule.setPositions?.map(\.intValue), [-1])
    XCTAssertEqual(rule.daysOfTheWeek?.map(\.dayOfTheWeek), [.friday])
  }

  func testAvailabilityAndStatusNames() {
    XCTAssertEqual(CalendarEventsStore.availability(from: "busy"), .busy)
    XCTAssertEqual(CalendarEventsStore.availability(from: "bogus"), .notSupported)
    XCTAssertEqual(CalendarEventsStore.name(for: EKEventAvailability.tentative), "tentative")
    XCTAssertEqual(CalendarEventsStore.name(for: EKEventStatus.canceled), "canceled")
  }
}

/// Integration tests against a real `EKEventStore` on the simulator.
/// They need calendar access, which CI grants with
/// `xcrun simctl privacy <device> grant calendar <app host bundle id>`.
/// Without access they are skipped rather than failed.
final class CalendarEventsStoreIntegrationTests: XCTestCase {
  private var store: CalendarEventsStore!
  private var calendarId: String!

  override func setUpWithError() throws {
    try super.setUpWithError()
    store = CalendarEventsStore()
    try XCTSkipUnless(store.isAccessGranted, "Calendar access not granted to the test host")

    let calendar = try store.buildCalendar(["title": "RNCalendarEvents Tests", "entityType": "event"])
    try store.eventStore.saveCalendar(calendar, commit: true)
    calendarId = calendar.calendarIdentifier
  }

  override func tearDownWithError() throws {
    if let calendarId, let calendar = store.eventStore.calendar(withIdentifier: calendarId) {
      try store.eventStore.removeCalendar(calendar, commit: true)
    }
    try super.tearDownWithError()
  }

  private func waitFor<T>(
    _ label: String, _ body: (@escaping (T) -> Void, @escaping CalendarEventsStore.Reject) -> Void
  ) throws -> T {
    let expectation = expectation(description: label)
    var value: T?
    var failure: String?
    body(
      { result in
        value = result; expectation.fulfill()
      },
      { _, message, _ in
        failure = message; expectation.fulfill()
      })
    wait(for: [expectation], timeout: 10)
    if let failure { throw CalendarEventsStore.StoreError(failure) }
    return try XCTUnwrap(value)
  }

  private func resolve<T>(
    _ label: String, _ body: (@escaping CalendarEventsStore.Resolve, @escaping CalendarEventsStore.Reject) -> Void
  ) throws -> T {
    let any: Any? = try waitFor(label) { resolve, reject in body({ resolve($0) }, reject) }
    return try XCTUnwrap(any as? T, "\(label) resolved with unexpected type \(String(describing: any))")
  }

  func testFindCalendarsIncludesTestCalendar() throws {
    let calendars: [[String: Any]] = try resolve("findCalendars") { store.findCalendars($0, reject: $1) }
    let found = calendars.first { $0["id"] as? String == calendarId }
    XCTAssertEqual(found?["title"] as? String, "RNCalendarEvents Tests")
    XCTAssertEqual(found?["allowsModifications"] as? Bool, true)
    XCTAssertNotNil(found?["color"] as? String)
  }

  func testSaveFindUpdateAndRemoveEvent() throws {
    let details: [String: Any] = [
      "calendarId": calendarId!,
      "startDate": "2026-03-02T10:00:00.000Z",
      "endDate": "2026-03-02T11:00:00.000Z",
      "notes": "bring sunglasses",
      "location": "Los Angeles, CA",
      "url": "https://example.com/meet",
      "alarms": [["date": -10]],
      "availability": "busy",
    ]
    let eventId: String = try resolve("saveEvent") {
      store.saveEvent("Lunch", details: details, options: [:], resolve: $0, reject: $1)
    }

    let event: [String: Any] = try resolve("findEventById") { store.findEventById(eventId, resolve: $0, reject: $1) }
    XCTAssertEqual(event["title"] as? String, "Lunch")
    XCTAssertEqual(event["startDate"] as? String, "2026-03-02T10:00:00.000Z")
    XCTAssertEqual(event["endDate"] as? String, "2026-03-02T11:00:00.000Z")
    XCTAssertEqual(event["notes"] as? String, "bring sunglasses")
    XCTAssertEqual(event["location"] as? String, "Los Angeles, CA")
    XCTAssertEqual(event["url"] as? String, "https://example.com/meet")
    // Local (simulator) calendars report no supported availabilities, in which
    // case EventKit stores `notSupported` regardless of what was requested.
    let supported = (event["calendar"] as? [String: Any])?["allowedAvailabilities"] as? [String] ?? []
    XCTAssertEqual(event["availability"] as? String, supported.contains("busy") ? "busy" : "notSupported")
    XCTAssertEqual(event["allDay"] as? Bool, false)
    XCTAssertEqual((event["calendar"] as? [String: Any])?["id"] as? String, calendarId)
    XCTAssertNil(event["recurrence"], "non-recurring events carry no recurrence keys")
    XCTAssertNil(event["recurrenceRule"])
    let alarms = try XCTUnwrap(event["alarms"] as? [[String: Any]])
    XCTAssertEqual(alarms.first?["date"] as? String, "2026-03-02T09:50:00.000Z")

    // Update by id.
    let updatedId: String = try resolve("saveEvent update") {
      store.saveEvent("Dinner", details: ["id": eventId, "allDay": true], options: [:], resolve: $0, reject: $1)
    }
    XCTAssertEqual(updatedId, eventId)
    let updated: [String: Any] = try resolve("findEventById after update") {
      store.findEventById(eventId, resolve: $0, reject: $1)
    }
    XCTAssertEqual(updated["title"] as? String, "Dinner")
    XCTAssertEqual(updated["allDay"] as? Bool, true)

    // Listed by date range, restricted to the test calendar.
    let listed: [[String: Any]] = try resolve("fetchAllEvents") {
      store.fetchAllEvents(
        "2026-03-01T00:00:00.000Z", endDate: "2026-03-03T00:00:00.000Z", calendarIds: [calendarId], resolve: $0,
        reject: $1)
    }
    XCTAssertEqual(listed.map { $0["id"] as? String }, [eventId])

    let removed: Bool = try resolve("removeEvent") { store.removeEvent(eventId, options: [:], resolve: $0, reject: $1) }
    XCTAssertTrue(removed)
    let gone: Any? = try waitFor("findEventById after remove") { resolve, reject in
      store.findEventById(eventId, resolve: { resolve($0) }, reject: reject)
    }
    XCTAssertTrue(gone is NSNull)
  }

  func testRecurringEventRoundTripAndSingleOccurrenceRemoval() throws {
    let details: [String: Any] = [
      "calendarId": calendarId!,
      "startDate": "2026-03-02T10:00:00.000Z",
      "endDate": "2026-03-02T11:00:00.000Z",
      "recurrenceRule": ["frequency": "daily", "interval": 1, "occurrence": 5],
    ]
    let eventId: String = try resolve("saveEvent recurring") {
      store.saveEvent("Standup", details: details, options: [:], resolve: $0, reject: $1)
    }

    let occurrences: [[String: Any]] = try resolve("fetchAllEvents recurring") {
      store.fetchAllEvents(
        "2026-03-01T00:00:00.000Z", endDate: "2026-03-10T00:00:00.000Z", calendarIds: [calendarId], resolve: $0,
        reject: $1)
    }
    XCTAssertEqual(occurrences.count, 5)
    let rule = try XCTUnwrap(occurrences.first?["recurrenceRule"] as? [String: Any])
    XCTAssertEqual(rule["frequency"] as? String, "daily")
    XCTAssertEqual(rule["occurrence"] as? Int, 5)
    XCTAssertEqual(occurrences.first?["recurrence"] as? String, "daily")

    // Remove only the third occurrence.
    let removed: Bool = try resolve("removeEvent occurrence") {
      store.removeEvent(eventId, options: ["exceptionDate": "2026-03-04T10:00:00.000Z"], resolve: $0, reject: $1)
    }
    XCTAssertTrue(removed)
    let remaining: [[String: Any]] = try resolve("fetchAllEvents after occurrence removal") {
      store.fetchAllEvents(
        "2026-03-01T00:00:00.000Z", endDate: "2026-03-10T00:00:00.000Z", calendarIds: [calendarId], resolve: $0,
        reject: $1)
    }
    XCTAssertEqual(remaining.count, 4)
    XCTAssertFalse(remaining.contains { $0["startDate"] as? String == "2026-03-04T10:00:00.000Z" })
  }

  func testRemovingARecurringEventWithoutOptionsRemovesTheWholeSeries() throws {
    let details: [String: Any] = [
      "calendarId": calendarId!,
      "startDate": "2026-03-02T10:00:00.000Z",
      "endDate": "2026-03-02T11:00:00.000Z",
      "recurrence": "daily",
    ]
    let eventId: String = try resolve("saveEvent recurring") {
      store.saveEvent("Daily", details: details, options: [:], resolve: $0, reject: $1)
    }
    let removed: Bool = try resolve("removeEvent series") {
      store.removeEvent(eventId, options: [:], resolve: $0, reject: $1)
    }
    XCTAssertTrue(removed)
    let remaining: [[String: Any]] = try resolve("fetchAllEvents after series removal") {
      store.fetchAllEvents(
        "2026-03-01T00:00:00.000Z", endDate: "2026-03-20T00:00:00.000Z", calendarIds: [calendarId], resolve: $0,
        reject: $1)
    }
    XCTAssertEqual(remaining.count, 0)
  }

  func testFindEventByIdResolvesNullWhenMissing() throws {
    let result: Any? = try waitFor("findEventById missing") { resolve, reject in
      store.findEventById("does-not-exist", resolve: { resolve($0) }, reject: reject)
    }
    XCTAssertTrue(result is NSNull)
  }

  func testRemoveMissingEventResolvesFalse() throws {
    let removed: Bool = try resolve("removeEvent missing") {
      store.removeEvent("does-not-exist", options: [:], resolve: $0, reject: $1)
    }
    XCTAssertFalse(removed)
  }

  func testSaveAndRemoveCalendarThroughPublicApi() throws {
    let id: String = try resolve("saveCalendar") {
      store.saveCalendar(["title": "Temp", "color": NSNumber(value: 0xff00ff00 as UInt32)], resolve: $0, reject: $1)
    }
    let calendars: [[String: Any]] = try resolve("findCalendars") { store.findCalendars($0, reject: $1) }
    let temp = calendars.first { $0["id"] as? String == id }
    XCTAssertEqual(temp?["title"] as? String, "Temp")
    XCTAssertEqual(temp?["color"] as? String, "#00FF00")
    let removed: Bool = try resolve("removeCalendar") { store.removeCalendar(id, resolve: $0, reject: $1) }
    XCTAssertTrue(removed)
  }

  func testRemovingAMissingCalendarResolvesFalse() throws {
    let removed: Bool = try resolve("removeCalendar missing") {
      store.removeCalendar("does-not-exist", resolve: $0, reject: $1)
    }
    XCTAssertFalse(removed)
  }

  func testUrlsAreNotDoubleEncoded() throws {
    let details: [String: Any] = [
      "calendarId": calendarId!,
      "startDate": "2026-03-02T10:00:00.000Z",
      "endDate": "2026-03-02T11:00:00.000Z",
      "url": "https://example.com/a%20b?q=1",
    ]
    let eventId: String = try resolve("saveEvent url") {
      store.saveEvent("Link", details: details, options: [:], resolve: $0, reject: $1)
    }
    let event: [String: Any] = try resolve("findEventById url") {
      store.findEventById(eventId, resolve: $0, reject: $1)
    }
    XCTAssertEqual(event["url"] as? String, "https://example.com/a%20b?q=1")
    // The embedded calendar reports whether it is the default one, like findCalendars does.
    let isDefault = store.eventStore.defaultCalendarForNewEvents?.calendarIdentifier == calendarId
    XCTAssertEqual((event["calendar"] as? [String: Any])?["isPrimary"] as? Bool, isDefault)
  }

  func testUnsupportedEntityTypeRejects() {
    let expectation = expectation(description: "saveCalendar bad type")
    store.saveCalendar(["title": "x", "entityType": "todo"], resolve: { _ in XCTFail("should reject") }) {
      _, message, _ in
      XCTAssertEqual(message, "Calendar entityType todo is not supported")
      expectation.fulfill()
    }
    wait(for: [expectation], timeout: 5)
  }
}
