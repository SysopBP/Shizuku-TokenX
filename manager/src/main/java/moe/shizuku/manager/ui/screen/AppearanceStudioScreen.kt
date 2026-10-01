package moe.shizuku.manager.ui.screen

import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.viewinterop.AndroidView
import androidx.appcompat.widget.SwitchCompat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ui.component.TokenXGlassCard
import moe.shizuku.manager.ui.theme.BackgroundMode
import moe.shizuku.manager.ui.theme.TokenXAppearanceKeys
import moe.shizuku.manager.ui.theme.TokenXAccent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import moe.shizuku.manager.ui.theme.ThemeState

@Composable
fun AppearanceStudioScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = ShizukuSettings.getPreferences()
    var glass by remember { mutableStateOf(prefs.getBoolean(TokenXAppearanceKeys.GLASS_ENABLED, true)) }
    var opacity by remember { mutableFloatStateOf(prefs.getFloat(TokenXAppearanceKeys.GLASS_OPACITY, .72f)) }
    var blur by remember { mutableFloatStateOf(prefs.getFloat(TokenXAppearanceKeys.GLASS_BLUR, 22f)) }
    var radius by remember { mutableFloatStateOf(prefs.getFloat(TokenXAppearanceKeys.GLASS_RADIUS, 28f)) }
    var border by remember { mutableFloatStateOf(prefs.getFloat(TokenXAppearanceKeys.GLASS_BORDER, .18f)) }
    var dim by remember { mutableFloatStateOf(prefs.getFloat(TokenXAppearanceKeys.BACKGROUND_DIM, .18f)) }
    var colorHex by remember { mutableStateOf(String.format("#%08X", prefs.getLong(TokenXAppearanceKeys.BACKGROUND_COLOR, 0xFF090A0FFF))) }
    var seslSwitch by remember { mutableStateOf(true) }
    var mode by remember { mutableStateOf(runCatching { BackgroundMode.valueOf(prefs.getString(TokenXAppearanceKeys.BACKGROUND_MODE, BackgroundMode.AMOLED_GRADIENT.name)!!) }.getOrDefault(BackgroundMode.AMOLED_GRADIENT)) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            prefs.edit()
                .putString(TokenXAppearanceKeys.BACKGROUND_IMAGE_URI, uri.toString())
                .putString(TokenXAppearanceKeys.BACKGROUND_MODE, BackgroundMode.CUSTOM_IMAGE.name)
                .apply()
            mode = BackgroundMode.CUSTOM_IMAGE
            ThemeState.refresh()
        }
    }

    fun refresh() = ThemeState.refresh()
    fun putFloat(key: String, v: Float) { prefs.edit().putFloat(key, v).apply(); refresh() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Appearance Studio", style = MaterialTheme.typography.headlineMedium)
        Text("Build your own TokenX look. Changes are saved as you make them.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        TokenXGlassCard {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Glass surfaces")
                    Switch(glass, onCheckedChange = {
                        glass = it; prefs.edit().putBoolean(TokenXAppearanceKeys.GLASS_ENABLED, it).apply(); refresh()
                    })
                }
                StudioSlider("Opacity", opacity, .25f..1f) { opacity = it; putFloat(TokenXAppearanceKeys.GLASS_OPACITY, it) }
                StudioSlider("Blur", blur, 0f..48f, " dp") { blur = it; putFloat(TokenXAppearanceKeys.GLASS_BLUR, it) }
                StudioSlider("Corner radius", radius, 8f..40f, " dp") { radius = it; putFloat(TokenXAppearanceKeys.GLASS_RADIUS, it) }
                StudioSlider("Border strength", border, 0f..0.5f) { border = it; putFloat(TokenXAppearanceKeys.GLASS_BORDER, it) }
            }
        }

        Text("SESL Test Lab", style = MaterialTheme.typography.titleMedium)
        Text("Live tribalfs SESL control hosted inside the TokenX Compose UI.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        TokenXGlassCard {
            Row(
                Modifier.fillMaxWidth().padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("SESL AppCompat switch", style = MaterialTheme.typography.titleSmall)
                    Text("Native Android View • SESL-backed", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AndroidView(
                    factory = { ctx -> SwitchCompat(ctx).apply { isChecked = seslSwitch } },
                    update = { view ->
                        view.setOnCheckedChangeListener(null)
                        view.isChecked = seslSwitch
                        view.setOnCheckedChangeListener { _, checked -> seslSwitch = checked }
                    }
                )
            }
        }

        Text("Accent color", style = MaterialTheme.typography.titleMedium)
        Text("Changes Material, TokenX glass highlights and active-state accents across the app.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TokenXAccent.entries.forEach { preset ->
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .background(Color(preset.argb.toInt()), CircleShape)
                            .clickable {
                                prefs.edit()
                                    .putLong(TokenXAppearanceKeys.ACCENT_COLOR, preset.argb)
                                    .putString(TokenXAppearanceKeys.ACCENT_PRESET, preset.name)
                                    .putBoolean(ShizukuSettings.Keys.KEY_USE_SYSTEM_COLOR, false)
                                    .apply()
                                refresh()
                            }
                    )
                    Text(preset.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        var customAccent by remember { mutableStateOf(String.format("#%08X", prefs.getLong(TokenXAppearanceKeys.ACCENT_COLOR, TokenXAccent.TOKEN_PURPLE.argb))) }
        OutlinedTextField(
            value = customAccent,
            onValueChange = { customAccent = it.take(9) },
            label = { Text("Custom accent (#AARRGGBB)") },
            singleLine = true
        )
        Button(onClick = {
            runCatching { AndroidColor.parseColor(customAccent) }.onSuccess { parsed ->
                prefs.edit()
                    .putLong(TokenXAppearanceKeys.ACCENT_COLOR, parsed.toLong() and 0xFFFFFFFFL)
                    .putString(TokenXAppearanceKeys.ACCENT_PRESET, "CUSTOM")
                    .putBoolean(ShizukuSettings.Keys.KEY_USE_SYSTEM_COLOR, false)
                    .apply()
                refresh()
            }
        }) { Text("Apply custom accent") }

        Text("Background", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BackgroundMode.entries.forEach { candidate ->
                FilterChip(
                    selected = mode == candidate,
                    onClick = {
                        mode = candidate
                        prefs.edit().putString(TokenXAppearanceKeys.BACKGROUND_MODE, candidate.name).apply()
                        refresh()
                    },
                    label = { Text(candidate.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }
        if (mode == BackgroundMode.CUSTOM_COLOR) {
            OutlinedTextField(
                value = colorHex,
                onValueChange = { colorHex = it.take(9) },
                label = { Text("Background color (#AARRGGBB)") },
                singleLine = true
            )
            Button(onClick = {
                runCatching { AndroidColor.parseColor(colorHex) }.onSuccess { parsed ->
                    prefs.edit().putLong(TokenXAppearanceKeys.BACKGROUND_COLOR, parsed.toLong() and 0xFFFFFFFFL).apply()
                    refresh()
                }
            }) { Text("Apply color") }
        }
        if (mode == BackgroundMode.CUSTOM_IMAGE) {
            Button(onClick = { imagePicker.launch(arrayOf("image/*")) }) { Text("Choose background image") }
        }
        StudioSlider("Background dim", dim, 0f..0.8f) { dim = it; putFloat(TokenXAppearanceKeys.BACKGROUND_DIM, it) }

        Text("Presets", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                glass=true; opacity=.42f; blur=10f; radius=30f; border=.24f
                prefs.edit().putBoolean(TokenXAppearanceKeys.GLASS_ENABLED,true).putFloat(TokenXAppearanceKeys.GLASS_OPACITY,opacity).putFloat(TokenXAppearanceKeys.GLASS_BLUR,blur).putFloat(TokenXAppearanceKeys.GLASS_RADIUS,radius).putFloat(TokenXAppearanceKeys.GLASS_BORDER,border).apply(); refresh()
            }) { Text("Clear") }
            Button(onClick = {
                glass=true; opacity=.72f; blur=28f; radius=28f; border=.18f
                prefs.edit().putBoolean(TokenXAppearanceKeys.GLASS_ENABLED,true).putFloat(TokenXAppearanceKeys.GLASS_OPACITY,opacity).putFloat(TokenXAppearanceKeys.GLASS_BLUR,blur).putFloat(TokenXAppearanceKeys.GLASS_RADIUS,radius).putFloat(TokenXAppearanceKeys.GLASS_BORDER,border).apply(); refresh()
            }) { Text("Frosted") }
            Button(onClick = {
                mode=BackgroundMode.AMOLED; opacity=.58f; border=.12f
                prefs.edit().putString(TokenXAppearanceKeys.BACKGROUND_MODE,mode.name).putFloat(TokenXAppearanceKeys.GLASS_OPACITY,opacity).putFloat(TokenXAppearanceKeys.GLASS_BORDER,border).apply(); refresh()
            }) { Text("AMOLED") }
        }
    }
}

@Composable
private fun StudioSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, suffix: String = "", onChange: (Float) -> Unit) {
    Column {
        Text(title + "  " + "%.2f".format(value) + suffix, style = MaterialTheme.typography.labelLarge)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
