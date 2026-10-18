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
    var selectedDayForEdit by remember { mutableStateOf<DayAttendance?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val isLocationSet = settings.workLatitude != 0.0 && settings.workLongitude != 0.0

    val report = remember(currentMonth, attendanceMap.values.toList(), settings) {
        SalaryCalculator.calculate(currentMonth, attendanceMap, settings)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Attendance & Payroll", fontWeight = FontWeight.Bold) },
                actions = {
                    Button(onClick = { showSettingsDialog = true }) {
                        Text("Config")
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
            // Month Header & GPS Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${currentMonth.year}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Button(onClick = onSetLocation) {
                    Text(if (isLocationSet) "Update GPS" else "Set Work GPS")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Work Location Indicator Badge
            LocationStatusBadge(isLocationSet = isLocationSet, lat = settings.workLatitude, lng = settings.workLongitude)

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Calendar Table (with Today highlighted)
            CalendarView(
                currentMonth = currentMonth,
                attendanceMap = attendanceMap,
                onDayClick = { selectedDayForEdit = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Summary Box: Absent, H-Days, Total Leaves & Dabba Absents
            AttendanceSummaryCard(
                currentMonth = currentMonth,
                attendanceMap = attendanceMap
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Salary Breakdown Card
            SalaryReportCard(report = report)
        }
    }

    // Manual Edit Dialog
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
                    text = "Workplace GPS: NOT Set (Tap 'Set Work GPS' at work)",
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
                val record = attendanceMap[dateStr] ?: DayAttendance(dateStr, dayNum)
                val isToday = (dateStr == todayString)

                DayCell(day = record, isToday = isToday, onClick = { onDayClick(record) })
            }
        }
    }
}

@Composable
fun DayCell(day: DayAttendance, isToday: Boolean, onClick: () -> Unit) {
    val statusColor = when (day.workStatus) {
        AttendanceStatus.PRESENT -> Color(0xFFC8E6C9)
        AttendanceStatus.HALF_DAY -> Color(0xFFFFF59D)
        AttendanceStatus.ABSENT -> Color(0xFFFFCDD2)
        AttendanceStatus.NOT_MARKED -> Color(0xFFF9F9F9)
    }

    val cellBorder = if (isToday) {
        BorderStroke(2.dp, Color(0xFF0D47A1)) // Prominent Blue border for Today
    } else {
        BorderStroke(0.5.dp, Color.LightGray)
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
            modifier = Modifier.padding(vertical = 3.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isToday) {
                Text(
                    text = "TODAY",
                    fontSize = 7.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0D47A1)
                )
            }
            Text(
                text = "${day.dayOfMonth}",
                fontSize = 11.sp,
                fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Bold,
                color = if (isToday) Color(0xFF0D47A1) else Color.Black
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
                fontWeight = FontWeight.SemiBold,
                color = if (day.dabbaStatus == DabbaStatus.ABSENT) Color.Red else Color.DarkGray
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
        attendanceMap[dateStr] ?: DayAttendance(dateStr, day)
    }

    val absentDays = records.filter { it.workStatus == AttendanceStatus.ABSENT }
    val halfDays = records.filter { it.workStatus == AttendanceStatus.HALF_DAY }
    val dabbaAbsentDays = records.filter { it.dabbaStatus == DabbaStatus.ABSENT }

    val totalLeaves = absentDays.size + (halfDays.size * 0.5)

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

            Text(
                text = "Work Attendance",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF333333)
            )
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

            Text(
                text = "Dabba Attendance",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF333333)
            )
            Spacer(modifier = Modifier.height(4.dp))

            SummaryItemRow(
                label = "Dabba Absent Dates:",
                dateList = if (dabbaAbsentDays.isEmpty()) "None" else dabbaAbsentDays.joinToString(", ") { "${it.dayOfMonth}" },
                textColor = if (dabbaAbsentDays.isNotEmpty()) Color(0xFFD32F2F) else Color.DarkGray
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Total Dabba Absent:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(
                    text = "${dabbaAbsentDays.size} Days",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (dabbaAbsentDays.isNotEmpty()) Color(0xFFD32F2F) else Color(0xFF2E7D32)
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
            SalaryRow("Dabba Deduction (${report.dabbaAbsentDays} Days A)", "-₹%.2f".format(report.dabbaDeduction), Color.Red)

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

@Composable
fun EditAttendanceDialog(
    day: DayAttendance,
    onDismiss: () -> Unit,
    onSave: (DayAttendance) -> Unit
) {
    var workStatus by remember { mutableStateOf(day.workStatus) }
    var dabbaStatus by remember { mutableStateOf(day.dabbaStatus) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Day: ${day.dateString}") },
        text = {
            Column {
                Text("Work Attendance:", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(AttendanceStatus.PRESENT, AttendanceStatus.HALF_DAY, AttendanceStatus.ABSENT).forEach { st ->
                        FilterChip(
                            selected = workStatus == st,
                            onClick = { workStatus = st },
                            label = { Text(st.code) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Dabba Attendance (₹900/mo):", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(DabbaStatus.PRESENT, DabbaStatus.ABSENT).forEach { st ->
                        FilterChip(
                            selected = dabbaStatus == st,
                            onClick = { dabbaStatus = st },
                            label = { Text(st.code) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(day.copy(workStatus = workStatus, dabbaStatus = dabbaStatus, isManual = true))
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
