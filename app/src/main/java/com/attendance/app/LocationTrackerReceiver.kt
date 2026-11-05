package com.attendance.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.backup.BackupManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.PowerManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Tasks
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.reflect.Type
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

class LocationTrackerReceiver : BroadcastReceiver() {

    private val gson = Gson()

    companion object {
        const val ACTION_TRACK_LOCATION = "com.attendance.app.ACTION_TRACK_LOCATION"
        private const val PENDING_INTENT_REQ_CODE = 3001

        fun scheduleNext(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val now = LocalDateTime.now()
            val time = now.toLocalTime()
            val today = LocalDate.now().toString()

            val prefs = context.getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)
            val isArrivalLogged = prefs.getBoolean("${today}_arrival_logged", false)

            val t1000 = LocalTime.of(10, 0)
            val t1030 = LocalTime.of(10, 30)
            val t1500 = LocalTime.of(15, 0)
            val t2100 = LocalTime.of(21, 0)
            val t2130 = LocalTime.of(21, 30)

            val nextRunTime: LocalDateTime = when {
                time.isBefore(t1000) -> now.withHour(10).withMinute(0).withSecond(0).withNano(0)
                time.isBefore(t1030) -> {
                    if (!isArrivalLogged) {
                        now.plusMinutes(1).withSecond(0).withNano(0)
                    } else {
                        now.withHour(10).withMinute(30).withSecond(0).withNano(0)
                    }
                }
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
                time.isBefore(t2130) -> now.plusMinutes(10).withSecond(0).withNano(0)
                else -> now.plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0)
            }

            val triggerMillis = nextRunTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val intent = Intent(context, LocationTrackerReceiver::class.java).apply {
                action = ACTION_TRACK_LOCATION
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, PENDING_INTENT_REQ_CODE, intent, flags)

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED || intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            scheduleNext(context)
            return
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GAttendance:ExactTrackerWakeLock")
        wakeLock?.acquire(35_000L)

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                checkLocationAndUpdateAttendance(context)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                scheduleNext(context)
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
                pendingResult.finish()
            }
        }
    }

    private fun checkLocationAndUpdateAttendance(context: Context) {
        val prefs = context.getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)
        var targetLat = prefs.getFloat("work_lat", 0f).toDouble()
        var targetLng = prefs.getFloat("work_lng", 0f).toDouble()
        var radius = prefs.getFloat("work_radius", 150f)

        if (targetLat == 0.0 && targetLng == 0.0) {
            val dbPrefs = context.getSharedPreferences("attendance_db", Context.MODE_PRIVATE)
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

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)

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
            val tolerance = location.accuracy.coerceAtMost(60f)
            results[0] <= (radius + tolerance)
        } else {
            false
        }

        evaluateAttendance(context, isInside, prefs)
    }

    private fun evaluateAttendance(context: Context, isInside: Boolean, trackerPrefs: android.content.SharedPreferences) {
        val now = LocalTime.now()
        val today = LocalDate.now().toString()
        val dayNum = LocalDate.now().dayOfMonth

        val t1015 = LocalTime.of(10, 15, 59)
        val t1500 = LocalTime.of(15, 0)
        val t2100 = LocalTime.of(21, 0)

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
                    .commit()

                recordedArrivalTimeToSave = formattedNow
                recordedArrivalStatusToSave = status
            }

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

        val dbPrefs = context.getSharedPreferences("attendance_db", Context.MODE_PRIVATE)
        val json = dbPrefs.getString("records", null)
        val type: Type = object : TypeToken<MutableMap<String, DayAttendance>>() {}.type
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
                BackupManager.dataChanged(context.packageName)
            }
            return
        }

        var newWorkStatus: AttendanceStatus = existingDay.safeWorkStatus
        var newDabbaStatus: DabbaStatus = existingDay.safeDabbaStatus

        val hasCompletedSecondHalf = wasInSecondHalf || (isInside && !now.isBefore(t1500))

        when {
            !now.isBefore(t2100) -> {
                newWorkStatus = when {
                    wasInFirstHalf && hasCompletedSecondHalf -> AttendanceStatus.PRESENT
                    wasInFirstHalf || hasCompletedSecondHalf -> AttendanceStatus.HALF_DAY
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

            !now.isBefore(t1500) -> {
                newWorkStatus = if (wasInFirstHalf) AttendanceStatus.HALF_DAY else AttendanceStatus.ABSENT
                newDabbaStatus = DabbaStatus.ABSENT
            }

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
        dbPrefs.edit().putString("records", gson.toJson(map)).commit()
        // Alert Android BackupManager of new cloud backup data
        BackupManager.dataChanged(context.packageName)
    }
}
