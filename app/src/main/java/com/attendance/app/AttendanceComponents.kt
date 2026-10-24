package com.attendance.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ModernSalesIncentiveCard(
    currentMonth: YearMonth,
    attendanceMap: Map<String, DayAttendance>
) {
    val daysInMonth = currentMonth.lengthOfMonth()

    val records = (1..daysInMonth).map { day ->
        val dateStr = currentMonth.atDay(day).toString()
        attendanceMap[dateStr] ?: DayAttendance(dateString = dateStr, dayOfMonth = day)
    }

    val totalSales = records.sumOf { it.salesAmount }
    val salesAfterGst = totalSales * 0.95
    val incentiveAmount = salesAfterGst * 0.01

    var absentDays = 0
    var halfDays = 0
    val weekendOrHolidayLeaveDates = mutableListOf<String>()

    for (day in 1..daysInMonth) {
        val date = currentMonth.atDay(day)
        val r = attendanceMap[date.toString()] ?: DayAttendance(dateString = date.toString(), dayOfMonth = day)
        val st = r.safeWorkStatus
        val isLeaveTaken = (st == AttendanceStatus.ABSENT || st == AttendanceStatus.HALF_DAY)

        if (st == AttendanceStatus.ABSENT) absentDays++
        if (st == AttendanceStatus.HALF_DAY) halfDays++

        if (isLeaveTaken) {
            val isWeekend = (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY)
            val isHoliday = r.isHoliday
            if (isWeekend || isHoliday) {
                val reasonType = when {
                    isHoliday -> "Holiday"
                    date.dayOfWeek == DayOfWeek.SUNDAY -> "Sun"
                    else -> "Sat"
                }
                weekendOrHolidayLeaveDates.add("$day ($reasonType)")
            }
        }
    }

    val totalLeavesTaken = absentDays + (halfDays * 0.5)
    val isLeavesWithinLimit = totalLeavesTaken <= 3.0
    val hasNoWeekendHolidayLeaves = weekendOrHolidayLeaveDates.isEmpty()

    val isSchemeEligible = isLeavesWithinLimit && hasNoWeekendHolidayLeaves
    val potentialSchemeAmount = incentiveAmount / 2.0

    var previewSchemeOverride by remember { mutableStateOf(false) }

    val effectiveSchemeAmount = if (isSchemeEligible) {
        potentialSchemeAmount
    } else if (previewSchemeOverride) {
        potentialSchemeAmount
    } else {
        0.0
    }

    val grandTotalIncentive = incentiveAmount + effectiveSchemeAmount
    var showSalesList by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = CardBg,
        border = BorderStroke(1.dp, CardBorder),
        shadowElevation = 3.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Sales & Incentive Hub", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Monthly turnover & commission", fontSize = 11.sp, color = TextSecondary)
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEEF2FF),
                    border = BorderStroke(1.dp, Color(0xFFC7D2FE)),
                    modifier = Modifier.clickable { showSalesList = !showSalesList }
                ) {
                    Text(
                        text = if (showSalesList) "Hide Details ▲" else "Day-Wise Sales ▼",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (showSalesList) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(10.dp)
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text("Day-Wise Sales Records:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        val salesDays = records.filter { it.salesAmount > 0 }
                        if (salesDays.isEmpty()) {
                            Text("No sales logged for this month yet. Tap any date to add sales.", fontSize = 11.sp, color = TextSecondary)
                        } else {
                            salesDays.forEach { d ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Day ${d.dayOfMonth} (${currentMonth.atDay(d.dayOfMonth).dayOfWeek.name.take(3)})",
                                        fontSize = 11.5.sp,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = "₹%.2f".format(d.salesAmount),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = CardBorder, thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            InvoiceRow("Total Monthly Sales", "₹%.2f".format(totalSales))
            InvoiceRow("Sales After 5% GST (95%)", "₹%.2f".format(salesAfterGst), isSub = true)
            InvoiceRow("Incentive Earned (1%)", "₹%.2f".format(incentiveAmount), color = SuccessGreen)

            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = Color(0xFFF1F5F9), thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Scheme Bonus (50% of Incentive)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(
                        text = if (isSchemeEligible) {
                            "Status: Eligible (Leaves: %.1f <= 3 & No Sat/Sun/Off leaves)".format(totalLeavesTaken)
                        } else {
                            buildString {
                                append("Status: Disqualified")
                                if (!isLeavesWithinLimit) append(" (Leaves: %.1f > 3)".format(totalLeavesTaken))
                                if (weekendOrHolidayLeaveDates.isNotEmpty()) append(" (Leave on: ${weekendOrHolidayLeaveDates.joinToString(", ")})")
                            }
                        },
                        fontSize = 10.5.sp,
                        color = if (isSchemeEligible) Color(0xFF047857) else DangerRed,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = if (isSchemeEligible || previewSchemeOverride) "+₹%.2f".format(potentialSchemeAmount) else "₹0.00",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSchemeEligible || previewSchemeOverride) SuccessGreen else Color.Gray
                )
            }

            if (!isSchemeEligible) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = WarningBg,
                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Preview Scheme Amount", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                            Text("Switch ON to simulate potential earnings", fontSize = 10.sp, color = Color(0xFFB45309))
                        }
                        Switch(
                            checked = previewSchemeOverride,
                            onCheckedChange = { previewSchemeOverride = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = WarningAmber
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF1E1B4B),
                                Color(0xFF312E81),
                                Color(0xFF4338CA)
                            )
                        )
                    )
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "GRAND TOTAL (INCENTIVE + SCHEME)",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC7D2FE),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "₹%.2f".format(grandTotalIncentive),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun ModernMonthNavigator(
    displayedMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onResetMonth: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = CardBg,
        border = BorderStroke(1.dp, CardBorder),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPreviousMonth,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF1F5F9))
            ) {
                Text("‹", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onResetMonth() }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${displayedMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${displayedMonth.year}",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                if (displayedMonth != YearMonth.now()) {
                    Text(
                        text = "Reset to current month",
                        fontSize = 11.sp,
                        color = BrandPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            IconButton(
                onClick = onNextMonth,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF1F5F9))
            ) {
                Text("›", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        }
    }
}

