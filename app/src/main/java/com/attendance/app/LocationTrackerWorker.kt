package com.attendance.app

import android.content.Context
import android.location.Location
import androidx.work.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Tasks
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class LocationTrackerWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val gson = Gson()

    companion object {
        const val WORK_NAME = "location_tracker_work"

        fun scheduleNext(context: Context) {
            val now = LocalDateTime.now()
            val time = now.toLocalTime()

            val t1000 = LocalTime.of(10, 0)
            val t1030 = LocalTime.of(10, 30)
            val t1500 = LocalTime.of(15, 0)
            val t2100 = LocalTime.of(21, 0)

            val nextRunTime: LocalDateTime = when {
                // Before 10:00 AM -> wait until 10:00 AM today
                time.isBefore(t1000) -> now.withHour(10).withMinute(0).withSecond(0).withNano(0)

                // 10:00 AM - 10:30 AM -> track every 1 minute for Dabba punch
                time.isBefore(t1030) -> now.plusMinutes(1).withSecond(0).withNano(0)

                // 10:30 AM - 3:00 PM -> track every 30 minutes; snap to 3:00:05 PM for 1st half evaluation
                time.isBefore(t1500) -> {
                    val candidate = now.plusMinutes(30)
                    if (candidate.toLocalTime().isAfter(t1500)) {
                        now.withHour(15).withMinute(0).withSecond(5).withNano(0)
                    } else {
                        candidate
                    }
                }

                // 3:00 PM - 9:00 PM -> track every 30 minutes; snap to 9:00:05 PM for 2nd half evaluation
                time.isBefore(t2100) -> {
                    val candidate = now.plusMinutes(30)
                    if (candidate.toLocalTime().isAfter(t2100)) {
                        now.withHour(21).withMinute(0).withSecond(5).withNano(0)
                    } else {
                        candidate
                    }
                }

                // After 9:00 PM -> sleep until 10:00 AM tomorrow
                else -> now.plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0)
            }

            val delayMillis = Duration.between(now, nextRunTime).toMillis().coerceAtLeast(1000L)

            val request = OneTimeWorkRequestBuilder<LocationTrackerWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        try {
            checkLocationAndUpdateAttendance()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // Automatically schedule the next run based on time of day
            scheduleNext(applicationContext)
        }
        return Result.success()
    }

    private fun checkLocationAndUpdateAttendance() {
        val prefs = applicationContext.getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)
        val targetLat = prefs.getFloat("work_lat", 0f).toDouble()
        val targetLng = prefs.getFloat("work_lng", 0f).toDouble()
        val radius = prefs.getFloat("work_radius", 150f)

        if (targetLat == 0.0 && targetLng == 0.0) return

        val fusedClient = LocationServices.getFusedLocationProviderClient(applicationContext)

        val location: Location? = try {
            val cts = CancellationTokenSource()
            Tasks.await(fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token))
                ?: Tasks.await(fusedClient.lastLocation)
        } catch (e: Exception) {
            null
        }

        val isInside = if (location != null) {
            val results = FloatArray(1)
            Location.distanceBetween(location.latitude, location.longitude, targetLat, targetLng, results)
            val tolerance = location.accuracy.coerceAtMost(50f)
            results[0] <= (radius + tolerance)
        } else {
            false
        }

        evaluateAttendance(isInside, prefs)
    }

    private fun evaluateAttendance(isInside: Boolean, trackerPrefs: android.content.SharedPreferences) {
        val now = LocalTime.now()
        val today = LocalDate.now().toString()
        val dayNum = LocalDate.now().dayOfMonth

        val t1000 = LocalTime.of(10, 0)
        val t1015 = LocalTime.of(10, 15, 59)
        val t1500 = LocalTime.of(15, 0)
        val t2100 = LocalTime.of(21, 0)

        // 1. Evaluate Dabba Attendance (10:00 AM - 10:30 AM and arrival tracking)
        val currentDabbaPref = trackerPrefs.getString("${today}_dabba", null)
        if (isInside) {
            if (now.isAfter(t1000) && !now.isAfter(t1015)) {
                // Reached on or before 10:15 AM -> "P" Present
                trackerPrefs.edit().putString("${today}_dabba", "P").apply()
            } else if (now.isAfter(t1015) && currentDabbaPref != "P") {
                // Reached after 10:15 AM (10:16 AM onwards) -> "L" Late
                trackerPrefs.edit().putString("${today}_dabba", "L").apply()
            }
        }

        // 2. Track presence across halves
        if (isInside) {
            if (now.isAfter(t1000) && now.isBefore(t1500)) {
                trackerPrefs.edit().putBoolean("${today}_h1", true).apply()
            }
            if (now.isAfter(t1500) && now.isBefore(t2100)) {
                trackerPrefs.edit().putBoolean("${today}_h2", true).apply()
            }
        }

        val wasInFirstHalf = trackerPrefs.getBoolean("${today}_h1", false)
        val wasInSecondHalf = trackerPrefs.getBoolean("${today}_h2", false)
        val recordedDabba = trackerPrefs.getString("${today}_dabba", null)

        // 3. Auto Attendance Evaluation
        val dbPrefs = applicationContext.getSharedPreferences("attendance_db", Context.MODE_PRIVATE)
        val json = dbPrefs.getString("records", null)
        val type = object : TypeToken<MutableMap<String, DayAttendance>>() {}.type
        val map: MutableMap<String, DayAttendance> = if (!json.isNullOrEmpty()) {
            gson.fromJson(json, type) ?: mutableMapOf()
        } else {
            mutableMapOf()
        }

        val existingDay = map[today] ?: DayAttendance(dateString = today, dayOfMonth = dayNum)

        // Do not overwrite manual edits
        if (existingDay.isManual) return

        var newWorkStatus: AttendanceStatus = existingDay.safeWorkStatus
        var newDabbaStatus: DabbaStatus = existingDay.safeDabbaStatus

        when {
            // Second Half Evaluation: Immediately after 9:00 PM
            now.isAfter(t2100) -> {
                newWorkStatus = when {
                    wasInFirstHalf && wasInSecondHalf -> AttendanceStatus.PRESENT // 10 AM to 9 PM = "P"
                    wasInFirstHalf || wasInSecondHalf -> AttendanceStatus.HALF_DAY // One half = "H"
                    else -> AttendanceStatus.ABSENT // Neither = "A"
                }

                newDabbaStatus = when {
                    newWorkStatus == AttendanceStatus.ABSENT -> DabbaStatus.ABSENT
                    recordedDabba == "P" -> DabbaStatus.PRESENT
                    recordedDabba == "L" -> DabbaStatus.L
                    else -> DabbaStatus.ABSENT
                }
            }

            // First Half Evaluation: Immediately after 3:00 PM
            now.isAfter(t1500) -> {
                newWorkStatus = if (wasInFirstHalf) AttendanceStatus.HALF_DAY else AttendanceStatus.ABSENT

                newDabbaStatus = when {
                    newWorkStatus == AttendanceStatus.ABSENT -> DabbaStatus.ABSENT
                    recordedDabba == "P" -> DabbaStatus.PRESENT
                    recordedDabba == "L" -> DabbaStatus.L
                    else -> DabbaStatus.ABSENT
                }
            }

            // Morning Window (10:00 AM - 3:00 PM) live updates
            else -> {
                if (recordedDabba == "P") {
                    newDabbaStatus = DabbaStatus.PRESENT
                } else if (recordedDabba == "L") {
                    newDabbaStatus = DabbaStatus.L
                }
            }
        }

        map[today] = existingDay.copy(
            workStatus = newWorkStatus,
            dabbaStatus = newDabbaStatus,
            holidayName = existingDay.holidayName ?: "",
            amavasyaName = existingDay.amavasyaName ?: "",
            salesAmount = existingDay.salesAmount,
            salesBills = existingDay.salesBills ?: ""
        )
        dbPrefs.edit().putString("records", gson.toJson(map)).apply()
    }
}
