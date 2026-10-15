package org.geekio.floorplanner.core

/** Solar Hijri (Shamsi) calendar dates, for Persian titles and exports. */
object Jalali {
    data class Date(val year: Int, val month: Int, val day: Int)

    private val monthNames = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )

    /** The Shamsi date of the Gregorian date [gy]-[gm]-[gd] (month 1..12). */
    fun fromGregorian(gy: Int, gm: Int, gd: Int): Date {
        val before = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + before[gm - 1]
        var jy = -1595 + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        return if (days < 186) Date(jy, 1 + days / 31, 1 + days % 31) else Date(jy, 7 + (days - 186) / 30, 1 + (days - 186) % 30)
    }

    /** "۲۳ مهر ۱۴۰۵". */
    fun format(d: Date): String = "${persianDigits(d.day.toString())} ${monthNames[d.month - 1]} ${persianDigits(d.year.toString())}"

    fun persianDigits(s: String): String = s.map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")
}
