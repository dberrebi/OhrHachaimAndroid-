package app.ohrhachaim.core

/** A verse in one book of the Torah. */
data class VersePlace(val chapter: Int, val verse: Int) : Comparable<VersePlace> {
    override fun compareTo(other: VersePlace): Int =
        compareValuesBy(this, other, VersePlace::chapter, VersePlace::verse)
}

/** One of the seven Shabbat aliyot, numbered 1 (ראשון) to 7 (שביעי). Maftir is not included. */
data class Aliyah(val number: Int, val start: VersePlace, val end: VersePlace)

/**
 * A row of the reading as the aliyot see it: a verse with commentary is a single place, and a
 * chapter with no commentary (Noach 10, Bechukotai 27) covers the whole chapter.
 */
data class RowSpan(val start: VersePlace, val end: VersePlace) {
    companion object {
        fun verse(chapter: Int, verse: Int) = RowSpan(VersePlace(chapter, verse), VersePlace(chapter, verse))
        fun chapter(chapter: Int) = RowSpan(VersePlace(chapter, 1), VersePlace(chapter, Int.MAX_VALUE))
    }
}

/**
 * Where each aliyah falls in a reading's rows.
 *
 * @property rowAliyah the aliyah number (1–7) of every row. Rows with no verse, such as a
 *   parasha heading or the footer, take the aliyah of the nearest row after them, or before
 *   them at the end.
 * @property targets for aliyot 1–7, the row to scroll to: the first row at or after the aliyah's
 *   start. Null when the reading has no row there.
 * @property labels for every row, the aliyot to name in its heading: a verse that opens its
 *   aliyah names that aliyah; a chapter without commentary also names every aliyah that starts
 *   inside it.
 */
class AliyahLayout(
    val aliyot: List<Aliyah>,
    val rowAliyah: IntArray,
    val targets: List<Int?>,
    val labels: List<List<Int>>,
)

object Aliyot {
    /**
     * The aliyot for a reading of one parasha, or of two read together, from Hebcal's leyning
     * data. Null for anything else, such as the author's introduction.
     */
    fun forParashot(parashaIds: List<Int>): List<Aliyah>? {
        val numbers = when (parashaIds.size) {
            1 -> ALIYOT_SINGLE.getOrNull(parashaIds[0] - 1)
            2 -> ALIYOT_COMBINED[parashaIds[0] to parashaIds[1]]
            else -> null
        } ?: return null
        return (0 until 7).map { i ->
            Aliyah(
                number = i + 1,
                start = VersePlace(numbers[i * 4], numbers[i * 4 + 1]),
                end = VersePlace(numbers[i * 4 + 2], numbers[i * 4 + 3]),
            )
        }
    }

    /** The aliyah a verse belongs to: the last one that starts at or before it, and 1 before the first. */
    fun numberAt(aliyot: List<Aliyah>, place: VersePlace): Int =
        aliyot.lastOrNull { it.start <= place }?.number ?: aliyot.first().number

    /** Lays [aliyot] over a reading whose rows cover [spans], with null for rows that have no verse. */
    fun layout(aliyot: List<Aliyah>, spans: List<RowSpan?>): AliyahLayout {
        val own = spans.map { span -> span?.let { numberAt(aliyot, it.start) } }
        val rowAliyah = IntArray(spans.size)
        var next = own.lastOrNull { it != null } ?: aliyot.first().number
        for (i in spans.indices.reversed()) {
            own[i]?.let { next = it }
            rowAliyah[i] = next
        }
        val targets = aliyot.map { aliyah ->
            spans.indexOfFirst { it != null && it.end >= aliyah.start }.takeIf { it >= 0 }
        }
        val seen = mutableSetOf<Int>()
        val labels = spans.mapIndexed { i, span ->
            if (span == null) return@mapIndexed emptyList()
            val names = sortedSetOf<Int>()
            if (seen.add(own[i]!!)) names += own[i]!!
            if (span.start != span.end) {
                aliyot.filter { it.start >= span.start && it.start <= span.end }.forEach {
                    names += it.number
                    seen += it.number
                }
            }
            names.toList()
        }
        return AliyahLayout(aliyot, rowAliyah, targets, labels)
    }

    /**
     * Today's aliyah in the week leading up to [reading]: Sunday is ראשון and Shabbat is שביעי,
     * following the custom of reading one aliyah a day. Null when the reading is not a regular
     * Shabbat reading or today is outside the six days before it.
     */
    fun todays(reading: WeeklyReading, today: IsoDay): Int? {
        if (reading.kind != "shabbat") return null
        val shabbat = IsoDay.parse(reading.date) ?: return null
        val days = daysBetween(today, shabbat)
        return if (days in 0..6) 7 - days else null
    }
}
