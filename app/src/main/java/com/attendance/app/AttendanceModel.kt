package com.attendance.app

enum class AttendanceStatus(val code: String) {
    PRESENT("P"),
    HALF_DAY("H"),
    ABSENT("A"),
    NOT_MARKED("-")
}

enum class DabbaStatus(val code: String, val isAbsent: Boolean) {
    NOT_MARKED("-", false),
    PRESENT("P", false),
    ABSENT("A", true),
    G("G", true),
    D("D", true),
    L("L", true)
}

data class DayAttendance(
    val dateString: String, // Format: "yyyy-MM-dd"
    val dayOfMonth: Int,
    var workStatus: AttendanceStatus = AttendanceStatus.NOT_MARKED,
    var dabbaStatus: DabbaStatus = DabbaStatus.NOT_MARKED,
    var isManual: Boolean = false
)

data class SalarySettings(
    val netMonthlySalary: Double = 25000.0,
    val professionalTax: Double = 200.0,
    val medicalFund: Double = 100.0,
    val isPfApplicable: Boolean = false,
    val pfAmount: Double = 0.0,
    val isSchemeApplicable: Boolean = false,
    val schemeAmount: Double = 0.0,
    val workLatitude: Double = 0.0,
    val workLongitude: Double = 0.0,
    val geofenceRadiusMeters: Float = 150f
)
