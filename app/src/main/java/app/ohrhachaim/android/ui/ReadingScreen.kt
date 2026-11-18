package app.ohrhachaim.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ohrhachaim.android.L10n
import app.ohrhachaim.android.OhrViewModel
import app.ohrhachaim.android.copyText
import app.ohrhachaim.android.shareText
import app.ohrhachaim.core.AppLanguage
import app.ohrhachaim.core.Comment
import app.ohrhachaim.core.CommentaryStore
import app.ohrhachaim.core.HebrewNumerals
import app.ohrhachaim.core.HebrewText
import app.ohrhachaim.core.ReadingRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val SETTLE_MS = 4_000L
private const val OPENING_MS = 20_000L

@Composable
fun ReadingScreen(model: OhrViewModel, route: ReadingRoute, onBack: (() -> Unit)?) {
    val store = model.store ?: return
    val language = LocalLanguage.current
    val context = LocalContext.current
    val rows = remember(route, store) { readingRows(store, route) }
    val listState = rememberLazyListState()
    val title = readingTitle(store, route, language)
    TrackReading(model, store, route, listState)
    StudyScaffold(
        title = title,
        onBack = onBack,
        actions = {
            IconButton(onClick = { shareText(context, portionShare(store, route, language, title)) }) {
                Icon(Icons.Filled.Share, contentDescription = L10n.text("a11y.share", language), tint = Color.White)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .widthIn(max = 740.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 48.dp,
                ),
            ) {
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is HeaderRow -> HeaderBlock(store, row.parashaId, language)
                        is GapRow -> GapBlock(row.chapter, language)
                        is VerseRow -> VerseBlock(row.chapter, row.verse, language)
                        is CommentItem -> CommentBlock(model, store, row.comment, language)
                        EmptyItem -> Text(
                            L10n.text("reading.empty", language),
                            style = menuStyle().copy(color = LocalColors.current.secondary),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        )
                        FooterItem -> FooterBlock(route, language)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackReading(
    model: OhrViewModel,
    store: CommentaryStore,
    route: ReadingRoute,
    listState: LazyListState,
) {
    val density = LocalDensity.current
    val slop = with(density) { 8.dp.toPx() }
    val band = with(density) { 140.dp.toPx() }
    var allowTracking by remember(route) { mutableStateOf(route.scrollTarget == null) }
    var initialTop by remember(route) { mutableStateOf<Long?>(null) }
    var hasScrolled by remember(route) { mutableStateOf(false) }
    var tracked by remember(route) { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    var dwell by remember(route) { mutableStateOf<Job?>(null) }

    LaunchedEffect(route) {
        val target = route.scrollTarget ?: return@LaunchedEffect
        val index = readingRows(store, route).indexOfFirst { it.key == target }
        if (index >= 0) listState.scrollToItem(index)
        delay(200)
        allowTracking = true
    }

    LaunchedEffect(allowTracking, route) {
        if (!allowTracking) return@LaunchedEffect
        snapshotFlow { topComment(listState, slop, band) }
            .distinctUntilChanged()
            .collect { pick ->
                if (pick == null) return@collect
                if (initialTop == null) initialTop = pick else if (pick != initialTop) hasScrolled = true
                val wait = if (hasScrolled) SETTLE_MS else OPENING_MS
                dwell?.cancel()
                dwell = scope.launch {
                    delay(wait)
                    if (pick != tracked) {
                        val comment = store.comment(pick) ?: return@launch
                        tracked = pick
                        model.library.note(comment)
                    }
                }
            }
    }

    DisposableEffect(route) {
        onDispose { dwell?.cancel() }
    }
}

private fun topComment(state: LazyListState, slop: Float, band: Float): Long? {
    return state.layoutInfo.visibleItemsInfo
        .asSequence()
        .filter { it.key is Long }
        .map { it.key as Long to it.offset.toFloat() }
        .filter { (_, y) -> y >= -slop && y < band }
        .minByOrNull { it.second }
        ?.first
}

@Composable
private fun HeaderBlock(store: CommentaryStore, parashaId: Int, language: AppLanguage) {
    val name = store.parasha(parashaId)?.name(language) ?: return
    Text(
        text = name,
        style = menuStyle().copy(color = LocalColors.current.emphasis),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
    )
}

@Composable
private fun GapBlock(chapter: Int, language: AppLanguage) {
    val numeral = if (language == AppLanguage.HE) HebrewNumerals.string(chapter) else chapter.toString()
    val family = if (language == AppLanguage.HE) LocalHebrew.current else null
    Text(
        text = L10n.format("reading.noCommentary", language, numeral),
        fontFamily = family,
        fontSize = 17.sp,
        color = LocalColors.current.secondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
    )
    Hairline()
}

@Composable
private fun VerseBlock(chapter: Int, verse: Int, language: AppLanguage) {
    val place = L10n.format(
        "reading.place",
        language,
        HebrewText.number(chapter, language),
        HebrewText.number(verse, language),
    )
    val family = if (language == AppLanguage.HE) LocalHebrew.current else null
    Text(
        text = place,
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        color = LocalColors.current.emphasis,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentBlock(model: OhrViewModel, store: CommentaryStore, comment: Comment, language: AppLanguage) {
    val context = LocalContext.current
    val favourite = model.library.isFavourite(comment.id)
    var menu by remember(comment.id) { mutableStateOf(false) }
    val spoken = HebrewText.plain(comment.body, stripNikud = true)
    val size = model.settings.commentarySize
    Column(Modifier.fillMaxWidth()) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box {
                    IconButton(
                        onClick = { model.library.toggleFavourite(comment.id) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = if (favourite) Icons.Filled.Star else Icons.Outlined.Star,
                            contentDescription = L10n.text(
                                if (favourite) "a11y.removeFavourite" else "a11y.addFavourite",
                                language,
                            ),
                            tint = Accent,
                        )
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    L10n.text(
                                        if (model.library.isFavourite(comment.id)) "a11y.removeFavourite" else "a11y.addFavourite",
                                        language,
                                    ),
                                )
                            },
                            onClick = {
                                model.library.toggleFavourite(comment.id)
                                menu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(L10n.text("action.copy", language)) },
                            onClick = {
                                copyText(context, passageShare(store, comment, language, model.settings.showNikud))
                                menu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(L10n.text("action.sharePassage", language)) },
                            onClick = {
                                shareText(context, passageShare(store, comment, language, model.settings.showNikud))
                                menu = false
                            },
                        )
                    }
                }
                Text(
                    text = hebrewBody(comment.body, size, model.settings.showNikud),
                    textAlign = TextAlign.Start,
                    lineHeight = (size * 1.38f).sp,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = spoken }
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { menu = true },
                        ),
                )
            }
        }
        Hairline()
    }
}

