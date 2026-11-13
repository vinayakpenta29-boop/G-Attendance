package com.attendance.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import java.time.LocalTime

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return

        val transition = event.geofenceTransition
        if (transition == Geofence.GEOFENCE_TRANSITION_ENTER || transition == Geofence.GEOFENCE_TRANSITION_DWELL) {
            val now = LocalTime.now()
            if (!now.isBefore(LocalTime.of(9, 30)) && !now.isAfter(LocalTime.of(21, 30))) {
                val triggerTimeMillis = event.triggeringLocation?.time ?: System.currentTimeMillis()
                val punched = ArrivalPunchHelper.recordExactArrivalIfNeeded(context, triggerTimeMillis)
                if (punched) {
                    context.stopService(Intent(context, ArrivalPunchService::class.java))
                }
            }
        }
    }
}
