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
object TokenXAppearanceKeys {
    const val GLASS_ENABLED = "tokenx_glass_enabled"
    const val GLASS_OPACITY = "tokenx_glass_opacity"
    const val GLASS_BLUR = "tokenx_glass_blur"
    const val GLASS_RADIUS = "tokenx_glass_radius"
    const val GLASS_BORDER = "tokenx_glass_border"
    const val BACKGROUND_MODE = "tokenx_background_mode"
    const val BACKGROUND_COLOR = "tokenx_background_color"
    const val BACKGROUND_IMAGE_URI = "tokenx_background_image_uri"
    const val BACKGROUND_DIM = "tokenx_background_dim"
    const val ACCENT_COLOR = "tokenx_accent_color"
}
