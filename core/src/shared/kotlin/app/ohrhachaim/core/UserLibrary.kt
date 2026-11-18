package app.ohrhachaim.core

import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

data class HistoryEntry(
    val commentId: Long,
    val parashaId: Int?,
    val chapter: Int,
    val verse: Int,
    val date: String,
) {
    /** One row per parasha. The introduction is its own row. */
    val id: String get() = parashaId?.let { "p-$it" } ?: "intro"
}

/**
 * Resume, history, and favourites. History is capped at 40 and keeps one row per parasha.
 * Writes wait 400ms, matching the iOS library file. An unreadable file is copied aside and the
 * library starts empty.
 *
 * Every write runs on one background thread, in the order it was requested, so an older
 * snapshot can never land on disk after a newer one.
 */
class UserLibrary(
    private val file: File,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
) {
    var lastCommentId: Long? = null
        private set
    var history: List<HistoryEntry> = emptyList()
        private set
    var favouriteIds: List<Long> = emptyList()
        private set

    private val writer = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "ohr-hachaim.library").apply { isDaemon = true }
    }
    private var pending: ScheduledFuture<*>? = null

    init {
        load()
    }

    fun isFavourite(id: Long): Boolean = favouriteIds.contains(id)

    fun toggleFavourite(id: Long) {
        favouriteIds = if (id in favouriteIds) {
            favouriteIds.filterNot { it == id }
        } else {
            listOf(id) + favouriteIds
        }
        saveNow()
    }

    /** Records reading progress for the parasha. Scrolling inside one parasha updates that row. */
    fun note(comment: Comment) {
        lastCommentId = comment.id
        val index = history.indexOfFirst { it.parashaId == comment.parashaId }
        if (index >= 0) {
            val current = history[index]
            if (current.commentId == comment.id &&
                current.chapter == comment.chapter &&
                current.verse == comment.verse
            ) {
                return
            }
            val updated = current.copy(
                commentId = comment.id,
                chapter = comment.chapter,
                verse = comment.verse,
                date = nowIso(),
            )
            history = if (index == 0) {
                history.toMutableList().also { it[0] = updated }
            } else {
                listOf(updated) + history.filterIndexed { i, _ -> i != index }
            }
        } else {
            val entry = HistoryEntry(
                commentId = comment.id,
                parashaId = comment.parashaId,
                chapter = comment.chapter,
                verse = comment.verse,
                date = nowIso(),
            )
            history = (listOf(entry) + history).take(40)
        }
        scheduleSave()
    }

    fun route(comment: Comment): ReadingRoute {
        if (comment.isIntroduction) return ReadingRoute.Introduction(comment.id)
        val parashaId = comment.parashaId
        if (parashaId != null) return ReadingRoute.Parasha(parashaId, comment.id)
        return ReadingRoute.Introduction(comment.id)
    }

    /** Writes the current state and waits for it, for example when the app goes to the background. */
    fun flush() {
        val done = synchronized(this) {
            if (writer.isShutdown) return
            pending?.cancel(false)
            pending = null
            val snapshot = snapshot()
            writer.submit { write(snapshot) }
        }
        try {
            done.get(5, TimeUnit.SECONDS)
        } catch (_: Exception) {
            // The write is still queued, or failed and left the previous file in place.
        }
    }

    fun close() {
        flush()
        synchronized(this) { writer.shutdown() }
    }

    /** Queues a write of the current state without waiting for it. */
    private fun saveNow() {
        synchronized(this) {
            if (writer.isShutdown) return
            pending?.cancel(false)
            pending = null
            val snapshot = snapshot()
            writer.execute { write(snapshot) }
        }
    }

    private fun nowIso(): String {
        val seconds = Math.floorDiv(clockMillis(), 1000L)
        val day = IsoDay.fromEpochDay(Math.floorDiv(seconds, 86_400L))
        val sod = Math.floorMod(seconds, 86_400L).toInt()
        val hour = sod / 3600
        val minute = (sod % 3600) / 60
        val second = sod % 60
        return String.format(Locale.US, "%sT%02d:%02d:%02dZ", day.text, hour, minute, second)
    }

    private fun scheduleSave() {
        synchronized(this) {
            if (writer.isShutdown) return
            pending?.cancel(false)
            val snapshot = snapshot()
            pending = writer.schedule({ write(snapshot) }, 400, TimeUnit.MILLISECONDS)
        }
    }

    private fun snapshot() = LibrarySnapshot(lastCommentId, history.toList(), favouriteIds.toList())

    private fun load() {
        if (!file.exists()) return
        try {
            val snapshot = LibraryJson.decode(file.readText())
            lastCommentId = snapshot.lastCommentId
            history = snapshot.history
            favouriteIds = snapshot.favouriteIds
        } catch (_: Exception) {
            val stamp = clockMillis() / 1000
            val backup = File(file.parentFile, file.name + ".corrupt-$stamp")
            try {
                file.copyTo(backup, overwrite = true)
            } catch (_: Exception) {
                // The library still starts empty when the backup cannot be written.
            }
        }
    }

    /** Runs only on [writer]. */
    private fun write(snapshot: LibrarySnapshot) {
        try {
            file.parentFile?.mkdirs()
            val data = LibraryJson.encode(snapshot)
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(data)
            if (!temp.renameTo(file)) {
                file.writeText(data)
                temp.delete()
            }
        } catch (_: Exception) {
            // A failed write leaves the previous file in place.
        }
    }
}

