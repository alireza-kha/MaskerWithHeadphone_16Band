package com.masker.app.audiogram

import java.util.Calendar
import java.util.Date

/**
 * تبدیل تاریخ میلادی به شمسی (جلالی) بدون نیاز به کتابخانه جانبی، بر اساس الگوریتم
 * متداول و شناخته‌شده تبدیل گرگوری به جلالی (مبتنی بر پیاده‌سازی مرجع رزبه پورنادر و
 * محمد طوسی) که در بسیاری از تقویم‌های فارسی متن‌باز استفاده می‌شود.
 */
object PersianDateUtils {

    private val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    fun toPersianDigits(input: String): String {
        val sb = StringBuilder()
        for (ch in input) {
            sb.append(if (ch in '0'..'9') persianDigits[ch - '0'] else ch)
        }
        return sb.toString()
    }

    /** خروجی به فرمت "۱۴۰۴/۰۴/۲۹" (با اعداد فارسی) */
    fun toJalaliString(date: Date): String {
        val cal = Calendar.getInstance()
        cal.time = date
        val gy = cal.get(Calendar.YEAR)
        val gm = cal.get(Calendar.MONTH) + 1
        val gd = cal.get(Calendar.DAY_OF_MONTH)
        val (jy, jm, jd) = gregorianToJalali(gy, gm, gd)
        val formatted = "%d/%02d/%02d".format(jy, jm, jd)
        return toPersianDigits(formatted)
    }

    /** خروجی به فرمت "2026/07/20" */
    fun toGregorianString(date: Date): String {
        val cal = Calendar.getInstance()
        cal.time = date
        val gy = cal.get(Calendar.YEAR)
        val gm = cal.get(Calendar.MONTH) + 1
        val gd = cal.get(Calendar.DAY_OF_MONTH)
        return "%d/%02d/%02d".format(gy, gm, gd)
    }

    private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) +
                ((gy2 + 399) / 400) + gd + gDaysInMonth[gm - 1]

        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }

        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + (days / 31)
            jd = 1 + (days % 31)
        } else {
            jm = 7 + ((days - 186) / 30)
            jd = 1 + ((days - 186) % 30)
        }
        return Triple(jy, jm, jd)
    }
}
