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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth

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
    var showAmavasyaDialog by remember { mutableStateOf(false) }
    var showLeavesHistoryDialog by remember { mutableStateOf(false) }
    var showCalendarPdfDialog by remember { mutableStateOf(false) }
    var showGpsConfirmDialog by remember { mutableStateOf(false) }
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
                    IconButton(onClick = { showCalendarPdfDialog = true }) {
                        Text(
                            text = "👁",
                            fontSize = 22.sp
                        )
                    }

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
                            text = { Text("Leaves History", fontWeight = FontWeight.Medium) },
                            onClick = {
                                menuExpanded = false
                                showLeavesHistoryDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add Holiday", fontWeight = FontWeight.Medium) },
                            onClick = {
                                menuExpanded = false
                                showHolidayDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add Amavasya", fontWeight = FontWeight.Medium) },
                            onClick = {
                                menuExpanded = false
                                showAmavasyaDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isLocationSet) "Update Work GPS" else "Set Work GPS", fontWeight = FontWeight.Medium) },
                            onClick = {
                                menuExpanded = false
                                showGpsConfirmDialog = true
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
            ModernMonthNavigator(
                displayedMonth = displayedMonth,
                onPreviousMonth = { displayedMonth = displayedMonth.minusMonths(1) },
                onNextMonth = { displayedMonth = displayedMonth.plusMonths(1) },
                onResetMonth = { displayedMonth = YearMonth.now() }
            )

            Spacer(modifier = Modifier.height(10.dp))

            ModernLocationBanner(
                isLocationSet = isLocationSet,
                lat = settings.workLatitude,
                lng = settings.workLongitude,
                onSetGpsClick = { showGpsConfirmDialog = true }
            )

            Spacer(modifier = Modifier.height(14.dp))

            ModernCalendarCard(
                currentMonth = displayedMonth,
                attendanceMap = attendanceMap,
                onDayClick = { selectedDayForEdit = it }
            )

            Spacer(modifier = Modifier.height(18.dp))

            ModernSummaryDashboard(
                currentMonth = displayedMonth,
                attendanceMap = attendanceMap
            )

            Spacer(modifier = Modifier.height(18.dp))

            ModernSalesIncentiveCard(
                currentMonth = displayedMonth,
                attendanceMap = attendanceMap
            )

            Spacer(modifier = Modifier.height(18.dp))

            ModernSalaryInvoiceCard(report = report)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showCalendarPdfDialog) {
        CalendarPdfDialog(
            initialYear = displayedMonth.year,
            onDismiss = { showCalendarPdfDialog = false }
        )
    }

    if (showGpsConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showGpsConfirmDialog = false },
            title = {
                Text(
                    text = if (isLocationSet) "Update Workplace GPS?" else "Set Workplace GPS?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = if (isLocationSet) {
                            "Warning: This will overwrite your existing workplace coordinates with your current physical location.\n\nOnly confirm if you are physically present at your workplace right now."
                        } else {
                            "Are you physically at your workplace right now? This will lock your current GPS spot as your workplace location."
                        },
                        fontSize = 13.sp,
                        color = TextSecondary
                    )

                    if (isLocationSet) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Current Locked GPS: %.4f, %.4f".format(settings.workLatitude, settings.workLongitude),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGpsConfirmDialog = false
                        onSetLocation()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isLocationSet) DangerRed else BrandPrimary)
                ) {
                    Text(if (isLocationSet) "Confirm & Update" else "Confirm & Set")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGpsConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

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

    if (showAmavasyaDialog) {
        ManageAmavasyaDialog(
            displayedMonth = displayedMonth,
            attendanceMap = attendanceMap,
            onDismiss = { showAmavasyaDialog = false },
            onSaveAmavasya = { amavasyaMap ->
                val daysInMonth = displayedMonth.lengthOfMonth()
                for (day in 1..daysInMonth) {
                    val dateStr = displayedMonth.atDay(day).toString()
                    val existing = attendanceMap[dateStr] ?: DayAttendance(dateString = dateStr, dayOfMonth = day)
                    val aName = amavasyaMap[day]
                    val isAmavasya = aName != null
                    if (existing.isAmavasya != isAmavasya || existing.safeAmavasyaName != (aName ?: "")) {
                        onUpdateDay(
                            existing.copy(
                                isAmavasya = isAmavasya,
                                amavasyaName = aName ?: ""
                            )
                        )
                    }
                }
                showAmavasyaDialog = false
            }
        )
    }

    if (showLeavesHistoryDialog) {
        LeavesHistoryDialog(
            initialYear = displayedMonth.year,
            attendanceMap = attendanceMap,
            onDismiss = { showLeavesHistoryDialog = false }
        )
    }

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
