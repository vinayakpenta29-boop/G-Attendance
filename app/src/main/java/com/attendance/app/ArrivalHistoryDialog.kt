package com.attendance.app

import android.app.TimePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArrivalHistoryDialog(
    initialMonth: YearMonth,
    attendanceMap: MutableMap<String, DayAttendance>,
    onDismiss: () -> Unit,
    onDayUpdated: ((DayAttendance) -> Unit)? = null
) {
    val context = LocalContext.current
    val gson = remember { Gson() }
    var displayedMonth by remember { mutableStateOf(initialMonth) }
    val todayString = remember { LocalDate.now().toString() }

    // State for the long-press edit popup
    var selectedDayForEdit by remember { mutableStateOf<DayAttendance?>(null) }

    val daysInMonth = displayedMonth.lengthOfMonth()
    val monthRecords = (1..daysInMonth).map { dayNum ->
        val dateStr = displayedMonth.atDay(dayNum).toString()
        attendanceMap[dateStr] ?: DayAttendance(dateString = dateStr, dayOfMonth = dayNum)
    }

    val totalLogged = monthRecords.count { !it.arrivalTime.isNullOrBlank() }
    val onTimeCount = monthRecords.count { it.arrivalStatus.equals("P", ignoreCase = true) }
    val lateCount = monthRecords.count { it.arrivalStatus.equals("L", ignoreCase = true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 20.dp)
            ) {
                // Header Titles
                Text(
                    text = "Daily Arrival Log",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Independent Workplace Punch Record",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Month Navigator
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { displayedMonth = displayedMonth.minusMonths(1) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Text("‹", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                        }

                        Text(
                            text = "${displayedMonth.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)} ${displayedMonth.year}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4F46E5)
                        )

                        IconButton(
                            onClick = { displayedMonth = displayedMonth.plusMonths(1) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Text("›", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Metrics Row (Total Logged, On Time, Late)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricSummaryItem(title = "Total Logged", value = "$totalLogged Days", color = Color(0xFF1E293B))
                    MetricSummaryItem(title = "On Time (P)", value = "$onTimeCount Days", color = Color(0xFF059669))
                    MetricSummaryItem(title = "Late (L)", value = "$lateCount Days", color = Color(0xFFD97706))
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4-Column Calendar Grid matching Daily Arrival Log
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(monthRecords.size) { index ->
                        val record = monthRecords[index]
                        val isToday = (record.dateString == todayString)
                        val hasArrival = !record.arrivalTime.isNullOrBlank()

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(94.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .combinedClickable(
                                    onClick = {
                                        Toast.makeText(context, "Long press day ${record.dayOfMonth} to edit arrival time", Toast.LENGTH_SHORT).show()
                                    },
                                    onLongClick = {
                                        selectedDayForEdit = record
                                    }
                                ),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isToday) Color(0xFFF5F7FF) else Color(0xFFFAFAFA),
                            border = BorderStroke(
                                width = if (isToday) 2.dp else 1.dp,
                                color = if (isToday) Color(0xFF4F46E5) else Color(0xFFE2E8F0)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Day Number
                                Text(
                                    text = "${record.dayOfMonth}",
                                    fontSize = 13.sp,
                                    fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Bold,
                                    color = if (isToday) Color(0xFF4F46E5) else Color(0xFF1E293B)
                                )

                                // Arrival Time Text
                                Text(
                                    text = if (hasArrival) record.arrivalTime!! else "--",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (hasArrival) Color(0xFF334155) else Color(0xFF94A3B8),
                                    maxLines = 1
                                )

                                // Status Badge (P / L / -)
                                val badgeColor = when (record.arrivalStatus?.uppercase()) {
                                    "P" -> Color(0xFF10B981)
                                    "L" -> Color(0xFFF59E0B)
                                    else -> Color(0xFFE2E8F0)
                                }
                                val textColor = if (hasArrival) Color.White else Color(0xFF94A3B8)

                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(badgeColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (hasArrival) (record.arrivalStatus?.uppercase() ?: "-") else "-",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Hint and Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "💡 Long press any day to edit",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }

    // Long-Press Edit Dialog Popup
    selectedDayForEdit?.let { targetDay ->
        EditArrivalPunchDialog(
            dayRecord = targetDay,
            onDismiss = { selectedDayForEdit = null },
            onSave = { updatedTime, updatedStatus ->
                val dateStr = targetDay.dateString
                val updatedRecord = targetDay.copy(
                    arrivalTime = updatedTime,
                    arrivalStatus = updatedStatus
                )

                // 1. Update in-memory map
                attendanceMap[dateStr] = updatedRecord

                // 2. Persist to attendance_db
                val dbPrefs = context.getSharedPreferences("attendance_db", Context.MODE_PRIVATE)
                val json = dbPrefs.getString("records", null)
                val type: Type = object : TypeToken<MutableMap<String, DayAttendance>>() {}.type
                val map: MutableMap<String, DayAttendance> = if (!json.isNullOrEmpty()) {
                    gson.fromJson(json, type) ?: mutableMapOf()
                } else {
                    mutableMapOf()
                }
                map[dateStr] = updatedRecord
                dbPrefs.edit().putString("records", gson.toJson(map)).commit()

                // 3. Persist to attendance_prefs
                val trPrefs = context.getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)
                if (updatedTime.isNotBlank()) {
                    trPrefs.edit()
                        .putBoolean("${dateStr}_arrival_logged", true)
                        .putString("${dateStr}_arrival_time", updatedTime)
                        .putString("${dateStr}_arrival_status", updatedStatus)
                        .commit()
                } else {
                    trPrefs.edit()
                        .remove("${dateStr}_arrival_logged")
                        .remove("${dateStr}_arrival_time")
                        .remove("${dateStr}_arrival_status")
                        .commit()
                }

                onDayUpdated?.invoke(updatedRecord)
                Toast.makeText(context, "Day ${targetDay.dayOfMonth} punch updated to $updatedTime ($updatedStatus)", Toast.LENGTH_SHORT).show()
                selectedDayForEdit = null
            }
        )
    }
}

@Composable
private fun MetricSummaryItem(title: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun EditArrivalPunchDialog(
    dayRecord: DayAttendance,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    val context = LocalContext.current
    var selectedTime by remember { mutableStateOf(dayRecord.arrivalTime ?: "") }
    var selectedStatus by remember { mutableStateOf(dayRecord.arrivalStatus ?: "") }

    // Parse initial hour/minute from existing time string if available (e.g. "10:26 AM")
    val initialTime = remember(dayRecord.arrivalTime) {
        try {
            if (!dayRecord.arrivalTime.isNullOrBlank()) {
                LocalTime.parse(dayRecord.arrivalTime, DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH))
            } else {
                LocalTime.of(10, 0)
            }
        } catch (e: Exception) {
            LocalTime.of(10, 0)
        }
    }

    val timePickerDialog = remember {
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val pickedLocalTime = LocalTime.of(hourOfDay, minute)
                val formatted = pickedLocalTime.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH))
                selectedTime = formatted

                // Automatically recommend status: On-Time if <= 10:15:59 AM, else Late
                val cutoff = LocalTime.of(10, 15, 59)
                selectedStatus = if (!pickedLocalTime.isAfter(cutoff)) "P" else "L"
            },
            initialTime.hour,
            initialTime.minute,
            false // 12-hour format with AM/PM
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Arrival Time - Day ${dayRecord.dayOfMonth}",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = Color(0xFF1E293B)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Select workplace arrival time for ${dayRecord.dateString}:",
                    fontSize = 12.5.sp,
                    color = Color(0xFF64748B)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Time picker display card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Punch Time", fontSize = 11.sp, color = Color(0xFF64748B))
                            Text(
                                text = if (selectedTime.isNotBlank()) selectedTime else "Not Set (--)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTime.isNotBlank()) Color(0xFF1E293B) else Color(0xFF94A3B8)
                            )
                        }

                        Button(
                            onClick = { timePickerDialog.show() },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Pick Time", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Status Selection: P (On Time) vs L (Late)
                Text("Arrival Status:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // "P" Chip
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = if (selectedStatus == "P") 2.dp else 1.dp,
                                color = if (selectedStatus == "P") Color(0xFF10B981) else Color(0xFFE2E8F0),
                                shape = RoundedCornerShape(8.dp)
                            ),
                        color = if (selectedStatus == "P") Color(0xFFECFDF5) else Color.White,
                        onClick = { selectedStatus = "P" }
                    ) {
                        Text(
                            text = "P (On Time)",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedStatus == "P") Color(0xFF047857) else Color(0xFF64748B),
                            modifier = Modifier.padding(vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    // "L" Chip
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = if (selectedStatus == "L") 2.dp else 1.dp,
                                color = if (selectedStatus == "L") Color(0xFFF59E0B) else Color(0xFFE2E8F0),
                                shape = RoundedCornerShape(8.dp)
                            ),
                        color = if (selectedStatus == "L") Color(0xFFFFFBEB) else Color.White,
                        onClick = { selectedStatus = "L" }
                    ) {
                        Text(
                            text = "L (Late)",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedStatus == "L") Color(0xFFB45309) else Color(0xFF64748B),
                            modifier = Modifier.padding(vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                if (selectedTime.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    TextButton(
                        onClick = {
                            selectedTime = ""
                            selectedStatus = ""
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Clear Punch Record", color = Color(0xFFDC2626), fontSize = 11.5.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(selectedTime, selectedStatus)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
