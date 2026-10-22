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

// Data model for Monthly Leaves Breakdown
data class MonthLeavesData(
    val monthName: String,
    val absentCount: Int,
    val halfDayCount: Int,
    val totalLeaves: Double
)

private val CardBorder = Color(0xFFE2E8F0)
private val TextPrimary = Color(0xFF0F172A)
private val TextSecondary = Color(0xFF64748B)
private val BrandPrimary = Color(0xFF4F46E5)
private val SuccessGreen = Color(0xFF10B981)
private val SuccessBg = Color(0xFFECFDF5)
private val DangerRed = Color(0xFFEF4444)
private val DangerBg = Color(0xFFFEF2F2)
private val WarningBg = Color(0xFFFFFBEB)

@Composable
fun LeavesHistoryDialog(
    initialYear: Int,
    attendanceMap: Map<String, DayAttendance>,
    onDismiss: () -> Unit
) {
    var selectedYear by remember { mutableStateOf(initialYear) }

    val yearlyData = remember(selectedYear, attendanceMap.values.toList()) {
        (1..12).map { month ->
            val ym = YearMonth.of(selectedYear, month)
            val daysInMonth = ym.lengthOfMonth()
            var absents = 0
            var halfDays = 0

            for (day in 1..daysInMonth) {
                val dateStr = ym.atDay(day).toString()
                val record = attendanceMap[dateStr]
                val st = record?.workStatus ?: AttendanceStatus.NOT_MARKED
                if (st == AttendanceStatus.ABSENT) {
                    absents++
                } else if (st == AttendanceStatus.HALF_DAY) {
                    halfDays++
                }
            }
            val totalLeaves = absents + (halfDays * 0.5)
            MonthLeavesData(
                monthName = ym.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                absentCount = absents,
                halfDayCount = halfDays,
                totalLeaves = totalLeaves
            )
        }
    }

    val totalYearLeaves = remember(yearlyData) { yearlyData.sumOf { it.totalLeaves } }
    val totalYearAbsents = remember(yearlyData) { yearlyData.sumOf { it.absentCount } }
    val totalYearHalfDays = remember(yearlyData) { yearlyData.sumOf { it.halfDayCount } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Leaves History",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { selectedYear -= 1 },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF1F5F9))
                    ) {
                        Text("‹", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }

                    Text(
                        text = "$selectedYear",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = BrandPrimary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    IconButton(
                        onClick = { selectedYear += 1 },
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
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BrandPrimary.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, BrandPrimary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "TOTAL LEAVES IN $selectedYear",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandPrimary,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "%.1f Days".format(totalYearLeaves),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = if (totalYearLeaves > 0) DangerRed else SuccessGreen
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$totalYearAbsents Full Absents  •  $totalYearHalfDays Half-Days",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Month-by-Month Breakdown",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                yearlyData.forEach { monthData ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (monthData.totalLeaves > 0) Color(0xFFFAFAFA) else Color(0xFFFFFFFF),
                        border = BorderStroke(0.8.dp, CardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = monthData.monthName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (monthData.totalLeaves > 0) {
                                        "${monthData.absentCount} A  •  ${monthData.halfDayCount} H"
                                    } else {
                                        "No leaves taken"
                                    },
                                    fontSize = 10.5.sp,
                                    color = if (monthData.totalLeaves > 0) TextSecondary else SuccessGreen
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = when {
                                    monthData.totalLeaves == 0.0 -> SuccessBg
                                    monthData.totalLeaves <= 4.0 -> WarningBg
                                    else -> DangerBg
                                },
                                border = BorderStroke(
                                    0.8.dp,
                                    when {
                                        monthData.totalLeaves == 0.0 -> Color(0xFFA7F3D0)
                                        monthData.totalLeaves <= 4.0 -> Color(0xFFFDE68A)
                                        else -> Color(0xFFFECACA)
                                    }
                                )
                            ) {
                                Text(
                                    text = "%.1f Days".format(monthData.totalLeaves),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        monthData.totalLeaves == 0.0 -> Color(0xFF047857)
                                        monthData.totalLeaves <= 4.0 -> Color(0xFFB45309)
                                        else -> DangerRed
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
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
