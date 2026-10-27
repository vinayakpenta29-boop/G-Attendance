package com.attendance.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.util.concurrent.TimeUnit
import androidx.work.*

class MainActivity : ComponentActivity() {

    private val gson = Gson()
    private val attendanceMap = mutableStateMapOf<String, DayAttendance>()
    private var settings by mutableStateOf(SalarySettings())

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            LocationTrackerWorker.scheduleNext(this)
            checkLiveLocationAtWorkplace()
        }
        scheduleDailySalesReminder()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createSalesNotificationChannel()
        loadData()
        requestPermissions()
        LocationTrackerWorker.scheduleNext(this)
        checkLiveLocationAtWorkplace()
        scheduleDailySalesReminder()

        setContent {
            val currentMonth = remember { YearMonth.now() }

            AttendanceScreen(
                currentMonth = currentMonth,
                attendanceMap = attendanceMap,
                settings = settings,
                onSaveSettings = { updated ->
                    settings = updated
                    saveSettings()
                },
                onUpdateDay = { day ->
                    // Compulsory rule: If work attendance is A or H, Dabba is locked to A
                    val finalDay = if (day.workStatus == AttendanceStatus.ABSENT || day.workStatus == AttendanceStatus.HALF_DAY) {
                        day.copy(dabbaStatus = DabbaStatus.ABSENT)
                    } else {
                        day
                    }
                    attendanceMap[finalDay.dateString] = finalDay
                    saveAttendance()
                },
                onSetLocation = { setCurrentLocationAsWork() }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        loadData()
        LocationTrackerWorker.scheduleNext(this)
        checkLiveLocationAtWorkplace()
    }

    private fun checkLiveLocationAtWorkplace() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        if (settings.workLatitude == 0.0 && settings.workLongitude == 0.0) return

        val fusedClient = LocationServices.getFusedLocationProviderClient(this)
        val cts = CancellationTokenSource()

        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).addOnSuccessListener { loc ->
            val location = loc ?: return@addOnSuccessListener
            val results = FloatArray(1)
            Location.distanceBetween(location.latitude, location.longitude, settings.workLatitude, settings.workLongitude, results)

            val tolerance = location.accuracy.coerceAtMost(50f)
            val isInside = results[0] <= (settings.geofenceRadiusMeters + tolerance)

            val trackerPrefs = getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)
            val now = LocalTime.now()
            val today = LocalDate.now().toString()
            val dayNum = LocalDate.now().dayOfMonth

            val t1015 = LocalTime.of(10, 15, 59)
            val t1500 = LocalTime.of(15, 0)
            val t2100 = LocalTime.of(21, 0)

            val isAlreadyPresentBefore1015 = trackerPrefs.getBoolean("${today}_present_before_1015", false)
            val currentDabbaPref = trackerPrefs.getString("${today}_dabba", null)

            if (isInside) {
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

            val currentRecord = attendanceMap[today] ?: DayAttendance(dateString = today, dayOfMonth = dayNum)
            if (currentRecord.isManual) return@addOnSuccessListener

            var newWorkStatus = currentRecord.safeWorkStatus
            var newDabbaStatus = currentRecord.safeDabbaStatus

            when {
                // Second Half (After 9:00 PM)
                now.isAfter(t2100) -> {
                    newWorkStatus = when {
                        wasInFirstHalf && wasInSecondHalf -> AttendanceStatus.PRESENT
                        wasInFirstHalf || wasInSecondHalf -> AttendanceStatus.HALF_DAY
                        else -> AttendanceStatus.ABSENT
                    }

                    // If Work is P -> restore Dabba to P or L. If Work is H or A -> Dabba MUST be A.
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

                // First Half (Between 3:00 PM and 9:00 PM)
                now.isAfter(t1500) -> {
                    newWorkStatus = if (wasInFirstHalf) AttendanceStatus.HALF_DAY else AttendanceStatus.ABSENT
                    // Compulsory Rule: Work is H or A -> Dabba switches to A
                    newDabbaStatus = DabbaStatus.ABSENT
                }

                // Morning Window before 3:00 PM
                else -> {
                    if (recordedDabba == "P") newDabbaStatus = DabbaStatus.PRESENT
                    else if (recordedDabba == "L") newDabbaStatus = DabbaStatus.L
                }
            }

            if (newWorkStatus != currentRecord.safeWorkStatus || newDabbaStatus != currentRecord.safeDabbaStatus) {
                val updatedRecord = currentRecord.copy(
                    workStatus = newWorkStatus,
                    dabbaStatus = newDabbaStatus,
                    holidayName = currentRecord.holidayName ?: "",
                    amavasyaName = currentRecord.amavasyaName ?: "",
                    salesAmount = currentRecord.salesAmount,
                    salesBills = currentRecord.salesBills ?: ""
                )
                attendanceMap[today] = updatedRecord
                saveAttendance()
            }
        }
    }

    private fun createSalesNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SalesReminderWorker.CHANNEL_ID,
                "Daily Sales Reminder",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminds you every day at 10 PM to log today's sales."
                enableVibration(true)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun scheduleDailySalesReminder() {
        val now = LocalDateTime.now()
        var target10PM = now.withHour(22).withMinute(0).withSecond(0).withNano(0)

        if (now.isAfter(target10PM)) {
            target10PM = target10PM.plusDays(1)
        }

        val initialDelayMillis = Duration.between(now, target10PM).toMillis()

        val reminderRequest = PeriodicWorkRequestBuilder<SalesReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_sales_reminder_10pm",
            ExistingPeriodicWorkPolicy.UPDATE,
            reminderRequest
        )
    }

    private fun setCurrentLocationAsWork() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show()
            return
        }

        val cts = CancellationTokenSource()
        LocationServices.getFusedLocationProviderClient(this)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    settings = settings.copy(workLatitude = loc.latitude, workLongitude = loc.longitude)
                    getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE).edit()
                        .putFloat("work_lat", loc.latitude.toFloat())
                        .putFloat("work_lng", loc.longitude.toFloat())
                        .apply()
                    saveSettings()
                    Toast.makeText(this, "Workplace GPS locked successfully!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Could not acquire GPS. Turn ON device location.", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun requestPermissions() {
        val permissionsList = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsList.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        permissionLauncher.launch(permissionsList.toTypedArray())
    }

    private fun loadData() {
        try {
            val sp = getSharedPreferences("attendance_db", Context.MODE_PRIVATE)
            val json = sp.getString("records", null)
            if (!json.isNullOrEmpty()) {
                val type = object : TypeToken<Map<String, DayAttendance>>() {}.type
                val map: Map<String, DayAttendance>? = gson.fromJson(json, type)

                if (map != null) {
                    val sanitized = mutableMapOf<String, DayAttendance>()
                    for ((key, raw) in map) {
                        val workSt = raw.workStatus ?: AttendanceStatus.NOT_MARKED
                        val dabbaSt = raw.dabbaStatus ?: DabbaStatus.NOT_MARKED

                        val finalDabba = if ((workSt == AttendanceStatus.ABSENT || workSt == AttendanceStatus.HALF_DAY) && dabbaSt != DabbaStatus.ABSENT) {
                            DabbaStatus.ABSENT
                        } else {
                            dabbaSt
                        }

                        sanitized[key] = DayAttendance(
                            dateString = if (raw.dateString.isBlank()) key else raw.dateString,
                            dayOfMonth = if (raw.dayOfMonth <= 0) 1 else raw.dayOfMonth,
                            workStatus = workSt,
                            dabbaStatus = finalDabba,
                            isManual = raw.isManual,
                            isHoliday = raw.isHoliday,
                            holidayName = raw.holidayName ?: "",
                            isAmavasya = raw.isAmavasya,
                            amavasyaName = raw.amavasyaName ?: "",
                            salesAmount = raw.salesAmount,
                            salesBills = raw.salesBills ?: ""
                        )
                    }

                    attendanceMap.clear()
                    attendanceMap.putAll(sanitized)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val sp = getSharedPreferences("attendance_db", Context.MODE_PRIVATE)
            val setJson = sp.getString("settings", null)
            if (!setJson.isNullOrEmpty()) {
                val loadedSettings: SalarySettings? = gson.fromJson(setJson, SalarySettings::class.java)
                if (loadedSettings != null) {
                    settings = loadedSettings
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            settings = SalarySettings()
        }
    }

    private fun saveAttendance() {
        try {
            val sp = getSharedPreferences("attendance_db", Context.MODE_PRIVATE)
            sp.edit().putString("records", gson.toJson(attendanceMap)).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveSettings() {
        try {
            val sp = getSharedPreferences("attendance_db", Context.MODE_PRIVATE)
            sp.edit().putString("settings", gson.toJson(settings)).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