internal data class LibrarySnapshot(
    val lastCommentId: Long?,
    val history: List<HistoryEntry>,
    val favouriteIds: List<Long>,
)

/** JSON for the library file. Keys match the iOS `ohr-hachaim-library.json` shape. */
internal object LibraryJson {
    fun encode(snapshot: LibrarySnapshot): String = buildString {
        append("{\n")
        append("  \"favouriteIDs\" : ")
        append(snapshot.favouriteIds.joinToString(prefix = "[", postfix = "]"))
        append(",\n  \"history\" : ")
        if (snapshot.history.isEmpty()) {
            append("[]")
        } else {
            append("[\n")
            snapshot.history.forEachIndexed { index, entry ->
                append("    {\n")
                append("      \"chapter\" : ${entry.chapter},\n")
                append("      \"commentID\" : ${entry.commentId},\n")
                append("      \"date\" : ${quote(entry.date)},\n")
                append("      \"parashaID\" : ${entry.parashaId?.toString() ?: "null"},\n")
                append("      \"verse\" : ${entry.verse}\n")
                append("    }")
                if (index != snapshot.history.lastIndex) append(",")
                append("\n")
            }
            append("  ]")
        }
        append(",\n  \"lastCommentID\" : ${snapshot.lastCommentId?.toString() ?: "null"}\n")
        append("}\n")
    }

    fun decode(text: String): LibrarySnapshot {
        val root = JsonParser(text).parse() as? Map<*, *> ?: error("library root")
        val favourites = (root["favouriteIDs"] as? List<*>)?.map { number(it) } ?: emptyList()
        val history = (root["history"] as? List<*>)?.map { item ->
            val obj = item as? Map<*, *> ?: error("history row")
            HistoryEntry(
                commentId = number(obj["commentID"] ?: error("commentID")),
                parashaId = obj["parashaID"]?.let { number(it).toInt() },
                chapter = number(obj["chapter"] ?: error("chapter")).toInt(),
                verse = number(obj["verse"] ?: error("verse")).toInt(),
                date = obj["date"] as? String ?: error("date"),
            )
        } ?: emptyList()
        val last = root["lastCommentID"]?.let { number(it) }
        return LibrarySnapshot(last, history, favourites)
    }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                else -> append(ch)
            }
        }
        append('"')
    }

    private fun number(value: Any?): Long = when (value) {
        is Long -> value
        is Int -> value.toLong()
        is Double -> value.toLong()
        is String -> value.toLong()
        else -> error("number")
    }
}

/** Just enough JSON for the library file: objects, arrays, strings, numbers, and null. */
private class JsonParser(private val text: String) {
    private var index = 0

    fun parse(): Any? {
        skip()
        val value = readValue()
        skip()
        if (index != text.length) error("trailing")
        return value
    }

    private fun readValue(): Any? {
        skip()
        if (index >= text.length) error("end")
        return when (text[index]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> readString()
            'n' -> {
                expect("null")
                null
            }
            't' -> {
                expect("true")
                true
            }
            'f' -> {
                expect("false")
                false
            }
            else -> readNumber()
        }
    }

    private fun readObject(): Map<String, Any?> {
        expect("{")
        val map = linkedMapOf<String, Any?>()
        skip()
        if (peek('}')) {
            index++
            return map
        }
        while (true) {
            skip()
            val key = readString()
            skip()
            expect(":")
            map[key] = readValue()
            skip()
            when {
                peek('}') -> {
                    index++
                    return map
                }
                peek(',') -> index++
                else -> error("object")
            }
        }
    }

    private fun readArray(): List<Any?> {
        expect("[")
        val list = mutableListOf<Any?>()
        skip()
        if (peek(']')) {
            index++
            return list
        }
        while (true) {
            list += readValue()
            skip()
            when {
                peek(']') -> {
                    index++
                    return list
                }
                peek(',') -> index++
                else -> error("array")
            }
        }
    }

    private fun readString(): String {
        expect("\"")
        val out = StringBuilder()
        while (index < text.length) {
            val ch = text[index++]
            when (ch) {
                '"' -> return out.toString()
                '\\' -> {
                    val esc = text[index++]
                    out.append(
                        when (esc) {
                            '"', '\\', '/' -> esc
                            'b' -> '\b'
                            'f' -> '\u000c'
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            'u' -> text.substring(index, index + 4).toInt(16).toChar().also { index += 4 }
                            else -> error("escape")
                        },
                    )
                }
                else -> out.append(ch)
            }
        }
        error("string")
    }

    private fun readNumber(): Long {
        val start = index
        if (peek('-')) index++
        while (index < text.length && text[index].isDigit()) index++
        if (start == index || (text[start] == '-' && index == start + 1)) error("number")
        return text.substring(start, index).toLong()
    }

    private fun skip() {
        while (index < text.length && text[index].isWhitespace()) index++
    }

    private fun peek(ch: Char): Boolean = index < text.length && text[index] == ch

    private fun expect(token: String) {
        skip()
        if (!text.startsWith(token, index)) error("expected $token")
        index += token.length
    }
}
