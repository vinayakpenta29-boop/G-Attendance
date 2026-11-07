package com.attendance.app

import android.app.backup.BackupManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.time.LocalDate

data class AttendanceBackup(
    val appName: String = "G-Attendance",
    val backupDate: String = LocalDate.now().toString(),
    val recordsJson: String?,
    val settingsJson: String?,
    val trackerPrefs: Map<String, *>? = null
)

object BackupHelper {
    private val gson = Gson()

    fun exportBackup(context: Context, uri: Uri) {
        try {
            val dbPrefs = context.getSharedPreferences("attendance_db", Context.MODE_PRIVATE)
            val trackerPrefs = context.getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)

            val backup = AttendanceBackup(
                recordsJson = dbPrefs.getString("records", null),
                settingsJson = dbPrefs.getString("settings", null),
                trackerPrefs = trackerPrefs.all
            )

            val jsonString = gson.toJson(backup)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(jsonString)
                    writer.flush()
                }
            }
            Toast.makeText(context, "Backup exported successfully!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun restoreBackup(context: Context, uri: Uri, onComplete: () -> Unit) {
        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            } ?: throw Exception("Empty backup file")

            val backup = gson.fromJson(jsonString, AttendanceBackup::class.java)
                ?: throw Exception("Invalid backup file format")

            // 1. Restore Attendance Records & Salary Settings
            val dbPrefs = context.getSharedPreferences("attendance_db", Context.MODE_PRIVATE)
            val dbEditor = dbPrefs.edit()
            if (!backup.recordsJson.isNullOrEmpty()) {
                dbEditor.putString("records", backup.recordsJson)
            }
            if (!backup.settingsJson.isNullOrEmpty()) {
                dbEditor.putString("settings", backup.settingsJson)
            }
            dbEditor.commit()

            // 2. Restore Arrival Logs & Geofence Tracker Prefs
            if (backup.trackerPrefs != null) {
                val trackerPrefs = context.getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)
                val trEditor = trackerPrefs.edit()
                for ((key, value) in backup.trackerPrefs) {
                    when (value) {
                        is Boolean -> trEditor.putBoolean(key, value)
                        is String -> trEditor.putString(key, value)
                        is Float -> trEditor.putFloat(key, value)
                        is Int -> trEditor.putInt(key, value)
                        is Long -> trEditor.putLong(key, value)
                        is Double -> trEditor.putFloat(key, value.toFloat())
                    }
                }
                trEditor.commit()
            }

            // 3. Notify Android BackupManager & Refresh UI
            BackupManager.dataChanged(context.packageName)
            Toast.makeText(context, "Backup restored successfully!", Toast.LENGTH_LONG).show()
            onComplete()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Restore failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
