package app.ohrhachaim.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.ohrhachaim.android.L10n
import app.ohrhachaim.android.OhrViewModel
import app.ohrhachaim.android.R
import app.ohrhachaim.android.deviceToday
import app.ohrhachaim.android.openPrivacy
import app.ohrhachaim.android.snapFontScale
import app.ohrhachaim.core.AppLanguage
import app.ohrhachaim.core.AppearanceChoice
import app.ohrhachaim.core.Comment
import app.ohrhachaim.core.HebrewText
import app.ohrhachaim.core.HomeDestination
import app.ohrhachaim.core.ReadingRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun OhrRoot(model: OhrViewModel) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) model.library.flush()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val language = model.settings.language
    val direction = if (language == AppLanguage.HE) LayoutDirection.Rtl else LayoutDirection.Ltr
    key(language) {
        CompositionLocalProvider(
            LocalLayoutDirection provides direction,
            LocalLanguage provides language,
        ) {
            OhrTheme(model.settings.appearance) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val regular = maxWidth >= 600.dp
                    if (!regular) {
                        BackHandler(model.nav.readings.isNotEmpty() || model.nav.section != null) {
                            model.nav.back()
                        }
                    } else if (model.nav.readings.isNotEmpty()) {
                        BackHandler { model.nav.back() }
                    }
                    if (regular) {
                        TabletFrame(model)
                    } else {
                        PhoneFrame(model)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneFrame(model: OhrViewModel) {
    val reading = model.nav.readings.lastOrNull()
    val section = model.nav.section
    when {
        reading != null -> ReadingScreen(model, reading, onBack = model.nav::back)
        section != null -> SectionScreen(model, section, onBack = model.nav::back)
        else -> HomeScreen(model, onOpen = model.nav::open)
    }
}

@Composable
private fun TabletFrame(model: OhrViewModel) {
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(340.dp).fillMaxHeight()) {
            HomeScreen(model, onOpen = model.nav::open)
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val reading = model.nav.readings.lastOrNull()
            val section = model.nav.section
            when {
                reading != null -> ReadingScreen(model, reading, onBack = model.nav::back)
                section != null -> SectionScreen(model, section, onBack = null)
                else -> Welcome(model)
            }
        }
    }
}

@Composable
private fun SectionScreen(model: OhrViewModel, section: HomeDestination, onBack: (() -> Unit)?) {
    // Settings, Help, and About work without the commentary, including when it failed to load.
    val needsCommentary = section !is HomeDestination.Settings &&
        section !is HomeDestination.Help &&
        section !is HomeDestination.About
    if (needsCommentary) {
        val title = L10n.text("app.title", LocalLanguage.current)
        if (model.failed || model.store?.loadError != null) {
            MessageScreen(model, title, L10n.text("data.missing", LocalLanguage.current), onBack)
            return
        }
        if (model.store == null) {
            MessageScreen(model, title, null, onBack)
            return
        }
    }
    when (section) {
        HomeDestination.Resume -> ResumeScreen(model, onBack)
        HomeDestination.History -> HistoryScreen(model, onBack)
        HomeDestination.Weekly -> WeeklyScreen(model, onBack)
        HomeDestination.Search -> SearchScreen(model, onBack)
        is HomeDestination.Book -> BookScreen(model, section.bookId, onBack)
        HomeDestination.Settings -> SettingsScreen(model, onBack)
        HomeDestination.Help -> HelpScreen(model, onBack)
        HomeDestination.About -> AboutScreen(model, onBack)
        HomeDestination.Favourites -> FavouritesScreen(model, onBack)
    }
}

@Composable
private fun Welcome(model: OhrViewModel) {
    val language = LocalLanguage.current
    StudyScaffold(title = L10n.text("app.title", language), onBack = null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(it).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.toolbar_mark),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
            )
            Text(
                L10n.text("app.title", language),
                style = menuStyle(),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                L10n.text("welcome.subtitle", language),
                fontFamily = if (language == AppLanguage.HE) LocalHebrew.current else null,
                fontSize = 17.sp,
                color = LocalColors.current.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
fun MessageScreen(model: OhrViewModel, title: String, message: String?, onBack: (() -> Unit)?) {
    StudyScaffold(title = title, onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
            if (message != null) {
                Text(
                    message,
                    style = menuStyle().copy(color = LocalColors.current.secondary),
                    textAlign = TextAlign.Center,
                )
            } else {
                // No message means the commentary is still being prepared.
                CircularProgressIndicator(color = LocalColors.current.emphasis)
            }
        }
    }
}

@Composable
private fun ResumeScreen(model: OhrViewModel, onBack: (() -> Unit)?) {
    val language = LocalLanguage.current
    val store = model.store
    val openedAt = remember { model.library.lastCommentId }
    if (store == null) {
        MessageScreen(model, L10n.text("home.resume", language), null, onBack)
        return
    }
    val comment = openedAt?.let { store.comment(it) }
    when {
        comment != null -> ReadingScreen(model, model.library.route(comment), onBack)
        openedAt != null -> MessageScreen(model, L10n.text("home.resume", language), L10n.text("reading.missingPlace", language), onBack)
        else -> MessageScreen(model, L10n.text("home.resume", language), L10n.text("home.resume.empty", language), onBack)
    }
}

@Composable
private fun WeeklyScreen(model: OhrViewModel, onBack: (() -> Unit)?) {
    val language = LocalLanguage.current
    val store = model.store
    val title = L10n.text("home.weekly", language)
    if (store == null) {
        MessageScreen(model, title, null, onBack)
        return
    }
    val today = deviceToday()
    if (!store.readingCovers(today)) {
        MessageScreen(
            model,
            title,
            L10n.format("home.weekly.outOfRange", language, store.yearStart, store.yearEnd),
            onBack,
        )
        return
    }
    val reading = store.currentReading(model.settings.israelCalendar, today)
    if (reading != null && reading.parashaIds.isNotEmpty()) {
        ReadingScreen(model, ReadingRoute.Weekly(reading.parashaIds, null), onBack)
    } else {
        MessageScreen(model, title, L10n.text("home.weekly.empty", language), onBack)
    }
}

@Composable
private fun BookScreen(model: OhrViewModel, bookId: Int, onBack: (() -> Unit)?) {
    val language = LocalLanguage.current
    val store = model.store ?: return
    val title = store.book(bookId)?.name(language).orEmpty()
    val parashot = store.parashotIn(bookId)
    StudyScaffold(title = title, onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())) {
            if (store.hasIntroduction(bookId)) {
                item {
                    FullWidthRow(onClick = { model.nav.openReading(ReadingRoute.Introduction(null)) }, modifier = Modifier.testTag("parasha.intro")) {
                        CenteredTitle(L10n.text("book.introduction", language))
                    }
                }
            }
            items(parashot, key = { it.id }) { parasha ->
                FullWidthRow(
                    onClick = { model.nav.openReading(ReadingRoute.Parasha(parasha.id, null)) },
                    modifier = Modifier.testTag("parasha.${parasha.id}"),
                ) {
                    CenteredTitle(parasha.name(language))
                }
            }
        }
    }
}

