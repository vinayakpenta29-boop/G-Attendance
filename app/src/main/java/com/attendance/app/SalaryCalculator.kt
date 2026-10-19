package com.attendance.app

import java.time.DayOfWeek
import java.time.YearMonth

data class SalaryReport(
    val netMonthlySalary: Double,
    val perDaySalary: Double,
    val totalMonthDays: Int,
    val leaveDaysTaken: Double,
    val leaveBonus: Double,
    val leaveDeduction: Double,
    val fifthMondayBonus: Double,
    val schemeBonus: Double,
    val professionalTax: Double,
    val pfDeduction: Double,
    val medicalDeduction: Double,
    val dabbaAbsentDays: Int,
    val perDayDabbaAmount: Double,
    val dabbaDeduction: Double,
    val netDabbaEarned: Double,
    val totalDeduction: Double,
    val grandTotalSalary: Double
)

object SalaryCalculator {

    fun calculate(
        yearMonth: YearMonth,
        attendanceMap: Map<String, DayAttendance>,
        settings: SalarySettings
    ): SalaryReport {
        val daysInMonth = yearMonth.lengthOfMonth()
        val perDaySalary = settings.netMonthlySalary / daysInMonth
        val perDayDabba = 900.0 / daysInMonth

        var absentDays = 0.0
        var halfDays = 0.0
        var dabbaAbsentCount = 0

        for (day in 1..daysInMonth) {
            val date = yearMonth.atDay(day)
            val record = attendanceMap[date.toString()]

            when (record?.workStatus) {
                AttendanceStatus.ABSENT -> absentDays += 1.0
                AttendanceStatus.HALF_DAY -> halfDays += 1.0
                else -> {}
            }

            // Dabba absent if explicitly absent (A, G, D, L) OR if work attendance is Absent/Half-Day
            val isDabbaAbsent = record?.dabbaStatus?.isAbsent == true ||
                    record?.workStatus == AttendanceStatus.ABSENT ||
                    record?.workStatus == AttendanceStatus.HALF_DAY

            if (isDabbaAbsent) {
                dabbaAbsentCount++
            }
        }

        // Half day = 0.5 leave
        val totalLeavesTaken = absentDays + (halfDays * 0.5)

        // 4 free leaves rule
        var leaveDeduction = 0.0
        var leaveBonus = 0.0
        if (totalLeavesTaken > 4.0) {
            leaveDeduction = (totalLeavesTaken - 4.0) * perDaySalary
        } else {
            leaveBonus = (4.0 - totalLeavesTaken) * perDaySalary
        }

        // 5th Monday bonus
        var mondayCount = 0
        for (d in 1..daysInMonth) {
            if (yearMonth.atDay(d).dayOfWeek == DayOfWeek.MONDAY) {
                mondayCount++
            }
        }
        val fifthMondayBonus = if (mondayCount >= 5) perDaySalary else 0.0

        // Dabba calculations
        val dabbaDeduction = dabbaAbsentCount * perDayDabba
        val netDabbaEarned = (900.0 - dabbaDeduction).coerceAtLeast(0.0)

        val pfVal = if (settings.isPfApplicable) settings.pfAmount else 0.0
        val schemeVal = if (settings.isSchemeApplicable) settings.schemeAmount else 0.0

        val totalDeduction = leaveDeduction +
                dabbaDeduction +
                settings.professionalTax +
                pfVal +
                settings.medicalFund

        val grandTotal = (settings.netMonthlySalary + 900.0 + leaveBonus + fifthMondayBonus + schemeVal) - totalDeduction

        return SalaryReport(
            netMonthlySalary = settings.netMonthlySalary,
            perDaySalary = perDaySalary,
            totalMonthDays = daysInMonth,
            leaveDaysTaken = totalLeavesTaken,
            leaveBonus = leaveBonus,
            leaveDeduction = leaveDeduction,
            fifthMondayBonus = fifthMondayBonus,
            schemeBonus = schemeVal,
            professionalTax = settings.professionalTax,
            pfDeduction = pfVal,
            medicalDeduction = settings.medicalFund,
            dabbaAbsentDays = dabbaAbsentCount,
            perDayDabbaAmount = perDayDabba,
            dabbaDeduction = dabbaDeduction,
            netDabbaEarned = netDabbaEarned,
            totalDeduction = totalDeduction,
            grandTotalSalary = grandTotal
        )
    }
}
