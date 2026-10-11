package moe.shizuku.manager.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * TokenX visual foundation.
 *
 * Kept separate from the legacy Shizuku theme so the new UI can evolve without
 * destabilising server/startup behaviour. Phase 1 exposes glass/background
 * settings; screens opt into these tokens incrementally.
 */
@Immutable
data class TokenXGlassStyle(
    val enabled: Boolean = true,
    val opacity: Float = 0.72f,
    val blurRadiusDp: Float = 22f,
    val cornerRadiusDp: Float = 28f,
    val borderOpacity: Float = 0.18f,
    val backgroundMode: BackgroundMode = BackgroundMode.AMOLED_GRADIENT,
    val accent: Color = Color(0xFF8B5CF6),
)

enum class FloatingBarStyle {
    FLOATING,
    FROSTED,
    SOLID,
    CLEAR,
}

enum class FloatingBarShape(val label: String) {
    ONE_UI("One UI"),
    SQUIRCLE("Squircle"),
    ROUNDED("Rounded"),
    PILL("Pill"),
}

enum class FloatingBarSelection(val label: String) {
    GLASS("Glass"),
    TILE("Tile"),
    INDICATOR("Indicator"),
    MINIMAL("Minimal"),
}

enum class TokenXThemePreset(val label: String) {
    SYSTEM("System"),
    TOKENX("TokenX"),
    GHOST("Ghost"),
    AMOLED("AMOLED"),
    ONE_UI("One UI"),
    CRYSTAL("Crystal"),
    SMOKE("Smoke"),
}

enum class TokenXHapticStrength(val label: String) {
    OFF("Off"),
    LIGHT("Light"),
    STANDARD("Standard"),
    STRONG("Strong"),
}

enum class BackgroundMode {
    SYSTEM,
    AMOLED,
    AMOLED_GRADIENT,
    CUSTOM_COLOR,
    CUSTOM_IMAGE,
}

val LocalTokenXGlass = staticCompositionLocalOf { TokenXGlassStyle() }

/**
 * Stable preference keys for the TokenX appearance editor.
 * Custom-image persistence will use a persisted content URI rather than copying
 * arbitrary image bytes into SharedPreferences.
 */
enum class TokenXAccent(val argb: Long, val label: String) {
    TOKEN_PURPLE(0xFF8B5CF6, "Token Purple"),
    SAMSUNG_BLUE(0xFF3B82F6, "Samsung Blue"),
    CYAN(0xFF06B6D4, "Cyan"),
    EMERALD(0xFF10B981, "Emerald"),
    WINE_RED(0xFF8E244D, "Wine Red"),
    CRIMSON(0xFFDC2626, "Crimson"),
    ORANGE(0xFFF97316, "Orange"),
    GOLD(0xFFEAB308, "Gold"),
    PINK(0xFFEC4899, "Pink"),
    ICE(0xFFCBD5E1, "Ice"),
    GRAPHITE(0xFF64748B, "Graphite"),
}

object TokenXAppearanceKeys {
    const val THEME_PRESET = "tokenx_theme_preset"
    const val UI_STYLE = "tokenx_ui_style"
    const val COLOR_STYLE = "tokenx_color_style"
    const val COLOR_SPEC = "tokenx_color_spec"
    const val GLASS_ENABLED = "tokenx_glass_enabled"
    const val GLASS_OPACITY = "tokenx_glass_opacity"
    const val GLASS_BLUR = "tokenx_glass_blur"
    const val GLASS_RADIUS = "tokenx_glass_radius"
    const val GLASS_BORDER = "tokenx_glass_border"
    const val GLASS_TINT = "tokenx_glass_tint"
    const val GLASS_PRESET = "tokenx_glass_preset"
    const val TEXT_CONTRAST = "tokenx_text_contrast"
    const val FLOATING_BAR_STYLE = "tokenx_floating_bar_style"
    const val FLOATING_BAR_OPACITY = "tokenx_floating_bar_opacity"
    const val FLOATING_BAR_SHAPE = "tokenx_floating_bar_shape"
    const val FLOATING_BAR_WIDTH = "tokenx_floating_bar_width"
    const val FLOATING_BAR_HEIGHT = "tokenx_floating_bar_height"
    const val FLOATING_BAR_BOTTOM_GAP = "tokenx_floating_bar_bottom_gap"
    const val FLOATING_BAR_BLUR = "tokenx_floating_bar_blur"
    const val FLOATING_BAR_BORDER = "tokenx_floating_bar_border"
    const val FLOATING_BAR_ELEVATION = "tokenx_floating_bar_elevation"
    const val FLOATING_BAR_SELECTION = "tokenx_floating_bar_selection"
    const val BACKGROUND_MODE = "tokenx_background_mode"
    const val BACKGROUND_COLOR = "tokenx_background_color"
    const val BACKGROUND_IMAGE_URI = "tokenx_background_image_uri"
    const val BACKGROUND_DIM = "tokenx_background_dim"
    const val ACCENT_COLOR = "tokenx_accent_color"
    const val ACCENT_PRESET = "tokenx_accent_preset"
    const val HAPTIC_STRENGTH = "tokenx_haptic_strength"
}
