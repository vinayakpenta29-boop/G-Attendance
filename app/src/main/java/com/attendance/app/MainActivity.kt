package com.attendance.app

import android.Manifest
import android.content.Context
import android.content.Intent
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
import java.lang.reflect.Type
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class MainActivity : ComponentActivity() {

    private val gson = Gson()
    private val attendanceMap = mutableStateMapOf<String, DayAttendance>()
    private var settings by mutableStateOf(SalarySettings())

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && (settings.workLatitude != 0.0 || settings.workLongitude != 0.0)) {
            GeofenceHelper.registerWorkplaceGeofence(
                this,
                settings.workLatitude,
                settings.workLongitude,
                settings.geofenceRadiusMeters
            )
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (fineGranted || coarseGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(
                    this,
                    "Select 'Allow all the time' for exact background arrival punch",
                    Toast.LENGTH_LONG
                ).show()
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }

            if (settings.workLatitude != 0.0 || settings.workLongitude != 0.0) {
                GeofenceHelper.registerWorkplaceGeofence(
                    this,
                    settings.workLatitude,
                    settings.workLongitude,
                    settings.geofenceRadiusMeters
                )
            }

            try {
                LocationTrackerReceiver.scheduleNext(this)
                checkLiveLocationAtWorkplace()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        SalesReminderReceiver.scheduleExact10PMRminder(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadData()
        requestPermissions()

        if (settings.workLatitude != 0.0 || settings.workLongitude != 0.0) {
            GeofenceHelper.registerWorkplaceGeofence(
                this,
                settings.workLatitude,
                settings.workLongitude,
                settings.geofenceRadiusMeters
            )
        }

        try {
            LocationTrackerReceiver.scheduleNext(this)
            checkLiveLocationAtWorkplace()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        SalesReminderReceiver.scheduleExact10PMRminder(this)

        setContent {
            val currentMonth = remember { YearMonth.now() }

            AttendanceScreen(
                currentMonth = currentMonth,
                attendanceMap = attendanceMap,
                settings = settings,
                onSaveSettings = { updated ->
                    settings = updated
                    saveSettings()
                    if (updated.workLatitude != 0.0 || updated.workLongitude != 0.0) {
                        GeofenceHelper.registerWorkplaceGeofence(
                            this,
                            updated.workLatitude,
                            updated.workLongitude,
                            updated.geofenceRadiusMeters
                        )
                    }
                },
                onUpdateDay = { day ->
                    val todayStr = LocalDate.now().toString()
                    if (day.dateString == todayStr) {
                        val trackerPrefs = getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)
                        val dabba = day.dabbaStatus
                        if (dabba == DabbaStatus.D || dabba == DabbaStatus.G) {
                            trackerPrefs.edit()
                                .putString("${todayStr}_custom_dabba", dabba.name)
                                .commit()
                        } else {
                            trackerPrefs.edit()
                                .remove("${todayStr}_custom_dabba")
                                .commit()
                        }
                    }
                    attendanceMap[day.dateString] = day
                    saveAttendance()
                },
                onSetLocation = { setCurrentLocationAsWork() },
                onRefresh = {
                    loadData()
                    checkLiveLocationAtWorkplace()
                    LocationTrackerReceiver.scheduleNext(this@MainActivity)
                    Toast.makeText(this@MainActivity, "Data & Location Synced", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        loadData()
        if (settings.workLatitude != 0.0 || settings.workLongitude != 0.0) {
            GeofenceHelper.registerWorkplaceGeofence(
                this,
                settings.workLatitude,
                settings.workLongitude,
                settings.geofenceRadiusMeters
            )
        }
        try {
            LocationTrackerReceiver.scheduleNext(this)
            checkLiveLocationAtWorkplace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        SalesReminderReceiver.scheduleExact10PMRminder(this)
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

            val tolerance = location.accuracy.coerceAtMost(60f)
            val isInside = results[0] <= (settings.geofenceRadiusMeters + tolerance)

            if (isInside) {
                val punched = ArrivalPunchHelper.recordExactArrivalIfNeeded(this, location.time)
                if (punched) {
                    stopService(Intent(this, ArrivalPunchService::class.java))
                    loadData()
                }
            }

            val trackerPrefs = getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)
            val now = LocalTime.now()
            val today = LocalDate.now().toString()
            val dayNum = LocalDate.now().dayOfMonth

            val t1015 = LocalTime.of(10, 15, 59)
            val t1500 = LocalTime.of(15, 0)
            val t2100 = LocalTime.of(21, 0)

            val currentRecord = attendanceMap[today] ?: DayAttendance(dateString = today, dayOfMonth = dayNum)
            val arrivalTimeToSave = currentRecord.arrivalTime?.takeIf { it.isNotBlank() }
                ?: trackerPrefs.getString("${today}_arrival_time", "")
            val arrivalStatusToSave = currentRecord.arrivalStatus?.takeIf { it.isNotBlank() }
                ?: trackerPrefs.getString("${today}_arrival_status", "")

            if (isInside) {
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
            val customDabbaName = trackerPrefs.getString("${today}_custom_dabba", null)

            val hasCompletedSecondHalf = wasInSecondHalf || (isInside && !now.isBefore(t1500))

            var newWorkStatus = currentRecord.safeWorkStatus
            var newDabbaStatus = currentRecord.safeDabbaStatus

            when {
                !now.isBefore(t2100) -> {
                    if (wasInFirstHalf && hasCompletedSecondHalf) {
                        newWorkStatus = AttendanceStatus.PRESENT
                    } else if (wasInFirstHalf || hasCompletedSecondHalf) {
                        newWorkStatus = AttendanceStatus.HALF_DAY
                    } else if (newWorkStatus == AttendanceStatus.NOT_MARKED) {
                        newWorkStatus = AttendanceStatus.ABSENT
                    }

                    newDabbaStatus = if (customDabbaName == "D" || customDabbaName == "G") {
                        DabbaStatus.valueOf(customDabbaName)
                    } else if (newWorkStatus == AttendanceStatus.PRESENT) {
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
                    if (newWorkStatus == AttendanceStatus.NOT_MARKED) {
                        newWorkStatus = if (wasInFirstHalf) AttendanceStatus.HALF_DAY else AttendanceStatus.ABSENT
                    }

                    newDabbaStatus = if (customDabbaName == "D" || customDabbaName == "G") {
                        DabbaStatus.valueOf(customDabbaName)
                    } else {
                        DabbaStatus.ABSENT
                    }
                }

                else -> {
                    newDabbaStatus = if (customDabbaName == "D" || customDabbaName == "G") {
                        DabbaStatus.valueOf(customDabbaName)
                    } else {
                        when (recordedDabba) {
                            "P" -> DabbaStatus.PRESENT
                            "L" -> DabbaStatus.L
                            else -> currentRecord.safeDabbaStatus
                        }
                    }
                }
            }

            if (newWorkStatus != currentRecord.safeWorkStatus || newDabbaStatus != currentRecord.safeDabbaStatus || arrivalTimeToSave != currentRecord.arrivalTime) {
                val updatedRecord = currentRecord.copy(
                    workStatus = newWorkStatus,
                    dabbaStatus = newDabbaStatus,
                    holidayName = currentRecord.holidayName ?: "",
                    amavasyaName = currentRecord.amavasyaName ?: "",
                    salesAmount = currentRecord.salesAmount,
                    salesBills = currentRecord.salesBills ?: "",
                    arrivalTime = arrivalTimeToSave,
                    arrivalStatus = arrivalStatusToSave
                )
                attendanceMap[today] = updatedRecord
                saveAttendance()
            }
        }
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
                        .commit()
                    saveSettings()
                    GeofenceHelper.registerWorkplaceGeofence(
                        this,
                        loc.latitude,
                        loc.longitude,
                        settings.geofenceRadiusMeters
                    )
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
                val type: Type = object : TypeToken<MutableMap<String, DayAttendance>>() {}.type
                val map: MutableMap<String, DayAttendance>? = gson.fromJson(json, type)

                if (map != null) {
                    val sanitized = mutableMapOf<String, DayAttendance>()
                    for ((key, raw) in map) {
                        val workSt = raw.workStatus ?: AttendanceStatus.NOT_MARKED
                        val dabbaSt = raw.dabbaStatus ?: DabbaStatus.NOT_MARKED

                        sanitized[key] = DayAttendance(
                            dateString = if (raw.dateString.isBlank()) key else raw.dateString,
                            dayOfMonth = if (raw.dayOfMonth <= 0) 1 else raw.dayOfMonth,
                            workStatus = workSt,
                            dabbaStatus = dabbaSt,
                            isManual = raw.isManual,
                            isHoliday = raw.isHoliday,
                            holidayName = raw.holidayName ?: "",
                            isAmavasya = raw.isAmavasya,
                            amavasyaName = raw.amavasyaName ?: "",
                            salesAmount = raw.salesAmount,
                            salesBills = raw.salesBills ?: "",
                            arrivalTime = raw.arrivalTime ?: "",
                            arrivalStatus = raw.arrivalStatus ?: ""
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
            sp.edit().putString("records", gson.toJson(attendanceMap)).commit()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveSettings() {
        try {
            val sp = getSharedPreferences("attendance_db", Context.MODE_PRIVATE)
            sp.edit().putString("settings", gson.toJson(settings)).commit()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
