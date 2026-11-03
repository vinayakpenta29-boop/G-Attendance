package com.attendance.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class LocationTrackerWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "location_tracker_work"

        fun scheduleNext(context: Context) {
            LocationTrackerReceiver.scheduleNext(context)
        }
    }

    override suspend fun doWork(): Result {
        LocationTrackerReceiver.scheduleNext(applicationContext)
        return Result.success()
    }
}
