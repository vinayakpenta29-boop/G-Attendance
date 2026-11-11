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
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
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
    val isOverridden: Boolean
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

    var selectedYear by remember { mutableStateOf(initialYear) }
    var refreshKey by remember { mutableStateOf(0) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var monthToEdit by remember { mutableStateOf<MonthIncentiveSummary?>(null) }

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

            val autoIncentive = (monthTotalSales * 0.95) * 0.01

            val overrideKey = "override_${selectedYear}_$monthNum"
            val hasOverride = sp.contains(overrideKey)
            val effectiveIncentive = if (hasOverride) {
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
                isOverridden = hasOverride
            )
        }
    }

    val totalAnnualIncentive = monthsData.sumOf { it.effectiveIncentive }
    val monthlyAverageIncentive = totalAnnualIncentive / 12.0

    // Match salary rule
    val matchedRule = rules.firstOrNull { rule ->
        monthlyAverageIncentive >= rule.minAvgIncentive && monthlyAverageIncentive <= rule.maxAvgIncentive
    } ?: rules.lastOrNull { monthlyAverageIncentive > it.maxAvgIncentive }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.94f)
                .shadow(16.dp, RoundedCornerShape(26.dp)),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFFFAFBFD),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 18.dp)
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
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF4F46E5), Color(0xFF6366F1))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("₹", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
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
                                text = "January salary increment calculation",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9))
                    ) {
                        Text("✕", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Year Switcher & Rules Button Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Year Navigator
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shadowElevation = 1.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            IconButton(
                                onClick = { selectedYear -= 1 },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Text("‹", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF334155))
                            }
                            Text(
                                text = "$selectedYear",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF4F46E5),
                                modifier = Modifier.padding(horizontal = 10.dp)
                            )
                            IconButton(
                                onClick = { selectedYear += 1 },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Text("›", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF334155))
                            }
                        }
                    }

                    // Rules Button
                    Button(
                        onClick = { showRulesDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEF2FF)),
                        border = BorderStroke(1.dp, Color(0xFFC7D2FE)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("⚙ Rules", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4F46E5))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Increment Prediction Banner
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "PROJECTED SALARY (${selectedYear + 1})",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B),
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = if (matchedRule != null) "₹%.0f / month".format(matchedRule.salaryAmount) else "Rule Not Configured",
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF047857)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFECFDF5),
                                border = BorderStroke(1.dp, Color(0xFFA7F3D0))
                            ) {
                                Text(
                                    text = "From Jan ${selectedYear + 1}",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF065F46),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Annual Incentive", fontSize = 10.sp, color = Color(0xFF64748B))
                                Text(
                                    text = "₹%.2f".format(totalAnnualIncentive),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Monthly Average (÷12)", fontSize = 10.sp, color = Color(0xFF64748B))
                                Text(
                                    text = "₹%.2f".format(monthlyAverageIncentive),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF4F46E5)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Active Tier", fontSize = 10.sp, color = Color(0xFF64748B))
                                Text(
                                    text = if (matchedRule != null) "₹%.0f - ₹%.0f".format(matchedRule.minAvgIncentive, matchedRule.maxAvgIncentive) else "None",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0F172A)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 12 Months Grid (3 columns x 4 rows)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(monthsData.size) { idx ->
                        val item = monthsData[idx]

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(82.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .combinedClickable(
                                    onClick = {
                                        Toast.makeText(
                                            context,
                                            "Hold ${item.monthName} to edit incentive",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onLongClick = {
                                        monthToEdit = item
                                    }
                                ),
                            shape = RoundedCornerShape(12.dp),
                            color = if (item.isOverridden) Color(0xFFFFFBEB) else Color.White,
                            border = BorderStroke(
                                1.dp,
                                if (item.isOverridden) Color(0xFFFDE68A) else Color(0xFFE2E8F0)
                            ),
                            shadowElevation = 1.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.monthName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF1E293B)
                                    )
                                    if (item.isOverridden) {
                                        Text(
                                            text = "EDITED",
                                            fontSize = 7.5.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFFB45309)
                                        )
                                    }
                                }

                                Text(
                                    text = "₹%.2f".format(item.effectiveIncentive),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (item.effectiveIncentive > 0) Color(0xFF047857) else Color(0xFF94A3B8)
                                )

                                Text(
                                    text = "Sales: ₹%.0f".format(item.totalSales),
                                    fontSize = 9.5.sp,
                                    color = Color(0xFF64748B),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "💡 Long press any month to edit incentive",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF475569)
                    )

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text("Done", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Edit Month Incentive Dialog
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
