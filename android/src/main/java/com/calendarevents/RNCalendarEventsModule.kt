package com.calendarevents

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.modules.core.PermissionAwareActivity
import com.facebook.react.modules.core.PermissionListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Turbo Module adapter. Permission handling lives here because it needs the
 * current Activity; all CalendarContract work is delegated to the React-free
 * [CalendarEventsStore] so it can be unit tested with Robolectric.
 */
class RNCalendarEventsModule(private val reactContext: ReactApplicationContext) :
  NativeCalendarEventsSpec(reactContext), PermissionListener {

  private val store = CalendarEventsStore(reactContext)
  private val executor = Executors.newSingleThreadExecutor()
  // Touched from the JS thread (request) and the main thread (result).
  private val pendingPermissionPromises = ConcurrentHashMap<Int, Promise>()
  private val nextPermissionRequestCode = AtomicInteger(37)

  override fun invalidate() {
    executor.shutdown()
    super.invalidate()
  }

  // region Permissions

  override fun checkPermissions(readOnly: Boolean, promise: Promise) {
    val requested = reactContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      .getBoolean(permissionKey(readOnly), false)
    promise.resolve(
      when {
        hasCalendarPermissions(readOnly) -> "authorized"
        !requested -> "undetermined"
        shouldShowRequestPermissionRationale(readOnly) -> "denied"
        else -> "restricted"
      }
    )
  }

  override fun requestPermissions(readOnly: Boolean, promise: Promise) {
    reactContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
      putBoolean(permissionKey(readOnly), true)
    }

    if (hasCalendarPermissions(readOnly)) {
      promise.resolve("authorized")
      return
    }
    val activity = reactApplicationContext.currentActivity
    if (activity == null) {
      promise.reject("E_ACTIVITY_DOES_NOT_EXIST", "Activity doesn't exist")
      return
    }
    if (activity !is PermissionAwareActivity) {
      promise.reject(
        "E_ACTIVITY_NOT_PERMISSION_AWARE",
        "Activity does not implement the PermissionAwareActivity interface"
      )
      return
    }
    val requestCode = nextPermissionRequestCode.getAndIncrement()
    pendingPermissionPromises[requestCode] = promise
    val permissions =
      if (readOnly) arrayOf(Manifest.permission.READ_CALENDAR)
      else arrayOf(Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CALENDAR)
    activity.requestPermissions(permissions, requestCode, this)
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<String>,
    grantResults: IntArray
  ): Boolean {
    val promise = pendingPermissionPromises.remove(requestCode) ?: return pendingPermissionPromises.isEmpty()
    when {
      grantResults.isEmpty() -> promise.reject("E_PERMISSION_CANCELLED", "Request was cancelled")
      grantResults.all { it == PackageManager.PERMISSION_GRANTED } -> promise.resolve("authorized")
      else -> promise.resolve("denied")
    }
    return pendingPermissionPromises.isEmpty()
  }

  private fun hasCalendarPermissions(readOnly: Boolean): Boolean {
    val read = ContextCompat.checkSelfPermission(reactContext, Manifest.permission.READ_CALENDAR)
    if (readOnly) return read == PackageManager.PERMISSION_GRANTED
    val write = ContextCompat.checkSelfPermission(reactContext, Manifest.permission.WRITE_CALENDAR)
    return read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED
  }

  private fun shouldShowRequestPermissionRationale(readOnly: Boolean): Boolean {
    val activity = reactApplicationContext.currentActivity as? PermissionAwareActivity ?: return false
    return if (readOnly) {
      activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)
    } else {
      activity.shouldShowRequestPermissionRationale(Manifest.permission.WRITE_CALENDAR)
    }
  }

  private fun permissionKey(readOnly: Boolean) = if (readOnly) "permissionRequestedRead" else "permissionRequested"

  // endregion

  // region Calendars

  override fun findCalendars(promise: Promise) = run(promise, readOnly = true, "calendar request error") {
    Arguments.makeNativeArray(store.findCalendars())
  }

  override fun saveCalendar(options: ReadableMap, promise: Promise) =
    run(promise, readOnly = false, "save calendar error") {
      store.saveCalendar(options.toHashMap()).toString()
    }

  override fun removeCalendar(id: String, promise: Promise) =
    run(promise, readOnly = false, "error removing calendar") { store.removeCalendar(id) }

  // endregion

  // region Events

  override fun fetchAllEvents(
    startDate: String,
    endDate: String,
    calendarIds: ReadableArray,
    promise: Promise
  ) = run(promise, readOnly = true, "find event error") {
    val ids = calendarIds.toArrayList().map { it.toString() }
    Arguments.makeNativeArray(store.fetchAllEvents(startDate, endDate, ids))
  }

  override fun findEventById(id: String, promise: Promise) = run(promise, readOnly = true, "find event error") {
    store.findEventById(id)?.let { Arguments.makeNativeMap(it) }
  }

  override fun saveEvent(title: String, details: ReadableMap, options: ReadableMap, promise: Promise) =
    run(promise, readOnly = false, "add event error") {
      store.saveEvent(title, details.toHashMap(), options.toHashMap()).toString()
    }

  override fun removeEvent(id: String, options: ReadableMap, promise: Promise) =
    run(promise, readOnly = false, "error removing event") { store.removeEvent(id, options.toHashMap()) }

  // endregion

  // region Android-only helpers

  override fun uriForCalendar(promise: Promise) {
    promise.resolve(CalendarContract.Events.CONTENT_URI.toString())
  }

  override fun openEventInCalendar(eventId: String) {
    try {
      val id = eventId.toLong()
      val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
      val intent = Intent(Intent.ACTION_VIEW).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).setData(uri)
      // Not gated on resolveActivity: package visibility rules make it return null on
      // Android 11+ unless the app declares <queries>; the manifest declares them, and
      // ActivityNotFoundException is caught below for the rare device with no calendar app.
      reactContext.startActivity(intent)
    } catch (t: Throwable) {
      Log.e(NAME, "error opening event in calendar", t)
    }
  }

  // endregion

  /** Checks permissions, then runs [block] off the JS thread and settles the promise. */
  private fun run(promise: Promise, readOnly: Boolean, errorCode: String, block: () -> Any?) {
    if (!hasCalendarPermissions(readOnly)) {
      promise.reject(errorCode, "you don't have permissions to access the users calendar")
      return
    }
    try {
      executor.execute {
        try {
          promise.resolve(block())
        } catch (t: Throwable) {
          Log.e(NAME, "$errorCode: ${t.message}", t)
          promise.reject(errorCode, t.message, t)
        }
      }
    } catch (t: Throwable) {
      // The executor is shut down when the module is invalidated (e.g. a reload mid-call).
      promise.reject(errorCode, "calendar module is no longer available", t)
    }
  }

  companion object {
    const val NAME = NativeCalendarEventsSpec.NAME
    private const val PREFS = "REACT_NATIVE_CALENDAR_PREFERENCES"
  }
}
