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
        topBar = {
            TopAppBar(
                title = { Text("Attendance & Payroll", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Text(
                            text = "⋮",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Salary Config") },
                            onClick = {
                                menuExpanded = false
                                showSettingsDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add Holiday") },
                            onClick = {
                                menuExpanded = false
                                showHolidayDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isLocationSet) "Update Work GPS" else "Set Work GPS") },
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
                .padding(16.dp)
        ) {
            // Month Navigation Bar
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = { displayedMonth = displayedMonth.minusMonths(1) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Text(text = "◀", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { displayedMonth = YearMonth.now() }
                    ) {
                        Text(
                            text = "${displayedMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${displayedMonth.year}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (displayedMonth != YearMonth.now()) {
                            Text(
                                text = "Tap to reset to Current Month",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    FilledTonalIconButton(
                        onClick = { displayedMonth = displayedMonth.plusMonths(1) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Text(text = "▶", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LocationStatusBadge(isLocationSet = isLocationSet, lat = settings.workLatitude, lng = settings.workLongitude)

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Calendar View
            CalendarView(
                currentMonth = displayedMonth,
                attendanceMap = attendanceMap,
                onDayClick = { selectedDayForEdit = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Summary Box
            AttendanceSummaryCard(
                currentMonth = displayedMonth,
                attendanceMap = attendanceMap
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Salary Breakdown Card
            SalaryReportCard(report = report)
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

    // Add / Manage Holiday Dialog
    if (showHolidayDialog) {
        ManageHolidayDialog(
            displayedMonth = displayedMonth,
            attendanceMap = attendanceMap,
            onDismiss = { showHolidayDialog = false },
            onSaveHolidays = { holidayDaysSet ->
                val daysInMonth = displayedMonth.lengthOfMonth()
                for (day in 1..daysInMonth) {
                    val dateStr = displayedMonth.atDay(day).toString()
                    val existing = attendanceMap[dateStr] ?: DayAttendance(dateStr, day)
                    val isMarkedHoliday = holidayDaysSet.contains(day)
                    if (existing.isHoliday != isMarkedHoliday) {
                        onUpdateDay(existing.copy(isHoliday = isMarkedHoliday))
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

@Composable
fun LocationStatusBadge(isLocationSet: Boolean, lat: Double, lng: Double) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isLocationSet) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
        border = BorderStroke(1.dp, if (isLocationSet) Color(0xFF4CAF50) else Color(0xFFF44336)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(if (isLocationSet) Color(0xFF2E7D32) else Color(0xFFD32F2F), CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (isLocationSet) {
                Text(
                    text = "Workplace GPS: Set (%.4f, %.4f)".format(lat, lng),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1B5E20)
                )
            } else {
                Text(
                    text = "Workplace GPS: NOT Set (Open ⋮ menu -> 'Set Work GPS')",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFC62828)
                )
            }
        }
    }
}

@Composable
fun CalendarView(
    currentMonth: YearMonth,
    attendanceMap: Map<String, DayAttendance>,
    onDayClick: (DayAttendance) -> Unit
) {
    val daysInMonth = currentMonth.lengthOfMonth()
    val days = listOf("M", "T", "W", "T", "F", "S", "S")
    val todayString = LocalDate.now().toString()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFCCCCCC), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            days.forEach { d ->
                Text(
                    text = d,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    color = Color.DarkGray
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.height(310.dp),
            userScrollEnabled = false
        ) {
            val firstDayOffset = currentMonth.atDay(1).dayOfWeek.value - 1
            items(firstDayOffset) {
                Box(modifier = Modifier.size(45.dp))
            }

            items(daysInMonth) { index ->
                val dayNum = index + 1
                val dateStr = currentMonth.atDay(dayNum).toString()
                val record = attendanceMap[dateStr]?.let {
                    if ((it.workStatus == AttendanceStatus.ABSENT || it.workStatus == AttendanceStatus.HALF_DAY) && it.dabbaStatus != DabbaStatus.ABSENT) {
                        it.copy(dabbaStatus = DabbaStatus.ABSENT)
                    } else {
                        it
                    }
                } ?: DayAttendance(dateStr, dayNum)

                val isToday = (dateStr == todayString)

                DayCell(day = record, isToday = isToday, onClick = { onDayClick(record) })
            }
        }
    }
}

@Composable
fun DayCell(day: DayAttendance, isToday: Boolean, onClick: () -> Unit) {
    // If not marked but marked as holiday, show soft amber color
    val statusColor = when {
        day.workStatus == AttendanceStatus.PRESENT -> Color(0xFFC8E6C9)
        day.workStatus == AttendanceStatus.HALF_DAY -> Color(0xFFFFF59D)
        day.workStatus == AttendanceStatus.ABSENT -> Color(0xFFFFCDD2)
        day.isHoliday -> Color(0xFFFFE0B2) // Warm amber holiday tint
        else -> Color(0xFFF9F9F9)
    }

    val cellBorder = when {
        isToday -> BorderStroke(2.dp, Color(0xFF0D47A1)) // Prominent Blue border for today
        day.isHoliday -> BorderStroke(1.5.dp, Color(0xFFFF9800)) // Amber border for holidays
        else -> BorderStroke(0.5.dp, Color.LightGray)
    }

    Surface(
        modifier = Modifier
            .padding(2.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(4.dp),
        color = statusColor,
        border = cellBorder
    ) {
        Column(
            modifier = Modifier.padding(vertical = 2.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isToday) {
                Text(
                    text = "TODAY",
                    fontSize = 7.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0D47A1)
                )
            } else if (day.isHoliday) {
                Text(
                    text = "HOLIDAY",
                    fontSize = 6.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFE65100)
                )
            }

            Text(
                text = "${day.dayOfMonth}",
                fontSize = 11.sp,
                fontWeight = if (isToday || day.isHoliday) FontWeight.ExtraBold else FontWeight.Bold,
                color = when {
                    isToday -> Color(0xFF0D47A1)
                    day.isHoliday -> Color(0xFFBF360C)
                    else -> Color.Black
                }
            )
            Text(
                text = "W: ${day.workStatus.code}",
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (day.workStatus == AttendanceStatus.ABSENT) Color.Red else Color.Black
            )
            Text(
                text = "D: ${day.dabbaStatus.code}",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    day.dabbaStatus.isAbsent -> Color.Red
                    day.dabbaStatus == DabbaStatus.PRESENT -> Color(0xFF2E7D32)
                    else -> Color.Gray
                }
            )
        }
    }
}

@Composable
fun AttendanceSummaryCard(
    currentMonth: YearMonth,
    attendanceMap: Map<String, DayAttendance>
) {
    val daysInMonth = currentMonth.lengthOfMonth()
    val records = (1..daysInMonth).map { day ->
        val dateStr = currentMonth.atDay(day).toString()
        val r = attendanceMap[dateStr] ?: DayAttendance(dateStr, day)
        if ((r.workStatus == AttendanceStatus.ABSENT || r.workStatus == AttendanceStatus.HALF_DAY) && r.dabbaStatus != DabbaStatus.ABSENT) {
            r.copy(dabbaStatus = DabbaStatus.ABSENT)
        } else {
            r
        }
    }

    val absentDays = records.filter { it.workStatus == AttendanceStatus.ABSENT }
    val halfDays = records.filter { it.workStatus == AttendanceStatus.HALF_DAY }
    val holidayDays = records.filter { it.isHoliday }
    val totalLeaves = absentDays.size + (halfDays.size * 0.5)

    val dabbaA = records.filter { it.dabbaStatus == DabbaStatus.ABSENT }
    val dabbaG = records.filter { it.dabbaStatus == DabbaStatus.G }
    val dabbaD = records.filter { it.dabbaStatus == DabbaStatus.D }
    val dabbaL = records.filter { it.dabbaStatus == DabbaStatus.L }
    val totalDabbaAbsent = dabbaA.size + dabbaG.size + dabbaD.size + dabbaL.size

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFDCDCDC))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Attendance & Leaves Summary",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Work Attendance
            Text(text = "Work Attendance", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))

            SummaryItemRow(
                label = "Absent Days (${absentDays.size}):",
                dateList = if (absentDays.isEmpty()) "None" else absentDays.joinToString(", ") { "${it.dayOfMonth}" },
                textColor = if (absentDays.isNotEmpty()) Color(0xFFD32F2F) else Color.DarkGray
            )

            SummaryItemRow(
                label = "Half-Days / H (${halfDays.size}):",
                dateList = if (halfDays.isEmpty()) "None" else halfDays.joinToString(", ") { "${it.dayOfMonth}" },
                textColor = if (halfDays.isNotEmpty()) Color(0xFFE65100) else Color.DarkGray
            )

            if (holidayDays.isNotEmpty()) {
                SummaryItemRow(
                    label = "Holidays Marked (${holidayDays.size}):",
                    dateList = holidayDays.joinToString(", ") { "${it.dayOfMonth}" },
                    textColor = Color(0xFFE65100)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Total Leaves Taken:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(
                    text = "%.1f Days".format(totalLeaves),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (totalLeaves > 4.0) Color(0xFFD32F2F) else Color(0xFF2E7D32)
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Dabba Section
            Text(text = "Dabba Attendance (Absents by Category)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))

            SummaryItemRow(
                label = "\"A\" Dabba Count (${dabbaA.size}):",
                dateList = if (dabbaA.isEmpty()) "None" else dabbaA.joinToString(", ") { "${it.dayOfMonth}" },
                textColor = if (dabbaA.isNotEmpty()) Color(0xFFD32F2F) else Color.DarkGray
            )

            SummaryItemRow(
                label = "\"G\" Dabba Count (${dabbaG.size}):",
                dateList = if (dabbaG.isEmpty()) "None" else dabbaG.joinToString(", ") { "${it.dayOfMonth}" },
                textColor = if (dabbaG.isNotEmpty()) Color(0xFFD32F2F) else Color.DarkGray
            )

            SummaryItemRow(
                label = "\"D\" Dabba Count (${dabbaD.size}):",
                dateList = if (dabbaD.isEmpty()) "None" else dabbaD.joinToString(", ") { "${it.dayOfMonth}" },
                textColor = if (dabbaD.isNotEmpty()) Color(0xFFD32F2F) else Color.DarkGray
            )

            SummaryItemRow(
                label = "\"L\" Dabba Count (${dabbaL.size}):",
                dateList = if (dabbaL.isEmpty()) "None" else dabbaL.joinToString(", ") { "${it.dayOfMonth}" },
                textColor = if (dabbaL.isNotEmpty()) Color(0xFFD32F2F) else Color.DarkGray
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Total Dabba Absent (A+G+D+L):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    text = "$totalDabbaAbsent Days",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    color = if (totalDabbaAbsent > 0) Color(0xFFD32F2F) else Color(0xFF2E7D32)
                )
            }
        }
    }
}

@Composable
fun SummaryItemRow(
    label: String,
    dateList: String,
    textColor: Color
) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(
            text = "Dates: $dateList",
            fontSize = 12.sp,
            color = textColor,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(start = 4.dp, top = 1.dp)
        )
    }
}

@Composable
fun SalaryReportCard(report: SalaryReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Salary Breakdown",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            SalaryRow("Net Monthly Salary", "₹%.2f".format(report.netMonthlySalary))
            SalaryRow("Per Day Salary (${report.totalMonthDays} Days)", "₹%.2f".format(report.perDaySalary))
            SalaryRow("Leave Days (Free: 4)", "%.1f Days".format(report.leaveDaysTaken))

            if (report.leaveBonus > 0) {
                SalaryRow("Leaves Bonus (+)", "₹%.2f".format(report.leaveBonus), Color(0xFF2E7D32))
            } else if (report.leaveDeduction > 0) {
                SalaryRow("Leaves Deduction (-)", "₹%.2f".format(report.leaveDeduction), Color.Red)
            }

            if (report.fifthMondayBonus > 0) {
                SalaryRow("5th Monday Bonus (+)", "₹%.2f".format(report.fifthMondayBonus), Color(0xFF2E7D32))
            }

            if (report.schemeBonus > 0) {
                SalaryRow("Scheme Bonus (+)", "₹%.2f".format(report.schemeBonus), Color(0xFF2E7D32))
            }

            SalaryRow("Professional Tax (PT)", "-₹%.2f".format(report.professionalTax), Color.Red)

            if (report.pfDeduction > 0) {
                SalaryRow("PF Deduction", "-₹%.2f".format(report.pfDeduction), Color.Red)
            }

            SalaryRow("Medical Fund", "-₹%.2f".format(report.medicalDeduction), Color.Red)
            SalaryRow("Dabba Deduction (${report.dabbaAbsentDays} Days Absent)", "-₹%.2f".format(report.dabbaDeduction), Color.Red)

            Divider(modifier = Modifier.padding(vertical = 8.dp))
            SalaryRow("Total Deductions", "-₹%.2f".format(report.totalDeduction), Color.Red, isBold = true)

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1B5E20), RoundedCornerShape(8.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "GRAND TOTAL MONTH SALARY", color = Color.White, fontSize = 12.sp)
                    Text(
                        text = "₹%.2f".format(report.grandTotalSalary),
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
fun SalaryRow(label: String, value: String, color: Color = Color.Unspecified, isBold: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal)
        Text(text = value, color = color, fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold)
    }
}

// Dialog for Managing Monthly Holidays
@Composable
fun ManageHolidayDialog(
    displayedMonth: YearMonth,
    attendanceMap: Map<String, DayAttendance>,
    onDismiss: () -> Unit,
    onSaveHolidays: (Set<Int>) -> Unit
) {
    val daysInMonth = displayedMonth.lengthOfMonth()

    // Pre-populate with currently saved holidays for this month
    val selectedHolidays = remember {
        mutableStateListOf<Int>().apply {
            for (day in 1..daysInMonth) {
                val dateStr = displayedMonth.atDay(day).toString()
                if (attendanceMap[dateStr]?.isHoliday == true) {
                    add(day)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Add Holidays - ${displayedMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${displayedMonth.year}",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Tap date numbers to mark/unmark as holiday:",
                    fontSize = 12.sp,
                    color = Color.DarkGray
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Interactive 7-Column Day Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                ) {
                    items(daysInMonth) { index ->
                        val dayNum = index + 1
                        val isSelected = selectedHolidays.contains(dayNum)

                        Box(
                            modifier = Modifier
                                .padding(3.dp)
                                .size(36.dp)
                                .background(
                                    if (isSelected) Color(0xFFFF9800) else Color(0xFFEEEEEE),
                                    RoundedCornerShape(6.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) Color(0xFFE65100) else Color.LightGray,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    if (isSelected) {
                                        selectedHolidays.remove(dayNum)
                                    } else {
                                        selectedHolidays.add(dayNum)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$dayNum",
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else Color.Black
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val sorted = selectedHolidays.sorted()
                Text(
                    text = "Selected (${sorted.size}): " + if (sorted.isEmpty()) "None" else sorted.joinToString(", "),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE65100)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveHolidays(selectedHolidays.toSet()) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
            ) {
                Text("Save Holidays", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun EditAttendanceDialog(
    day: DayAttendance,
    onDismiss: () -> Unit,
    onSave: (DayAttendance) -> Unit
) {
    var workStatus by remember { mutableStateOf(day.workStatus) }
    var isHoliday by remember { mutableStateOf(day.isHoliday) }
    var dabbaStatus by remember {
        mutableStateOf(
            if (day.workStatus == AttendanceStatus.ABSENT || day.workStatus == AttendanceStatus.HALF_DAY) {
                DabbaStatus.ABSENT
            } else {
                day.dabbaStatus
            }
        )
    }

    val isWorkAbsentOrHalf = workStatus == AttendanceStatus.ABSENT || workStatus == AttendanceStatus.HALF_DAY

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Day: ${day.dateString}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Holiday Checkbox
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isHoliday = !isHoliday },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isHoliday,
                        onCheckedChange = { isHoliday = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFFFF9800))
                    )
                    Text(
                        text = "Mark this day as Holiday",
                        fontWeight = FontWeight.Bold,
                        color = if (isHoliday) Color(0xFFE65100) else Color.Unspecified
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                Text("Work Attendance:", fontWeight = FontWeight.Bold)
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
                            label = { Text(st.code) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Dabba Attendance (₹900/mo):", fontWeight = FontWeight.Bold)

                if (isWorkAbsentOrHalf) {
                    Text(
                        text = "Work is '${workStatus.code}' → Dabba auto-set to 'A' (Others Disabled)",
                        fontSize = 11.sp,
                        color = Color(0xFFD32F2F),
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "P = Present | A, G, D, L = Absent | - = Unset",
                        fontSize = 11.sp,
                        color = Color.Gray
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
            Button(onClick = {
                val finalDabba = if (isWorkAbsentOrHalf) DabbaStatus.ABSENT else dabbaStatus
                onSave(
                    day.copy(
                        workStatus = workStatus,
                        dabbaStatus = finalDabba,
                        isHoliday = isHoliday,
                        isManual = true
                    )
                )
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
        title = { Text("Salary Configuration") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = netSalary,
                    onValueChange = { netSalary = it },
                    label = { Text("Net Monthly Salary (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = pt,
                    onValueChange = { pt = it },
                    label = { Text("Professional Tax (PT)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = medical,
                    onValueChange = { medical = it },
                    label = { Text("Medical Fund (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = hasPf, onCheckedChange = { hasPf = it })
                    Text("Enable PF")
                }
                if (hasPf) {
                    OutlinedTextField(
                        value = pfAmt,
                        onValueChange = { pfAmt = it },
                        label = { Text("PF Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = hasScheme, onCheckedChange = { hasScheme = it })
                    Text("Enable Scheme")
                }
                if (hasScheme) {
                    OutlinedTextField(
                        value = schemeAmt,
                        onValueChange = { schemeAmt = it },
                        label = { Text("Scheme Bonus Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
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
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
