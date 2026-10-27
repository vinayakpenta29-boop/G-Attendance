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

            val nextRunTime: LocalDateTime = when {
                time.isBefore(t1000) -> now.withHour(10).withMinute(0).withSecond(0).withNano(0)
                time.isBefore(t1030) -> now.plusMinutes(1).withSecond(0).withNano(0)
                time.isBefore(t1500) -> {
                    val candidate = now.plusMinutes(30)
                    if (candidate.toLocalTime().isAfter(t1500)) {
                        now.withHour(15).withMinute(0).withSecond(5).withNano(0)
                    } else {
                        candidate
                    }
                }
                time.isBefore(t2100) -> {
                    val candidate = now.plusMinutes(30)
                    if (candidate.toLocalTime().isAfter(t2100)) {
                        now.withHour(21).withMinute(0).withSecond(5).withNano(0)
                    } else {
                        candidate
                    }
                }
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

        val t1015 = LocalTime.of(10, 15, 59)
        val t1500 = LocalTime.of(15, 0)
        val t2100 = LocalTime.of(21, 0)

        // --- NEW: RECORD WORKPLACE REACHING TIME (Independent Record) ---
        var recordedArrivalTimeToSave: String? = null
        var recordedArrivalStatusToSave: String? = null

        if (isInside) {
            val isArrivalLogged = trackerPrefs.getBoolean("${today}_arrival_logged", false)
            if (!isArrivalLogged) {
                val formattedNow = now.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH))
                val status = if (!now.isAfter(t1015)) "P" else "L"

                trackerPrefs.edit()
                    .putBoolean("${today}_arrival_logged", true)
                    .putString("${today}_arrival_time", formattedNow)
                    .putString("${today}_arrival_status", status)
                    .apply()

                recordedArrivalTimeToSave = formattedNow
                recordedArrivalStatusToSave = status
            }

            // Punch tracking for Dabba
            val isAlreadyPresentBefore1015 = trackerPrefs.getBoolean("${today}_present_before_1015", false)
            val currentDabbaPref = trackerPrefs.getString("${today}_dabba", null)
            if (!now.isAfter(t1015)) {
                trackerPrefs.edit()
                    .putString("${today}_dabba", "P")
                    .putBoolean("${today}_present_before_1015", true)
                    .apply()
            } else if (currentDabbaPref != "P" && !isAlreadyPresentBefore1015) {
                trackerPrefs.edit()
                    .putString("${today}_dabba", "L")
                    .apply()
            }

            // Shifts Presence Tracking
            if (now.isBefore(LocalTime.of(15, 15))) {
                trackerPrefs.edit().putBoolean("${today}_h1", true).apply()
            }
            if (now.isAfter(t1500) && now.isBefore(LocalTime.of(21, 30))) {
                trackerPrefs.edit().putBoolean("${today}_h2", true).apply()
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

        // Keep arrival time updated in database even if attendance was manually marked
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
                dbPrefs.edit().putString("records", gson.toJson(map)).apply()
            }
            return
        }

        var newWorkStatus: AttendanceStatus = existingDay.safeWorkStatus
        var newDabbaStatus: DabbaStatus = existingDay.safeDabbaStatus

        when {
            // Second Half Evaluation (After 9:00 PM)
            now.isAfter(t2100) -> {
                newWorkStatus = when {
                    wasInFirstHalf && wasInSecondHalf -> AttendanceStatus.PRESENT
                    wasInFirstHalf || wasInSecondHalf -> AttendanceStatus.HALF_DAY
                    else -> AttendanceStatus.ABSENT
                }

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

            // First Half Evaluation (Between 3:00 PM and 9:00 PM)
            now.isAfter(t1500) -> {
                newWorkStatus = if (wasInFirstHalf) AttendanceStatus.HALF_DAY else AttendanceStatus.ABSENT
                newDabbaStatus = DabbaStatus.ABSENT
            }

            // Morning Window (before 3:00 PM)
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
        dbPrefs.edit().putString("records", gson.toJson(map)).apply()
    }
}
