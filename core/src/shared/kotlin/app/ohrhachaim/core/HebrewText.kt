package app.ohrhachaim.core

object HebrewNumerals {
    /** Hebrew letter-numbers with geresh / gershayim. 15 and 16 avoid the divine name. */
    fun string(number: Int): String {
        if (number <= 0 || number >= 1000) return number.toString()
        if (number == 15) return "ט״ו"
        if (number == 16) return "ט״ז"
        val ones = arrayOf("", "א", "ב", "ג", "ד", "ה", "ו", "ז", "ח", "ט")
        val tens = arrayOf("", "י", "כ", "ל", "מ", "נ", "ס", "ע", "פ", "צ")
        val hundreds = arrayOf("", "ק", "ר", "ש", "ת", "תק", "תר", "תש", "תת", "תתק")
        var letters = hundreds[number / 100]
        val rest = number % 100
        letters += when (rest) {
            15 -> "טו"
            16 -> "טז"
            else -> tens[rest / 10] + ones[rest % 10]
        }
        if (letters.length <= 1) return letters + "׳"
        return letters.dropLast(1) + "״" + letters.last()
    }
}

data class MarkupSegment(val text: String, val emphasis: Boolean)

object HebrewText {
    fun containsHebrew(text: String): Boolean =
        text.any { it.code in 0x0590..0x05FF }

    fun number(value: Int, language: AppLanguage): String =
        if (language == AppLanguage.HE) HebrewNumerals.string(value) else value.toString()

    /**
     * FTS5 query of Hebrew letters only. Nikud and cantillation are dropped, and each word is a prefix.
     * `אוֹרָה` becomes `"אורה"*`.
     */
    fun ftsQuery(raw: String): String {
        val stripped = stripNikud(raw)
        val words = mutableListOf<String>()
        val current = StringBuilder()
        for (ch in stripped) {
            if (ch.code in 0x05D0..0x05EA) {
                current.append(ch)
            } else if (current.isNotEmpty()) {
                words += current.toString()
                current.clear()
            }
        }
        if (current.isNotEmpty()) words += current.toString()
        return words.joinToString(" ") { "\"$it\"*" }
    }

    fun stripNikud(text: String): String = buildString {
        for (ch in text) {
            val value = ch.code
            if (value in 0x0591..0x05BD) continue
            when (value) {
                0x05BF, 0x05C1, 0x05C2, 0x05C4, 0x05C5, 0x05C7 -> continue
                else -> append(ch)
            }
        }
    }

    fun plain(markup: String, stripNikud: Boolean): String {
        val withoutTags = segments(markup).joinToString("") { it.text }
        val cleaned = if (stripNikud) stripNikud(withoutTags) else withoutTags
        return cleaned.replace(Regex("[ \t]+"), " ").trim()
    }

    fun preview(comment: Comment, stripNikud: Boolean): String {
        val source = if (comment.dibbur.isEmpty()) comment.body else comment.dibbur
        val text = plain(source, stripNikud)
        if (text.length <= 90) return text
        return text.take(90) + "…"
    }

    /** The vocalized edition marks the dibbur hamatchil, and other phrases, with `<b>`. */
    fun segments(markup: String): List<MarkupSegment> {
        val output = mutableListOf<MarkupSegment>()
        val buffer = StringBuilder()
        var emphasis = false
        var index = 0
        while (index < markup.length) {
            if (markup[index] == '<') {
                if (markup.startsWith("<b>", index)) {
                    flush(buffer, emphasis, output)
                    emphasis = true
                    index += 3
                    continue
                }
                if (markup.startsWith("</b>", index)) {
                    flush(buffer, emphasis, output)
                    emphasis = false
                    index += 4
                    continue
                }
            }
            buffer.append(markup[index])
            index += 1
        }
        flush(buffer, emphasis, output)
        return output
    }

    private fun flush(buffer: StringBuilder, emphasis: Boolean, output: MutableList<MarkupSegment>) {
        if (buffer.isNotEmpty()) {
            output += MarkupSegment(buffer.toString(), emphasis)
            buffer.clear()
        }
    }
}
