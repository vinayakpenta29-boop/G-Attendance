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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
import java.time.format.TextStyle
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
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f)
                .shadow(16.dp, RoundedCornerShape(26.dp)),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFFFAFBFD),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 18.dp)
            ) {
                // --- Top Header ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF4F46E5), Color(0xFF6366F1))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⏱", fontSize = 19.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Daily Arrival Log",
                                fontSize = 18.5.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0F172A),
                                letterSpacing = (-0.4).sp
                            )
                            Text(
                                text = "Independent workplace punch record",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9))
                    ) {
                        Text("✕", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // --- Month Navigator Card ---
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shadowElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { displayedMonth = displayedMonth.minusMonths(1) },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF8FAFC))
                        ) {
                            Text("‹", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF334155))
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${displayedMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${displayedMonth.year}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF1E293B)
                            )
                        }

                        IconButton(
                            onClick = { displayedMonth = displayedMonth.plusMonths(1) },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF8FAFC))
                        ) {
                            Text("›", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF334155))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // --- Summary KPI Cards ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PremiumKpiCard(
                        modifier = Modifier.weight(1f),
                        label = "Total Logged",
                        value = "$totalLogged",
                        suffix = "Days",
                        themeColor = Color(0xFF4F46E5),
                        bgColor = Color(0xFFEEF2FF),
                        borderColor = Color(0xFFC7D2FE)
                    )
                    PremiumKpiCard(
                        modifier = Modifier.weight(1f),
                        label = "On Time (P)",
                        value = "$onTimeCount",
                        suffix = "Days",
                        themeColor = Color(0xFF059669),
                        bgColor = Color(0xFFECFDF5),
                        borderColor = Color(0xFFA7F3D0)
                    )
                    PremiumKpiCard(
                        modifier = Modifier.weight(1f),
                        label = "Late (L)",
                        value = "$lateCount",
                        suffix = "Days",
                        themeColor = Color(0xFFD97706),
                        bgColor = Color(0xFFFFFBEB),
                        borderColor = Color(0xFFFDE68A)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // --- Calendar Cards Grid (5 Boxes in a Row) ---
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(monthRecords.size) { index ->
                        val record = monthRecords[index]
                        val isToday = (record.dateString == todayString)
                        val hasArrival = !record.arrivalTime.isNullOrBlank()
                        val isLate = record.arrivalStatus.equals("L", ignoreCase = true)
                        val isOnTime = record.arrivalStatus.equals("P", ignoreCase = true)

                        val cellBg = when {
                            isToday -> Color(0xFFF4F6FF)
                            hasArrival && isOnTime -> Color(0xFFFAFDFA)
                            hasArrival && isLate -> Color(0xFFFFFDF8)
                            else -> Color.White
                        }

                        val cellBorder = when {
                            isToday -> Color(0xFF4F46E5)
                            hasArrival && isOnTime -> Color(0xFFA7F3D0)
                            hasArrival && isLate -> Color(0xFFFED7AA)
                            else -> Color(0xFFE2E8F0)
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .shadow(if (isToday) 2.5.dp else 0.8.dp, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .combinedClickable(
                                    onClick = {
                                        Toast.makeText(
                                            context,
                                            "Hold day ${record.dayOfMonth} to edit punch time",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onLongClick = {
                                        selectedDayForEdit = record
                                    }
                                ),
                            shape = RoundedCornerShape(12.dp),
                            color = cellBg,
                            border = BorderStroke(if (isToday) 1.8.dp else 0.9.dp, cellBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Day Header Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${record.dayOfMonth}",
                                        fontSize = 12.sp,
                                        fontWeight = if (isToday) FontWeight.Black else FontWeight.Bold,
                                        color = if (isToday) Color(0xFF4F46E5) else Color(0xFF1E293B)
                                    )

                                    if (isToday) {
                                        Surface(
                                            shape = RoundedCornerShape(3.dp),
                                            color = Color(0xFF4F46E5)
                                        ) {
                                            Text(
                                                text = "TODAY",
                                                fontSize = 5.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 2.dp, vertical = 0.5.dp)
                                            )
                                        }
                                    }
                                }

                                // Arrival Punch Time
                                Text(
                                    text = if (hasArrival) record.arrivalTime!! else "-- : --",
                                    fontSize = 8.5.sp,
                                    fontWeight = if (hasArrival) FontWeight.Bold else FontWeight.Medium,
                                    color = if (hasArrival) Color(0xFF0F172A) else Color(0xFF94A3B8),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )

                                // Badge Pill
                                when {
                                    hasArrival && isOnTime -> {
                                        Surface(
                                            shape = RoundedCornerShape(5.dp),
                                            color = Color(0xFFDCFCE7),
                                            border = BorderStroke(0.8.dp, Color(0xFF86EFAC)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "P • ON TIME",
                                                fontSize = 7.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF166534),
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                modifier = Modifier.padding(vertical = 1.5.dp)
                                            )
                                        }
                                    }
                                    hasArrival && isLate -> {
                                        Surface(
                                            shape = RoundedCornerShape(5.dp),
                                            color = Color(0xFFFEF3C7),
                                            border = BorderStroke(0.8.dp, Color(0xFFFDE68A)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "L • LATE",
                                                fontSize = 7.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF92400E),
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                modifier = Modifier.padding(vertical = 1.5.dp)
                                            )
                                        }
                                    }
                                    else -> {
                                        Surface(
                                            shape = RoundedCornerShape(5.dp),
                                            color = Color(0xFFF1F5F9),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "-",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF94A3B8),
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(vertical = 1.5.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // --- Footer Navigation & Hint ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("💡", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Long press cell to edit punch",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569)
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text("Done", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // --- Long-Press Edit Dialog Popup ---
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

                attendanceMap[dateStr] = updatedRecord

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
                Toast.makeText(
                    context,
                    "Day ${targetDay.dayOfMonth} updated to $updatedTime ($updatedStatus)",
                    Toast.LENGTH_SHORT
                ).show()
                selectedDayForEdit = null
            }
        )
    }
}

@Composable
private fun PremiumKpiCard(
    label: String,
    value: String,
    suffix: String,
    themeColor: Color,
    bgColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = themeColor.copy(alpha = 0.9f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = themeColor
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = suffix,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = themeColor.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 1.dp)
                )
            }
        }
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

                val cutoff = LocalTime.of(10, 15, 59)
                selectedStatus = if (!pickedLocalTime.isAfter(cutoff)) "P" else "L"
            },
            initialTime.hour,
            initialTime.minute,
            false
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Punch - Day ${dayRecord.dayOfMonth}",
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                color = Color(0xFF0F172A)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Update arrival punch record for ${dayRecord.dateString}:",
                    fontSize = 12.5.sp,
                    color = Color(0xFF64748B)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
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
                            Text("PUNCH TIME", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                            Text(
                                text = if (selectedTime.isNotBlank()) selectedTime else "-- : --",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = if (selectedTime.isNotBlank()) Color(0xFF0F172A) else Color(0xFF94A3B8)
                            )
                        }

                        Button(
                            onClick = { timePickerDialog.show() },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Pick Time", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("PUNCH STATUS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = if (selectedStatus == "P") 2.dp else 1.dp,
                                color = if (selectedStatus == "P") Color(0xFF10B981) else Color(0xFFE2E8F0),
                                shape = RoundedCornerShape(10.dp)
                            ),
                        color = if (selectedStatus == "P") Color(0xFFECFDF5) else Color.White,
                        onClick = { selectedStatus = "P" }
                    ) {
                        Text(
                            text = "P (On Time)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedStatus == "P") Color(0xFF047857) else Color(0xFF64748B),
                            modifier = Modifier.padding(vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = if (selectedStatus == "L") 2.dp else 1.dp,
                                color = if (selectedStatus == "L") Color(0xFFF59E0B) else Color(0xFFE2E8F0),
                                shape = RoundedCornerShape(10.dp)
                            ),
                        color = if (selectedStatus == "L") Color(0xFFFFFBEB) else Color.White,
                        onClick = { selectedStatus = "L" }
                    ) {
                        Text(
                            text = "L (Late)",
                            fontSize = 12.sp,
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
                        Text("Reset / Clear Punch", color = Color(0xFFDC2626), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedTime, selectedStatus) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save Changes", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF64748B))
            }
        }
    )
}
