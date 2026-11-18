package app.ohrhachaim.android

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.ohrhachaim.core.AppearanceChoice
import app.ohrhachaim.core.AppLanguage
import app.ohrhachaim.core.Comment
import app.ohrhachaim.core.CommentaryStore
import app.ohrhachaim.core.DatabaseInstaller
import app.ohrhachaim.core.HistoryEntry
import app.ohrhachaim.core.HomeDestination
import app.ohrhachaim.core.IsoDay
import app.ohrhachaim.core.ReadingRoute
import app.ohrhachaim.core.UserLibrary
import app.ohrhachaim.core.usesIsraelCalendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The privacy page is the one the iOS app opens. It links on to the support page, which has a
 * PayPal donation; linking the privacy page from Android was approved on that basis.
 * The support page (https://dberrebi.github.io/OhrHachaim/) is not linked directly from the app,
 * matching iOS, which defines that URL but never opens it.
 */
object AppLinks {
    const val PRIVACY = "https://dberrebi.github.io/OhrHachaim/privacy/"
}

class AppSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ohr-hachaim", Context.MODE_PRIVATE)

    var language by mutableStateOf(AppLanguage.fromCode(prefs.getString(KEY_LANGUAGE, null)))
        private set
    var israelCalendar by mutableStateOf(readIsrael(context))
        private set
    var showNikud by mutableStateOf(if (prefs.contains(KEY_NIKUD)) prefs.getBoolean(KEY_NIKUD, true) else true)
        private set
    var fontScale by mutableFloatStateOf(readFontScale())
        private set
    var appearance by mutableStateOf(AppearanceChoice.fromCode(prefs.getString(KEY_APPEARANCE, null)))
        private set

    val commentarySize: Float get() = 22f * fontScale

    fun chooseLanguage(value: AppLanguage) {
        language = value
        prefs.edit().putString(KEY_LANGUAGE, value.code).apply()
    }

    fun chooseIsraelCalendar(value: Boolean) {
        israelCalendar = value
        prefs.edit().putBoolean(KEY_ISRAEL, value).apply()
    }

    fun chooseNikud(value: Boolean) {
        showNikud = value
        prefs.edit().putBoolean(KEY_NIKUD, value).apply()
    }

    fun chooseFontScale(value: Float) {
        val snapped = snapFontScale(value)
        fontScale = snapped
        prefs.edit().putFloat(KEY_FONT, snapped).apply()
    }

    fun chooseAppearance(value: AppearanceChoice) {
        appearance = value
        prefs.edit().putString(KEY_APPEARANCE, value.code).apply()
    }

    private fun readIsrael(context: Context): Boolean {
        if (!prefs.contains(KEY_ISRAEL)) return usesIsraelCalendar(regionCode(context))
        return prefs.getBoolean(KEY_ISRAEL, false)
    }

    private fun readFontScale(): Float {
        if (!prefs.contains(KEY_FONT)) return 1f
        return prefs.getFloat(KEY_FONT, 1f).coerceIn(0.85f, 1.75f)
    }

    private companion object {
        const val KEY_LANGUAGE = "ui.language"
        const val KEY_ISRAEL = "calendar.israel"
        const val KEY_NIKUD = "text.nikud"
        const val KEY_FONT = "text.fontScale"
        const val KEY_APPEARANCE = "ui.appearance"
    }
}

fun snapFontScale(value: Float): Float {
    val steps = ((value - 0.85f) / 0.05f).toInt().let { raw ->
        val exact = 0.85f + raw * 0.05f
        if (value - exact >= 0.025f) raw + 1 else raw
    }
    return (0.85f + steps * 0.05f).coerceIn(0.85f, 1.75f)
}

fun regionCode(context: Context): String? {
    val country = context.resources.configuration.locales[0].country
    return country.ifEmpty { null }
}

fun deviceToday(): IsoDay {
    val date = java.time.LocalDate.now()
    return IsoDay(date.year, date.monthValue, date.dayOfMonth)
}

class OhrNav {
    var section by mutableStateOf<HomeDestination?>(null)
    val readings = mutableStateListOf<ReadingRoute>()

    /** Survives opening a result and coming back. A new one starts each time Search is opened. */
    var search by mutableStateOf(SearchSession())
        private set

    fun open(destination: HomeDestination) {
        if (destination == HomeDestination.Search && section != HomeDestination.Search) {
            search = SearchSession()
        }
        section = destination
        readings.clear()
    }

    fun openReading(route: ReadingRoute) {
        readings.add(route)
    }

    fun back() {
        if (readings.isNotEmpty()) {
            readings.removeAt(readings.lastIndex)
        } else {
            section = null
        }
    }
}

/**
 * Compose sees library changes. [UserLibrary] itself is a plain object, so screens read [tick].
 */
class ObservableLibrary(file: File) {
    private val library = UserLibrary(file)
    var tick by mutableIntStateOf(0)
        private set

    val lastCommentId: Long? get() { tick; return library.lastCommentId }
    val history: List<HistoryEntry> get() { tick; return library.history }
    val favouriteIds: List<Long> get() { tick; return library.favouriteIds }

    fun isFavourite(id: Long): Boolean {
        tick
        return library.isFavourite(id)
    }

    fun toggleFavourite(id: Long) {
        library.toggleFavourite(id)
        tick++
    }

    fun note(comment: Comment) {
        library.note(comment)
        tick++
    }

    fun route(comment: Comment): ReadingRoute = library.route(comment)

    fun flush() = library.flush()

    fun close() = library.close()
}

class OhrViewModel(app: Application) : AndroidViewModel(app) {
    val settings = AppSettings(app)
    val library = ObservableLibrary(File(app.filesDir, "ohr-hachaim-library.json"))
    val nav = OhrNav()
    var store by mutableStateOf<CommentaryStore?>(null)
        private set
    var failed by mutableStateOf(false)
        private set
    private var launchApplied = false

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = DatabaseInstaller.install(app.filesDir) { name -> app.assets.open(name) }
                val opened = CommentaryStore.open(file.absolutePath)
                withContext(Dispatchers.Main) {
                    store = opened
                    failed = opened.loadError != null
                }
            } catch (_: Throwable) {
                withContext(Dispatchers.Main) { failed = true }
            }
        }
    }

    /**
     * Debug builds only: lets screenshot and UI tests open a screen in a chosen language and
     * appearance. Release builds ignore these extras, because the launcher activity is exported and
     * any app could otherwise change the saved settings.
     */
    fun applyLaunch(intent: Intent) {
        if (launchApplied || !BuildConfig.DEBUG) return
        launchApplied = true
        intent.getStringExtra("ohr_language")?.let { settings.chooseLanguage(AppLanguage.fromCode(it)) }
        intent.getStringExtra("ohr_appearance")?.let { settings.chooseAppearance(AppearanceChoice.fromCode(it)) }
        when (intent.getStringExtra("ohr_screen")) {
            "search" -> nav.open(HomeDestination.Search)
            "settings" -> nav.open(HomeDestination.Settings)
            "about" -> nav.open(HomeDestination.About)
            "help" -> nav.open(HomeDestination.Help)
            "weekly" -> nav.open(HomeDestination.Weekly)
            "history" -> nav.open(HomeDestination.History)
            "favourites" -> nav.open(HomeDestination.Favourites)
            "resume" -> nav.open(HomeDestination.Resume)
            "book" -> nav.open(HomeDestination.Book(intent.getIntExtra("ohr_book", 1)))
        }
        intent.getStringExtra("ohr_search")?.let { nav.search.query = it }
    }

    override fun onCleared() {
        library.close()
        store?.close()
    }
}
