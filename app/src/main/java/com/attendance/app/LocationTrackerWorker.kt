package com.attendance.app

import android.content.Context
import android.location.Location
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDate
import java.time.LocalTime

class LocationTrackerWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val gson = Gson()

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)
        val targetLat = prefs.getFloat("work_lat", 0f).toDouble()
        val targetLng = prefs.getFloat("work_lng", 0f).toDouble()
        val radius = prefs.getFloat("work_radius", 150f)

        // Exit if workplace GPS is not configured yet
        if (targetLat == 0.0 && targetLng == 0.0) return Result.success()

        val fusedClient = LocationServices.getFusedLocationProviderClient(applicationContext)
        val location: Location? = try {
            Tasks.await(fusedClient.lastLocation)
        } catch (e: Exception) {
            null
        }

        val isInside = if (location != null) {
            val results = FloatArray(1)
            Location.distanceBetween(location.latitude, location.longitude, targetLat, targetLng, results)
            results[0] <= radius
        } else {
            false
        }

        evaluateAndPersistAttendance(isInside, prefs)
        return Result.success()
    }

    private fun evaluateAndPersistAttendance(isInside: Boolean, trackerPrefs: android.content.SharedPreferences) {
        val now = LocalTime.now()
        val today = LocalDate.now().toString()
        val dayNum = LocalDate.now().dayOfMonth

        val h1Start = LocalTime.of(10, 0)
        val splitTime = LocalTime.of(15, 0)
        val h2End = LocalTime.of(21, 0)

        // Record presence logs during shifts
        if (isInside) {
            if (now.isAfter(h1Start) && now.isBefore(splitTime)) {
                trackerPrefs.edit().putBoolean("${today}_h1", true).apply()
            }
            if (now.isAfter(splitTime) && now.isBefore(h2End)) {
                trackerPrefs.edit().putBoolean("${today}_h2", true).apply()
            }
        }

        val wasInFirstHalf = trackerPrefs.getBoolean("${today}_h1", false)
        val wasInSecondHalf = trackerPrefs.getBoolean("${today}_h2", false)

        // Determine status
        val calculatedStatus = when {
            // After 9:00 PM -> Final Evaluation
            now.isAfter(h2End) -> {
                when {
                    wasInFirstHalf && wasInSecondHalf -> AttendanceStatus.PRESENT
                    wasInFirstHalf || wasInSecondHalf -> AttendanceStatus.HALF_DAY
                    else -> AttendanceStatus.ABSENT
                }
            }
            // During shift progress
            wasInFirstHalf && isInside && now.isAfter(splitTime) -> AttendanceStatus.PRESENT
            wasInFirstHalf || (isInside && now.isAfter(h1Start)) -> AttendanceStatus.HALF_DAY
            else -> AttendanceStatus.NOT_MARKED
        }

        // Save directly to main attendance database
        val dbPrefs = applicationContext.getSharedPreferences("attendance_db", Context.MODE_PRIVATE)
        val json = dbPrefs.getString("records", null)
        val type = object : TypeToken<MutableMap<String, DayAttendance>>() {}.type
        val map: MutableMap<String, DayAttendance> = if (!json.isNullOrEmpty()) {
            gson.fromJson(json, type)
        } else {
            mutableMapOf()
        }

        val existingDay = map[today] ?: DayAttendance(dateString = today, dayOfMonth = dayNum)

        // Don't overwrite if manually edited
        if (!existingDay.isManual && calculatedStatus != AttendanceStatus.NOT_MARKED) {
            map[today] = existingDay.copy(workStatus = calculatedStatus)
            dbPrefs.edit().putString("records", gson.toJson(map)).apply()
        }
    }
}
