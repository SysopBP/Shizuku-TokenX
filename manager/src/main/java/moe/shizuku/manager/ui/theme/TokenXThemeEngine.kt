package moe.shizuku.manager.ui.theme

import android.content.SharedPreferences
import moe.shizuku.manager.ShizukuSettings

/**
 * Single source of truth for TokenX appearance presets.
 *
 * Renderers (Material, Ghost and Miuix) and the advanced editor all consume the
 * same preference keys. Presets only configure those keys; they are not a second
 * theme engine.
 */
object TokenXThemeEngine {

    fun applyPreset(prefs: SharedPreferences, preset: TokenXThemePreset) {
        val editor = prefs.edit().putString(TokenXAppearanceKeys.THEME_PRESET, preset.name)

        when (preset) {
            TokenXThemePreset.SYSTEM -> editor
                .putBoolean(TokenXAppearanceKeys.GLASS_ENABLED, true)
                .putFloat(TokenXAppearanceKeys.GLASS_OPACITY, .72f)
                .putFloat(TokenXAppearanceKeys.GLASS_BLUR, 22f)
                .putFloat(TokenXAppearanceKeys.GLASS_RADIUS, 28f)
                .putFloat(TokenXAppearanceKeys.GLASS_BORDER, .18f)
                .putFloat(TokenXAppearanceKeys.BACKGROUND_DIM, .18f)
                .putString(TokenXAppearanceKeys.BACKGROUND_MODE, BackgroundMode.SYSTEM.name)
                .putBoolean(ShizukuSettings.Keys.KEY_USE_SYSTEM_COLOR, true)
                .putBoolean(ShizukuSettings.Keys.KEY_BLACK_NIGHT_THEME, false)

            TokenXThemePreset.TOKENX -> editor
                .putBoolean(TokenXAppearanceKeys.GLASS_ENABLED, true)
                .putFloat(TokenXAppearanceKeys.GLASS_OPACITY, .72f)
                .putFloat(TokenXAppearanceKeys.GLASS_BLUR, 28f)
                .putFloat(TokenXAppearanceKeys.GLASS_RADIUS, 28f)
                .putFloat(TokenXAppearanceKeys.GLASS_BORDER, .18f)
                .putFloat(TokenXAppearanceKeys.BACKGROUND_DIM, .18f)
                .putString(TokenXAppearanceKeys.BACKGROUND_MODE, BackgroundMode.AMOLED_GRADIENT.name)
                .putLong(TokenXAppearanceKeys.ACCENT_COLOR, TokenXAccent.TOKEN_PURPLE.argb)
                .putString(TokenXAppearanceKeys.ACCENT_PRESET, TokenXAccent.TOKEN_PURPLE.name)
                .putBoolean(ShizukuSettings.Keys.KEY_USE_SYSTEM_COLOR, false)
                .putBoolean(ShizukuSettings.Keys.KEY_BLACK_NIGHT_THEME, false)

            TokenXThemePreset.GHOST -> editor
                .putBoolean(TokenXAppearanceKeys.GLASS_ENABLED, true)
                .putFloat(TokenXAppearanceKeys.GLASS_OPACITY, .26f)
                .putFloat(TokenXAppearanceKeys.GLASS_BLUR, 38f)
                .putFloat(TokenXAppearanceKeys.GLASS_RADIUS, 34f)
                .putFloat(TokenXAppearanceKeys.GLASS_BORDER, .24f)
                .putFloat(TokenXAppearanceKeys.BACKGROUND_DIM, .08f)
                .putString(TokenXAppearanceKeys.BACKGROUND_MODE, BackgroundMode.AMOLED_GRADIENT.name)
                .putLong(TokenXAppearanceKeys.ACCENT_COLOR, TokenXAccent.ICE.argb)
                .putString(TokenXAppearanceKeys.ACCENT_PRESET, TokenXAccent.ICE.name)
                .putBoolean(ShizukuSettings.Keys.KEY_USE_SYSTEM_COLOR, false)
                .putBoolean(ShizukuSettings.Keys.KEY_BLACK_NIGHT_THEME, false)

            TokenXThemePreset.AMOLED -> editor
                .putBoolean(TokenXAppearanceKeys.GLASS_ENABLED, true)
                .putFloat(TokenXAppearanceKeys.GLASS_OPACITY, .58f)
                .putFloat(TokenXAppearanceKeys.GLASS_BLUR, 20f)
                .putFloat(TokenXAppearanceKeys.GLASS_RADIUS, 28f)
                .putFloat(TokenXAppearanceKeys.GLASS_BORDER, .12f)
                .putFloat(TokenXAppearanceKeys.BACKGROUND_DIM, .12f)
                .putString(TokenXAppearanceKeys.BACKGROUND_MODE, BackgroundMode.AMOLED.name)
                .putBoolean(ShizukuSettings.Keys.KEY_USE_SYSTEM_COLOR, false)
                .putBoolean(ShizukuSettings.Keys.KEY_BLACK_NIGHT_THEME, true)

            TokenXThemePreset.ONE_UI -> editor
                .putBoolean(TokenXAppearanceKeys.GLASS_ENABLED, true)
                .putFloat(TokenXAppearanceKeys.GLASS_OPACITY, .64f)
                .putFloat(TokenXAppearanceKeys.GLASS_BLUR, 26f)
                .putFloat(TokenXAppearanceKeys.GLASS_RADIUS, 26f)
                .putFloat(TokenXAppearanceKeys.GLASS_BORDER, .10f)
                .putFloat(TokenXAppearanceKeys.BACKGROUND_DIM, .26f)
                .putString(TokenXAppearanceKeys.BACKGROUND_MODE, BackgroundMode.AMOLED_GRADIENT.name)
                .putLong(TokenXAppearanceKeys.ACCENT_COLOR, TokenXAccent.SAMSUNG_BLUE.argb)
                .putString(TokenXAppearanceKeys.ACCENT_PRESET, TokenXAccent.SAMSUNG_BLUE.name)
                .putBoolean(ShizukuSettings.Keys.KEY_USE_SYSTEM_COLOR, false)
                .putBoolean(ShizukuSettings.Keys.KEY_BLACK_NIGHT_THEME, false)

            TokenXThemePreset.CRYSTAL -> editor
                .putBoolean(TokenXAppearanceKeys.GLASS_ENABLED, true)
                .putFloat(TokenXAppearanceKeys.GLASS_OPACITY, .34f)
                .putFloat(TokenXAppearanceKeys.GLASS_BLUR, 34f)
                .putFloat(TokenXAppearanceKeys.GLASS_RADIUS, 32f)
                .putFloat(TokenXAppearanceKeys.GLASS_BORDER, .22f)
                .putFloat(TokenXAppearanceKeys.BACKGROUND_DIM, .12f)
                .putString(TokenXAppearanceKeys.BACKGROUND_MODE, BackgroundMode.AMOLED_GRADIENT.name)
                .putLong(TokenXAppearanceKeys.ACCENT_COLOR, TokenXAccent.ICE.argb)
                .putString(TokenXAppearanceKeys.ACCENT_PRESET, TokenXAccent.ICE.name)
                .putBoolean(ShizukuSettings.Keys.KEY_USE_SYSTEM_COLOR, false)
                .putBoolean(ShizukuSettings.Keys.KEY_BLACK_NIGHT_THEME, false)

            TokenXThemePreset.SMOKE -> editor
                .putBoolean(TokenXAppearanceKeys.GLASS_ENABLED, true)
                .putFloat(TokenXAppearanceKeys.GLASS_OPACITY, .52f)
                .putFloat(TokenXAppearanceKeys.GLASS_BLUR, 24f)
                .putFloat(TokenXAppearanceKeys.GLASS_RADIUS, 34f)
                .putFloat(TokenXAppearanceKeys.GLASS_BORDER, .28f)
                .putFloat(TokenXAppearanceKeys.BACKGROUND_DIM, .20f)
                .putString(TokenXAppearanceKeys.BACKGROUND_MODE, BackgroundMode.AMOLED_GRADIENT.name)
                .putLong(TokenXAppearanceKeys.ACCENT_COLOR, TokenXAccent.GRAPHITE.argb)
                .putString(TokenXAppearanceKeys.ACCENT_PRESET, TokenXAccent.GRAPHITE.name)
                .putBoolean(ShizukuSettings.Keys.KEY_USE_SYSTEM_COLOR, false)
                .putBoolean(ShizukuSettings.Keys.KEY_BLACK_NIGHT_THEME, false)
        }

        editor.apply()
    }
}
