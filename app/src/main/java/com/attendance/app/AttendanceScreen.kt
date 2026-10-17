package com.attendance.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
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
            // Month Header
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
                    Text("Set Work GPS")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Calendar Table
            CalendarView(
                currentMonth = currentMonth,
                attendanceMap = attendanceMap,
                onDayClick = { selectedDayForEdit = it }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Salary Breakdown Card
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

    // Salary Inputs Dialog
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
fun CalendarView(
    currentMonth: YearMonth,
    attendanceMap: Map<String, DayAttendance>,
    onDayClick: (DayAttendance) -> Unit
) {
    val daysInMonth = currentMonth.lengthOfMonth()
    val days = listOf("M", "T", "W", "T", "F", "S", "S")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // Week Header
        Row(modifier = Modifier.fillMaxWidth()) {
            days.forEach { d ->
                Text(
                    text = d,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))

        // Days Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.height(300.dp),
            userScrollEnabled = false
        ) {
            // Alignment for 1st day of month
            val firstDayOffset = currentMonth.atDay(1).dayOfWeek.value - 1
            items(firstDayOffset) {
                Box(modifier = Modifier.size(45.dp))
            }

            items(daysInMonth) { index ->
                val dayNum = index + 1
                val dateStr = currentMonth.atDay(dayNum).toString()
                val record = attendanceMap[dateStr] ?: DayAttendance(dateStr, dayNum)

                DayCell(day = record, onClick = { onDayClick(record) })
            }
        }
    }
}

@Composable
fun DayCell(day: DayAttendance, onClick: () -> Unit) {
    val statusColor = when (day.workStatus) {
        AttendanceStatus.PRESENT -> Color(0xFFC8E6C9)
        AttendanceStatus.HALF_DAY -> Color(0xFFFFF59D)
        AttendanceStatus.ABSENT -> Color(0xFFFFCDD2)
        AttendanceStatus.NOT_MARKED -> Color(0xFFF5F5F5)
    }

    Column(
        modifier = Modifier
            .padding(2.dp)
            .background(statusColor, RoundedCornerShape(4.dp))
            .border(0.5.dp, Color.LightGray, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "${day.dayOfMonth}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "W: ${day.workStatus.code}",
            fontSize = 10.sp,
            color = if (day.workStatus == AttendanceStatus.ABSENT) Color.Red else Color.Black
        )
        Text(
            text = "D: ${day.dabbaStatus.code}",
            fontSize = 10.sp,
            color = if (day.dabbaStatus == DabbaStatus.ABSENT) Color.Red else Color.DarkGray
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
            SalaryRow("Leaves Taken (Free: 4)", "%.1f Days".format(report.leaveDaysTaken))

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

            // Prominent Grand Box
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
