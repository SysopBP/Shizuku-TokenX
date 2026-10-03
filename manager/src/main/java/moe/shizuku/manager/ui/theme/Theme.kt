package moe.shizuku.manager.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import moe.shizuku.manager.ShizukuSettings

/** Shizuku brand indigo, matching the XML theme's primaryColor. */
private val BrandColor = Color(0xFF3F51B5)

/**
 * True while the pure black (AMOLED) scheme is in use. Cards and the page are the
 * same colour in that scheme, so surfaces need something else to stay visible see
 * [moe.shizuku.manager.ui.component.SegmentedColumn].
 */
val LocalAmoledTheme = staticCompositionLocalOf { false }

/** True when the TokenX MIUIX-inspired surface treatment is selected. */
val LocalMiuixTheme = staticCompositionLocalOf { false }

private const val PREF_THEME_STYLE = "tokenx_theme_style"
private const val THEME_STYLE_MIUIX = "MIUIX"

/** Bumped when a theme preference changes so the theme re-reads the prefs. */
object ThemeState {
    var version by mutableIntStateOf(0)
        private set

    fun refresh() {
        version++
    }
}

@Composable
fun ShizukuTheme(content: @Composable () -> Unit) {
    // Read the counter so theme changes recompose the tree.
    ThemeState.version

    val context = LocalContext.current
    val prefs = ShizukuSettings.getPreferences()

    val darkTheme = when (ShizukuSettings.getNightMode()) {
        AppCompatDelegate.MODE_NIGHT_NO -> false
        AppCompatDelegate.MODE_NIGHT_YES -> true
        else -> isSystemInDarkTheme()
    }

    val useSystemColor = prefs.getBoolean(ShizukuSettings.Keys.KEY_USE_SYSTEM_COLOR, false)
    val amoled = darkTheme && prefs.getBoolean(ShizukuSettings.Keys.KEY_BLACK_NIGHT_THEME, false)
    val miuix = prefs.getString(PREF_THEME_STYLE, "MATERIAL") == THEME_STYLE_MIUIX

    // Like KernelSU: keep the chosen key color by default, only follow the
    // wallpaper when the user enables system color. A fixed seed avoids a
    // washed-out grey palette on desaturated wallpapers.
    val seed = if (useSystemColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).primary
    } else {
        BrandColor
    }

    val baseScheme = rememberDynamicColorScheme(
        seedColor = seed,
        isDark = darkTheme,
        isAmoled = amoled,
        style = PaletteStyle.TonalSpot,
        specVersion = ColorSpec.SpecVersion.SPEC_2021,
    )

    // The AMOLED switch only repaints the base surface roles, while the container
    // roles that the cards and the navigation bar actually use kept the standard
    // dark greys a black page with grey cards. Spread the black across every
    // surface role; row dividers keep the list readable without the card shape.
    val colorScheme = when {
        amoled -> baseScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceBright = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color.Black,
            surfaceContainer = Color.Black,
            surfaceContainerHigh = Color.Black,
            surfaceContainerHighest = Color.Black,
            onBackground = Color(0xFFE6E6E6),
            onSurface = Color(0xFFE6E6E6),
            onSurfaceVariant = Color(0xFFB8B8B8),
            outline = Color(0xFF8A8A8A),
            outlineVariant = Color(0xFF4A4A4A),
        )
        miuix && darkTheme -> baseScheme.copy(
            background = Color(0xFF0D0D0F),
            surface = Color(0xFF121214),
            surfaceContainerLowest = Color(0xFF101012),
            surfaceContainerLow = Color(0xFF171719),
            surfaceContainer = Color(0xFF1C1C1F),
            surfaceContainerHigh = Color(0xFF222225),
            surfaceContainerHighest = Color(0xFF29292D),
            outlineVariant = Color(0xFF34343A),
        )
        miuix -> baseScheme.copy(
            background = Color(0xFFF5F5F7),
            surface = Color(0xFFFAFAFC),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFF7F7F9),
            surfaceContainer = Color(0xFFF0F0F3),
            surfaceContainerHigh = Color(0xFFEAEAEF),
            surfaceContainerHighest = Color(0xFFE3E3E9),
            outlineVariant = Color(0xFFD7D7DE),
        )
        else -> baseScheme
    }

    // Match the status/navigation bar icons to the app's theme, not the system's;
    // otherwise a white in-app theme gets light icons on a white bar (invisible).
    val view = LocalView.current
    if (!view.isInEditMode) {
        LaunchedEffect(darkTheme) {
            val window = view.context.findActivity()?.window ?: return@LaunchedEffect
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalAmoledTheme provides amoled, LocalMiuixTheme provides miuix) {
        MaterialTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            content = content
        )
    }
}

private fun Context.findActivity(): Activity? {
    var context: Context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return context as? Activity
}
