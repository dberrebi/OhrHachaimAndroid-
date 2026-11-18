package app.ohrhachaim.core

import java.util.Locale

/**
 * A Gregorian calendar day, with no clock and no time zone.
 * Day differences match `Calendar.dateComponents([.day])` in the iOS reader.
 */
data class IsoDay(val year: Int, val month: Int, val day: Int) : Comparable<IsoDay> {
    /** Always ASCII digits, so it matches `weekly.gregorian_date` whatever the device language. */
    val text: String get() = String.format(Locale.US, "%04d-%02d-%02d", year, month, day)

    fun toEpochDay(): Long = daysFromCivil(year, month, day)

    override fun compareTo(other: IsoDay): Int = toEpochDay().compareTo(other.toEpochDay())

    companion object {
        fun parse(text: String): IsoDay? {
            val parts = text.split("-")
            if (parts.size != 3) return null
            val year = parts[0].toIntOrNull() ?: return null
            val month = parts[1].toIntOrNull() ?: return null
            val day = parts[2].toIntOrNull() ?: return null
            if (month !in 1..12 || day !in 1..31) return null
            return IsoDay(year, month, day)
        }

        fun fromEpochDay(epochDay: Long): IsoDay {
            val (year, month, day) = civilFromDays(epochDay)
            return IsoDay(year, month, day)
        }
    }
}

fun daysBetween(from: IsoDay, to: IsoDay): Int = (to.toEpochDay() - from.toEpochDay()).toInt()

/** Howard Hinnant's civil_from_days / days_from_civil, public-domain calendar arithmetic. */
private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    var y = year
    y -= if (month <= 2) 1 else 0
    val era = if (y >= 0) y / 400 else (y - 399) / 400
    val yoe = (y - era * 400).toLong()
    val doy = (153 * (month + if (month > 2) -3 else 9) + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy.toLong()
    return era.toLong() * 146097 + doe - 719468
}

private fun civilFromDays(epochDay: Long): Triple<Int, Int, Int> {
    val z = epochDay + 719468
    val era = if (z >= 0) z / 146097 else (z - 146096) / 146097
    val doe = (z - era * 146097).toInt()
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    var y = yoe + era.toInt() * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = mp + if (mp < 10) 3 else -9
    y += if (m <= 2) 1 else 0
    return Triple(y, m, d)
}
