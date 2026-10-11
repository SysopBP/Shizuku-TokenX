package moe.shizuku.manager.ui.screen
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ui.component.TokenXGlassCard
private const val P = "tokenx_liquid_glass_"
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidGlassLabsScreen(onBack: () -> Unit) {
    val p = ShizukuSettings.getPreferences()
    var enabled by remember { mutableStateOf(p.getBoolean(P + "enabled", false)) }
    var blur by remember { mutableFloatStateOf(p.getFloat(P + "blur", 28f)) }
    var opacity by remember { mutableFloatStateOf(p.getFloat(P + "opacity", .72f)) }
    var radius by remember { mutableFloatStateOf(p.getFloat(P + "radius", 28f)) }
    var notifications by remember { mutableStateOf(p.getBoolean(P + "notifications", true)) }
    var qs by remember { mutableStateOf(p.getBoolean(P + "qs", true)) }
    var clock by remember { mutableStateOf(p.getBoolean(P + "clock", true)) }
    var shade by remember { mutableStateOf(p.getBoolean(P + "shade", true)) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("TokenX Liquid Glass") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            windowInsets = WindowInsets(0.dp)
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TokenXGlassCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.BlurOn, null)
                    Text("Liquid Glass Engine", style = MaterialTheme.typography.titleLarge)
                    Text("Clean-room SystemUI glass controls", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    GS("Enable engine", "Master switch for the future SystemUI hook backend.", enabled) {
                        enabled = it
                        p.edit().putBoolean(P + "enabled", it).apply()
                    }
                }
            }
            TokenXGlassCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Optics", style = MaterialTheme.typography.titleMedium)
                    GSl("Blur", blur, 0f..80f) { blur = it; p.edit().putFloat(P + "blur", it).apply() }
                    GSl("Opacity", opacity, .1f..1f) { opacity = it; p.edit().putFloat(P + "opacity", it).apply() }
                    GSl("Corner radius", radius, 0f..60f) { radius = it; p.edit().putFloat(P + "radius", it).apply() }
                }
            }
            TokenXGlassCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("SystemUI targets", style = MaterialTheme.typography.titleMedium)
                    GS("Notifications", "Glass notification surfaces.", notifications) { notifications = it; p.edit().putBoolean(P + "notifications", it).apply() }
                    GS("Quick Settings", "Glass QS tiles and containers.", qs) { qs = it; p.edit().putBoolean(P + "qs", it).apply() }
                    GS("Clock", "Glass treatment around supported clock surfaces.", clock) { clock = it; p.edit().putBoolean(P + "clock", it).apply() }
                    GS("Shade background", "Blur behind the notification shade.", shade) { shade = it; p.edit().putBoolean(P + "shade", it).apply() }
                }
            }
            Text(
                "Settings are stored now; rendering stays off until the TokenX SystemUI hook backend is attached.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GS(t:String,s:String,c:Boolean,o:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){Column(Modifier.weight(1f)){Text(t,style=MaterialTheme.typography.titleSmall);Text(s,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Switch(checked = c, onCheckedChange = o)}}
@Composable
private fun GSl(t: String, v: Float, r: ClosedFloatingPointRange<Float>, o: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(t)
            Text(if (r.endInclusive <= 1f) ((v * 100).toInt()).toString() + "%" else v.toInt().toString())
        }
        Slider(value = v, onValueChange = o, valueRange = r)
    }
}