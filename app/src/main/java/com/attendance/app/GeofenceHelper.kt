package com.attendance.app

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

object GeofenceHelper {
    private const val GEOFENCE_ID = "G_ATTENDANCE_WORKPLACE"
    private const val GEOFENCE_REQ_CODE = 4001

    fun registerWorkplaceGeofence(context: Context, lat: Double, lng: Double, radiusMeters: Float) {
        if (lat == 0.0 && lng == 0.0) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        try {
            val geofencingClient = LocationServices.getGeofencingClient(context)
            val effectiveRadius = radiusMeters.coerceAtLeast(100f)

            val geofence = Geofence.Builder()
                .setRequestId(GEOFENCE_ID)
                .setCircularRegion(lat, lng, effectiveRadius)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_DWELL)
                .setLoiteringDelay(15_000)
                .build()

            val request = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build()

            val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, GEOFENCE_REQ_CODE, intent, flags)

            geofencingClient.removeGeofences(listOf(GEOFENCE_ID)).addOnCompleteListener {
                try {
                    geofencingClient.addGeofences(request, pendingIntent)
                } catch (e: SecurityException) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
