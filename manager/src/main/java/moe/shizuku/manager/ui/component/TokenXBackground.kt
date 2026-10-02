package moe.shizuku.manager.ui.component

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.rememberAsyncImagePainter
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ui.theme.BackgroundMode
import moe.shizuku.manager.ui.theme.LocalTokenXGlass
import moe.shizuku.manager.ui.theme.TokenXAppearanceKeys

@Composable
fun TokenXBackground(content: @Composable BoxScope.() -> Unit) {
    val context = LocalContext.current
    val prefs = ShizukuSettings.getPreferences()
    val style = LocalTokenXGlass.current
    val dim = prefs.getFloat(TokenXAppearanceKeys.BACKGROUND_DIM, .18f).coerceIn(0f, .9f)
    val customColor = runCatching {
        Color(prefs.getLong(TokenXAppearanceKeys.BACKGROUND_COLOR, 0xFF090A0FFF))
    }.getOrDefault(Color(0xFF090A0F))
    val imageUri = prefs.getString(TokenXAppearanceKeys.BACKGROUND_IMAGE_URI, null)

    Box(Modifier.fillMaxSize()) {
        when (style.backgroundMode) {
            BackgroundMode.SYSTEM -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            BackgroundMode.AMOLED -> Box(Modifier.fillMaxSize().background(Color.Black))
            BackgroundMode.AMOLED_GRADIENT -> Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Black, MaterialTheme.colorScheme.primary.copy(alpha = .16f), Color.Black)
                    )
                )
            )
            BackgroundMode.CUSTOM_COLOR -> Box(Modifier.fillMaxSize().background(customColor))
            BackgroundMode.CUSTOM_IMAGE -> {
                if (!imageUri.isNullOrBlank()) {
                    Image(
                        painter = rememberAsyncImagePainter(Uri.parse(imageUri)),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else Box(Modifier.fillMaxSize().background(Color.Black))
            }
        }
        if (dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        // TokenXBackground is also a hand-drawn surface. Establish a readable
        // default foreground for every custom TokenX screen placed on it.
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onBackground
        ) {
            content()
        }
    }
}
