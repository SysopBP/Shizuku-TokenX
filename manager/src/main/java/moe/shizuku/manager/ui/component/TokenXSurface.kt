package moe.shizuku.manager.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.ui.theme.LocalTokenXUiStyle
import moe.shizuku.manager.ui.theme.TokenXUiStyle

/**
 * Framework-aware TokenX surface. New screens should depend on this API instead
 * of choosing Material/Ghost components directly.
 */
@Composable
fun TokenXSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    when (LocalTokenXUiStyle.current) {
        TokenXUiStyle.GHOST -> TokenXGlassCard(modifier = modifier, content = content)
        TokenXUiStyle.MATERIAL,
        TokenXUiStyle.MIUIX -> Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            androidx.compose.foundation.layout.Box(content = content)
        }
    }
}
