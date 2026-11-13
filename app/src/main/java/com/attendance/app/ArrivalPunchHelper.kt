package com.attendance.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object ArrivalPunchHelper {
    private val gson = Gson()
    private val lock = Any()

    fun isTodayArrivalLogged(context: Context): Boolean {
        val today = LocalDate.now().toString()
        val trackerPrefs = context.getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)
        if (trackerPrefs.getBoolean("${today}_arrival_logged", false)) return true

        val dbPrefs = context.getSharedPreferences("attendance_db", Context.MODE_PRIVATE)
        val json = dbPrefs.getString("records", null) ?: return false
        return try {
            val type: Type = object : TypeToken<MutableMap<String, DayAttendance>>() {}.type
            val map: MutableMap<String, DayAttendance>? = gson.fromJson(json, type)
            !map?.get(today)?.arrivalTime.isNullOrBlank()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Atomically records the exact workplace arrival timestamp if not already logged today.
     * Uses the actual GPS fix / geofence trigger timestamp rather than app-open time.
     */
    fun recordExactArrivalIfNeeded(context: Context, triggerEpochMillis: Long): Boolean {
        synchronized(lock) {
            val zone = ZoneId.systemDefault()
            val triggerDateTime = Instant.ofEpochMilli(triggerEpochMillis).atZone(zone).toLocalDateTime()
            val nowDateTime = LocalDateTime.now(zone)

            // Use actual GPS fix timestamp if it is from today and within 10 minutes of system clock
            val effectiveDateTime = if (
                triggerDateTime.toLocalDate() == nowDateTime.toLocalDate() &&
                !triggerDateTime.isAfter(nowDateTime.plusMinutes(1)) &&
                ChronoUnit.MINUTES.between(triggerDateTime, nowDateTime) <= 10
            ) {
                triggerDateTime
            } else {
                nowDateTime
            }

            val today = effectiveDateTime.toLocalDate().toString()
            val dayNum = effectiveDateTime.dayOfMonth
            val arrivalTimeObj = effectiveDateTime.toLocalTime()

            val trackerPrefs = context.getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)
            val dbPrefs = context.getSharedPreferences("attendance_db", Context.MODE_PRIVATE)

            val json = dbPrefs.getString("records", null)
            val type: Type = object : TypeToken<MutableMap<String, DayAttendance>>() {}.type
            val map: MutableMap<String, DayAttendance> = if (!json.isNullOrEmpty()) {
                gson.fromJson(json, type) ?: mutableMapOf()
            } else {
                mutableMapOf()
            }

            val existingDay = map[today] ?: DayAttendance(dateString = today, dayOfMonth = dayNum)

            if (trackerPrefs.getBoolean("${today}_arrival_logged", false) || !existingDay.arrivalTime.isNullOrBlank()) {
                return false
            }

            val t1015 = LocalTime.of(10, 15, 59)
            val formattedTime = arrivalTimeObj.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH))
            val status = if (!arrivalTimeObj.isAfter(t1015)) "P" else "L"

            val prefEditor = trackerPrefs.edit()
                .putBoolean("${today}_arrival_logged", true)
                .putString("${today}_arrival_time", formattedTime)
                .putString("${today}_arrival_status", status)

            if (!arrivalTimeObj.isAfter(t1015)) {
                prefEditor.putString("${today}_dabba", "P")
                prefEditor.putBoolean("${today}_present_before_1015", true)
            } else if (trackerPrefs.getString("${today}_dabba", null) != "P" &&
                !trackerPrefs.getBoolean("${today}_present_before_1015", false)
            ) {
                prefEditor.putString("${today}_dabba", "L")
            }

            if (arrivalTimeObj.isBefore(LocalTime.of(15, 15))) {
                prefEditor.putBoolean("${today}_h1", true)
            }
            if (arrivalTimeObj.isAfter(LocalTime.of(15, 0)) && arrivalTimeObj.isBefore(LocalTime.of(21, 35))) {
                prefEditor.putBoolean("${today}_h2", true)
            }
            prefEditor.commit()

            val updatedDabba = if (existingDay.safeDabbaStatus == DabbaStatus.NOT_MARKED && arrivalTimeObj.isBefore(LocalTime.of(15, 0))) {
                if (status == "P") DabbaStatus.PRESENT else DabbaStatus.L
            } else {
                existingDay.safeDabbaStatus
            }

            map[today] = existingDay.copy(
                dabbaStatus = updatedDabba,
                holidayName = existingDay.holidayName ?: "",
                amavasyaName = existingDay.amavasyaName ?: "",
                salesAmount = existingDay.salesAmount,
                salesBills = existingDay.salesBills ?: "",
                arrivalTime = formattedTime,
                arrivalStatus = status
            )
            dbPrefs.edit().putString("records", gson.toJson(map)).commit()
            return true
        }
    }
}
