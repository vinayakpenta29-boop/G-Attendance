package com.attendance.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun EditAttendanceDialog(
    day: DayAttendance,
    onDismiss: () -> Unit,
    onSave: (DayAttendance) -> Unit
) {
    val isFutureDate = remember(day.dateString) {
        try {
            LocalDate.parse(day.dateString).isAfter(LocalDate.now())
        } catch (e: Exception) {
            false
        }
    }

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

    var salesInput by remember {
        mutableStateOf(
            if (!day.salesBills.isNullOrBlank()) {
                day.salesBills!!
            } else if (day.salesAmount > 0) {
                if (day.salesAmount % 1.0 == 0.0) day.salesAmount.toLong().toString() else day.salesAmount.toString()
            } else ""
        )
    }

    val computedSales = remember(salesInput) {
        if (salesInput.isBlank()) {
            0.0
        } else {
            salesInput.split("+")
                .mapNotNull { it.trim().toDoubleOrNull() }
                .sum()
        }
    }

    val isWorkAbsentOrHalf = workStatus == AttendanceStatus.ABSENT || workStatus == AttendanceStatus.HALF_DAY

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isFutureDate) "Day Details: ${day.dateString}" else "Edit Record: ${day.dateString}",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (isFutureDate) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF1F5F9),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🔒", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Future Date: Attendance and sales can only be entered on or after this date.",
                                fontSize = 11.5.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

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
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (day.isAmavasya) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFAF5FF),
                        border = BorderStroke(1.dp, Color(0xFFE9D5FF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🌑", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Amavasya: ${day.safeAmavasyaName}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = AmavasyaPurple
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (!day.isHoliday && !day.isAmavasya) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📅", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Normal Working Day",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = salesInput,
                    onValueChange = { if (!isFutureDate) salesInput = it },
                    label = { Text("Daily Sales Amount (₹)") },
                    placeholder = { Text(if (isFutureDate) "Locked" else "e.g. 500+250+300") },
                    supportingText = {
                        if (salesInput.contains("+") && computedSales > 0) {
                            Text(
                                text = "Total Sum: ₹%.2f".format(computedSales),
                                color = SuccessGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    enabled = !isFutureDate,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = if (isFutureDate) "Work Attendance (Locked)" else "Work Attendance",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isFutureDate) TextSecondary else TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(AttendanceStatus.PRESENT, AttendanceStatus.HALF_DAY, AttendanceStatus.ABSENT).forEach { st ->
                        FilterChip(
                            selected = workStatus == st,
                            onClick = {
                                if (!isFutureDate) {
                                    workStatus = st
                                    if (st == AttendanceStatus.ABSENT || st == AttendanceStatus.HALF_DAY) {
                                        dabbaStatus = DabbaStatus.ABSENT
                                    }
                                }
                            },
                            enabled = !isFutureDate,
                            label = { Text(st.code, fontWeight = FontWeight.Bold) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isFutureDate) "Dabba Allowance (Locked)" else "Dabba Allowance",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isFutureDate) TextSecondary else TextPrimary
                )

                if (isWorkAbsentOrHalf && !isFutureDate) {
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
                        val isEnabled = !isFutureDate && (!isWorkAbsentOrHalf || st == DabbaStatus.ABSENT)

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
            if (!isFutureDate) {
                Button(
                    onClick = {
                        val finalDabba = if (isWorkAbsentOrHalf) DabbaStatus.ABSENT else dabbaStatus
                        val finalSales = if (salesInput.isBlank()) {
                            0.0
                        } else {
                            salesInput.split("+")
                                .mapNotNull { it.trim().toDoubleOrNull() }
                                .sum()
                        }

                        onSave(
                            day.copy(
                                workStatus = workStatus,
                                dabbaStatus = finalDabba,
                                isManual = true,
                                holidayName = day.holidayName ?: "",
                                amavasyaName = day.amavasyaName ?: "",
                                salesAmount = finalSales,
                                salesBills = salesInput.trim()
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Save Changes")
                }
            } else {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64748B))
                ) {
                    Text("Close")
                }
            }
        },
        dismissButton = {
            if (!isFutureDate) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
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
fun ManageAmavasyaDialog(
    displayedMonth: YearMonth,
    attendanceMap: Map<String, DayAttendance>,
    onDismiss: () -> Unit,
    onSaveAmavasya: (Map<Int, String>) -> Unit
) {
    val daysInMonth = displayedMonth.lengthOfMonth()

    val amavasyaMap = remember {
        mutableStateMapOf<Int, String>().apply {
            for (day in 1..daysInMonth) {
                val dateStr = displayedMonth.atDay(day).toString()
                val record = attendanceMap[dateStr]
                if (record?.isAmavasya == true) {
                    put(day, record.safeAmavasyaName)
                }
            }
        }
    }

    var editingDay by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Manage Amavasya",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AmavasyaPurple
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Tap a date number to set Amavasya title:",
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
                        val isAmavasya = amavasyaMap.containsKey(dayNum)

                        Box(
                            modifier = Modifier
                                .padding(3.dp)
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isAmavasya) AmavasyaPurple else Color(0xFFF1F5F9))
                                .clickable { editingDay = dayNum },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$dayNum",
                                fontSize = 12.sp,
                                fontWeight = if (isAmavasya) FontWeight.Bold else FontWeight.Normal,
                                color = if (isAmavasya) Color.White else TextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (amavasyaMap.isNotEmpty()) {
                    Text(
                        text = "Configured Amavasya (${amavasyaMap.size}):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmavasyaPurple
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 70.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        amavasyaMap.entries.sortedBy { it.key }.forEach { (day, name) ->
                            Text(
                                text = "• Day $day: $name",
                                fontSize = 11.sp,
                                color = Color(0xFF581C87)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveAmavasya(amavasyaMap.toMap()) },
                colors = ButtonDefaults.buttonColors(containerColor = AmavasyaPurple)
            ) {
                Text("Save Amavasya", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    editingDay?.let { day ->
        var nameInput by remember { mutableStateOf(amavasyaMap[day] ?: "") }

        AlertDialog(
            onDismissRequest = { editingDay = null },
            title = { Text("Amavasya Name (Day $day)", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    placeholder = { Text("e.g. Somvati Amavasya") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalName = nameInput.trim().ifBlank { "Amavasya" }
                        amavasyaMap[day] = finalName
                        editingDay = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Set", color = Color.White)
                }
            },
            dismissButton = {
                Row {
                    if (amavasyaMap.containsKey(day)) {
                        TextButton(
                            onClick = {
                                amavasyaMap.remove(day)
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
