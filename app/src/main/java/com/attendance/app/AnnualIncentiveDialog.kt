package com.attendance.app

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

data class SalaryIncrementRule(
    val id: String = UUID.randomUUID().toString(),
    val minAvgIncentive: Double,
    val maxAvgIncentive: Double,
    val salaryAmount: Double
)

data class MonthIncentiveSummary(
    val monthNumber: Int,
    val monthName: String,
    val totalSales: Double,
    val autoIncentive: Double,
    val effectiveIncentive: Double,
    val isOverridden: Boolean,
    val isFuture: Boolean
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnnualIncentiveDialog(
    initialYear: Int,
    attendanceMap: Map<String, DayAttendance>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val gson = remember { Gson() }
    val sp = remember { context.getSharedPreferences("annual_incentive_prefs", Context.MODE_PRIVATE) }

    val today = remember { LocalDate.now() }
    val currentYear = today.year
    val currentMonthValue = today.monthValue

    var selectedYear by remember { mutableStateOf(initialYear) }
    var refreshKey by remember { mutableStateOf(0) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showGoalDialog by remember { mutableStateOf(false) }
    var monthToEdit by remember { mutableStateOf<MonthIncentiveSummary?>(null) }

    // Target Salary Goal (Default: ₹20,000)
    var targetSalaryGoal by remember(refreshKey) {
        mutableStateOf(sp.getFloat("target_salary_goal", 20000f).toDouble())
    }

    // Load or initialize default rules
    var rules by remember(refreshKey) {
        val json = sp.getString("salary_rules", null)
        val loadedList: List<SalaryIncrementRule>? = if (!json.isNullOrEmpty()) {
            val type: Type = object : TypeToken<List<SalaryIncrementRule>>() {}.type
            gson.fromJson(json, type)
        } else null

        val finalRules = loadedList ?: listOf(
            SalaryIncrementRule(minAvgIncentive = 0.0, maxAvgIncentive = 3000.0, salaryAmount = 16000.0),
            SalaryIncrementRule(minAvgIncentive = 3001.0, maxAvgIncentive = 5000.0, salaryAmount = 17000.0),
            SalaryIncrementRule(minAvgIncentive = 5001.0, maxAvgIncentive = 7000.0, salaryAmount = 18500.0),
            SalaryIncrementRule(minAvgIncentive = 7001.0, maxAvgIncentive = 10000.0, salaryAmount = 20000.0)
        )
        mutableStateOf(finalRules.sortedBy { it.minAvgIncentive })
    }

    // Calculate all 12 months data
    val monthsData = remember(selectedYear, attendanceMap, refreshKey) {
        (1..12).map { monthNum ->
            val isFuture = (selectedYear > currentYear) || (selectedYear == currentYear && monthNum > currentMonthValue)

            val ym = YearMonth.of(selectedYear, monthNum)
            val daysInMonth = ym.lengthOfMonth()

            var monthTotalSales = 0.0
            for (d in 1..daysInMonth) {
                val dateStr = "%04d-%02d-%02d".format(selectedYear, monthNum, d)
                val rec = attendanceMap[dateStr]
                if (rec != null) {
                    monthTotalSales += rec.salesAmount
                }
            }

            // Net Incentive: (Sales * 0.95) * 1%
            val autoIncentive = (monthTotalSales * 0.95) * 0.01

            val overrideKey = "override_${selectedYear}_$monthNum"
            val hasOverride = sp.contains(overrideKey)
            val effectiveIncentive = if (hasOverride && !isFuture) {
                sp.getFloat(overrideKey, autoIncentive.toFloat()).toDouble()
            } else {
                autoIncentive
            }

            MonthIncentiveSummary(
                monthNumber = monthNum,
                monthName = Month.of(monthNum).getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(),
                totalSales = monthTotalSales,
                autoIncentive = autoIncentive,
                effectiveIncentive = effectiveIncentive,
                isOverridden = hasOverride && !isFuture,
                isFuture = isFuture
            )
        }
    }

    val totalAnnualIncentive = monthsData.sumOf { it.effectiveIncentive }
    val monthlyAverageIncentive = totalAnnualIncentive / 12.0

    // Match salary rule for Current Run-Rate
    val matchedRule = rules.firstOrNull { rule ->
        monthlyAverageIncentive >= rule.minAvgIncentive && monthlyAverageIncentive <= rule.maxAvgIncentive
    } ?: rules.lastOrNull { monthlyAverageIncentive > it.maxAvgIncentive }

    // --- Goal Calculations ---
    val requiredRuleForGoal = rules.firstOrNull { it.salaryAmount >= targetSalaryGoal }
        ?: rules.maxByOrNull { it.salaryAmount }

    val requiredMinAvgIncentive = requiredRuleForGoal?.minAvgIncentive ?: 0.0
    val targetAnnualIncentiveRequired = requiredMinAvgIncentive * 12.0

    // Past + Present Earned Incentive
    val earnedIncentiveSoFar = monthsData.filter { !it.isFuture }.sumOf { it.effectiveIncentive }
    val remainingIncentiveNeeded = (targetAnnualIncentiveRequired - earnedIncentiveSoFar).coerceAtLeast(0.0)

    // Sales with GST: Incentive = (Sales * 0.95) * 0.01 => Sales = Incentive / 0.0095
    val remainingSalesWithGst = if (remainingIncentiveNeeded > 0) remainingIncentiveNeeded / 0.0095 else 0.0

    // Days remaining in selected year
    val daysRemainingInYear = when {
        selectedYear < currentYear -> 0L
        selectedYear > currentYear -> YearMonth.of(selectedYear, 1).isLeapYear.let { if (it) 366L else 365L }
        else -> (ChronoUnit.DAYS.between(today, LocalDate.of(selectedYear, 12, 31)) + 1L).coerceAtLeast(1L)
    }

    val requiredDailySales = if (daysRemainingInYear > 0 && remainingSalesWithGst > 0) {
        remainingSalesWithGst / daysRemainingInYear
    } else {
        0.0
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.95f)
                .shadow(16.dp, RoundedCornerShape(26.dp)),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFFFAFBFD),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF4F46E5), Color(0xFF6366F1))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("₹", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Annual Incentive & Increment",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0F172A),
                                letterSpacing = (-0.3).sp
                            )
                            Text(
                                text = "Target goals & January salary projection",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9))
                    ) {
                        Text("✕", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Year Switcher, Goal Button & Rules Button Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Year Navigator
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shadowElevation = 1.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            IconButton(
                                onClick = { selectedYear -= 1 },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text("‹", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF334155))
                            }
                            Text(
                                text = "$selectedYear",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF4F46E5),
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                            IconButton(
                                onClick = { selectedYear += 1 },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text("›", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF334155))
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { showGoalDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFECFDF5)),
                            border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("🎯 Goal: ₹%.0f".format(targetSalaryGoal), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                        }

                        Button(
                            onClick = { showRulesDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEF2FF)),
                            border = BorderStroke(1.dp, Color(0xFFC7D2FE)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("⚙ Rules", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4F46E5))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // --- Target Salary Goal & Sales Runway Card ---
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.2.dp, if (remainingIncentiveNeeded == 0.0) Color(0xFFA7F3D0) else Color(0xFFCBD5E1)),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(11.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🎯", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "SALARY GOAL: ₹%.0f / MONTH".format(targetSalaryGoal),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = "Requires monthly avg incentive ≥ ₹%.0f".format(requiredMinAvgIncentive),
                                        fontSize = 9.5.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }

                            if (remainingIncentiveNeeded == 0.0) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFDCFCE7)
                                ) {
                                    Text(
                                        text = "QUALIFIED 🎉",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF166534),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                                    )
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFFEF3C7)
                                ) {
                                    Text(
                                        text = "$daysRemainingInYear DAYS LEFT",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF92400E),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(8.dp))

                        if (remainingIncentiveNeeded == 0.0) {
                            Text(
                                text = "Congratulations! Your earned incentive already satisfies the requirement for ₹%.0f salary increment next year.",
                                fontSize = 11.sp,
                                color = Color(0xFF047857),
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Need Incentive", fontSize = 9.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                                    Text("₹%.0f".format(remainingIncentiveNeeded), fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFFD97706))
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Need Total Sales (GST)", fontSize = 9.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                                    Text("₹%.0f".format(remainingSalesWithGst), fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F172A))
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Sales Per Day", fontSize = 9.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                                    Text("₹%.0f / day".format(requiredDailySales), fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF4F46E5))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Current Projection Mini Banner
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Current Year Total: ₹%.2f (Avg: ₹%.2f/mo)".format(totalAnnualIncentive, monthlyAverageIncentive),
                            fontSize = 10.sp,
                            color = Color(0xFF475569),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Qualifying: ₹%.0f".format(matchedRule?.salaryAmount ?: 0.0),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF047857)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 12 Months Grid (3 columns x 4 rows)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(monthsData.size) { idx ->
                        val item = monthsData[idx]

                        val cellBg = when {
                            item.isFuture -> Color(0xFFF8FAFC)
                            item.isOverridden -> Color(0xFFFFFBEB)
                            else -> Color.White
                        }

                        val cellBorder = when {
                            item.isFuture -> Color(0xFFE2E8F0)
                            item.isOverridden -> Color(0xFFFDE68A)
                            else -> Color(0xFFCBD5E1)
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(82.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .combinedClickable(
                                    onClick = {
                                        if (item.isFuture) {
                                            Toast.makeText(context, "${item.monthName} is a future month (locked)", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Hold ${item.monthName} to edit incentive", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onLongClick = {
                                        if (item.isFuture) {
                                            Toast.makeText(context, "Future months cannot be edited", Toast.LENGTH_SHORT).show()
                                        } else {
                                            monthToEdit = item
                                        }
                                    }
                                ),
                            shape = RoundedCornerShape(11.dp),
                            color = cellBg,
                            border = BorderStroke(1.dp, cellBorder),
                            shadowElevation = if (item.isFuture) 0.dp else 0.8.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 7.dp, vertical = 5.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.monthName,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (item.isFuture) Color(0xFF94A3B8) else Color(0xFF1E293B)
                                    )

                                    if (item.isFuture) {
                                        Text(
                                            text = "UPCOMING 🔒",
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF94A3B8)
                                        )
                                    } else if (item.isOverridden) {
                                        Text(
                                            text = "EDITED",
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFFB45309)
                                        )
                                    }
                                }

                                Text(
                                    text = if (item.isFuture && item.effectiveIncentive == 0.0) "--" else "₹%.2f".format(item.effectiveIncentive),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = when {
                                        item.isFuture -> Color(0xFF94A3B8)
                                        item.effectiveIncentive > 0 -> Color(0xFF047857)
                                        else -> Color(0xFF94A3B8)
                                    }
                                )

                                Text(
                                    text = if (item.isFuture && item.totalSales == 0.0) "Sales: --" else "Sales: ₹%.0f".format(item.totalSales),
                                    fontSize = 9.sp,
                                    color = if (item.isFuture) Color(0xFFCBD5E1) else Color(0xFF64748B),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔒 Future months locked • Hold past month to edit",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF475569)
                    )

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)
                    ) {
                        Text("Done", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Set Salary Goal Dialog
    if (showGoalDialog) {
        var goalInputText by remember { mutableStateOf("%.0f".format(targetSalaryGoal)) }

        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            title = {
                Text(
                    text = "Set Salary Target Goal",
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter your desired salary for next year. We will calculate the remaining sales and daily runway required to hit this tier.",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = goalInputText,
                        onValueChange = { goalInputText = it },
                        label = { Text("Target Monthly Salary (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = goalInputText.toDoubleOrNull() ?: targetSalaryGoal
                        sp.edit().putFloat("target_salary_goal", parsed.toFloat()).apply()
                        targetSalaryGoal = parsed
                        refreshKey++
                        showGoalDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857))
                ) {
                    Text("Save Goal")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoalDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Month Incentive Dialog (Past & Present only)
    monthToEdit?.let { targetMonth ->
        EditMonthIncentiveDialog(
            year = selectedYear,
            monthSummary = targetMonth,
            onDismiss = { monthToEdit = null },
            onSave = { newAmount ->
                val key = "override_${selectedYear}_${targetMonth.monthNumber}"
                sp.edit().putFloat(key, newAmount.toFloat()).apply()
                refreshKey++
                monthToEdit = null
            },
            onResetToAuto = {
                val key = "override_${selectedYear}_${targetMonth.monthNumber}"
                sp.edit().remove(key).apply()
                refreshKey++
                monthToEdit = null
            }
        )
    }

    // Rules Configuration Dialog
    if (showRulesDialog) {
        ManageSalaryRulesDialog(
            currentRules = rules,
            onDismiss = { showRulesDialog = false },
            onSaveRules = { updatedList ->
                sp.edit().putString("salary_rules", gson.toJson(updatedList)).apply()
                rules = updatedList.sortedBy { it.minAvgIncentive }
                refreshKey++
                showRulesDialog = false
            }
        )
    }
}

@Composable
fun EditMonthIncentiveDialog(
    year: Int,
    monthSummary: MonthIncentiveSummary,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
    onResetToAuto: () -> Unit
) {
    var amountText by remember { mutableStateOf("%.2f".format(monthSummary.effectiveIncentive)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${monthSummary.monthName} $year Incentive",
                fontWeight = FontWeight.Black,
                fontSize = 17.sp,
                color = Color(0xFF0F172A)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Auto-calculated: ₹%.2f (from Sales: ₹%.2f)".format(
                        monthSummary.autoIncentive,
                        monthSummary.totalSales
                    ),
                    fontSize = 11.5.sp,
                    color = Color(0xFF64748B)
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Monthly Incentive Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (monthSummary.isOverridden) {
                    Spacer(modifier = Modifier.height(10.dp))
                    TextButton(
                        onClick = onResetToAuto,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Reset to Auto-Calculated", color = Color(0xFFDC2626), fontSize = 11.5.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = amountText.toDoubleOrNull() ?: 0.0
                    onSave(parsed)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ManageSalaryRulesDialog(
    currentRules: List<SalaryIncrementRule>,
    onDismiss: () -> Unit,
    onSaveRules: (List<SalaryIncrementRule>) -> Unit
) {
    var rulesList by remember { mutableStateOf(currentRules.toMutableList()) }
    var showAddDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Salary Increment Rules",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Matched against 12-month average incentive",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    Button(
                        onClick = { showAddDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("+ Add Rule", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(rulesList, key = { it.id }) { rule ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Avg: ₹%.0f to ₹%.0f".format(rule.minAvgIncentive, rule.maxAvgIncentive),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = "New Salary: ₹%.0f / month".format(rule.salaryAmount),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF047857)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        rulesList = rulesList.filter { it.id != rule.id }.toMutableList()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Text("🗑", fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFF64748B))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSaveRules(rulesList) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                    ) {
                        Text("Save Rules")
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var minText by remember { mutableStateOf("") }
        var maxText by remember { mutableStateOf("") }
        var salaryText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Salary Rule", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minText,
                        onValueChange = { minText = it },
                        label = { Text("Min Average Incentive (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = maxText,
                        onValueChange = { maxText = it },
                        label = { Text("Max Average Incentive (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = salaryText,
                        onValueChange = { salaryText = it },
                        label = { Text("Resulting Salary (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val minVal = minText.toDoubleOrNull() ?: 0.0
                        val maxVal = maxText.toDoubleOrNull() ?: 0.0
                        val salVal = salaryText.toDoubleOrNull() ?: 0.0
                        rulesList = (rulesList + SalaryIncrementRule(
                            minAvgIncentive = minVal,
                            maxAvgIncentive = maxVal,
                            salaryAmount = salVal
                        )).sortedBy { it.minAvgIncentive }.toMutableList()
                        showAddDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
