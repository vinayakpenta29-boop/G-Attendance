package com.attendance.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
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
    val todayString = remember { LocalDate.now().toString() }

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

    // Group records into 5 boxes per row
    val chunkedRows = remember(monthRecords) {
        monthRecords.chunked(5)
    }

    // State for tapping on any cell to view full arrival detail
    var selectedDayRecord by remember { mutableStateOf<DayAttendance?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Daily Arrival Log",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Independent Workplace Punch Record",
                    fontSize = 11.5.sp,
                    color = TextSecondary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. Month Navigator
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                displayedMonth = displayedMonth.minusMonths(1)
                                selectedDayRecord = null
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Text("‹", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }

                        Text(
                            text = "${displayedMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${displayedMonth.year}",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandPrimary
                        )

                        IconButton(
                            onClick = {
                                displayedMonth = displayedMonth.plusMonths(1)
                                selectedDayRecord = null
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Text("›", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Summary Overview Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Total Logged", fontSize = 10.5.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                            Text("$totalRecorded Days", fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("On Time (P)", fontSize = 10.5.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                            Text("$onTimeCount Days", fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = SuccessGreen)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Late (L)", fontSize = 10.5.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                            Text("$lateCount Days", fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = WarningAmber)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3. 5-Boxes-Per-Row Grid (Without Day Headers)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = CardBg,
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        chunkedRows.forEach { rowDays ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                rowDays.forEach { record ->
                                    val isToday = (record.dateString == todayString)
                                    val isSelected = (selectedDayRecord?.dateString == record.dateString)

                                    ArrivalCalendarCell(
                                        record = record,
                                        isToday = isToday,
                                        isSelected = isSelected,
                                        onClick = { selectedDayRecord = record },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // Fill blank slots in the final row if it has fewer than 5 days
                                repeat(5 - rowDays.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // 4. Tap-to-Inspect Detail Card
                if (selectedDayRecord != null) {
                    val rec = selectedDayRecord!!
                    val hasTime = !rec.arrivalTime.isNullOrBlank()

                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = when (rec.arrivalStatus) {
                            "P" -> SuccessBg
                            "L" -> WarningBg
                            else -> Color(0xFFF1F5F9)
                        },
                        border = BorderStroke(
                            1.dp,
                            when (rec.arrivalStatus) {
                                "P" -> Color(0xFFA7F3D0)
                                "L" -> Color(0xFFFDE68A)
                                else -> CardBorder
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Day ${rec.dayOfMonth} Arrival Details",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (hasTime) "Reaching Time: ${rec.arrivalTime}" else "Reaching Time: Not Detected",
                                    fontSize = 11.5.sp,
                                    color = TextSecondary
                                )
                            }

                            Text(
                                text = when (rec.arrivalStatus) {
                                    "P" -> "On Time (P)"
                                    "L" -> "Late (L)"
                                    else -> "No Record"
                                },
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = when (rec.arrivalStatus) {
                                    "P" -> Color(0xFF047857)
                                    "L" -> Color(0xFFB45309)
                                    else -> TextSecondary
                                }
                            )
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

@Composable
fun ArrivalCalendarCell(
    record: DayAttendance,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (bgColor, borderColor) = when {
        isSelected -> Pair(Color(0xFFEEF2FF), BrandPrimary)
        isToday -> Pair(Color(0xFFEEF2FF), BrandSecondary)
        record.arrivalStatus == "P" -> Pair(Color(0xFFF0FDF4), Color(0xFFBBF7D0))
        record.arrivalStatus == "L" -> Pair(Color(0xFFFFFBEB), Color(0xFFFDE68A))
        else -> Pair(Color(0xFFFAFAFA), Color(0xFFF1F5F9))
    }

    Surface(
        modifier = modifier
            .padding(2.5.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(if (isSelected || isToday) 1.6.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Day Number
            Text(
                text = "${record.dayOfMonth}",
                fontSize = 12.sp,
                fontWeight = if (isToday || isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                color = if (isToday || isSelected) BrandPrimary else TextPrimary
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Arrival Time String (e.g. "10:29 AM" or "--")
            val timeDisplay = remember(record.arrivalTime) {
                if (record.arrivalTime.isNullOrBlank()) {
                    "--"
                } else {
                    record.arrivalTime!!.trim()
                }
            }
            Text(
                text = timeDisplay,
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (!record.arrivalTime.isNullOrBlank()) TextPrimary else Color(0xFF94A3B8),
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(3.dp))

            // Status Pill (P, L, or -)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        when (record.arrivalStatus) {
                            "P" -> SuccessGreen
                            "L" -> WarningAmber
                            else -> Color(0xFFE2E8F0)
                        }
                    )
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = when (record.arrivalStatus) {
                        "P" -> "P"
                        "L" -> "L"
                        else -> "-"
                    },
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (record.arrivalStatus.isNullOrBlank()) Color.DarkGray else Color.White
                )
            }
        }
    }
}
