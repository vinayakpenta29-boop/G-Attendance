package com.attendance.app

import android.content.Context
import android.location.Location
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
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

        if (targetLat == 0.0 && targetLng == 0.0) return Result.success()

        val fusedClient = LocationServices.getFusedLocationProviderClient(applicationContext)

        // Force fresh GPS acquisition rather than relying on null/stale cache
        val location: Location? = try {
            val cts = CancellationTokenSource()
            Tasks.await(fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token))
                ?: Tasks.await(fusedClient.lastLocation)
        } catch (e: Exception) {
            try {
                Tasks.await(fusedClient.lastLocation)
            } catch (ex: Exception) {
                null
            }
        }

        if (location == null) return Result.success()

        val results = FloatArray(1)
        Location.distanceBetween(location.latitude, location.longitude, targetLat, targetLng, results)

        // Include GPS accuracy tolerance (up to 50m) to prevent indoor drift false-negatives
        val accuracyTolerance = location.accuracy.coerceAtMost(50f)
        val isInside = results[0] <= (radius + accuracyTolerance)

        evaluateAndPersistAttendance(isInside, prefs)
        return Result.success()
    }

    private fun evaluateAndPersistAttendance(isInside: Boolean, trackerPrefs: android.content.SharedPreferences) {
        val now = LocalTime.now()
        val today = LocalDate.now().toString()
        val dayNum = LocalDate.now().dayOfMonth

        val h1Start = LocalTime.of(10, 30)
        val splitTime = LocalTime.of(15, 0)
        val h2End = LocalTime.of(21, 0)
        val postWorkFinalizeTime = LocalTime.of(21, 30) // Grace period up to 9:20 PM

        if (isInside) {
            // First Half: 10:00 AM to 3:00 PM
            if (now.isAfter(h1Start) && now.isBefore(splitTime)) {
                trackerPrefs.edit().putBoolean("${today}_h1", true).apply()
            }
            // Second Half: 3:00 PM to 9:20 PM
            if (now.isAfter(splitTime) && now.isBefore(postWorkFinalizeTime)) {
                trackerPrefs.edit().putBoolean("${today}_h2", true).apply()
            }
        }

        val wasInFirstHalf = trackerPrefs.getBoolean("${today}_h1", false)
        val wasInSecondHalf = trackerPrefs.getBoolean("${today}_h2", false)

        val calculatedStatus = when {
            // Final decision after shift ends (after 9:20 PM)
            now.isAfter(postWorkFinalizeTime) -> {
                when {
                    wasInFirstHalf && wasInSecondHalf -> AttendanceStatus.PRESENT
                    wasInFirstHalf || wasInSecondHalf -> AttendanceStatus.HALF_DAY
                    else -> AttendanceStatus.ABSENT
                }
            }
            // Live status during work hours
            wasInFirstHalf && wasInSecondHalf -> AttendanceStatus.PRESENT
            wasInFirstHalf && isInside && now.isAfter(splitTime) -> AttendanceStatus.PRESENT
            wasInFirstHalf || wasInSecondHalf -> AttendanceStatus.HALF_DAY
            isInside && now.isAfter(h1Start) -> AttendanceStatus.HALF_DAY
            else -> AttendanceStatus.NOT_MARKED
        }

        try {
            val dbPrefs = applicationContext.getSharedPreferences("attendance_db", Context.MODE_PRIVATE)
            val json = dbPrefs.getString("records", null)
            val type = object : TypeToken<MutableMap<String, DayAttendance>>() {}.type
            val map: MutableMap<String, DayAttendance> = if (!json.isNullOrEmpty()) {
                gson.fromJson(json, type) ?: mutableMapOf()
            } else {
                mutableMapOf()
            }

            val existingDay = map[today] ?: DayAttendance(dateString = today, dayOfMonth = dayNum)

            // Never overwrite manually edited days
            if (!existingDay.isManual && calculatedStatus != AttendanceStatus.NOT_MARKED) {
                val autoDabba = if (calculatedStatus == AttendanceStatus.ABSENT || calculatedStatus == AttendanceStatus.HALF_DAY) {
                    DabbaStatus.ABSENT
                } else {
                    existingDay.dabbaStatus ?: DabbaStatus.NOT_MARKED
                }

                map[today] = existingDay.copy(
                    workStatus = calculatedStatus,
                    dabbaStatus = autoDabba,
                    holidayName = existingDay.holidayName ?: "",
                    amavasyaName = existingDay.amavasyaName ?: "",
                    salesAmount = existingDay.salesAmount,
                    salesBills = existingDay.salesBills ?: ""
                )
                dbPrefs.edit().putString("records", gson.toJson(map)).apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