@Composable
private fun hebrewBody(markup: String, size: Float, showNikud: Boolean) = buildAnnotatedString {
    val colors = LocalColors.current
    val family = LocalHebrew.current
    for (segment in HebrewText.segments(markup)) {
        val visible = if (showNikud) segment.text else HebrewText.stripNikud(segment.text)
        if (visible.isEmpty()) continue
        withStyle(
            SpanStyle(
                fontFamily = family,
                fontWeight = if (segment.emphasis) FontWeight.Bold else FontWeight.Normal,
                fontSize = size.sp,
                color = if (segment.emphasis) colors.emphasis else colors.text,
            ),
        ) {
            append(visible)
        }
    }
}

@Composable
private fun FooterBlock(route: ReadingRoute, language: AppLanguage) {
    val key = if (route is ReadingRoute.Introduction) "reading.footer.introduction" else "reading.footer.vocalized"
    Text(
        text = L10n.text(key, language),
        fontSize = 13.sp,
        color = LocalColors.current.secondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
    )
}

private fun readingTitle(store: CommentaryStore, route: ReadingRoute, language: AppLanguage): String =
    when (route) {
        is ReadingRoute.Parasha -> store.parasha(route.id)?.name(language).orEmpty()
        is ReadingRoute.Introduction -> L10n.text("book.introduction", language)
        is ReadingRoute.Weekly -> store.title(route.ids, language)
    }

private fun portionShare(store: CommentaryStore, route: ReadingRoute, language: AppLanguage, title: String): String {
    if (route is ReadingRoute.Introduction) return L10n.text("share.introHeading", language)
    return L10n.format("share.portion", language, title)
}

private fun passageShare(store: CommentaryStore, comment: Comment, language: AppLanguage, showNikud: Boolean): String {
    val plain = HebrewText.plain(comment.body, stripNikud = !showNikud)
    if (comment.isIntroduction) return L10n.format("share.intro", language, plain)
    val book = store.book(comment.bookId)?.name(language).orEmpty()
    val parasha = comment.parashaId?.let { store.parasha(it)?.name(language) }.orEmpty()
    return L10n.format(
        "share.passage",
        language,
        book,
        parasha,
        HebrewText.number(comment.chapter, language),
        HebrewText.number(comment.verse, language),
        plain,
    )
}

private sealed interface ReadingRow {
    val key: Any
}

private data class HeaderRow(val parashaId: Int) : ReadingRow {
    override val key: Any = "h-$parashaId"
}

private data class GapRow(val parashaId: Int, val chapter: Int) : ReadingRow {
    override val key: Any = "g-$parashaId-$chapter"
}

private data class VerseRow(val parashaId: Int, val chapter: Int, val verse: Int) : ReadingRow {
    override val key: Any = "v-$parashaId-$chapter-$verse"
}

private data class CommentItem(val comment: Comment) : ReadingRow {
    override val key: Any = comment.id
}

private data object EmptyItem : ReadingRow {
    override val key: Any = "empty"
}

private data object FooterItem : ReadingRow {
    override val key: Any = "footer"
}

private fun readingRows(store: CommentaryStore, route: ReadingRoute): List<ReadingRow> {
    val body = when (route) {
        is ReadingRoute.Introduction -> {
            val comments = store.introduction()
            if (comments.isEmpty()) emptyList() else comments.map { CommentItem(it) }
        }
        is ReadingRoute.Parasha -> parashaRows(store, route.id, header = false)
        is ReadingRoute.Weekly -> route.ids.flatMap { parashaRows(store, it, header = route.ids.size > 1) }
    }
    if (body.isEmpty()) return listOf(EmptyItem, FooterItem)
    return body + FooterItem
}

private fun parashaRows(store: CommentaryStore, parashaId: Int, header: Boolean): List<ReadingRow> {
    val comments = store.comments(parashaId)
    val gaps = store.gapChapters(parashaId).toSet()
    val byChapter = comments.groupBy { it.chapter }
    val chapters = (byChapter.keys + gaps).sorted()
    val rows = mutableListOf<ReadingRow>()
    if (header) rows += HeaderRow(parashaId)
    for (chapter in chapters) {
        val verses = byChapter[chapter].orEmpty()
        if (verses.isEmpty()) {
            rows += GapRow(parashaId, chapter)
            continue
        }
        val byVerse = verses.groupBy { it.verse }
        for (verse in byVerse.keys.sorted()) {
            rows += VerseRow(parashaId, chapter, verse)
            byVerse.getValue(verse).sortedBy { it.index }.forEach { rows += CommentItem(it) }
        }
    }
    return rows
}
