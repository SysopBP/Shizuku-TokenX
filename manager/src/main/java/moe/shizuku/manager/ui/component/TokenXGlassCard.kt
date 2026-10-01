package moe.shizuku.manager.ui.component

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.ui.theme.LocalTokenXGlass

/**
 * Reusable TokenX glass surface. The translucent gradient is the reliable base
 * on every Android version; blur is deliberately subtle and can be disabled by
 * the appearance editor for performance/accessibility.
 */
@Composable
fun TokenXGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = LocalTokenXGlass.current
    val shape = RoundedCornerShape(glass.cornerRadiusDp.dp)
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val tint = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (glass.enabled) {
                    Brush.linearGradient(
                        listOf(
                            base.copy(alpha = glass.opacity),
                            tint.copy(alpha = glass.opacity * 0.14f),
                            base.copy(alpha = (glass.opacity * 0.78f).coerceIn(0f, 1f)),
                        )
                    )
                } else {
                    Brush.linearGradient(listOf(base, base))
                }
            )
            .border(
                BorderStroke(
                    1.dp,
                    Color.White.copy(alpha = if (glass.enabled) glass.borderOpacity else 0f)
                ),
                shape
            ),
        content = content
    )
}
