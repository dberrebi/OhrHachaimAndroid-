package app.ohrhachaim.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class OhrTests {
    @Test
    fun hebrewNumerals() {
        assertEquals("א׳", HebrewNumerals.string(1))
        assertEquals("י׳", HebrewNumerals.string(10))
        assertEquals("ט״ו", HebrewNumerals.string(15))
        assertEquals("ט״ז", HebrewNumerals.string(16))
        assertEquals("כ״א", HebrewNumerals.string(21))
    }

    @Test
    fun searchQueryDropsNikud() {
        assertEquals("\"אורה\"*", HebrewText.ftsQuery("אוֹרָה"))
        assertEquals("\"בראשית\"* \"ברא\"*", HebrewText.ftsQuery("בראשית ברא"))
        assertEquals("", HebrewText.ftsQuery("light"))
    }

    @Test
    fun nikudStrippingAndMarkup() {
        assertEquals("בראשית", HebrewText.stripNikud("בְּרֵאשִׁית"))
        val marked = "<b>בְּרֵאשִׁית</b> בָּרָא"
        assertEquals("בראשית ברא", HebrewText.plain(marked, stripNikud = true))
        val pointed = HebrewText.plain(marked, stripNikud = false)
        assertTrue(pointed.contains("בְּרֵאשִׁית"))
        assertFalse(pointed.contains("<"))
    }

    @Test
    fun isoDayMatchesCalendarDistances() {
        val nov16 = IsoDay.parse("2026-11-16")!!
        assertEquals("2026-11-21", IsoDay.fromEpochDay(nov16.toEpochDay() + 5).text)
        assertEquals(5, daysBetween(nov16, IsoDay.parse("2026-11-21")!!))
        assertEquals(11, daysBetween(IsoDay.parse("2024-10-05")!!, IsoDay.parse("2024-10-16")!!))
        assertEquals(nov16, IsoDay.fromEpochDay(nov16.toEpochDay()))
    }

    @Test
    fun databaseChecksumMatchesManifest() {
        val manifest = repoFile("data/manifest.json").readText()
        val expected = Regex(""""sha256"\s*:\s*"([0-9a-fA-F]+)"""")
            .find(manifest)!!
            .groupValues[1]
            .lowercase()
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(repoFile("data/ohr_hachaim.sqlite").readBytes())
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, actual)
        assertEquals("9df2715ed8c7c389a0a6ff827b36c1008da050f83823eb9352fefd208c88e142", actual)
    }

    @Test
    fun bundledCommentaryAndFts5() {
        store().use { store ->
            assertNull(store.loadError)
            assertEquals(listOf("בראשית", "שמות", "ויקרא", "במדבר", "דברים"), store.books.map { it.heName })
            assertEquals(54, store.parashot.size)
            assertEquals(listOf(10), store.gapChapters(2))
            assertEquals(listOf(27), store.gapChapters(33))
            assertEquals(14, store.introduction().size)
            assertEquals("Torah reading schedule from Hebcal.com, CC BY 4.0", store.hebcalAttribution)

            val opening = store.comment(1_001_001_001)
            assertEquals(1, opening?.chapter)
            assertEquals(1, opening?.verse)
            assertTrue(opening?.body?.contains("אוֹרָה") == true)

            val plain = store.search("אורה")
            assertTrue(plain.isNotEmpty())
            assertTrue(plain.any { it.comment.body.contains("אוֹרָה") })
            assertTrue(plain[0].snippet.isNotEmpty())
            val pointed = store.search("אוֹרָה")
            assertEquals(plain.map { it.comment.id }, pointed.map { it.comment.id })
            assertTrue(store.search("   ").isEmpty())
            assertTrue(store.search("xyzzy").isEmpty())
            assertNotNull(plain[0].comment.parashaId)
        }
    }

    @Test
    fun currentReadingForIsraelAndDiaspora() {
        store().use { store ->
            val nov16 = day("2026-11-16")
            val israel = store.currentReading(israel = true, today = nov16)
            val diaspora = store.currentReading(israel = false, today = nov16)
            assertEquals(listOf(7), israel?.parashaIds)
            assertEquals(listOf(7), diaspora?.parashaIds)
            assertEquals("ויצא", store.parasha(7)?.heName)
            assertEquals("shabbat", israel?.kind)

            // The day itself, for both calendars.
            assertEquals(listOf(7), store.currentReading(true, day("2026-11-21"))?.parashaIds)
            assertEquals(listOf(7), store.currentReading(false, day("2026-11-21"))?.parashaIds)

            // Six days before the next Shabbat already selects that Shabbat, including the day after
            // the previous one. 21 Nov 2026 is Vayetzei; 22 Nov is six days before Vayishlach.
            assertEquals(listOf(8), store.currentReading(true, day("2026-11-22"))?.parashaIds)

            // Same Gregorian date, different portion.
            assertEquals(listOf(36), store.currentReading(true, day("2026-05-30"))?.parashaIds)
            assertEquals(listOf(35), store.currentReading(false, day("2026-05-30"))?.parashaIds)
            assertEquals("בהעלותך", store.parasha(36)?.heName)
            assertEquals("נשא", store.parasha(35)?.heName)

            // Combined on one side only: diaspora Chukat-Balak, Israel Balak.
            assertEquals(listOf(40), store.currentReading(true, day("2026-06-27"))?.parashaIds)
            assertEquals(listOf(39, 40), store.currentReading(false, day("2026-06-27"))?.parashaIds)
            assertEquals("חקת־בלק", store.title(listOf(39, 40), AppLanguage.HE))
            assertEquals("Chukat–Balak", store.title(listOf(39, 40), AppLanguage.EN))

            // A week that is combined for both calendars.
            assertEquals(listOf(42, 43), store.currentReading(true, day("2024-08-03"))?.parashaIds)
            assertEquals(listOf(42, 43), store.currentReading(false, day("2024-08-03"))?.parashaIds)
            assertEquals("מטות־מסעי", store.title(listOf(42, 43), AppLanguage.HE))

            // Simchat Torah is a day earlier in Israel. וזאת הברכה is parasha 54.
            val israelSimchat = store.currentReading(true, day("2026-10-03"))
            val diasporaSimchat = store.currentReading(false, day("2026-10-04"))
            assertEquals(listOf(54), israelSimchat?.parashaIds)
            assertEquals("simchat_torah", israelSimchat?.kind)
            assertTrue(israelSimchat?.isSimchatTorah == true)
            assertEquals(listOf(54), diasporaSimchat?.parashaIds)
            assertEquals("simchat_torah", diasporaSimchat?.kind)
            assertEquals("וזאת הברכה", store.parasha(54)?.heName)
            // The other calendar's Simchat Torah is still inside the 1–6 day window.
            assertEquals(listOf(54), store.currentReading(false, day("2026-10-03"))?.parashaIds)
            assertEquals("simchat_torah", store.currentReading(false, day("2026-10-03"))?.kind)

            // After Sukkot the table has a gap longer than ten days back and six ahead.
            assertNull(store.currentReading(true, day("2024-10-16")))
            assertNull(store.currentReading(true, day("2024-10-17")))
            assertEquals("simchat_torah", store.currentReading(true, day("2024-10-18"))?.kind)
            assertEquals(listOf(54), store.currentReading(true, day("2024-10-24"))?.parashaIds)

            assertFalse(store.readingCovers(day("2023-06-01")))
            assertTrue(store.readingCovers(day("2026-11-16")))
            assertFalse(store.readingCovers(day("2039-01-01")))
            assertNull(store.currentReading(true, day("2039-06-01")))
        }
    }

    @Test
    fun israelCalendarFollowsRegion() {
        assertTrue(usesIsraelCalendar("IL"))
        assertFalse(usesIsraelCalendar("US"))
        assertFalse(usesIsraelCalendar("GB"))
        assertFalse(usesIsraelCalendar(null))
        assertFalse(usesIsraelCalendar(""))
    }

    @Test
    fun historyKeepsOneRowPerParasha() {
        val file = tempLibrary()
        val library = UserLibrary(file)
        val first = comment(id = 1, parashaId = 1, chapter = 1, verse = 1)
        val later = comment(id = 9, parashaId = 1, chapter = 1, verse = 3)
        val other = comment(id = 20, parashaId = 2, chapter = 6, verse = 9)
        library.note(first)
        library.note(later)
        assertEquals(1, library.history.size)
        assertEquals(9L, library.history[0].commentId)
        library.note(other)
        assertEquals(2, library.history.size)
        assertEquals(2, library.history[0].parashaId)
        library.note(comment(id = 21, parashaId = null, chapter = 0, verse = 1, source = "on-your-way-introduction"))
        assertEquals(3, library.history.size)
        assertEquals("intro", library.history[0].id)
        repeat(50) { index ->
            library.note(comment(id = 100L + index, parashaId = 100 + index, chapter = 1, verse = 1))
        }
        assertEquals(40, library.history.size)
        library.flush()
        val reloaded = UserLibrary(file)
        assertEquals(40, reloaded.history.size)
        assertEquals(library.history[0].commentId, reloaded.history[0].commentId)
        assertEquals(library.lastCommentId, reloaded.lastCommentId)
        library.close()
        reloaded.close()
    }

    @Test
    fun unreadableLibraryIsBackedUp() {
        val directory = File(System.getProperty("java.io.tmpdir"), "ohr-" + System.nanoTime())
        directory.mkdirs()
        val file = File(directory, "library.json")
        file.writeText("not json")
        val library = UserLibrary(file)
        assertTrue(library.history.isEmpty())
        assertNull(library.lastCommentId)
        val names = directory.list()?.toList().orEmpty()
        assertTrue(names.any { it.contains("corrupt-") })
        library.close()
    }

    @Test
    fun datesUseAsciiDigitsInAnyDeviceLanguage() {
        val saved = Locale.getDefault()
        try {
            // Arabic and Persian format %d with their own digits by default.
            for (tag in listOf("ar-EG", "fa-IR")) {
                Locale.setDefault(Locale.forLanguageTag(tag))
                assertEquals("2026-11-21", day("2026-11-21").text)
                store().use { store ->
                    // On Shabbat itself the match is exact; it must not fall back to last week.
                    assertEquals(listOf(7), store.currentReading(true, day("2026-11-21"))?.parashaIds)
                    assertEquals(listOf(7), store.currentReading(false, day("2026-11-21"))?.parashaIds)
                }
                val file = tempLibrary()
                UserLibrary(file, clockMillis = { 1_795_000_000_000L }).apply {
                    note(comment(id = 1, parashaId = 1, chapter = 1, verse = 1))
                    close()
                }
                assertTrue(file.readText().contains("\"2026-11-18T"))
            }
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun databaseUpdateOfTheSameSizeIsInstalled() {
        val directory = tempDirectory()
        val v1 = ByteArray(8192) { 1 }
        val v2 = ByteArray(8192) { 2 }

        val first = DatabaseInstaller.install(directory, bundle(v1))
        assertArrayEquals(v1, first.readBytes())

        // The next launch of the same build reuses the copy without reading the database asset.
        val opened = mutableListOf<String>()
        DatabaseInstaller.install(directory) { name -> opened += name; bundle(v1)(name) }
        assertEquals(listOf(DatabaseInstaller.MANIFEST), opened)

        // An app update that ships a different database with the same byte count replaces it.
        val updated = DatabaseInstaller.install(directory, bundle(v2))
        assertArrayEquals(v2, updated.readBytes())
        assertEquals(DatabaseInstaller.hex(sha256(v2)), File(directory, "ohr_hachaim.sqlite.sha256").readText())
    }

    @Test
    fun badBundleKeepsTheInstalledDatabase() {
        val directory = tempDirectory()
        val good = ByteArray(4096) { 3 }
        DatabaseInstaller.install(directory, bundle(good))
        val manifest = manifestFor(ByteArray(4096) { 4 })
        assertThrows(IllegalStateException::class.java) {
            DatabaseInstaller.install(directory) { name ->
                if (name == DatabaseInstaller.MANIFEST) manifest.byteInputStream() else good.inputStream()
            }
        }
        assertArrayEquals(good, File(directory, DatabaseInstaller.DATABASE).readBytes())
        assertFalse(File(directory, DatabaseInstaller.DATABASE + ".part").exists())
    }

    @Test
    fun bundledManifestMatchesTheInstallerChecksum() {
        val directory = tempDirectory()
        val installed = DatabaseInstaller.install(directory) { name -> repoFile("data/$name").inputStream() }
        assertEquals(
            DatabaseInstaller.sha256In(repoFile("data/manifest.json").readText()),
            DatabaseInstaller.sha256(installed),
        )
    }

    @Test
    fun favouritesAndHistoryAreBothSaved() {
        val file = tempLibrary()
        val library = UserLibrary(file)
        library.note(comment(id = 5, parashaId = 1, chapter = 1, verse = 2))
        library.toggleFavourite(5)
        library.note(comment(id = 6, parashaId = 1, chapter = 1, verse = 3))
        library.toggleFavourite(6)
        library.flush()
        val reloaded = UserLibrary(file)
        assertEquals(listOf(6L, 5L), reloaded.favouriteIds)
        assertEquals(6L, reloaded.lastCommentId)
        assertEquals(6L, reloaded.history.single().commentId)
        library.close()
        // Calls after close are ignored rather than throwing.
        library.flush()
        library.toggleFavourite(7)
        library.close()
        reloaded.close()
    }

    @Test
    fun storePreloadsAndServesSeveralThreads() {
        store().use { store ->
            val weekly = ReadingRoute.Weekly(listOf(39, 40), null)
            val introduction = ReadingRoute.Introduction(null)
            assertFalse(store.isLoaded(weekly))
            assertFalse(store.isLoaded(introduction))
            store.preload(weekly)
            store.preload(introduction)
            assertTrue(store.isLoaded(weekly))
            assertTrue(store.isLoaded(introduction))
            assertTrue(store.isLoaded(ReadingRoute.Parasha(39, null)))
            assertFalse(store.isLoaded(ReadingRoute.Parasha(1, null)))

            val pool = Executors.newFixedThreadPool(4)
            try {
                val jobs = (1..54).map { id ->
                    Callable {
                        store.comments(id).size + store.gapChapters(id).size + store.search("אור").size
                    }
                }
                pool.invokeAll(jobs).forEach { it.get() }
            } finally {
                pool.shutdown()
            }
            assertTrue((1..54).all { store.isLoaded(ReadingRoute.Parasha(it, null)) })
            assertEquals(5_864, (1..54).sumOf { store.comments(it).size })
        }
    }

    private fun bundle(database: ByteArray): (String) -> java.io.InputStream = { name ->
        when (name) {
            DatabaseInstaller.MANIFEST -> manifestFor(database).byteInputStream()
            DatabaseInstaller.DATABASE -> database.inputStream()
            else -> error(name)
        }
    }

    private fun manifestFor(database: ByteArray): String =
        """{"sha256": "${DatabaseInstaller.hex(sha256(database))}", "bytes": ${database.size}}"""

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun tempDirectory(): File =
        File(System.getProperty("java.io.tmpdir"), "ohr-" + System.nanoTime()).also { it.mkdirs() }

    private fun store(): CommentaryStore = CommentaryStore.open(repoFile("data/ohr_hachaim.sqlite").absolutePath)

    private fun day(text: String): IsoDay = IsoDay.parse(text)!!

    private fun repoFile(relative: String): File {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "data/manifest.json").isFile }
        return File(root, relative)
    }

    private fun tempLibrary(): File {
        val directory = File(System.getProperty("java.io.tmpdir"), "ohr-" + System.nanoTime())
        directory.mkdirs()
        return File(directory, "library.json")
    }

    private fun comment(
        id: Long,
        parashaId: Int?,
        chapter: Int,
        verse: Int,
        source: String = "sefaria-vocalized",
    ) = Comment(
        id = id,
        bookId = 1,
        parashaId = parashaId,
        chapter = chapter,
        verse = verse,
        index = 1,
        dibbur = "",
        body = "body",
        source = source,
    )
}
