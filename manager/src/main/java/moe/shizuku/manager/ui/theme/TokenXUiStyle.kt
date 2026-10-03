package moe.shizuku.manager.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * UI component framework selected independently from color/brightness.
 * MIUIX is reserved until its native renderer is linked; callers must fall back
 * to Material rather than impersonating Miuix with Material components.
 */
enum class TokenXUiStyle(val label: String) {
    MATERIAL("Material"),
    GHOST("Ghost"),
    MIUIX("Miuix"),
}

val LocalTokenXUiStyle = staticCompositionLocalOf { TokenXUiStyle.MATERIAL }
