package com.attendance.app

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import com.google.gson.Gson
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.time.LocalDate

data class PdfBackupItem(
    val fileName: String,
    val relativePath: String,
    val base64Data: String
)

data class AttendanceBackup(
    val appName: String = "G-Attendance",
    val backupDate: String = LocalDate.now().toString(),
    val recordsJson: String?,
    val settingsJson: String?,
    val trackerPrefs: Map<String, *>? = null,
    val allPrefs: Map<String, Map<String, *>>? = null,
    val pdfFiles: List<PdfBackupItem>? = null
)

object BackupHelper {
    private val gson = Gson()

    fun exportBackup(context: Context, uri: Uri) {
        try {
            val dbPrefs = context.getSharedPreferences("attendance_db", Context.MODE_PRIVATE)
            val trackerPrefs = context.getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)

            // 1. Collect all app SharedPreferences (including UI toggles & PDF states)
            val allPrefsMap = mutableMapOf<String, Map<String, *>>()
            val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            if (sharedPrefsDir.exists() && sharedPrefsDir.isDirectory) {
                sharedPrefsDir.listFiles { _, name -> name.endsWith(".xml") }?.forEach { xmlFile ->
                    val prefName = xmlFile.name.removeSuffix(".xml")
                    val sp = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    allPrefsMap[prefName] = sp.all
                }
            }

            // 2. Discover and package all Calendar PDF files
            val pdfList = mutableListOf<PdfBackupItem>()

            // Scan internal files directory
            context.filesDir.walkTopDown().filter { isPdfFile(it) }.forEach { file ->
                val relPath = file.relativeTo(context.filesDir).path
                val base64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                pdfList.add(PdfBackupItem(fileName = file.name, relativePath = relPath, base64Data = base64))
            }

            // Scan external files directory (if used)
            context.getExternalFilesDir(null)?.walkTopDown()?.filter { isPdfFile(it) }?.forEach { file ->
                val base64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                pdfList.add(PdfBackupItem(fileName = file.name, relativePath = "ext_${file.name}", base64Data = base64))
            }

            val backup = AttendanceBackup(
                recordsJson = dbPrefs.getString("records", null),
                settingsJson = dbPrefs.getString("settings", null),
                trackerPrefs = trackerPrefs.all,
                allPrefs = allPrefsMap,
                pdfFiles = pdfList
            )

            // 3. Stream directly into the backup file to conserve device RAM
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    gson.toJson(backup, AttendanceBackup::class.java, writer)
                    writer.flush()
                }
            }

            val pdfMsg = if (pdfList.isNotEmpty()) " with Calendar PDF (${pdfList.size} file)" else ""
            Toast.makeText(context, "Backup exported successfully$pdfMsg!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun restoreBackup(context: Context, uri: Uri, onComplete: () -> Unit) {
        try {
            // Stream parse JSON directly
            val backup = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
                    gson.fromJson(reader, AttendanceBackup::class.java)
                }
            } ?: throw Exception("Empty or unreadable backup file")

            // 1. Restore Attendance Records & Settings to attendance_db
            val dbPrefs = context.getSharedPreferences("attendance_db", Context.MODE_PRIVATE)
            val dbEditor = dbPrefs.edit()
            if (!backup.recordsJson.isNullOrEmpty()) {
                dbEditor.putString("records", backup.recordsJson)
            }
            if (!backup.settingsJson.isNullOrEmpty()) {
                dbEditor.putString("settings", backup.settingsJson)
            }
            dbEditor.commit()

            // 2. Restore Attendance Tracking Preferences
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

            // 3. Restore all auxiliary SharedPreferences (grid lines, PDF viewer state, etc.)
            backup.allPrefs?.forEach { (prefName, entries) ->
                val sp = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                val editor = sp.edit()
                for ((key, value) in entries) {
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is String -> editor.putString(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Double -> editor.putFloat(key, value.toFloat())
                    }
                }
                editor.commit()
            }

            // 4. Restore Calendar PDF Files
            var restoredPdfCount = 0
            if (!backup.pdfFiles.isNullOrEmpty()) {
                for (pdfItem in backup.pdfFiles) {
                    try {
                        val pdfBytes = Base64.decode(pdfItem.base64Data, Base64.DEFAULT)

                        val targetFile = if (pdfItem.relativePath.startsWith("ext_")) {
                            val cleanName = pdfItem.relativePath.removePrefix("ext_")
                            File(context.getExternalFilesDir(null) ?: context.filesDir, cleanName)
                        } else {
                            File(context.filesDir, pdfItem.relativePath)
                        }

                        targetFile.parentFile?.mkdirs()
                        targetFile.writeBytes(pdfBytes)

                        // Ensure a direct copy exists in filesDir for immediate viewer access
                        val directFile = File(context.filesDir, pdfItem.fileName)
                        if (!directFile.exists()) {
                            directFile.writeBytes(pdfBytes)
                        }

                        restoredPdfCount++
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            val pdfMsg = if (restoredPdfCount > 0) " & Calendar PDF" else ""
            Toast.makeText(context, "All data$pdfMsg restored successfully!", Toast.LENGTH_LONG).show()
            onComplete()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Restore failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun isPdfFile(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        if (file.extension.equals("pdf", ignoreCase = true)) return true

        // Verify PDF magic bytes (%PDF) in case extension is missing
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(4)
                val read = input.read(header)
                read == 4 && header[0] == 0x25.toByte() && header[1] == 0x50.toByte() && header[2] == 0x44.toByte() && header[3] == 0x46.toByte()
            }
        } catch (e: Exception) {
            false
        }
    }
}
