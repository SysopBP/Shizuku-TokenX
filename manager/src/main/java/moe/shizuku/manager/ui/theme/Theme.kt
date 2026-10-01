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

    // Like KernelSU: keep the chosen key color by default, only follow the
    // wallpaper when the user enables system color. A fixed seed avoids a
    // washed-out grey palette on desaturated wallpapers.
    val savedAccent = prefs.getLong(TokenXAppearanceKeys.ACCENT_COLOR, TokenXAccent.TOKEN_PURPLE.argb)
    // ACCENT_COLOR is stored as a conventional 32-bit ARGB value. Passing it to the
    // packed-ULong Color constructor makes Compose interpret ARGB bits as a packed
    // color-space value and can produce an invalid color-space index at startup.
    val tokenAccent = Color(savedAccent.toInt())
    val seed = if (useSystemColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).primary
    } else {
        tokenAccent
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
    // TKN Boot keeps wallpaper/glass surfaces neutral. The selected accent is for
    // controls and active state, not for repainting every card/container.
    val neutralScheme = baseScheme.copy(
        background = if (darkTheme) Color(0xFF090A0D) else Color(0xFFF7F7F9),
        surface = if (darkTheme) Color(0xFF0D0F13) else Color(0xFFFFFFFF),
        surfaceDim = if (darkTheme) Color(0xFF090A0D) else Color(0xFFE6E7EA),
        surfaceBright = if (darkTheme) Color(0xFF25282E) else Color(0xFFFFFFFF),
        surfaceContainerLowest = if (darkTheme) Color(0xFF090A0D) else Color(0xFFFFFFFF),
        surfaceContainerLow = if (darkTheme) Color(0xFF101216) else Color(0xFFF4F4F6),
        surfaceContainer = if (darkTheme) Color(0xFF14161B) else Color(0xFFF0F0F3),
        surfaceContainerHigh = if (darkTheme) Color(0xFF191C21) else Color(0xFFE9E9ED),
        surfaceContainerHighest = if (darkTheme) Color(0xFF202329) else Color(0xFFE2E3E7),
        onBackground = if (darkTheme) Color(0xFFF5F5F7) else Color(0xFF17181B),
        onSurface = if (darkTheme) Color(0xFFF5F5F7) else Color(0xFF17181B),
        onSurfaceVariant = if (darkTheme) Color(0xFFC7C9D0) else Color(0xFF555861),
    )

    val colorScheme = if (amoled) neutralScheme.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceBright = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color.Black,
        surfaceContainer = Color.Black,
        surfaceContainerHigh = Color.Black,
        surfaceContainerHighest = Color.Black,
    ) else neutralScheme

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

    val tokenXGlass = TokenXGlassStyle(
        enabled = prefs.getBoolean(TokenXAppearanceKeys.GLASS_ENABLED, true),
        opacity = prefs.getFloat(TokenXAppearanceKeys.GLASS_OPACITY, .72f),
        blurRadiusDp = prefs.getFloat(TokenXAppearanceKeys.GLASS_BLUR, 22f),
        cornerRadiusDp = prefs.getFloat(TokenXAppearanceKeys.GLASS_RADIUS, 28f),
        borderOpacity = prefs.getFloat(TokenXAppearanceKeys.GLASS_BORDER, .18f),
        accent = tokenAccent,
        backgroundMode = runCatching {
            BackgroundMode.valueOf(
                prefs.getString(TokenXAppearanceKeys.BACKGROUND_MODE, BackgroundMode.AMOLED_GRADIENT.name)
                    ?: BackgroundMode.AMOLED_GRADIENT.name
            )
        }.getOrDefault(BackgroundMode.AMOLED_GRADIENT),
    )

    CompositionLocalProvider(
        LocalAmoledTheme provides amoled,
        LocalTokenXGlass provides tokenXGlass
    ) {
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
