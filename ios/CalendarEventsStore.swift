import CoreLocation
import EventKit
import Foundation
import UIKit

/// Pure EventKit layer with no React Native dependency.
/// The Turbo Module adapter in `RNCalendarEvents.mm` forwards every call here.
/// Keeping this class free of RN types is what makes it unit-testable with XCTest.
@objc(RNCalendarEventsStore)
public final class CalendarEventsStore: NSObject {
  public typealias Resolve = (Any?) -> Void
  public typealias Reject = (String, String, NSError?) -> Void

  /// Recreated after every permission request: an `EKEventStore` created
  /// before access was granted keeps returning stale data (issue #459).
  private(set) var eventStore: EKEventStore
  private let queue = DispatchQueue(label: "com.calendarevents.store", qos: .userInitiated)

  public override convenience init() {
    self.init(eventStore: EKEventStore())
  }

  init(eventStore: EKEventStore) {
    self.eventStore = eventStore
    super.init()
  }

  // MARK: - Permissions

  @objc public func checkPermissions() -> String {
    return Self.statusString(EKEventStore.authorizationStatus(for: .event))
  }

  @objc public func requestPermissions(_ resolve: @escaping Resolve, reject: @escaping Reject) {
    let completion: (Bool, Error?) -> Void = { [weak self] _, error in
      guard let self else { return }
      // Swap on the serial queue so no queued work observes a half-replaced store.
      self.queue.async {
        self.eventStore = EKEventStore()
        if let error = error as NSError? {
          reject("error", "authorization request error", error)
        } else {
          resolve(self.checkPermissions())
        }
      }
    }
    queue.async { [self] in
      if #available(iOS 17.0, *) {
        eventStore.requestFullAccessToEvents(completion: completion)
      } else {
        eventStore.requestAccess(to: .event, completion: completion)
      }
    }
  }

  var isAccessGranted: Bool {
    let status = EKEventStore.authorizationStatus(for: .event)
    // `.fullAccess` (iOS 17) shares the raw value of the deprecated `.authorized`.
    return status.rawValue == EKAuthorizationStatus.authorized.rawValue
  }

  /// iOS 17 write-only access (raw value 4) allows adding events but not reading them.
  var isWriteAccessGranted: Bool {
    return isAccessGranted || EKEventStore.authorizationStatus(for: .event).rawValue == 4
  }

  static func statusString(_ status: EKAuthorizationStatus) -> String {
    switch status {
    case .denied: return "denied"
    case .restricted: return "restricted"
    case .notDetermined: return "undetermined"
    default:
      // iOS 17 added `.writeOnly` (raw value 4). Comparing raw values avoids
      // availability-gated enum cases in the switch.
      return status.rawValue == 4 ? "writeOnly" : "authorized"
    }
  }

  // MARK: - Calendars

  @objc public func findCalendars(_ resolve: @escaping Resolve, reject: @escaping Reject) {
    guard isAccessGranted else { return rejectUnauthorized(reject) }
    queue.async { [self] in
      let defaultCalendar = eventStore.defaultCalendarForNewEvents
      let calendars = eventStore.calendars(for: .event).map { calendar in
        Self.serialize(calendar: calendar, isPrimary: calendar == defaultCalendar)
      }
      resolve(calendars)
    }
  }

  @objc public func saveCalendar(_ options: [String: Any], resolve: @escaping Resolve, reject: @escaping Reject) {
    guard isAccessGranted else { return rejectUnauthorized(reject) }
    queue.async { [self] in
      do {
        let calendar = try buildCalendar(options)
        try eventStore.saveCalendar(calendar, commit: true)
        resolve(calendar.calendarIdentifier)
      } catch let error as StoreError {
        reject("error", error.message, nil)
      } catch {
        let title = options["title"] as? String ?? ""
        reject("error", "Calendar \(title) could not be saved", error as NSError)
      }
    }
  }

  @objc public func removeCalendar(_ calendarId: String, resolve: @escaping Resolve, reject: @escaping Reject) {
    guard isAccessGranted else { return rejectUnauthorized(reject) }
    queue.async { [self] in
      // Resolve false rather than reject, matching Android and removeEvent.
      guard let calendar = eventStore.calendar(withIdentifier: calendarId) else { return resolve(false) }
      do {
        try eventStore.removeCalendar(calendar, commit: true)
        resolve(true)
      } catch {
        reject("error", error.localizedDescription, error as NSError)
      }
    }
  }

  func buildCalendar(_ options: [String: Any]) throws -> EKCalendar {
    let entityType: EKEntityType
    switch options["entityType"] as? String {
    case "event", nil: entityType = .event
    case "reminder": entityType = .reminder
    case let other?: throw StoreError("Calendar entityType \(other) is not supported")
    }

    // Prefer the user's iCloud account, then fall back to a local source.
    let sources = eventStore.sources
    let source =
      sources.first { $0.sourceType == .calDAV && $0.title == "iCloud" }
      ?? sources.first { $0.sourceType == .local }
      ?? sources.first { $0.sourceType == .subscribed }
    guard let source else {
      throw StoreError("no source found to create the calendar (local & icloud)")
    }

    let calendar = EKCalendar(for: entityType, eventStore: eventStore)
    calendar.source = source
    if let title = options["title"] as? String {
      calendar.title = title
    }
    if let color = Self.color(from: options["color"]) {
      calendar.cgColor = color.cgColor
    }
    return calendar
  }

  // MARK: - Events

  @objc public func fetchAllEvents(
    _ startDate: String, endDate: String, calendarIds: [String],
    resolve: @escaping Resolve, reject: @escaping Reject
  ) {
    guard isAccessGranted else { return rejectUnauthorized(reject) }
    guard let start = Self.date(from: startDate), let end = Self.date(from: endDate) else {
      return reject("error", "startDate and endDate must be ISO-8601 strings", nil)
    }
    queue.async { [self] in
      let calendars: [EKCalendar]? =
        calendarIds.isEmpty
        ? nil
        : eventStore.calendars(for: .event).filter { calendarIds.contains($0.calendarIdentifier) }
      let predicate = eventStore.predicateForEvents(withStart: start, end: end, calendars: calendars)
      let events = eventStore.events(matching: predicate)
        .sorted { $0.compareStartDate(with: $1) == .orderedAscending }
      let defaultCalendar = eventStore.defaultCalendarForNewEvents
      resolve(events.map { Self.serialize(event: $0, defaultCalendar: defaultCalendar) })
    }
  }

  @objc public func findEventById(_ eventId: String, resolve: @escaping Resolve, reject: @escaping Reject) {
    guard isAccessGranted else { return rejectUnauthorized(reject) }
    queue.async { [self] in
      if let event = eventStore.calendarItem(withIdentifier: eventId) as? EKEvent {
        resolve(Self.serialize(event: event, defaultCalendar: eventStore.defaultCalendarForNewEvents))
      } else {
        resolve(NSNull())
      }
    }
  }

  @objc public func saveEvent(
    _ title: String, details: [String: Any], options: [String: Any],
    resolve: @escaping Resolve, reject: @escaping Reject
  ) {
    guard isWriteAccessGranted else { return rejectUnauthorized(reject) }
    queue.async { [self] in
      do {
        var details = details
        details["title"] = title
        let event = try buildEvent(details, options: options)
        try eventStore.save(event, span: Self.span(for: options), commit: true)
        resolve(event.calendarItemIdentifier)
      } catch let error as StoreError {
        reject("error", error.message, nil)
      } catch {
        reject("error", error.localizedDescription, error as NSError)
      }
    }
  }

  @objc public func removeEvent(
    _ eventId: String, options: [String: Any], resolve: @escaping Resolve, reject: @escaping Reject
  ) {
    guard isAccessGranted else { return rejectUnauthorized(reject) }
    queue.async { [self] in
      let event: EKEvent?
      if let exceptionDate = Self.date(from: options["exceptionDate"]) {
        event = occurrence(of: eventId, startingAt: exceptionDate)
      } else {
        event = eventStore.calendarItem(withIdentifier: eventId) as? EKEvent
      }
      // Resolve false rather than reject, matching Android: "nothing to remove" is not an error.
      guard let event else { return resolve(false) }
      do {
        try eventStore.remove(event, span: Self.span(for: options), commit: true)
        resolve(true)
      } catch {
        reject("error", error.localizedDescription, error as NSError)
      }
    }
  }

  /// Without an `exceptionDate` the whole series is affected, matching Android.
  /// With one, `futureEvents` decides between that occurrence and all later ones (PR #385).
  static func span(for options: [String: Any]) -> EKSpan {
    // Decide on parseability, like removeEvent/buildEvent do, so a null exceptionDate means "whole series".
    if date(from: options["exceptionDate"]) == nil { return .futureEvents }
    return (options["futureEvents"] as? Bool ?? false) ? .futureEvents : .thisEvent
  }

  /// Finds the occurrence of a recurring event that starts exactly at `date`.
  private func occurrence(of eventId: String, startingAt date: Date) -> EKEvent? {
    let end = Calendar.current.date(byAdding: .day, value: 1, to: date) ?? date.addingTimeInterval(86_400)
    let predicate = eventStore.predicateForEvents(withStart: date, end: end, calendars: nil)
    return eventStore.events(matching: predicate).first {
      $0.calendarItemIdentifier == eventId && $0.startDate == date
    }
  }

  func buildEvent(_ details: [String: Any], options: [String: Any]) throws -> EKEvent {
    let event: EKEvent
    if let eventId = details["id"] as? String {
      let existing: EKEvent?
      if let exceptionDate = Self.date(from: options["exceptionDate"]) {
        existing = occurrence(of: eventId, startingAt: exceptionDate)
      } else {
        existing = eventStore.calendarItem(withIdentifier: eventId) as? EKEvent
      }
      guard let existing else { throw StoreError("No event found with id \(eventId)") }
      event = existing
    } else {
      event = EKEvent(eventStore: eventStore)
      event.calendar = eventStore.defaultCalendarForNewEvents
      event.timeZone = TimeZone.current
      if let calendarId = details["calendarId"] as? String,
        let calendar = eventStore.calendar(withIdentifier: calendarId)
      {
        event.calendar = calendar
      }
    }

    if let name = details["timeZone"] as? String {
      event.timeZone = TimeZone(identifier: name)
    }
    if let title = details["title"] as? String { event.title = title }
    if let location = details["location"] as? String { event.location = location }
    if let startDate = Self.date(from: details["startDate"]) { event.startDate = startDate }
    if let endDate = Self.date(from: details["endDate"]) { event.endDate = endDate }
    if let allDay = details["allDay"] as? Bool { event.isAllDay = allDay }
    if let notes = details["notes"] as? String { event.notes = notes }
    if let alarms = details["alarms"] as? [[String: Any]] {
      event.alarms = alarms.compactMap(Self.alarm(from:))
    }
    if let frequency = details["recurrence"] as? String,
      let rule = Self.recurrenceRule(["frequency": frequency])
    {
      event.recurrenceRules = [rule]
    }
    if let ruleOptions = details["recurrenceRule"] as? [String: Any] {
      event.recurrenceRules = Self.recurrenceRule(ruleOptions).map { [$0] }
    }
    if let availability = details["availability"] as? String {
      event.availability = Self.availability(from: availability)
    }
    if let url = details["url"] as? String {
      // Only percent-encode when the string is not already a valid URL, so encoded input stays as is.
      let parsed =
        URL(string: url)
        ?? url.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed).flatMap(URL.init(string:))
      if let parsed { event.url = parsed }
    }
    if let location = details["structuredLocation"] as? [String: Any], !location.isEmpty {
      event.structuredLocation = Self.structuredLocation(from: location)
    }
    return event
  }

  // MARK: - Alarms

  static func alarm(from options: [String: Any]) -> EKAlarm? {
    let hasDate = isPresent(options["date"])
    let hasLocation = (options["structuredLocation"] as? [String: Any])?.isEmpty == false
    guard hasDate || hasLocation else { return nil }

    let alarm: EKAlarm
    switch options["date"] {
    case let dateString as String:
      guard let date = date(from: dateString) else { return nil }  // unparseable: skip rather than ring at start
      alarm = EKAlarm(absoluteDate: date)
    case let minutes as NSNumber:
      alarm = EKAlarm(relativeOffset: minutes.doubleValue * 60)
    default:
      alarm = EKAlarm()  // location-only alarm
    }

    if let location = options["structuredLocation"] as? [String: Any], !location.isEmpty {
      alarm.structuredLocation = structuredLocation(from: location)
      switch location["proximity"] as? String {
      case "enter": alarm.proximity = .enter
      case "leave": alarm.proximity = .leave
      default: alarm.proximity = .none
      }
    }
    return alarm
  }

  static func structuredLocation(from options: [String: Any]) -> EKStructuredLocation {
    let location = EKStructuredLocation(title: options["title"] as? String ?? "")
    if let geo = options["coords"] as? [String: Any],
      let latitude = (geo["latitude"] as? NSNumber)?.doubleValue,
      let longitude = (geo["longitude"] as? NSNumber)?.doubleValue
    {
      location.geoLocation = CLLocation(latitude: latitude, longitude: longitude)
    }
    if let radius = (options["radius"] as? NSNumber)?.doubleValue {
      location.radius = radius
    }
    return location
  }

  // MARK: - Recurrence

  static func recurrenceRule(_ options: [String: Any]) -> EKRecurrenceRule? {
    guard let frequencyName = options["frequency"] as? String,
      let frequency = frequency(from: frequencyName)
    else { return nil }

    var end: EKRecurrenceEnd?
    if let endDate = date(from: options["endDate"]) {
      end = EKRecurrenceEnd(end: endDate)
    } else if let occurrence = (options["occurrence"] as? NSNumber)?.intValue, occurrence > 0 {
      end = EKRecurrenceEnd(occurrenceCount: occurrence)
    }

    let interval = max((options["interval"] as? NSNumber)?.intValue ?? 1, 1)

    let days = (options["daysOfWeek"] as? [String])?.compactMap(weekday(from:)).map { EKRecurrenceDayOfWeek($0) }

    var setPositions: [NSNumber]?
    if let position = (options["weekPositionInMonth"] as? NSNumber)?.intValue, position != 0 {
      setPositions = [NSNumber(value: position)]
    }

    return EKRecurrenceRule(
      recurrenceWith: frequency,
      interval: interval,
      daysOfTheWeek: days?.isEmpty == false ? days : nil,
      daysOfTheMonth: nil,
      monthsOfTheYear: nil,
      weeksOfTheYear: nil,
      daysOfTheYear: nil,
      setPositions: setPositions,
      end: end
    )
  }

  static func frequency(from name: String) -> EKRecurrenceFrequency? {
    switch name {
    case "daily": return .daily
    case "weekly": return .weekly
    case "monthly": return .monthly
    case "yearly": return .yearly
    default: return nil
    }
  }

  static func name(for frequency: EKRecurrenceFrequency) -> String {
    switch frequency {
    case .daily: return "daily"
    case .weekly: return "weekly"
    case .monthly: return "monthly"
    case .yearly: return "yearly"
    @unknown default: return ""
    }
  }

  static func weekday(from code: String) -> EKWeekday? {
    switch code {
    case "SU": return .sunday
    case "MO": return .monday
    case "TU": return .tuesday
    case "WE": return .wednesday
    case "TH": return .thursday
    case "FR": return .friday
    case "SA": return .saturday
    default: return nil
    }
  }

  static func code(for weekday: EKWeekday) -> String {
    switch weekday {
    case .sunday: return "SU"
    case .monday: return "MO"
    case .tuesday: return "TU"
    case .wednesday: return "WE"
    case .thursday: return "TH"
    case .friday: return "FR"
    case .saturday: return "SA"
    @unknown default: return ""
    }
  }

  // MARK: - Availability & status

  static func availability(from name: String) -> EKEventAvailability {
    switch name {
    case "busy": return .busy
    case "free": return .free
    case "tentative": return .tentative
    case "unavailable": return .unavailable
    default: return .notSupported
    }
  }

  static func name(for availability: EKEventAvailability) -> String {
    switch availability {
    case .busy: return "busy"
    case .free: return "free"
    case .tentative: return "tentative"
    case .unavailable: return "unavailable"
    case .notSupported: return "notSupported"
    @unknown default: return "notSupported"
    }
  }

  static func name(for status: EKEventStatus) -> String {
    switch status {
    case .confirmed: return "confirmed"
    case .tentative: return "tentative"
    case .canceled: return "canceled"
    case .none: return "none"
    @unknown default: return "none"
    }
  }

  static func availabilities(from mask: EKCalendarEventAvailabilityMask) -> [String] {
    var result: [String] = []
    if mask.contains(.busy) { result.append("busy") }
    if mask.contains(.free) { result.append("free") }
    if mask.contains(.tentative) { result.append("tentative") }
    if mask.contains(.unavailable) { result.append("unavailable") }
    return result
  }

  // MARK: - Serialization

  static func serialize(calendar: EKCalendar, isPrimary: Bool) -> [String: Any] {
    return [
      "id": calendar.calendarIdentifier,
      "title": calendar.title,
      "type": typeString(calendar.type),
      "source": calendar.source?.title ?? "",
      "isPrimary": isPrimary,
      "allowsModifications": calendar.allowsContentModifications,
      "allowedAvailabilities": availabilities(from: calendar.supportedEventAvailabilities),
      "color": hexString(from: calendar.cgColor),
    ]
  }

  static func serialize(event: EKEvent, defaultCalendar: EKCalendar? = nil) -> [String: Any] {
    var result: [String: Any] = [
      "id": event.calendarItemIdentifier,
      "title": event.title ?? "",
      "location": event.location ?? "",
      "startDate": event.startDate.map(isoString) ?? "",
      "endDate": event.endDate.map(isoString) ?? "",
      "allDay": event.isAllDay,
      "notes": event.notes ?? "",
      "url": event.url?.absoluteString ?? "",
      "alarms": (event.alarms ?? []).map { serialize(alarm: $0, eventStart: event.startDate) },
      "attendees": (event.attendees ?? []).map(serialize(attendee:)),
      "availability": name(for: event.availability),
      "status": name(for: event.status),
      "timeZone": event.timeZone?.identifier ?? "",
      "isDetached": event.isDetached,
    ]

    if let calendar = event.calendar {
      result["calendar"] = serialize(calendar: calendar, isPrimary: calendar == defaultCalendar)
    }
    if let occurrenceDate = event.occurrenceDate {
      result["occurrenceDate"] = isoString(occurrenceDate)
    }
    if let rule = event.recurrenceRules?.first {
      let frequency = name(for: rule.frequency)
      result["recurrence"] = frequency
      var serializedRule: [String: Any] = ["frequency": frequency, "interval": rule.interval]
      if let endDate = rule.recurrenceEnd?.endDate {
        serializedRule["endDate"] = isoString(endDate)
      }
      if let count = rule.recurrenceEnd?.occurrenceCount, count > 0 {
        serializedRule["occurrence"] = count
      }
      if let days = rule.daysOfTheWeek, !days.isEmpty {
        serializedRule["daysOfWeek"] = days.map { code(for: $0.dayOfTheWeek) }
      }
      if let position = rule.setPositions?.first?.intValue {
        serializedRule["weekPositionInMonth"] = position
      }
      result["recurrenceRule"] = serializedRule
    }
    if let location = event.structuredLocation, location.radius > 0 {
      result["structuredLocation"] = serialize(structuredLocation: location)
    }
    return result
  }

  static func serialize(alarm: EKAlarm, eventStart: Date?) -> [String: Any] {
    var result: [String: Any] = [:]
    if let absolute = alarm.absoluteDate {
      result["date"] = isoString(absolute)
    } else {
      let base = eventStart ?? Date()
      result["date"] = isoString(base.addingTimeInterval(alarm.relativeOffset))
    }
    if let location = alarm.structuredLocation {
      var serialized = serialize(structuredLocation: location)
      switch alarm.proximity {
      case .enter: serialized["proximity"] = "enter"
      case .leave: serialized["proximity"] = "leave"
      default: serialized["proximity"] = "none"
      }
      result["structuredLocation"] = serialized
    }
    return result
  }

  static func serialize(structuredLocation location: EKStructuredLocation) -> [String: Any] {
    var result: [String: Any] = [
      "title": location.title ?? "",
      "radius": location.radius,
    ]
    if let geo = location.geoLocation {
      result["coords"] = ["latitude": geo.coordinate.latitude, "longitude": geo.coordinate.longitude]
    }
    return result
  }

  static func serialize(attendee: EKParticipant) -> [String: Any] {
    let url = attendee.url
    let email = url.scheme?.lowercased() == "mailto" ? (url.absoluteString.dropFirst("mailto:".count)) : ""
    let phone = url.scheme?.lowercased() == "tel" ? (url.absoluteString.dropFirst("tel:".count)) : ""
    return [
      "name": attendee.name ?? "",
      "email": String(email),
      "phone": String(phone),
    ]
  }

  static func typeString(_ type: EKCalendarType) -> String {
    switch type {
    case .local: return "local"
    case .calDAV: return "calDAV"
    case .exchange: return "exchange"
    case .subscription: return "subscription"
    case .birthday: return "birthday"
    @unknown default: return "unknown"
    }
  }

  /// JS `null` arrives as NSNull, which is not `nil`.
  static func isPresent(_ value: Any?) -> Bool {
    return value != nil && !(value is NSNull)
  }

  // MARK: - Dates

  private static let isoWithFraction: ISO8601DateFormatter = {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    return formatter
  }()

  private static let isoWithoutFraction: ISO8601DateFormatter = {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime]
    return formatter
  }()

  /// Accepts ISO-8601 strings (with or without fractional seconds) and
  /// millisecond epoch numbers, matching what the v2 bridge accepted.
  static func date(from value: Any?) -> Date? {
    switch value {
    case let string as String:
      return isoWithFraction.date(from: string) ?? isoWithoutFraction.date(from: string)
    case let number as NSNumber:
      return Date(timeIntervalSince1970: number.doubleValue / 1000)
    default:
      return nil
    }
  }

  /// Always UTC with milliseconds, e.g. `2026-01-01T10:00:00.000Z`.
  static func isoString(_ date: Date) -> String {
    return isoWithFraction.string(from: date)
  }

  // MARK: - Colors

  /// Decodes the ARGB integer produced by React Native's `processColor`.
  static func color(from value: Any?) -> UIColor? {
    guard let number = value as? NSNumber else { return nil }
    let argb = UInt32(truncatingIfNeeded: number.int64Value)
    return UIColor(
      red: CGFloat((argb >> 16) & 0xff) / 255,
      green: CGFloat((argb >> 8) & 0xff) / 255,
      blue: CGFloat(argb & 0xff) / 255,
      alpha: CGFloat((argb >> 24) & 0xff) / 255
    )
  }

  static func hexString(from color: CGColor?) -> String {
    guard let color else { return "#FFFFFF" }
    // Convert to sRGB first: calendar colors may be grayscale, P3 or extended range.
    let srgb =
      CGColorSpace(name: CGColorSpace.sRGB).flatMap { color.converted(to: $0, intent: .defaultIntent, options: nil) }
      ?? color
    guard let components = srgb.components, components.count >= 3 else { return "#FFFFFF" }
    func channel(_ value: CGFloat) -> Int { Int((min(max(value, 0), 1) * 255).rounded()) }
    return String(format: "#%02X%02X%02X", channel(components[0]), channel(components[1]), channel(components[2]))
  }

  // MARK: - Errors

  struct StoreError: Error {
    let message: String
    init(_ message: String) { self.message = message }
  }

  private func rejectUnauthorized(_ reject: Reject) {
    reject("error", "unauthorized to access calendar", nil)
  }
}
