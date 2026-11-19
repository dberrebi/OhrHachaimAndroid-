package app.ohrhachaim.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ohrhachaim.android.L10n
import app.ohrhachaim.android.OhrViewModel
import app.ohrhachaim.android.copyText
import app.ohrhachaim.android.deviceToday
import app.ohrhachaim.android.shareText
import app.ohrhachaim.core.Aliyah
import app.ohrhachaim.core.Aliyot
import app.ohrhachaim.core.AppLanguage
import app.ohrhachaim.core.Comment
import app.ohrhachaim.core.CommentaryStore
import app.ohrhachaim.core.HebrewNumerals
import app.ohrhachaim.core.HebrewText
import app.ohrhachaim.core.ReadingRoute
import app.ohrhachaim.core.RowSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SETTLE_MS = 4_000L
private const val OPENING_MS = 20_000L

@Composable
fun ReadingScreen(model: OhrViewModel, route: ReadingRoute, onBack: (() -> Unit)?) {
    val store = model.store ?: return
    val language = LocalLanguage.current
    val context = LocalContext.current
    // The first visit to a parasha reads it from the database off the main thread. Later visits,
    // and recreating the screen after rotation, find it in memory and lay it out immediately.
    var loaded by remember(route, store) { mutableStateOf(store.isLoaded(route)) }
    LaunchedEffect(route, store) {
        if (!loaded) {
            withContext(Dispatchers.IO) { store.preload(route) }
            loaded = true
        }
    }
    val rows = remember(route, store, loaded) { if (loaded) readingRows(store, route) else emptyList() }
    val listState = rememberLazyListState()
    val title = readingTitle(store, route, language)
    TrackReading(model, store, route, rows, listState)

    // Aliyot: the title shows the one at the top of the list and opens a menu of all seven.
    val aliyot = remember(route) { aliyotFor(route) }
    val layout = remember(aliyot, rows) {
        if (aliyot == null || rows.isEmpty()) null else Aliyot.layout(aliyot, rows.map { it.span })
    }
    val currentAliyah by remember(layout) {
        derivedStateOf {
            // The list draws behind the top bar, so the first "visible" row can be hidden under it.
            // Use the first row that reaches below the bar: after choosing an aliyah, that is the
            // aliyah's own first row, not the end of the one before.
            val top = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.offset + it.size > 0 }
            layout?.rowAliyah?.getOrNull(top?.index ?: listState.firstVisibleItemIndex)
        }
    }
    val todaysAliyah = todaysAliyah(model, store, route)
    val scope = rememberCoroutineScope()

    StudyScaffold(
        title = title,
        onBack = onBack,
        titleContent = if (layout == null) null else {
            {
                AliyahTitle(
                    title = title,
                    current = currentAliyah ?: 1,
                    aliyot = layout.aliyot,
                    today = todaysAliyah,
                    onSelect = { number ->
                        val target = layout.targets[number - 1] ?: (rows.size - 1)
                        scope.launch { listState.scrollToItem(target) }
                    },
                )
            }
        },
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
                itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                    val aliyahLabel = layout?.labels?.getOrNull(index)?.let { aliyahLabel(it, language) }
                    when (row) {
                        is HeaderRow -> HeaderBlock(store, row.parashaId, language)
                        is GapRow -> GapBlock(row.chapter, language, aliyahLabel)
                        is VerseRow -> VerseBlock(row.chapter, row.verse, language, aliyahLabel)
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
    rows: List<ReadingRow>,
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
    // Saved across rotation, so turning the device does not jump back to the target.
    var targetShown by rememberSaveable(route) { mutableStateOf(false) }

    LaunchedEffect(route, rows) {
        val target = route.scrollTarget ?: return@LaunchedEffect
        if (rows.isEmpty()) return@LaunchedEffect
        if (!targetShown) {
            val index = rows.indexOfFirst { it.key == target }
            if (index >= 0) listState.scrollToItem(index)
            targetShown = true
            delay(200)
        }
        allowTracking = true
    }

    LaunchedEffect(allowTracking, route) {
        if (!allowTracking) return@LaunchedEffect
        snapshotFlow { topComment(listState, slop, band) }
            .distinctUntilChanged()
            .collect { pick ->
                if (pick == null) {
                    // No passage is at the top any more (a heading, a gap note, or the footer is),
                    // so the passage being timed was scrolled away: do not save it.
                    dwell?.cancel()
                    dwell = null
                    return@collect
                }
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

/**
 * The passage being read: the one under the top edge of the list, so a long passage still counts
 * after its first lines scroll away; otherwise the first passage that starts within [band] of the
 * top. Null when only headings, gap notes, or the footer are there.
 */
private fun topComment(state: LazyListState, slop: Float, band: Float): Long? {
    val passages = state.layoutInfo.visibleItemsInfo.filter { it.key is Long }
    val covering = passages.firstOrNull { it.offset <= slop && it.offset + it.size > slop }
    val starting = passages.filter { it.offset > slop && it.offset < band }.minByOrNull { it.offset }
    return (covering ?: starting)?.key as Long?
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
private fun GapBlock(chapter: Int, language: AppLanguage, aliyahLabel: String?) {
    val numeral = if (language == AppLanguage.HE) HebrewNumerals.string(chapter) else chapter.toString()
    val family = if (language == AppLanguage.HE) LocalHebrew.current else null
    if (aliyahLabel != null) {
        // Aliyot that start in a chapter without commentary (Bechukotai 27) are named here.
        Text(
            text = aliyahLabel,
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = LocalColors.current.emphasis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp).semantics { heading() },
        )
    }
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
private fun VerseBlock(chapter: Int, verse: Int, language: AppLanguage, aliyahLabel: String?) {
    val place = L10n.format(
        "reading.place",
        language,
        HebrewText.number(chapter, language),
        HebrewText.number(verse, language),
    )
    val family = if (language == AppLanguage.HE) LocalHebrew.current else null
    // The first verse of each aliyah carries its name: "שלישי · פרק כ״ט · פסוק ז׳". Those headings
    // are marked as headings, so TalkBack can move from aliyah to aliyah.
    Text(
        text = if (aliyahLabel == null) place else "$aliyahLabel · $place",
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        color = LocalColors.current.emphasis,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 8.dp)
            .then(if (aliyahLabel != null) Modifier.semantics { heading() } else Modifier),
    )
}

/** One aliyah's name, or "רביעי–שביעי" when several start in the same place. */
private fun aliyahLabel(numbers: List<Int>, language: AppLanguage): String? = when (numbers.size) {
    0 -> null
    1 -> aliyahName(numbers[0], language)
    else -> aliyahName(numbers.first(), language) + "–" + aliyahName(numbers.last(), language)
}

private fun aliyahName(number: Int, language: AppLanguage): String = L10n.text("aliyah.$number", language)

/** The aliyot of a parasha, or of two read together. None for the author's introduction. */
private fun aliyotFor(route: ReadingRoute): List<Aliyah>? = when (route) {
    is ReadingRoute.Parasha -> Aliyot.forParashot(listOf(route.id))
    is ReadingRoute.Weekly -> Aliyot.forParashot(route.ids)
    is ReadingRoute.Introduction -> null
}

/** In this week's parasha only: Sunday's aliyah is ראשון, through שביעי on Shabbat. */
@Composable
private fun todaysAliyah(model: OhrViewModel, store: CommentaryStore, route: ReadingRoute): Int? {
    if (route !is ReadingRoute.Weekly) return null
    val today = deviceToday()
    val reading = store.currentReading(model.settings.israelCalendar, today) ?: return null
    if (reading.parashaIds != route.ids) return null
    return Aliyot.todays(reading, today)
}

/**
 * The reading's title as a menu: "<parasha> · <aliyah> ▾". Choosing an aliyah scrolls to its
 * first verse. Today's aliyah, in this week's parasha, has a small gold dot; nothing jumps to it.
 */
@Composable
private fun AliyahTitle(
    title: String,
    current: Int,
    aliyot: List<Aliyah>,
    today: Int?,
    onSelect: (Int) -> Unit,
) {
    val language = LocalLanguage.current
    val colors = LocalColors.current
    var open by remember { mutableStateOf(false) }
    val currentName = aliyahName(current, language)
    val description = L10n.format("a11y.aliyahMenu", language, title, currentName)
    val actionLabel = L10n.text("a11y.aliyahMenuAction", language)
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = actionLabel, role = Role.Button) { open = true }
                .semantics { contentDescription = description }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Text(
                text = title,
                style = titleStyle(title),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(text = " · $currentName", style = titleStyle(currentName), maxLines = 1)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = Color.White)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            aliyot.forEach { aliyah ->
                val name = aliyahName(aliyah.number, language)
                val shown = L10n.format(
                    "aliyah.menuItem",
                    language,
                    name,
                    HebrewText.number(aliyah.start.chapter, language),
                    HebrewText.number(aliyah.start.verse, language),
                )
                // TalkBack reads the chapter and verse as numbers, not as Hebrew letters.
                var spoken = L10n.format(
                    "a11y.aliyahItem",
                    language,
                    name,
                    aliyah.start.chapter.toString(),
                    aliyah.start.verse.toString(),
                )
                if (aliyah.number == today) spoken += ", " + L10n.text("a11y.aliyahToday", language)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = shown,
                            fontFamily = if (language == AppLanguage.HE) LocalHebrew.current else null,
                            fontSize = 17.sp,
                            fontWeight = if (aliyah.number == current) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    trailingIcon = if (aliyah.number == today) {
                        {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .background(colors.starSaved, CircleShape),
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        onSelect(aliyah.number)
                    },
                    modifier = Modifier.semantics { contentDescription = spoken },
                )
            }
        }
    }
}

@Composable
private fun CommentBlock(model: OhrViewModel, store: CommentaryStore, comment: Comment, language: AppLanguage) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val favourite = model.library.isFavourite(comment.id)
    var menu by remember(comment.id) { mutableStateOf(false) }
    val spoken = HebrewText.plain(comment.body, stripNikud = true)
    val size = model.settings.commentarySize
    val actionsLabel = L10n.text("a11y.passageActions", language)
    val copyLabel = L10n.text("action.copy", language)
    val shareLabel = L10n.text("action.sharePassage", language)
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
                            imageVector = if (favourite) Icons.Filled.Star else StarOutline,
                            contentDescription = L10n.text(
                                if (favourite) "a11y.removeFavourite" else "a11y.addFavourite",
                                language,
                            ),
                            tint = if (favourite) LocalColors.current.starSaved else LocalColors.current.starUnsaved,
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
                // A long press opens copy and share. There is no tap action, so the passage is not
                // announced as a button; TalkBack users get the same actions from its actions menu.
                Text(
                    text = hebrewBody(comment.body, size, model.settings.showNikud),
                    textAlign = TextAlign.Start,
                    lineHeight = (size * 1.38f).sp,
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = spoken
                            onLongClick(label = actionsLabel) {
                                menu = true
                                true
                            }
                            customActions = listOf(
                                CustomAccessibilityAction(copyLabel) {
                                    copyText(context, passageShare(store, comment, language, model.settings.showNikud))
                                    true
                                },
                                CustomAccessibilityAction(shareLabel) {
                                    shareText(context, passageShare(store, comment, language, model.settings.showNikud))
                                    true
                                },
                            )
                        }
                        .pointerInput(comment.id) {
                            detectTapGestures(
                                onLongPress = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menu = true
                                },
                            )
                        },
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

    /** The verses this row stands for, for placing the aliyot. Null for headings and the footer. */
    val span: RowSpan? get() = null
}

private data class HeaderRow(val parashaId: Int) : ReadingRow {
    override val key: Any = "h-$parashaId"
}

private data class GapRow(val parashaId: Int, val chapter: Int) : ReadingRow {
    override val key: Any = "g-$parashaId-$chapter"
    override val span: RowSpan get() = RowSpan.chapter(chapter)
}

private data class VerseRow(val parashaId: Int, val chapter: Int, val verse: Int) : ReadingRow {
    override val key: Any = "v-$parashaId-$chapter-$verse"
    override val span: RowSpan get() = RowSpan.verse(chapter, verse)
}

private data class CommentItem(val comment: Comment) : ReadingRow {
    override val key: Any = comment.id
    override val span: RowSpan? get() = if (comment.isIntroduction) null else RowSpan.verse(comment.chapter, comment.verse)
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
