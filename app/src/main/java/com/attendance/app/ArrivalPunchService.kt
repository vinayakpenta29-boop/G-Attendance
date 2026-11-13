package com.attendance.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.gson.Gson
import java.time.LocalTime

class ArrivalPunchService : Service() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private val gson = Gson()

    companion object {
        private const val CHANNEL_ID = "arrival_punch_channel"
        private const val NOTIFICATION_ID = 4099
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }

        val now = LocalTime.now()
        if (ArrivalPunchHelper.isTodayArrivalLogged(this) ||
            now.isBefore(LocalTime.of(9, 50)) ||
            now.isAfter(LocalTime.of(11, 15))
        ) {
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startHighAccuracyTracking()
        return START_STICKY
    }

    private fun startHighAccuracyTracking() {
        val prefs = getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)
        var targetLat = prefs.getFloat("work_lat", 0f).toDouble()
        var targetLng = prefs.getFloat("work_lng", 0f).toDouble()
        var radius = prefs.getFloat("work_radius", 150f)

        if (targetLat == 0.0 && targetLng == 0.0) {
            val dbPrefs = getSharedPreferences("attendance_db", Context.MODE_PRIVATE)
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

        if (targetLat == 0.0 && targetLng == 0.0) {
            stopSelf()
            return
        }

        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 20_000L)
            .setMinUpdateIntervalMillis(15_000L)
            .setMaxUpdateDelayMillis(20_000L)
            .build()

        locationCallback?.let { fusedClient.removeLocationUpdates(it) }

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val now = LocalTime.now()
                if (ArrivalPunchHelper.isTodayArrivalLogged(this@ArrivalPunchService) ||
                    now.isAfter(LocalTime.of(11, 15))
                ) {
                    stopTrackingAndService()
                    return
                }

                for (loc in result.locations) {
                    val dist = FloatArray(1)
                    Location.distanceBetween(loc.latitude, loc.longitude, targetLat, targetLng, dist)
                    val tolerance = loc.accuracy.coerceAtMost(60f)
                    if (dist[0] <= (radius + tolerance)) {
                        ArrivalPunchHelper.recordExactArrivalIfNeeded(this@ArrivalPunchService, loc.time)
                        stopTrackingAndService()
                        return
                    }
                }
            }
        }

        locationCallback = callback
        try {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    private fun stopTrackingAndService() {
        locationCallback?.let {
            if (::fusedClient.isInitialized) {
                fusedClient.removeLocationUpdates(it)
            }
        }
        locationCallback = null
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Workplace Arrival Detection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Detects exact arrival time at workplace"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("G-Attendance")
            .setContentText("Detecting workplace arrival time...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        locationCallback?.let {
            if (::fusedClient.isInitialized) {
                fusedClient.removeLocationUpdates(it)
            }
        }
        super.onDestroy()
    }
}