@Composable
fun ModernLocationBanner(
    isLocationSet: Boolean,
    lat: Double,
    lng: Double,
    onSetGpsClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isLocationSet) SuccessBg else DangerBg,
        border = BorderStroke(1.dp, if (isLocationSet) Color(0xFFA7F3D0) else Color(0xFFFECACA)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(if (isLocationSet) SuccessGreen else DangerRed, CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (isLocationSet) "Workplace Location Locked" else "Workplace GPS Not Configured",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLocationSet) Color(0xFF065F46) else Color(0xFF991B1B)
                    )
                    Text(
                        text = if (isLocationSet) "GPS: %.4f, %.4f".format(lat, lng) else "Auto-attendance will remain inactive",
                        fontSize = 11.sp,
                        color = if (isLocationSet) Color(0xFF047857) else Color(0xFFB91C1C)
                    )
                }
            }

            if (!isLocationSet) {
                Text(
                    text = "SET GPS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DangerRed,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSetGpsClick() }
                        .background(Color.White)
                        .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun ModernCalendarCard(
    currentMonth: YearMonth,
    attendanceMap: Map<String, DayAttendance>,
    onDayClick: (DayAttendance) -> Unit
) {
    val daysInMonth = currentMonth.lengthOfMonth()
    val days = listOf("M", "T", "W", "T", "F", "S", "S")
    val todayString = LocalDate.now().toString()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        border = BorderStroke(1.dp, CardBorder),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                days.forEach { d ->
                    Text(
                        text = d,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.height(330.dp),
                userScrollEnabled = false
            ) {
                val firstDayOffset = currentMonth.atDay(1).dayOfWeek.value - 1
                items(firstDayOffset) {
                    Box(modifier = Modifier.size(46.dp))
                }

                items(daysInMonth) { index ->
                    val dayNum = index + 1
                    val dateStr = currentMonth.atDay(dayNum).toString()
                    val record = attendanceMap[dateStr]?.let {
                        if ((it.safeWorkStatus == AttendanceStatus.ABSENT || it.safeWorkStatus == AttendanceStatus.HALF_DAY) && it.safeDabbaStatus != DabbaStatus.ABSENT) {
                            it.copy(dabbaStatus = DabbaStatus.ABSENT)
                        } else {
                            it
                        }
                    } ?: DayAttendance(dateString = dateStr, dayOfMonth = dayNum)

                    val isToday = (dateStr == todayString)

                    ModernDayCell(day = record, isToday = isToday, onClick = { onDayClick(record) })
                }
            }
        }
    }
}

@Composable
fun ModernDayCell(day: DayAttendance, isToday: Boolean, onClick: () -> Unit) {
    val (bgColor, borderColor) = when {
        isToday -> Pair(Color(0xFFEEF2FF), BrandPrimary)
        day.safeWorkStatus == AttendanceStatus.PRESENT -> Pair(Color(0xFFF0FDF4), Color(0xFFBBF7D0))
        day.safeWorkStatus == AttendanceStatus.HALF_DAY -> Pair(Color(0xFFFFFBEB), Color(0xFFFDE68A))
        day.safeWorkStatus == AttendanceStatus.ABSENT -> Pair(Color(0xFFFEF2F2), Color(0xFFFECACA))
        day.isHoliday -> Pair(Color(0xFFFFF7ED), Color(0xFFFED7AA))
        day.isAmavasya -> Pair(Color(0xFFFAF5FF), Color(0xFFE9D5FF))
        else -> Pair(Color(0xFFFAFAFA), Color(0xFFF1F5F9))
    }

    Surface(
        modifier = Modifier
            .padding(2.5.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(if (isToday) 1.8.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 4.dp, horizontal = 1.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isToday) {
                Text(
                    text = "TODAY",
                    fontSize = 7.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = BrandPrimary
                )
            } else if (day.isHoliday) {
                Text(
                    text = "HOLIDAY",
                    fontSize = 6.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFEA580C),
                    maxLines = 1
                )
            } else if (day.isAmavasya) {
                Text(
                    text = "AMAVASYA",
                    fontSize = 5.2.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AmavasyaPurple,
                    maxLines = 1
                )
            } else {
                Spacer(modifier = Modifier.height(9.dp))
            }

            Text(
                text = "${day.dayOfMonth}",
                fontSize = 12.sp,
                fontWeight = if (isToday || day.isHoliday || day.isAmavasya) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = when {
                    isToday -> BrandPrimary
                    day.isAmavasya -> AmavasyaPurple
                    else -> TextPrimary
                }
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        when (day.safeWorkStatus) {
                            AttendanceStatus.PRESENT -> SuccessGreen
                            AttendanceStatus.HALF_DAY -> WarningAmber
                            AttendanceStatus.ABSENT -> DangerRed
                            else -> Color(0xFFE2E8F0)
                        }
                    )
                    .padding(horizontal = 4.dp, vertical = 0.5.dp)
            ) {
                Text(
                    text = "W: ${day.safeWorkStatus.code}",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (day.safeWorkStatus == AttendanceStatus.NOT_MARKED) Color.DarkGray else Color.White
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "D: ${day.safeDabbaStatus.code}",
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    day.safeDabbaStatus.isAbsent -> DangerRed
                    day.safeDabbaStatus == DabbaStatus.PRESENT -> SuccessGreen
                    else -> Color(0xFF94A3B8)
                }
            )
        }
    }
}

