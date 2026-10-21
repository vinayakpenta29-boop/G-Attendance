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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// --- Compatibility Extensions (Prevents Unresolved Reference Errors) ---
private val DayAttendance.safeWorkStatus: AttendanceStatus
    get() = this.workStatus ?: AttendanceStatus.NOT_MARKED

private val DayAttendance.safeDabbaStatus: DabbaStatus
    get() = this.dabbaStatus ?: DabbaStatus.NOT_MARKED

private val DayAttendance.safeHolidayName: String
    get() = if (this.holidayName.isNullOrBlank()) "Holiday" else this.holidayName!!

// --- Premium Design System Colors ---
private val SlateBg = Color(0xFFF8FAFC)
private val CardBg = Color(0xFFFFFFFF)
private val CardBorder = Color(0xFFE2E8F0)
private val TextPrimary = Color(0xFF0F172A)
private val TextSecondary = Color(0xFF64748B)

private val BrandPrimary = Color(0xFF4F46E5) // Indigo 600
private val BrandSecondary = Color(0xFF6366F1) // Indigo 500

private val SuccessGreen = Color(0xFF10B981) // Emerald 500
private val SuccessBg = Color(0xFFECFDF5)
private val DangerRed = Color(0xFFEF4444) // Rose 500
private val DangerBg = Color(0xFFFEF2F2)
private val WarningAmber = Color(0xFFF59E0B) // Amber 500
private val WarningBg = Color(0xFFFFFBEB)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    currentMonth: YearMonth,
    attendanceMap: MutableMap<String, DayAttendance>,
    settings: SalarySettings,
    onSaveSettings: (SalarySettings) -> Unit,
    onUpdateDay: (DayAttendance) -> Unit,
    onSetLocation: () -> Unit
) {
    var displayedMonth by remember { mutableStateOf(currentMonth) }
    var selectedDayForEdit by remember { mutableStateOf<DayAttendance?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showHolidayDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    val isLocationSet = settings.workLatitude != 0.0 && settings.workLongitude != 0.0

    val report = remember(displayedMonth, attendanceMap.values.toList(), settings) {
        SalaryCalculator.calculate(displayedMonth, attendanceMap, settings)
    }

    Scaffold(
        containerColor = SlateBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = TextPrimary
                ),
                modifier = Modifier.shadow(2.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Brush.linearGradient(listOf(BrandPrimary, BrandSecondary))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("₹", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Attendance & Payroll",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            letterSpacing = (-0.3).sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Text(
                            text = "⋮",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Salary Configuration", fontWeight = FontWeight.Medium) },
                            onClick = {
                                menuExpanded = false
                                showSettingsDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add / Manage Holidays", fontWeight = FontWeight.Medium) },
                            onClick = {
                                menuExpanded = false
                                showHolidayDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isLocationSet) "Update Work GPS" else "Set Work GPS", fontWeight = FontWeight.Medium) },
                            onClick = {
                                menuExpanded = false
                                onSetLocation()
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // 1. Month Navigation Header
            ModernMonthNavigator(
                displayedMonth = displayedMonth,
                onPreviousMonth = { displayedMonth = displayedMonth.minusMonths(1) },
                onNextMonth = { displayedMonth = displayedMonth.plusMonths(1) },
                onResetMonth = { displayedMonth = YearMonth.now() }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. GPS Status Banner
            ModernLocationBanner(
                isLocationSet = isLocationSet,
                lat = settings.workLatitude,
                lng = settings.workLongitude,
                onSetGpsClick = onSetLocation
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Calendar View
            ModernCalendarCard(
                currentMonth = displayedMonth,
                attendanceMap = attendanceMap,
                onDayClick = { selectedDayForEdit = it }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 4. Metric Dashboard (Summary of Attendance & Dabba)
            ModernSummaryDashboard(
                currentMonth = displayedMonth,
                attendanceMap = attendanceMap
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 5. Executive Salary Report Card
            ModernSalaryInvoiceCard(report = report)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Manual Edit Day Dialog
    selectedDayForEdit?.let { day ->
        EditAttendanceDialog(
            day = day,
            onDismiss = { selectedDayForEdit = null },
            onSave = { updated ->
                onUpdateDay(updated)
                selectedDayForEdit = null
            }
        )
    }

    // Manage Holidays Dialog
    if (showHolidayDialog) {
        ManageHolidayDialog(
            displayedMonth = displayedMonth,
            attendanceMap = attendanceMap,
            onDismiss = { showHolidayDialog = false },
            onSaveHolidays = { holidayMap ->
                val daysInMonth = displayedMonth.lengthOfMonth()
                for (day in 1..daysInMonth) {
                    val dateStr = displayedMonth.atDay(day).toString()
                    val existing = attendanceMap[dateStr] ?: DayAttendance(dateString = dateStr, dayOfMonth = day)
                    val hName = holidayMap[day]
                    val isHoliday = hName != null
                    if (existing.isHoliday != isHoliday || existing.safeHolidayName != (hName ?: "")) {
                        onUpdateDay(
                            existing.copy(
                                isHoliday = isHoliday,
                                holidayName = hName ?: ""
                            )
                        )
                    }
                }
                showHolidayDialog = false
            }
        )
    }

    // Salary Configuration Dialog
    if (showSettingsDialog) {
        SettingsDialog(
            initial = settings,
            onDismiss = { showSettingsDialog = false },
            onSave = {
                onSaveSettings(it)
                showSettingsDialog = false
            }
        )
    }
}

// -------------------------------------------------------------
// SUB-COMPONENTS: MODERN UI SYSTEM
// -------------------------------------------------------------

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
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp),
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
                    text = "OFF",
                    fontSize = 6.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFEA580C)
                )
            } else {
                Spacer(modifier = Modifier.height(9.dp))
            }

            Text(
                text = "${day.dayOfMonth}",
                fontSize = 12.sp,
                fontWeight = if (isToday || day.isHoliday) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = if (isToday) BrandPrimary else TextPrimary
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

// -------------------------------------------------------------
// DIALOGS: REFINED & MODERNIZED
// -------------------------------------------------------------

@Composable
fun EditAttendanceDialog(
    day: DayAttendance,
    onDismiss: () -> Unit,
    onSave: (DayAttendance) -> Unit
) {
    var workStatus by remember { mutableStateOf<AttendanceStatus>(day.safeWorkStatus) }
    var dabbaStatus by remember {
        mutableStateOf<DabbaStatus>(
            if (day.safeWorkStatus == AttendanceStatus.ABSENT || day.safeWorkStatus == AttendanceStatus.HALF_DAY) {
                DabbaStatus.ABSENT
            } else {
                day.safeDabbaStatus
            }
        )
    }

    val isWorkAbsentOrHalf = workStatus == AttendanceStatus.ABSENT || workStatus == AttendanceStatus.HALF_DAY

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Edit Record: ${day.dateString}",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (day.isHoliday) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFFF7ED),
                        border = BorderStroke(1.dp, Color(0xFFFED7AA)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🎉", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Holiday: ${day.safeHolidayName}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFFC2410C)
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Regular Working Day",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text("Work Attendance", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(AttendanceStatus.PRESENT, AttendanceStatus.HALF_DAY, AttendanceStatus.ABSENT).forEach { st ->
                        FilterChip(
                            selected = workStatus == st,
                            onClick = {
                                workStatus = st
                                if (st == AttendanceStatus.ABSENT || st == AttendanceStatus.HALF_DAY) {
                                    dabbaStatus = DabbaStatus.ABSENT
                                }
                            },
                            label = { Text(st.code, fontWeight = FontWeight.Bold) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Dabba Allowance", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)

                if (isWorkAbsentOrHalf) {
                    Text(
                        text = "Work is '${workStatus.code}' → Dabba auto-locked to Absent",
                        fontSize = 11.sp,
                        color = DangerRed,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        DabbaStatus.PRESENT,
                        DabbaStatus.ABSENT,
                        DabbaStatus.G,
                        DabbaStatus.D,
                        DabbaStatus.L,
                        DabbaStatus.NOT_MARKED
                    ).forEach { st ->
                        val isEnabled = !isWorkAbsentOrHalf || st == DabbaStatus.ABSENT

                        FilterChip(
                            selected = dabbaStatus == st,
                            onClick = {
                                if (isEnabled) {
                                    dabbaStatus = st
                                }
                            },
                            enabled = isEnabled,
                            label = { Text(st.code, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalDabba = if (isWorkAbsentOrHalf) DabbaStatus.ABSENT else dabbaStatus
                    onSave(
                        day.copy(
                            workStatus = workStatus,
                            dabbaStatus = finalDabba,
                            isManual = true,
                            holidayName = day.holidayName ?: ""
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ManageHolidayDialog(
    displayedMonth: YearMonth,
    attendanceMap: Map<String, DayAttendance>,
    onDismiss: () -> Unit,
    onSaveHolidays: (Map<Int, String>) -> Unit
) {
    val daysInMonth = displayedMonth.lengthOfMonth()

    val holidayMap = remember {
        mutableStateMapOf<Int, String>().apply {
            for (day in 1..daysInMonth) {
                val dateStr = displayedMonth.atDay(day).toString()
                val record = attendanceMap[dateStr]
                if (record?.isHoliday == true) {
                    put(day, record.safeHolidayName)
                }
            }
        }
    }

    var editingDay by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Manage Holidays",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Tap a date number to set holiday title:",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                ) {
                    items(daysInMonth) { index ->
                        val dayNum = index + 1
                        val isHoliday = holidayMap.containsKey(dayNum)

                        Box(
                            modifier = Modifier
                                .padding(3.dp)
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isHoliday) WarningAmber else Color(0xFFF1F5F9))
                                .clickable { editingDay = dayNum },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$dayNum",
                                fontSize = 12.sp,
                                fontWeight = if (isHoliday) FontWeight.Bold else FontWeight.Normal,
                                color = if (isHoliday) Color.White else TextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (holidayMap.isNotEmpty()) {
                    Text(
                        text = "Configured Holidays (${holidayMap.size}):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WarningAmber
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 70.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        holidayMap.entries.sortedBy { it.key }.forEach { (day, name) ->
                            Text(
                                text = "• Day $day: $name",
                                fontSize = 11.sp,
                                color = Color(0xFF9A3412)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveHolidays(holidayMap.toMap()) },
                colors = ButtonDefaults.buttonColors(containerColor = WarningAmber)
            ) {
                Text("Save Holidays", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    editingDay?.let { day ->
        var nameInput by remember { mutableStateOf(holidayMap[day] ?: "") }

        AlertDialog(
            onDismissRequest = { editingDay = null },
            title = { Text("Holiday Name (Day $day)", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    placeholder = { Text("e.g. Diwali, Dasara") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalName = nameInput.trim().ifBlank { "Holiday" }
                        holidayMap[day] = finalName
                        editingDay = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WarningAmber)
                ) {
                    Text("Set", color = Color.White)
                }
            },
            dismissButton = {
                Row {
                    if (holidayMap.containsKey(day)) {
                        TextButton(
                            onClick = {
                                holidayMap.remove(day)
                                editingDay = null
                            }
                        ) {
                            Text("Remove", color = DangerRed)
                        }
                    }
                    TextButton(onClick = { editingDay = null }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
fun SettingsDialog(
    initial: SalarySettings,
    onDismiss: () -> Unit,
    onSave: (SalarySettings) -> Unit
) {
    var netSalary by remember { mutableStateOf(initial.netMonthlySalary.toString()) }
    var pt by remember { mutableStateOf(initial.professionalTax.toString()) }
    var medical by remember { mutableStateOf(initial.medicalFund.toString()) }
    var hasPf by remember { mutableStateOf(initial.isPfApplicable) }
    var pfAmt by remember { mutableStateOf(initial.pfAmount.toString()) }
    var hasScheme by remember { mutableStateOf(initial.isSchemeApplicable) }
    var schemeAmt by remember { mutableStateOf(initial.schemeAmount.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Salary Configuration", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = netSalary,
                    onValueChange = { netSalary = it },
                    label = { Text("Net Monthly Salary (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pt,
                    onValueChange = { pt = it },
                    label = { Text("Professional Tax (PT)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = medical,
                    onValueChange = { medical = it },
                    label = { Text("Medical Fund (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = hasPf, onCheckedChange = { hasPf = it })
                    Text("Enable Provident Fund (PF)")
                }
                if (hasPf) {
                    OutlinedTextField(
                        value = pfAmt,
                        onValueChange = { pfAmt = it },
                        label = { Text("PF Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = hasScheme, onCheckedChange = { hasScheme = it })
                    Text("Enable Company Scheme")
                }
                if (hasScheme) {
                    OutlinedTextField(
                        value = schemeAmt,
                        onValueChange = { schemeAmt = it },
                        label = { Text("Scheme Bonus Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            netMonthlySalary = netSalary.toDoubleOrNull() ?: 0.0,
                            professionalTax = pt.toDoubleOrNull() ?: 0.0,
                            medicalFund = medical.toDoubleOrNull() ?: 0.0,
                            isPfApplicable = hasPf,
                            pfAmount = pfAmt.toDoubleOrNull() ?: 0.0,
                            isSchemeApplicable = hasScheme,
                            schemeAmount = schemeAmt.toDoubleOrNull() ?: 0.0
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
            ) {
                Text("Save Configuration")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