@Composable
private fun SearchScreen(model: OhrViewModel, onBack: (() -> Unit)?) {
    val language = LocalLanguage.current
    val store = model.store
    // The session outlives this screen, so Back from a result restores the query, the results,
    // and the scroll position. Results stay on screen while the next query runs.
    val session = model.nav.search
    val query = session.query
    val results = session.results
    LaunchedEffect(session, query, store) {
        if (store == null) return@LaunchedEffect
        session.refresh { text -> withContext(Dispatchers.IO) { store.search(text) } }
    }
    StudyScaffold(title = L10n.text("search.title", language), onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            state = session.listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
        ) {
            item(key = "search.field") {
                BasicTextField(
                    value = query,
                    onValueChange = { session.query = it },
                    textStyle = TextStyle(
                        fontFamily = if (language == AppLanguage.HE) LocalHebrew.current else null,
                        fontSize = if (language == AppLanguage.HE) 20.sp else 17.sp,
                        color = LocalColors.current.text,
                        textAlign = if (language == AppLanguage.HE) TextAlign.Right else TextAlign.Left,
                    ),
                    cursorBrush = SolidColor(LocalColors.current.emphasis),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("search.field"),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    L10n.text("search.prompt", language),
                                    color = LocalColors.current.secondary,
                                    fontFamily = if (language == AppLanguage.HE) LocalHebrew.current else null,
                                    fontSize = if (language == AppLanguage.HE) 20.sp else 17.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = if (language == AppLanguage.HE) TextAlign.Right else TextAlign.Left,
                                )
                            }
                            inner()
                        }
                    },
                )
                Hairline()
            }
            if (query.isBlank()) {
                item(key = "search.note") { Note(L10n.text("search.empty", language)) }
            } else if (results.isEmpty()) {
                if (session.showsNoResults) item(key = "search.note") { Note(L10n.text("search.noResults", language)) }
            } else {
                items(results, key = { it.id }) { hit ->
                    FullWidthRow(
                        onClick = { openHit(model, hit.comment) },
                        modifier = Modifier.testTag("search.hit.${hit.id}"),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                locationTitle(store!!, hit.comment, language),
                                fontFamily = if (language == AppLanguage.HE) LocalHebrew.current else null,
                                fontSize = if (language == AppLanguage.HE) 18.sp else 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LocalColors.current.emphasis,
                            )
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                Text(
                                    hit.snippet,
                                    fontFamily = LocalHebrew.current,
                                    fontSize = 16.sp,
                                    color = LocalColors.current.text,
                                    textAlign = TextAlign.Start,
                                    maxLines = 3,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun openHit(model: OhrViewModel, comment: Comment) {
    if (comment.isIntroduction) {
        model.nav.openReading(ReadingRoute.Introduction(comment.id))
    } else {
        val parashaId = comment.parashaId
        if (parashaId != null) {
            model.nav.openReading(ReadingRoute.Parasha(parashaId, comment.id))
        }
    }
}

@Composable
private fun Note(text: String) {
    val language = LocalLanguage.current
    Text(
        text,
        fontFamily = if (language == AppLanguage.HE) LocalHebrew.current else null,
        fontSize = 17.sp,
        color = LocalColors.current.secondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(22.dp),
    )
}

@Composable
private fun HistoryScreen(model: OhrViewModel, onBack: (() -> Unit)?) {
    LibraryList(
        model = model,
        title = L10n.text("history.title", LocalLanguage.current),
        empty = L10n.text("history.empty", LocalLanguage.current),
        comments = model.library.history.mapNotNull { entry -> model.store?.comment(entry.commentId) },
        onBack = onBack,
    )
}

@Composable
private fun FavouritesScreen(model: OhrViewModel, onBack: (() -> Unit)?) {
    LibraryList(
        model = model,
        title = L10n.text("favourites.title", LocalLanguage.current),
        empty = L10n.text("favourites.empty", LocalLanguage.current),
        comments = model.library.favouriteIds.mapNotNull { id -> model.store?.comment(id) },
        onBack = onBack,
    )
}

@Composable
private fun LibraryList(
    model: OhrViewModel,
    title: String,
    empty: String,
    comments: List<Comment>,
    onBack: (() -> Unit)?,
) {
    val language = LocalLanguage.current
    val store = model.store
    if (comments.isEmpty() || store == null) {
        MessageScreen(model, title, empty, onBack)
        return
    }
    StudyScaffold(title = title, onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())) {
            items(comments, key = { it.id }) { comment ->
                FullWidthRow(onClick = { model.nav.openReading(model.library.route(comment)) }) {
                    CenteredTitle(
                        locationTitle(store, comment, language),
                        HebrewText.preview(comment, stripNikud = !model.settings.showNikud),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(model: OhrViewModel, onBack: (() -> Unit)?) {
    val language = LocalLanguage.current
    val colors = LocalColors.current
    val context = LocalContext.current
    StudyScaffold(title = L10n.text("settings.title", language), onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SectionLabel(L10n.text("settings.language", language))
            ChoiceRow(
                options = listOf(AppLanguage.HE to L10n.text("settings.language.he", language), AppLanguage.EN to L10n.text("settings.language.en", language)),
                selected = language,
                onSelect = model.settings::chooseLanguage,
            )
            SectionLabel(L10n.text("settings.calendar", language))
            ChoiceRow(
                options = listOf(true to L10n.text("settings.calendar.israel", language), false to L10n.text("settings.calendar.diaspora", language)),
                selected = model.settings.israelCalendar,
                onSelect = model.settings::chooseIsraelCalendar,
            )
            SectionLabel(L10n.text("settings.nikud", language))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(L10n.text("settings.nikud", language), style = menuStyle().copy(fontSize = 18.sp))
                Switch(checked = model.settings.showNikud, onCheckedChange = model.settings::chooseNikud)
            }
            Text(L10n.text("settings.fontSize", language), color = colors.text, fontSize = 16.sp)
            Slider(
                value = model.settings.fontScale,
                onValueChange = { model.settings.chooseFontScale(snapFontScale(it)) },
                valueRange = 0.85f..1.75f,
                steps = 17,
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Text(
                    hebrewPreview(model),
                    textAlign = TextAlign.Start,
                    lineHeight = (model.settings.commentarySize * 1.38f).sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                L10n.text("settings.nikud.footer", language) + "\n" + L10n.text("settings.dynamicType", language),
                color = colors.secondary,
                fontSize = 13.sp,
            )
            SectionLabel(L10n.text("settings.appearance", language))
            ChoiceRow(
                options = listOf(
                    AppearanceChoice.SYSTEM to L10n.text("settings.appearance.system", language),
                    AppearanceChoice.LIGHT to L10n.text("settings.appearance.light", language),
                    AppearanceChoice.DARK to L10n.text("settings.appearance.dark", language),
                ),
                selected = model.settings.appearance,
                onSelect = model.settings::chooseAppearance,
            )
            Text(
                L10n.text("about.privacy", language),
                color = LocalColors.current.accent,
                fontSize = 17.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings.privacy")
                    .padding(vertical = 8.dp)
                    .clickable { openPrivacy(context) },
            )
            Text(L10n.text("settings.privacy.footer", language), color = colors.secondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun hebrewPreview(model: OhrViewModel) = buildAnnotatedString {
    val colors = LocalColors.current
    val size = model.settings.commentarySize
    val show = model.settings.showNikud
    val markup = "בְּרֵאשִׁית בָּרָא אֱלֹהִים אֵת הַשָּׁמַיִם וְאֵת הָאָרֶץ"
    for (segment in HebrewText.segments(markup)) {
        val visible = if (show) segment.text else HebrewText.stripNikud(segment.text)
        withStyle(
            SpanStyle(
                fontFamily = LocalHebrew.current,
                fontSize = size.sp,
                color = colors.text,
            ),
        ) { append(visible) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = LocalColors.current.emphasis.copy(alpha = 0.18f),
                    activeContentColor = LocalColors.current.emphasis,
                    inactiveContainerColor = LocalColors.current.background,
                    inactiveContentColor = LocalColors.current.text,
                ),
            ) {
                Text(label, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = LocalColors.current.secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun HelpScreen(model: OhrViewModel, onBack: (() -> Unit)?) {
    val language = LocalLanguage.current
    StudyScaffold(title = L10n.text("help.title", language), onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                L10n.text("help.body", language),
                color = LocalColors.current.text,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AboutScreen(model: OhrViewModel, onBack: (() -> Unit)?) {
    val language = LocalLanguage.current
    val store = model.store
    val context = LocalContext.current
    val colors = LocalColors.current
    StudyScaffold(title = L10n.text("about.title", language), onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 740.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                AboutBlock(L10n.text("about.authorHeading", language), L10n.text("about.author", language))
                AboutBlock(L10n.text("about.sourcesHeading", language), L10n.text("about.sources", language))
                if (store != null) {
                    Text(
                        store.hebcalAttribution,
                        color = colors.secondary,
                        fontSize = 13.sp,
                    )
                    Text(countsLine(model, language), color = colors.secondary, fontSize = 13.sp)
                }
                Text(L10n.text("about.offline", language), color = colors.secondary, fontSize = 13.sp)
                Text(
                    L10n.text("about.privacy", language),
                    color = LocalColors.current.accent,
                    fontSize = 17.sp,
                    modifier = Modifier.testTag("about.privacy").clickable { openPrivacy(context) },
                )
            }
        }
    }
}

@Composable
private fun AboutBlock(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = LocalColors.current.emphasis, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Text(body, color = LocalColors.current.text, fontSize = 17.sp, lineHeight = 23.sp)
    }
}

@Composable
private fun countsLine(model: OhrViewModel, language: AppLanguage): String {
    val store = model.store ?: return ""
    val version = app.ohrhachaim.android.BuildConfig.VERSION_NAME
    val build = app.ohrhachaim.android.BuildConfig.VERSION_CODE.toString()
    return L10n.format(
        "about.counts",
        language,
        version,
        build,
        store.yearStart,
        store.yearEnd,
        formatGenerated(store.meta["generated_utc"].orEmpty(), language),
    )
}

private fun formatGenerated(raw: String, language: AppLanguage): String {
    val instant = runCatching { Instant.parse(raw) }.getOrNull() ?: return raw
    val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    val locale = if (language == AppLanguage.HE) Locale.forLanguageTag("he") else Locale.ENGLISH
    return date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
}
