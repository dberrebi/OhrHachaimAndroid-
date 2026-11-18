package app.ohrhachaim.android

import app.ohrhachaim.core.Comment
import app.ohrhachaim.core.HomeDestination
import app.ohrhachaim.core.ReadingRoute
import app.ohrhachaim.core.SearchHit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSessionTest {
    private val calls = mutableListOf<String>()

    private suspend fun search(text: String): List<SearchHit> {
        calls += text
        return if (text == "אור") listOf(hit(1), hit(2)) else emptyList()
    }

    @Test
    fun searchesOncePerQuery() = runBlocking {
        val session = SearchSession("אור")
        session.refresh(debounceMillis = 0, search = ::search)
        assertEquals(listOf(1L, 2L), session.results.map { it.id })
        assertEquals("אור", session.searched)

        // Coming back to the screen with the same query keeps the results without searching again.
        session.refresh(debounceMillis = 0, search = ::search)
        assertEquals(listOf("אור"), calls)
    }

    @Test
    fun noResultsOnlyAfterTheSearchForTheCurrentTextFinishes() = runBlocking {
        val session = SearchSession()
        session.query = "xyz"
        assertFalse(session.showsNoResults)
        session.refresh(debounceMillis = 0, search = ::search)
        assertTrue(session.showsNoResults)

        session.query = "xyzw"
        assertFalse(session.showsNoResults)

        session.query = "  "
        session.refresh(debounceMillis = 0, search = ::search)
        assertTrue(session.results.isEmpty())
        assertFalse(session.showsNoResults)
        assertEquals(listOf("xyz"), calls)
    }

    @Test
    fun backFromAResultKeepsTheSearch() {
        val nav = OhrNav()
        nav.open(HomeDestination.Search)
        val session = nav.search
        session.query = "אור"

        nav.openReading(ReadingRoute.Parasha(1, scrollTarget = 1))
        nav.back()
        assertEquals(HomeDestination.Search, nav.section)
        assertSame(session, nav.search)
        assertEquals("אור", nav.search.query)

        // Choosing Search again while it is open keeps it; opening it from Home starts afresh.
        nav.open(HomeDestination.Search)
        assertSame(session, nav.search)
        nav.back()
        nav.open(HomeDestination.Search)
        assertNotSame(session, nav.search)
        assertEquals("", nav.search.query)
    }

    private fun hit(id: Long) = SearchHit(
        Comment(
            id = id,
            bookId = 1,
            parashaId = 1,
            chapter = 1,
            verse = 1,
            index = 1,
            dibbur = "",
            body = "אור",
            source = "sefaria-vocalized",
        ),
        snippet = "אור",
    )
}
