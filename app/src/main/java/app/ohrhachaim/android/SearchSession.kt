package app.ohrhachaim.android

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ohrhachaim.core.SearchHit
import kotlinx.coroutines.delay

/**
 * The state of one visit to Search: the query, its results, and the scroll position.
 *
 * It belongs to [OhrNav], not to the search screen. Opening a result replaces the screen with the
 * reading, and Back recreates it; keeping the state here means Back returns to the same query,
 * the same results, and the same place in the list. Opening Search again from Home starts a new
 * session, as on iOS.
 */
class SearchSession(initialQuery: String = "") {
    var query by mutableStateOf(initialQuery)

    var results by mutableStateOf<List<SearchHit>>(emptyList())
        private set

    /** The query that [results] belong to, or null before the first search finishes. */
    var searched by mutableStateOf<String?>(null)
        private set

    val listState = LazyListState()

    /** True once the search for the current query has finished and found nothing. */
    val showsNoResults: Boolean get() = query.isNotBlank() && searched == query && results.isEmpty()

    /**
     * Brings [results] up to date with [query]. Typing waits [debounceMillis] first, so each
     * keystroke does not query the index. Coming back to a session whose results already match
     * does nothing, which keeps the list and its scroll position as they were.
     */
    suspend fun refresh(debounceMillis: Long = SEARCH_DEBOUNCE_MS, search: suspend (String) -> List<SearchHit>) {
        val text = query
        if (text == searched) return
        if (text.isBlank()) {
            results = emptyList()
            searched = text
            return
        }
        delay(debounceMillis)
        val found = search(text)
        results = found
        searched = text
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
