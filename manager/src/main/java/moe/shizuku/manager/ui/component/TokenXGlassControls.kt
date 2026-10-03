package moe.shizuku.manager.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat

@Composable
fun TokenXGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .58f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .62f)),
        tonalElevation = 2.dp,
        onClick = {
            ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CONFIRM)
            onClick()
        },
        enabled = enabled,
    ) {
        androidx.compose.foundation.layout.Box(Modifier.padding(PaddingValues(horizontal = 18.dp, vertical = 12.dp))) { content() }
    }
}

@Composable
fun TokenXGlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    detents: Int = 20,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val view = LocalView.current
    var lastDetent by remember(valueRange.start, valueRange.endInclusive, detents) { mutableIntStateOf(-1) }
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(.0001f)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .46f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)),
    ) {
        Slider(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            value = value,
            enabled = enabled,
            valueRange = valueRange,
            onValueChange = { next ->
                val fraction = ((next - valueRange.start) / span).coerceIn(0f, 1f)
                val d = (fraction * detents).toInt()
                if (d != lastDetent) {
                    ViewCompat.performHapticFeedback(
                        view,
                        if (d == 0 || d == detents) HapticFeedbackConstantsCompat.CONFIRM else HapticFeedbackConstantsCompat.CLOCK_TICK
                    )
                    lastDetent = d
                }
                onValueChange(next)
            },
            onValueChangeFinished = {
                ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CONFIRM)
                onValueChangeFinished?.invoke()
            },
            colors = SliderDefaults.colors(
                activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = .78f),
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .42f),
                thumbColor = MaterialTheme.colorScheme.primaryContainer,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}
