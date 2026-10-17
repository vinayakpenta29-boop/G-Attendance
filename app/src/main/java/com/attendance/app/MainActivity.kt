package com.attendance.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.work.*
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val gson = Gson()
    private val attendanceMap = mutableStateMapOf<String, DayAttendance>()
    private var settings by mutableStateOf(SalarySettings())

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startBackgroundWorker()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadData()
        requestPermissions()

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
                    attendanceMap[day.dateString] = day
                    saveAttendance()
                },
                onSetLocation = { setCurrentLocationAsWork() }
            )
        }
    }

    private fun setCurrentLocationAsWork() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show()
            return
        }

        LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                settings = settings.copy(workLatitude = loc.latitude, workLongitude = loc.longitude)
                getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE).edit()
                    .putFloat("work_lat", loc.latitude.toFloat())
                    .putFloat("work_lng", loc.longitude.toFloat())
                    .apply()
                saveSettings()
                Toast.makeText(this, "Workplace GPS locked to current spot!", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startBackgroundWorker() {
        val workRequest = PeriodicWorkRequestBuilder<LocationTrackerWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "attendance_tracker",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun requestPermissions() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun loadData() {
        val sp = getSharedPreferences("attendance_db", Context.MODE_PRIVATE)
        val json = sp.getString("records", null)
        if (!json.isNullOrEmpty()) {
            val type = object : TypeToken<Map<String, DayAttendance>>() {}.type
            val map: Map<String, DayAttendance> = gson.fromJson(json, type)
            attendanceMap.putAll(map)
        }

        val setJson = sp.getString("settings", null)
        if (!setJson.isNullOrEmpty()) {
            settings = gson.fromJson(setJson, SalarySettings::class.java)
        }
    }

    private fun saveAttendance() {
        val sp = getSharedPreferences("attendance_db", Context.MODE_PRIVATE)
        sp.edit().putString("records", gson.toJson(attendanceMap)).apply()
    }

    private fun saveSettings() {
        val sp = getSharedPreferences("attendance_db", Context.MODE_PRIVATE)
        sp.edit().putString("settings", gson.toJson(settings)).apply()
    }
}
