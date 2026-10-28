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
import java.time.format.DateTimeFormatter
import java.util.Locale
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
            val t2130 = LocalTime.of(21, 30)

            val nextRunTime: LocalDateTime = when {
                // Before 10:00 AM -> wait until 10:00 AM sharp
                time.isBefore(t1000) -> now.withHour(10).withMinute(0).withSecond(0).withNano(0)

                // 10:00 AM - 10:30 AM -> track every 1 minute for Dabba punch
                time.isBefore(t1030) -> now.plusMinutes(1).withSecond(0).withNano(0)

                // 10:30 AM - 3:00 PM -> track every 30 minutes; snap to 3:00:05 PM for H1 evaluation
                time.isBefore(t1500) -> {
                    val candidate = now.plusMinutes(30)
                    if (candidate.toLocalTime().isAfter(t1500)) {
                        now.withHour(15).withMinute(0).withSecond(5).withNano(0)
                    } else {
                        candidate
                    }
                }

                // 3:00 PM - 9:00 PM -> track every 30 minutes; snap to 9:00:05 PM for H2 evaluation
                time.isBefore(t2100) -> {
                    val candidate = now.plusMinutes(30)
                    if (candidate.toLocalTime().isAfter(t2100)) {
                        now.withHour(21).withMinute(0).withSecond(5).withNano(0)
                    } else {
                        candidate
                    }
                }

                // 9:00 PM - 9:30 PM -> Keep running every 10 minutes to guarantee full-day attendance is recorded
                time.isBefore(t2130) -> now.plusMinutes(10).withSecond(0).withNano(0)

                // After 9:30 PM -> Sleep until 10:00 AM sharp tomorrow
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
            scheduleNext(applicationContext)
        }
        return Result.success()
    }

    private fun checkLocationAndUpdateAttendance() {
        val prefs = applicationContext.getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)
        var targetLat = prefs.getFloat("work_lat", 0f).toDouble()
        var targetLng = prefs.getFloat("work_lng", 0f).toDouble()
        var radius = prefs.getFloat("work_radius", 150f)

        // Reliable fallback to attendance_db settings if attendance_prefs is unpopulated
        if (targetLat == 0.0 && targetLng == 0.0) {
            val dbPrefs = applicationContext.getSharedPreferences("attendance_db", Context.MODE_PRIVATE)
            val setJson = dbPrefs.getString("settings", null)
            if (!setJson.isNullOrEmpty()) {
                try {
                    val loadedSettings: SalarySettings? = gson.fromJson(setJson, SalarySettings::class.java)
                    if (loadedSettings != null && loadedSettings.workLatitude != 0.0) {
                        targetLat = loadedSettings.workLatitude
                        targetLng = loadedSettings.workLongitude
                        radius = loadedSettings.geofenceRadiusMeters
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        if (targetLat == 0.0 && targetLng == 0.0) return

        val fusedClient = LocationServices.getFusedLocationProviderClient(applicationContext)

        // Resilient location fetching with timeout to prevent hanging in background
        val location: Location? = try {
            val cts = CancellationTokenSource()
            val task = fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            Tasks.await(task, 12, TimeUnit.SECONDS)
                ?: Tasks.await(fusedClient.lastLocation, 4, TimeUnit.SECONDS)
        } catch (e: Exception) {
            try {
                Tasks.await(fusedClient.lastLocation, 4, TimeUnit.SECONDS)
            } catch (ex: Exception) {
                null
            }
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

        val t1015 = LocalTime.of(10, 15, 59)
        val t1500 = LocalTime.of(15, 0)
        val t2100 = LocalTime.of(21, 0)

        var recordedArrivalTimeToSave: String? = null
        var recordedArrivalStatusToSave: String? = null

        if (isInside) {
            // 1. Record daily reaching time log on first detection
            val isArrivalLogged = trackerPrefs.getBoolean("${today}_arrival_logged", false)
            if (!isArrivalLogged) {
                val formattedNow = now.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH))
                val status = if (!now.isAfter(t1015)) "P" else "L"

                trackerPrefs.edit()
                    .putBoolean("${today}_arrival_logged", true)
                    .putString("${today}_arrival_time", formattedNow)
                    .putString("${today}_arrival_status", status)
                    .commit()

                recordedArrivalTimeToSave = formattedNow
                recordedArrivalStatusToSave = status
            }

            // 2. Dabba Punch tracking
            val isAlreadyPresentBefore1015 = trackerPrefs.getBoolean("${today}_present_before_1015", false)
            val currentDabbaPref = trackerPrefs.getString("${today}_dabba", null)
            if (!now.isAfter(t1015)) {
                trackerPrefs.edit()
                    .putString("${today}_dabba", "P")
                    .putBoolean("${today}_present_before_1015", true)
                    .commit()
            } else if (currentDabbaPref != "P" && !isAlreadyPresentBefore1015) {
                trackerPrefs.edit()
                    .putString("${today}_dabba", "L")
                    .commit()
            }

            // 3. Shift Presence
            if (now.isBefore(LocalTime.of(15, 15))) {
                trackerPrefs.edit().putBoolean("${today}_h1", true).commit()
            }
            if (now.isAfter(t1500) && now.isBefore(LocalTime.of(21, 35))) {
                trackerPrefs.edit().putBoolean("${today}_h2", true).commit()
            }
        }

        val wasInFirstHalf = trackerPrefs.getBoolean("${today}_h1", false)
        val wasInSecondHalf = trackerPrefs.getBoolean("${today}_h2", false)
        val recordedDabba = trackerPrefs.getString("${today}_dabba", null)

        val dbPrefs = applicationContext.getSharedPreferences("attendance_db", Context.MODE_PRIVATE)
        val json = dbPrefs.getString("records", null)
        val type = object : TypeToken<MutableMap<String, DayAttendance>>() {}.type
        val map: MutableMap<String, DayAttendance> = if (!json.isNullOrEmpty()) {
            gson.fromJson(json, type) ?: mutableMapOf()
        } else {
            mutableMapOf()
        }

        val existingDay = map[today] ?: DayAttendance(dateString = today, dayOfMonth = dayNum)

        val finalArrivalTime = if (!existingDay.arrivalTime.isNullOrBlank()) {
            existingDay.arrivalTime
        } else {
            recordedArrivalTimeToSave ?: trackerPrefs.getString("${today}_arrival_time", "")
        }

        val finalArrivalStatus = if (!existingDay.arrivalStatus.isNullOrBlank()) {
            existingDay.arrivalStatus
        } else {
            recordedArrivalStatusToSave ?: trackerPrefs.getString("${today}_arrival_status", "")
        }

        if (existingDay.isManual) {
            if (finalArrivalTime != existingDay.arrivalTime || finalArrivalStatus != existingDay.arrivalStatus) {
                map[today] = existingDay.copy(
                    arrivalTime = finalArrivalTime,
                    arrivalStatus = finalArrivalStatus
                )
                dbPrefs.edit().putString("records", gson.toJson(map)).commit()
            }
            return
        }

        var newWorkStatus: AttendanceStatus = existingDay.safeWorkStatus
        var newDabbaStatus: DabbaStatus = existingDay.safeDabbaStatus

        // If currently inside at or after 9:00 PM, Second Half is validated
        val hasCompletedSecondHalf = wasInSecondHalf || (isInside && !now.isBefore(t1500))

        when {
            // Second Half Evaluation: 9:00:00 PM onwards (or anytime at/after 9 PM)
            !now.isBefore(t2100) -> {
                newWorkStatus = when {
                    wasInFirstHalf && hasCompletedSecondHalf -> AttendanceStatus.PRESENT
                    wasInFirstHalf || hasCompletedSecondHalf -> AttendanceStatus.HALF_DAY
                    else -> AttendanceStatus.ABSENT
                }

                // If Work is P -> restore morning punch (P or L). If H or A -> Dabba is strictly A.
                newDabbaStatus = if (newWorkStatus == AttendanceStatus.PRESENT) {
                    when (recordedDabba) {
                        "P" -> DabbaStatus.PRESENT
                        "L" -> DabbaStatus.L
                        else -> DabbaStatus.ABSENT
                    }
                } else {
                    DabbaStatus.ABSENT
                }
            }

            // First Half Evaluation: Between 3:00 PM and 8:59 PM
            !now.isBefore(t1500) -> {
                newWorkStatus = if (wasInFirstHalf) AttendanceStatus.HALF_DAY else AttendanceStatus.ABSENT
                // While work is H or A -> Dabba is compulsory A
                newDabbaStatus = DabbaStatus.ABSENT
            }

            // Morning Window (before 3:00 PM) live display
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
            salesBills = existingDay.salesBills ?: "",
            arrivalTime = finalArrivalTime,
            arrivalStatus = finalArrivalStatus
        )
        // Use synchronous commit so data immediately flushes to disk in background
        dbPrefs.edit().putString("records", gson.toJson(map)).commit()
    }
}
