package com.attendance.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ArrivalHistoryDialog(
    initialMonth: YearMonth,
    attendanceMap: Map<String, DayAttendance>,
    onDismiss: () -> Unit
) {
    var displayedMonth by remember { mutableStateOf(initialMonth) }
    val daysInMonth = displayedMonth.lengthOfMonth()

    // Aggregate monthly arrival metrics
    val monthRecords = remember(displayedMonth, attendanceMap.values.toList()) {
        (1..daysInMonth).map { day ->
            val dateStr = displayedMonth.atDay(day).toString()
            attendanceMap[dateStr] ?: DayAttendance(dateString = dateStr, dayOfMonth = day)
        }
    }

    val onTimeCount = remember(monthRecords) {
        monthRecords.count { it.arrivalStatus == "P" }
    }
    val lateCount = remember(monthRecords) {
        monthRecords.count { it.arrivalStatus == "L" }
    }
    val totalRecorded = onTimeCount + lateCount

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Daily Arrival Log",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Independent Workplace Punch Record",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                // Month Switcher (‹ Month Year ›)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { displayedMonth = displayedMonth.minusMonths(1) },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF1F5F9))
                    ) {
                        Text("‹", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }

                    Text(
                        text = displayedMonth.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + displayedMonth.year,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandPrimary
                    )

                    IconButton(
                        onClick = { displayedMonth = displayedMonth.plusMonths(1) },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF1F5F9))
                    ) {
                        Text("›", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Monthly Summary Overview Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Total Logged", fontSize = 10.5.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                            Text("$totalRecorded Days", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("On Time (P)", fontSize = 10.5.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                            Text("$onTimeCount Days", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = SuccessGreen)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Late (L)", fontSize = 10.5.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                            Text("$lateCount Days", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = WarningAmber)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Day-Wise Arrival Records",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Day-by-day listings
                monthRecords.forEach { record ->
                    val dayDate = displayedMonth.atDay(record.dayOfMonth)
                    val dayOfWeekStr = dayDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                    val hasTime = !record.arrivalTime.isNullOrBlank()

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (hasTime) Color.White else Color(0xFFFAFAFA),
                        border = BorderStroke(0.8.dp, CardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.5.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Day ${record.dayOfMonth}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "($dayOfWeekStr)",
                                    fontSize = 11.5.sp,
                                    color = TextSecondary
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (hasTime) record.arrivalTime!! else "Not Detected",
                                    fontSize = 12.sp,
                                    fontWeight = if (hasTime) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (hasTime) TextPrimary else TextSecondary
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                // Status Pill: P (Green), L (Amber), or - (Gray)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = when (record.arrivalStatus) {
                                        "P" -> SuccessBg
                                        "L" -> WarningBg
                                        else -> Color(0xFFF1F5F9)
                                    },
                                    border = BorderStroke(
                                        0.8.dp,
                                        when (record.arrivalStatus) {
                                            "P" -> Color(0xFFA7F3D0)
                                            "L" -> Color(0xFFFDE68A)
                                            else -> CardBorder
                                        }
                                    )
                                ) {
                                    Text(
                                        text = when (record.arrivalStatus) {
                                            "P" -> "P (On Time)"
                                            "L" -> "L (Late)"
                                            else -> "-"
                                        },
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (record.arrivalStatus) {
                                            "P" -> Color(0xFF047857)
                                            "L" -> Color(0xFFB45309)
                                            else -> TextSecondary
                                        },
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
            ) {
                Text("Close")
            }
        }
    )
}
