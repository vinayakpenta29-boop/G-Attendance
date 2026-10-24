package com.attendance.app

import androidx.compose.ui.graphics.Color

// --- Compatibility Extensions ---
internal val DayAttendance.safeWorkStatus: AttendanceStatus
    get() = this.workStatus ?: AttendanceStatus.NOT_MARKED

internal val DayAttendance.safeDabbaStatus: DabbaStatus
    get() = this.dabbaStatus ?: DabbaStatus.NOT_MARKED

internal val DayAttendance.safeHolidayName: String
    get() = if (this.holidayName.isNullOrBlank()) "Holiday" else this.holidayName!!

internal val DayAttendance.safeAmavasyaName: String
    get() = if (this.amavasyaName.isNullOrBlank()) "Amavasya" else this.amavasyaName!!

// --- Premium Design System Colors ---
internal val SlateBg = Color(0xFFF8FAFC)
internal val CardBg = Color(0xFFFFFFFF)
internal val CardBorder = Color(0xFFE2E8F0)
internal val TextPrimary = Color(0xFF0F172A)
internal val TextSecondary = Color(0xFF64748B)

internal val BrandPrimary = Color(0xFF4F46E5) // Indigo 600
internal val BrandSecondary = Color(0xFF6366F1) // Indigo 500

internal val SuccessGreen = Color(0xFF10B981) // Emerald 500
internal val SuccessBg = Color(0xFFECFDF5)
internal val DangerRed = Color(0xFFEF4444) // Rose 500
internal val DangerBg = Color(0xFFFEF2F2)
internal val WarningAmber = Color(0xFFF59E0B) // Amber 500
internal val WarningBg = Color(0xFFFFFBEB)
internal val AmavasyaPurple = Color(0xFF7E22CE) // Purple 700
