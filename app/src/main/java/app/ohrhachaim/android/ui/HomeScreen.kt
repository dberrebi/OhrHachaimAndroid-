package app.ohrhachaim.android.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import app.ohrhachaim.android.L10n
import app.ohrhachaim.android.OhrViewModel
import app.ohrhachaim.android.deviceToday
import app.ohrhachaim.android.shareText
import app.ohrhachaim.core.AppLanguage
import app.ohrhachaim.core.HomeDestination

@Composable
fun HomeScreen(model: OhrViewModel, onOpen: (HomeDestination) -> Unit) {
    val language = LocalLanguage.current
    val store = model.store
    val context = LocalContext.current
    val rows = homeRows(model)
    StudyScaffold(
        title = L10n.text("app.title", language),
        onBack = null,
        showMark = true,
        actions = {
            IconButton(onClick = { shareText(context, homeShareText(model)) }) {
                Icon(Icons.Filled.Share, contentDescription = L10n.text("a11y.share", language), tint = Color.White)
            }
            IconButton(
                onClick = { onOpen(HomeDestination.Favourites) },
                modifier = Modifier.testTag("home.favourites"),
            ) {
                Icon(Icons.Outlined.Star, contentDescription = L10n.text("a11y.favourites", language), tint = Color.White)
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
        ) {
            items(rows, key = { it.tag }) { row ->
                FullWidthRow(
                    onClick = { onOpen(row.destination) },
                    modifier = Modifier.testTag(row.tag),
                ) {
                    CenteredTitle(row.title, row.subtitle)
                }
            }
            if (store == null && !model.failed) {
                item { }
            }
        }
    }
}

private data class HomeRow(
    val tag: String,
    val title: String,
    val subtitle: String?,
    val destination: HomeDestination,
)

@Composable
private fun homeRows(model: OhrViewModel): List<HomeRow> {
    val language = LocalLanguage.current
    val store = model.store
    val rows = mutableListOf(
        HomeRow("home.resume", L10n.text("home.resume", language), resumeSubtitle(model), HomeDestination.Resume),
        HomeRow("home.history", L10n.text("home.history", language), null, HomeDestination.History),
        HomeRow("home.weekly", L10n.text("home.weekly", language), weeklySubtitle(model), HomeDestination.Weekly),
        HomeRow("home.search", L10n.text("home.search", language), null, HomeDestination.Search),
    )
    store?.books?.forEach { book ->
        rows += HomeRow(
            tag = "home.book.${book.id}",
            title = book.name(language),
            subtitle = null,
            destination = HomeDestination.Book(book.id),
        )
    }
    rows += HomeRow("home.settings", L10n.text("home.settings", language), null, HomeDestination.Settings)
    rows += HomeRow("home.help", L10n.text("home.help", language), null, HomeDestination.Help)
    rows += HomeRow("home.about", L10n.text("home.about", language), null, HomeDestination.About)
    return rows
}

@Composable
private fun resumeSubtitle(model: OhrViewModel): String {
    val language = LocalLanguage.current
    val id = model.library.lastCommentId ?: return L10n.text("home.resume.empty", language)
    val store = model.store ?: return L10n.text("home.resume.empty", language)
    val comment = store.comment(id) ?: return L10n.text("reading.missingPlace", language)
    return locationTitle(store, comment, language)
}

@Composable
private fun weeklySubtitle(model: OhrViewModel): String? {
    val language = LocalLanguage.current
    val store = model.store ?: return null
    val today = deviceToday()
    if (!store.readingCovers(today)) {
        return L10n.format("home.weekly.outOfRange", language, store.yearStart, store.yearEnd)
    }
    val reading = store.currentReading(model.settings.israelCalendar, today)
        ?: return L10n.text("home.weekly.empty", language)
    var title = store.title(reading.parashaIds, language)
    if (reading.isSimchatTorah) {
        title += " · " + L10n.text("weekly.simchat", language)
    }
    return title
}

private fun homeShareText(model: OhrViewModel): String {
    val language = model.settings.language
    val store = model.store
    val reading = store?.currentReading(model.settings.israelCalendar, deviceToday())
    if (store != null && reading != null) {
        return L10n.format("share.app", language, store.title(reading.parashaIds, language))
    }
    return L10n.text("app.title", language)
}

internal fun locationTitle(
    store: app.ohrhachaim.core.CommentaryStore,
    comment: app.ohrhachaim.core.Comment,
    language: AppLanguage,
): String {
    val place = L10n.format(
        "reading.place",
        language,
        app.ohrhachaim.core.HebrewText.number(comment.chapter, language),
        app.ohrhachaim.core.HebrewText.number(comment.verse, language),
    )
    return store.locationTitle(comment, language, L10n.text("book.introduction", language), place)
}
