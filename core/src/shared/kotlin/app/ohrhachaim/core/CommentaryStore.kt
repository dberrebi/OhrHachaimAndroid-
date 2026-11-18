package app.ohrhachaim.core

/**
 * Read-only view of the bundled `ohr_hachaim.sqlite`.
 * `currentReading` is the iOS rule: today's row, else the next row 1–6 days ahead,
 * else the latest row 1–10 days back.
 */
class CommentaryStore internal constructor(
    private val session: SqlSession,
) : AutoCloseable {
    var books: List<Book> = emptyList()
        private set
    var parashot: List<Parasha> = emptyList()
        private set
    var weekly: List<WeeklyReading> = emptyList()
        private set
    var meta: Map<String, String> = emptyMap()
        private set
    var loadError: String? = null
        private set

    private val commentsByParasha = mutableMapOf<Int, List<Comment>>()
    private var introductionCache: List<Comment>? = null
    private val gapsByParasha = mutableMapOf<Int, List<Int>>()
    private var readingCacheKey: String? = null
    private var readingCacheValue: WeeklyReading? = null

    val yearStart: String get() = meta["year_start"] ?: "2024"
    val yearEnd: String get() = meta["year_end"] ?: "2038"
    val hebcalAttribution: String
        get() = meta["hebcal_attribution"] ?: "Torah reading schedule from Hebcal.com, CC BY 4.0"

    init {
        load()
    }

    fun book(id: Int): Book? = books.firstOrNull { it.id == id }

    fun parasha(id: Int): Parasha? = parashot.firstOrNull { it.id == id }

    fun parashotIn(bookId: Int): List<Parasha> =
        parashot.filter { it.bookId == bookId }.sortedBy { it.sortOrder }

    fun hasIntroduction(bookId: Int): Boolean = bookId == 1 && introduction().isNotEmpty()

    fun introduction(): List<Comment> {
        introductionCache?.let { return it }
        val rows = comments(
            """
            SELECT id, book_id, parasha_id, chapter, verse, comment_index, dibbur, body, source
            FROM comments
            WHERE source = 'on-your-way-introduction'
            ORDER BY verse, comment_index
            """.trimIndent(),
        )
        introductionCache = rows
        return rows
    }

    fun comments(parashaId: Int): List<Comment> {
        commentsByParasha[parashaId]?.let { return it }
        val rows = comments(
            """
            SELECT id, book_id, parasha_id, chapter, verse, comment_index, dibbur, body, source
            FROM comments
            WHERE parasha_id = ?
            ORDER BY chapter, verse, comment_index
            """.trimIndent(),
        ) { it.bindLong(1, parashaId.toLong()) }
        commentsByParasha[parashaId] = rows
        return rows
    }

    fun search(rawQuery: String, limit: Int = 40): List<SearchHit> {
        val match = HebrewText.ftsQuery(rawQuery)
        if (match.isEmpty() || loadError == "fts5") return emptyList()
        val rows = try {
            session.query(
                """
                SELECT comment_id, snippet(comments_fts, 1, '', '', '…', 18)
                FROM comments_fts
                WHERE comments_fts MATCH ?
                ORDER BY rank
                LIMIT ?
                """.trimIndent(),
                bind = {
                    it.bindText(1, match)
                    it.bindLong(2, limit.toLong())
                },
            ) { row -> row.getLong(0) to row.getText(1) }
        } catch (_: Exception) {
            return emptyList()
        }
        return rows.mapNotNull { (id, snippet) ->
            val found = comment(id) ?: return@mapNotNull null
            val visible = snippet.ifEmpty { HebrewText.preview(found, stripNikud = true) }
            SearchHit(found, visible)
        }
    }

    fun comment(id: Long): Comment? {
        commentsByParasha.values.forEach { list ->
            list.firstOrNull { it.id == id }?.let { return it }
        }
        introductionCache?.firstOrNull { it.id == id }?.let { return it }
        return comments(
            """
            SELECT id, book_id, parasha_id, chapter, verse, comment_index, dibbur, body, source
            FROM comments
            WHERE id = ?
            LIMIT 1
            """.trimIndent(),
        ) { it.bindLong(1, id) }.firstOrNull()
    }

    fun gapChapters(parashaId: Int): List<Int> {
        gapsByParasha[parashaId]?.let { return it }
        val chapters = session.query(
            "SELECT chapter FROM gaps WHERE parasha_id = ? ORDER BY chapter",
            bind = { it.bindLong(1, parashaId.toLong()) },
        ) { it.getInt(0) }
        gapsByParasha[parashaId] = chapters
        return chapters
    }

    fun title(parashaIds: List<Int>, language: AppLanguage): String {
        val names = parashaIds.mapNotNull { parasha(it)?.name(language) }
        val separator = if (language == AppLanguage.HE) "־" else "–"
        return names.joinToString(separator)
    }

    fun locationTitle(comment: Comment, language: AppLanguage, introductionLabel: String, place: String): String {
        if (comment.isIntroduction) return introductionLabel
        val parashaName = comment.parashaId?.let { parasha(it)?.name(language) }.orEmpty()
        if (parashaName.isEmpty()) return place
        return "$parashaName · $place"
    }

    /**
     * The reading for "this week": today's row, otherwise the one coming within six days,
     * otherwise the most recent reading in the last ten days.
     */
    fun currentReading(israel: Boolean, today: IsoDay): WeeklyReading? {
        val key = "${today.text}#${if (israel) 1 else 0}"
        if (key == readingCacheKey) return readingCacheValue
        val value = resolveReading(israel, today)
        readingCacheKey = key
        readingCacheValue = value
        return value
    }

    fun readingCovers(today: IsoDay): Boolean {
        val start = yearStart.toIntOrNull() ?: return true
        val end = yearEnd.toIntOrNull() ?: return true
        return today.year in start..end
    }

    private fun resolveReading(israel: Boolean, today: IsoDay): WeeklyReading? {
        val rows = weekly.filter { it.israel == israel }
        rows.firstOrNull { it.date == today.text }?.let { return it }
        rows.firstOrNull { row ->
            val date = IsoDay.parse(row.date) ?: return@firstOrNull false
            val days = daysBetween(today, date)
            days > 0 && days <= 6
        }?.let { return it }
        rows.asReversed().firstOrNull { row ->
            val date = IsoDay.parse(row.date) ?: return@firstOrNull false
            val days = daysBetween(date, today)
            days > 0 && days <= 10
        }?.let { return it }
        return null
    }

    private fun load() {
        val fts = try {
            session.query("SELECT sqlite_compileoption_used('ENABLE_FTS5')") { it.getLong(0) }.firstOrNull()
        } catch (_: Exception) {
            null
        }
        if (fts != 1L) {
            loadError = "fts5"
        }
        books = session.query("SELECT id, he_name, en_name FROM books ORDER BY sort_order") { row ->
            Book(row.getInt(0), row.getText(1), row.getText(2))
        }
        parashot = session.query(
            """
            SELECT id, book_id, he_name, en_name, sort_order
            FROM parashot
            ORDER BY sort_order
            """.trimIndent(),
        ) { row ->
            Parasha(row.getInt(0), row.getInt(1), row.getText(2), row.getText(3), row.getInt(4))
        }
        weekly = session.query(
            """
            SELECT gregorian_date, israel, hebcal_title, hdate, parasha_ids, kind
            FROM weekly
            ORDER BY gregorian_date
            """.trimIndent(),
        ) { row ->
            val ids = row.getText(4).split(',').mapNotNull { it.toIntOrNull() }
            WeeklyReading(
                date = row.getText(0),
                israel = row.getInt(1) == 1,
                hebcalTitle = row.getText(2),
                hdate = row.getText(3),
                parashaIds = ids,
                kind = row.getText(5),
            )
        }
        val metaRows = session.query("SELECT key, value FROM meta") { row ->
            row.getText(0) to row.getText(1)
        }
        meta = metaRows.toMap()
        if (loadError == null && (books.isEmpty() || parashot.size != 54)) {
            loadError = "incomplete"
        }
        if (loadError == null) {
            try {
                session.query(
                    """SELECT comment_id FROM comments_fts WHERE comments_fts MATCH '"אור"*' LIMIT 1""",
                ) { it.getLong(0) }
            } catch (_: Exception) {
                loadError = "fts5"
            }
        }
    }

    private fun comments(sql: String, bind: (SqlBinder) -> Unit = {}): List<Comment> =
        session.query(sql, bind) { row ->
            Comment(
                id = row.getLong(0),
                bookId = row.getInt(1),
                parashaId = if (row.isNull(2)) null else row.getInt(2),
                chapter = row.getInt(3),
                verse = row.getInt(4),
                index = row.getInt(5),
                dibbur = row.getText(6),
                body = row.getText(7),
                source = row.getText(8),
            )
        }

    override fun close() {
        session.close()
    }

    companion object {
        fun open(path: String): CommentaryStore = CommentaryStore(openBundledSession(path))
    }
}