@Composable
fun ModernSummaryDashboard(
    currentMonth: YearMonth,
    attendanceMap: Map<String, DayAttendance>
) {
    val daysInMonth = currentMonth.lengthOfMonth()
    val records = (1..daysInMonth).map { day ->
        val dateStr = currentMonth.atDay(day).toString()
        val r = attendanceMap[dateStr] ?: DayAttendance(dateString = dateStr, dayOfMonth = day)
        if ((r.safeWorkStatus == AttendanceStatus.ABSENT || r.safeWorkStatus == AttendanceStatus.HALF_DAY) && r.safeDabbaStatus != DabbaStatus.ABSENT) {
            r.copy(dabbaStatus = DabbaStatus.ABSENT)
        } else {
            r
        }
    }

    val absentDays = records.filter { it.safeWorkStatus == AttendanceStatus.ABSENT }
    val halfDays = records.filter { it.safeWorkStatus == AttendanceStatus.HALF_DAY }
    val holidayDays = records.filter { it.isHoliday }
    val amavasyaDays = records.filter { it.isAmavasya }
    val totalLeaves = absentDays.size + (halfDays.size * 0.5)

    val dabbaA = records.filter { it.safeDabbaStatus == DabbaStatus.ABSENT }
    val dabbaG = records.filter { it.safeDabbaStatus == DabbaStatus.G }
    val dabbaD = records.filter { it.safeDabbaStatus == DabbaStatus.D }
    val dabbaL = records.filter { it.safeDabbaStatus == DabbaStatus.L }
    val totalDabbaAbsent = dabbaA.size + dabbaG.size + dabbaD.size + dabbaL.size

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        border = BorderStroke(1.dp, CardBorder),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Monthly Performance Metrics",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Leaves tracking and allowance overview",
                fontSize = 11.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Absent",
                    value = "${absentDays.size} Days",
                    dates = if (absentDays.isEmpty()) "None" else absentDays.joinToString(",") { "${it.dayOfMonth}" },
                    color = DangerRed,
                    bgColor = DangerBg,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Half-Days",
                    value = "${halfDays.size} Days",
                    dates = if (halfDays.isEmpty()) "None" else halfDays.joinToString(",") { "${it.dayOfMonth}" },
                    color = WarningAmber,
                    bgColor = WarningBg,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Leaves Taken",
                    value = "%.1f Days".format(totalLeaves),
                    dates = if (totalLeaves > 4) "Deduction (${totalLeaves - 4}d)" else "Free Limit: 4",
                    color = if (totalLeaves > 4) DangerRed else SuccessGreen,
                    bgColor = if (totalLeaves > 4) DangerBg else SuccessBg,
                    modifier = Modifier.weight(1f)
                )
            }

            if (holidayDays.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFFF7ED),
                    border = BorderStroke(1.dp, Color(0xFFFED7AA)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎉", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Holidays Marked (${holidayDays.size}):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC2410C)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = holidayDays.joinToString(" • ") {
                                val name = it.safeHolidayName
                                if (name != "Holiday") "${it.dayOfMonth} ($name)" else "${it.dayOfMonth}"
                            },
                            fontSize = 11.sp,
                            color = Color(0xFF9A3412),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (amavasyaDays.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFAF5FF),
                    border = BorderStroke(1.dp, Color(0xFFE9D5FF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🌑", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Amavasya Marked (${amavasyaDays.size}):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmavasyaPurple
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = amavasyaDays.joinToString(" • ") {
                                val name = it.safeAmavasyaName
                                if (name != "Amavasya") "${it.dayOfMonth} ($name)" else "${it.dayOfMonth}"
                            },
                            fontSize = 11.sp,
                            color = Color(0xFF6B21A8),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = CardBorder, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Dabba Absents by Category (₹900/mo)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DabbaMiniBadge("A", dabbaA.size, modifier = Modifier.weight(1f))
                DabbaMiniBadge("G", dabbaG.size, modifier = Modifier.weight(1f))
                DabbaMiniBadge("D", dabbaD.size, modifier = Modifier.weight(1f))
                DabbaMiniBadge("L", dabbaL.size, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF1F5F9),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Total Dabba Absents (A+G+D+L)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(
                        text = "$totalDabbaAbsent Days",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (totalDabbaAbsent > 0) DangerRed else SuccessGreen
                    )
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    dates: String,
    color: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = dates,
                fontSize = 9.sp,
                maxLines = 1,
                color = color.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun DabbaMiniBadge(code: String, count: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("\"$code\"", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            Text(
                text = "$count",
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (count > 0) DangerRed else TextPrimary
            )
        }
    }
}

@Composable
fun ModernSalaryInvoiceCard(report: SalaryReport) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = CardBg,
        border = BorderStroke(1.dp, CardBorder),
        shadowElevation = 3.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Salary Breakdown", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Based on verified attendance", fontSize = 11.sp, color = TextSecondary)
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Text(
                        "${report.totalMonthDays} Days",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = CardBorder, thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            InvoiceRow("Net Monthly Salary", "₹%.2f".format(report.netMonthlySalary))
            InvoiceRow("Per Day Salary Rate", "₹%.2f".format(report.perDaySalary), isSub = true)
            InvoiceRow("Leaves Taken (Free: 4)", "%.1f Days".format(report.leaveDaysTaken), isSub = true)

            if (report.leaveBonus > 0) {
                InvoiceRow("Leaves Bonus (Unused Free Days)", "+₹%.2f".format(report.leaveBonus), color = SuccessGreen)
            } else if (report.leaveDeduction > 0) {
                InvoiceRow("Leaves Deduction (> 4 Days)", "-₹%.2f".format(report.leaveDeduction), color = DangerRed)
            }

            if (report.fifthMondayBonus > 0) {
                InvoiceRow("5th Monday Extra Day Bonus", "+₹%.2f".format(report.fifthMondayBonus), color = SuccessGreen)
            }

            if (report.schemeBonus > 0) {
                InvoiceRow("Company Scheme Bonus", "+₹%.2f".format(report.schemeBonus), color = SuccessGreen)
            }

            Spacer(modifier = Modifier.height(6.dp))
            Divider(color = Color(0xFFF1F5F9), thickness = 1.dp)
            Spacer(modifier = Modifier.height(6.dp))

            InvoiceRow("Professional Tax (PT)", "-₹%.2f".format(report.professionalTax), color = DangerRed)

            if (report.pfDeduction > 0) {
                InvoiceRow("Provident Fund (PF)", "-₹%.2f".format(report.pfDeduction), color = DangerRed)
            }

            InvoiceRow("Medical Fund", "-₹%.2f".format(report.medicalDeduction), color = DangerRed)
            InvoiceRow("Dabba Deduction (${report.dabbaAbsentDays} Days Missed)", "-₹%.2f".format(report.dabbaDeduction), color = DangerRed)

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = CardBorder, thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total Monthly Deductions", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("-₹%.2f".format(report.totalDeduction), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = DangerRed)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF064E3B),
                                Color(0xFF047857),
                                Color(0xFF059669)
                            )
                        )
                    )
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "GRAND TOTAL NET PAYOUT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA7F3D0),
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "₹%.2f".format(report.grandTotalSalary),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = (-0.5).sp
                    )
                }
            }
        }
    }
}

@Composable
fun InvoiceRow(
    label: String,
    value: String,
    color: Color = TextPrimary,
    isSub: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = if (isSub) 12.sp else 13.sp,
            color = if (isSub) TextSecondary else TextPrimary,
            fontWeight = if (isSub) FontWeight.Normal else FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = if (isSub) 12.sp else 13.sp,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}
