package com.attendance.app

import android.content.Context
import android.location.Location
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import java.time.LocalDate
import java.time.LocalTime

class LocationTrackerWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)
        val targetLat = prefs.getFloat("work_lat", 0f).toDouble()
        val targetLng = prefs.getFloat("work_lng", 0f).toDouble()
        val radius = prefs.getFloat("work_radius", 150f)

        if (targetLat == 0.0 && targetLng == 0.0) return Result.success()

        val fusedClient = LocationServices.getFusedLocationProviderClient(applicationContext)
        val location: Location? = try {
            Tasks.await(fusedClient.lastLocation)
        } catch (e: Exception) {
            null
        }

        location?.let {
            val results = FloatArray(1)
            Location.distanceBetween(it.latitude, it.longitude, targetLat, targetLng, results)
            val isInside = results[0] <= radius

            evaluateWorkTimeWindow(isInside)
        }

        return Result.success()
    }

    private fun evaluateWorkTimeWindow(isInsideWorkplace: Boolean) {
        val now = LocalTime.now()
        val today = LocalDate.now().toString()

        val startDay = LocalTime.of(10, 0)
        val halfSplit = LocalTime.of(15, 0)
        val endDay = LocalTime.of(21, 0)

        // Only evaluate during official hours (10:00 AM - 9:00 PM)
        if (now.isBefore(startDay) || now.isAfter(endDay)) return

        val prefs = applicationContext.getSharedPreferences("attendance_records", Context.MODE_PRIVATE)
        val currentStatus = prefs.getString("${today}_status", AttendanceStatus.NOT_MARKED.name)

        // Auto transition logic
        val newStatus = when {
            isInsideWorkplace && (now.isAfter(startDay) && now.isBefore(halfSplit)) -> {
                if (currentStatus == AttendanceStatus.NOT_MARKED.name) AttendanceStatus.HALF_DAY.name else currentStatus
            }
            isInsideWorkplace && now.isAfter(halfSplit) -> {
                AttendanceStatus.PRESENT.name
            }
            !isInsideWorkplace && currentStatus == AttendanceStatus.NOT_MARKED.name -> {
                AttendanceStatus.ABSENT.name
            }
            else -> currentStatus
        }

        prefs.edit().putString("${today}_status", newStatus).apply()
    }
}
