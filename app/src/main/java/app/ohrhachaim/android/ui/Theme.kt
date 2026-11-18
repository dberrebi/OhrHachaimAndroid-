package app.ohrhachaim.android.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import app.ohrhachaim.android.L10n
import app.ohrhachaim.android.R
import app.ohrhachaim.core.AppearanceChoice
import app.ohrhachaim.core.AppLanguage
import app.ohrhachaim.core.HebrewText

val Bar = Color(0xFF1C1612)
/** The iOS gold. On the dark bar and the dark background it reads well (7.7:1). */
val Accent = Color(0xFFC6A15B)

/**
 * The gold on the light background measures only 2.16:1, too low for links (4.5:1) and for
 * the favourite star (3:1). Light mode uses this darker gold instead: 5.34:1 on [BackgroundLight].
 */
private val AccentLight = Color(0xFF7D5E1F)
private val BackgroundLight = Color(0xFFF6F1E7)
private val BackgroundDark = Color(0xFF141210)
private val TextLight = Color(0xFF221C16)
private val TextDark = Color(0xFFF3EBDD)
private val SecondaryLight = Color(0xFF5C5348)
private val SecondaryDark = Color(0xFFC4B8A6)
private val EmphasisLight = Color(0xFF7A3E12)
private val EmphasisDark = Color(0xFFE2B56A)

data class StudyColors(
    val background: Color,
    val text: Color,
    val secondary: Color,
    val emphasis: Color,
    /** Links and the favourite star on the page background. */
    val accent: Color,
    val divider: Color,
    val dark: Boolean,
)

private val LightColors = StudyColors(
    background = BackgroundLight,
    text = TextLight,
    secondary = SecondaryLight,
    emphasis = EmphasisLight,
    accent = AccentLight,
    divider = Color.Black.copy(alpha = 0.14f),
    dark = false,
)

private val DarkColors = StudyColors(
    background = BackgroundDark,
    text = TextDark,
    secondary = SecondaryDark,
    emphasis = EmphasisDark,
    accent = Accent,
    divider = Color.White.copy(alpha = 0.16f),
    dark = true,
)

val LocalColors = staticCompositionLocalOf { LightColors }
val LocalHebrew = staticCompositionLocalOf<FontFamily> { FontFamily.Serif }
val LocalLanguage = staticCompositionLocalOf { AppLanguage.HE }

@OptIn(ExperimentalTextApi::class)
private fun hebrewFamily(): FontFamily {
    fun face(weight: FontWeight, axis: Int) = Font(
        resId = R.font.noto_serif_hebrew_variable,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(axis)),
    )
    return FontFamily(
        face(FontWeight.Normal, 400),
        face(FontWeight.Medium, 500),
        face(FontWeight.SemiBold, 600),
        face(FontWeight.Bold, 700),
    )
}

@Composable
fun OhrTheme(appearance: AppearanceChoice, content: @Composable () -> Unit) {
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = when (appearance) {
        AppearanceChoice.SYSTEM -> systemDark
        AppearanceChoice.LIGHT -> false
        AppearanceChoice.DARK -> true
    }
    val colors = if (dark) DarkColors else LightColors
    val hebrew = remember { hebrewFamily() }
    val scheme = if (dark) {
        darkColorScheme(
            primary = EmphasisDark,
            onPrimary = Color.Black,
            secondary = Accent,
            background = BackgroundDark,
            surface = BackgroundDark,
            onBackground = TextDark,
            onSurface = TextDark,
            onSurfaceVariant = SecondaryDark,
        )
    } else {
        lightColorScheme(
            primary = EmphasisLight,
            onPrimary = Color.White,
            secondary = AccentLight,
            background = BackgroundLight,
            surface = BackgroundLight,
            onBackground = TextLight,
            onSurface = TextLight,
            onSurfaceVariant = SecondaryLight,
        )
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = !dark
            // Android 15+ draws the app behind a transparent navigation bar and ignores this colour;
            // there the screen background already shows through. Older versions still need it.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                @Suppress("DEPRECATION")
                window.navigationBarColor = if (dark) 0xFF141210.toInt() else 0xFFF6F1E7.toInt()
            }
        }
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalColors provides colors,
            LocalHebrew provides hebrew,
        ) {
            Box(Modifier.fillMaxSize().background(colors.background)) {
                content()
            }
        }
    }
}

@Composable
fun menuStyle(): TextStyle {
    val language = LocalLanguage.current
    return if (language == AppLanguage.HE) {
        TextStyle(fontFamily = LocalHebrew.current, fontSize = 22.sp, color = LocalColors.current.text)
    } else {
        TextStyle(fontSize = 20.sp, color = LocalColors.current.text)
    }
}

@Composable
fun subtitleStyle(text: String): TextStyle {
    val language = LocalLanguage.current
    val hebrew = language == AppLanguage.HE || HebrewText.containsHebrew(text)
    return if (hebrew) {
        TextStyle(fontFamily = LocalHebrew.current, fontSize = 15.sp, color = LocalColors.current.secondary)
    } else {
        TextStyle(fontSize = 13.sp, color = LocalColors.current.secondary)
    }
}

@Composable
fun titleStyle(title: String): TextStyle {
    return if (HebrewText.containsHebrew(title)) {
        TextStyle(fontFamily = LocalHebrew.current, fontSize = 18.sp, color = Color.White)
    } else {
        TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScaffold(
    title: String,
    onBack: (() -> Unit)?,
    showMark: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    val language = LocalLanguage.current
    Scaffold(
        containerColor = LocalColors.current.background,
        contentColor = LocalColors.current.text,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) {
                        if (showMark) {
                            androidx.compose.foundation.Image(
                                painter = painterResource(R.drawable.toolbar_mark),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Text(
                            text = title,
                            style = titleStyle(title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (language == AppLanguage.HE) "חזרה" else "Back",
                            )
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Bar,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        },
        content = content,
    )
}

@Composable
fun Hairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(LocalColors.current.divider),
    )
}

@Composable
fun CenteredTitle(title: String, subtitle: String? = null) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = menuStyle(),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!subtitle.isNullOrEmpty()) {
            Text(
                text = subtitle,
                style = subtitleStyle(subtitle),
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }
    }
}

@Composable
fun FullWidthRow(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            content()
        }
        Hairline()
    }
}
