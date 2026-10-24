package com.attendance.app

import androidx.compose.ui.graphics.Color

// Shared data model for Leaves History
data class MonthLeavesData(
    val monthName: String,
    val absentCount: Int,
    val halfDayCount: Int,
    val totalLeaves: Double
)

// --- Compatibility Extensions ---
val DayAttendance.safeWorkStatus: AttendanceStatus
    get() = this.workStatus ?: AttendanceStatus.NOT_MARKED

val DayAttendance.safeDabbaStatus: DabbaStatus
    get() = this.dabbaStatus ?: DabbaStatus.NOT_MARKED

val DayAttendance.safeHolidayName: String
    get() = if (this.holidayName.isNullOrBlank()) "Holiday" else this.holidayName!!

val DayAttendance.safeAmavasyaName: String
    get() = if (this.amavasyaName.isNullOrBlank()) "Amavasya" else this.amavasyaName!!

// --- Shared Design System Colors ---
val SlateBg = Color(0xFFF8FAFC)
val CardBg = Color(0xFFFFFFFF)
val CardBorder = Color(0xFFE2E8F0)
val TextPrimary = Color(0xFF0F172A)
val TextSecondary = Color(0xFF64748B)

val BrandPrimary = Color(0xFF4F46E5) // Indigo 600
val BrandSecondary = Color(0xFF6366F1) // Indigo 500

val SuccessGreen = Color(0xFF10B981) // Emerald 500
val SuccessBg = Color(0xFFECFDF5)
val DangerRed = Color(0xFFEF4444) // Rose 500
val DangerBg = Color(0xFFFEF2F2)
val WarningAmber = Color(0xFFF59E0B) // Amber 500
val WarningBg = Color(0xFFFFFBEB)
val AmavasyaPurple = Color(0xFF7E22CE) // Purple 700
