package moe.shizuku.manager.ui.screen

import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalView
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
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
import moe.shizuku.manager.ui.theme.TokenXThemePreset
import moe.shizuku.manager.ui.theme.TokenXThemeEngine
import moe.shizuku.manager.ui.theme.TokenXHapticStrength
import moe.shizuku.manager.ui.theme.FloatingBarStyle
import moe.shizuku.manager.ui.theme.FloatingBarShape
import moe.shizuku.manager.ui.theme.FloatingBarSelection
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import moe.shizuku.manager.ui.theme.ThemeState
import moe.shizuku.manager.ui.theme.TokenXUiStyle
import com.materialkolor.PaletteStyle

@Composable
fun AppearanceStudioScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = ShizukuSettings.getPreferences()
    var themePreset by remember {
        mutableStateOf(runCatching {
            TokenXThemePreset.valueOf(
                prefs.getString(TokenXAppearanceKeys.THEME_PRESET, TokenXThemePreset.TOKENX.name)!!
            )
        }.getOrDefault(TokenXThemePreset.TOKENX))
    }
    var uiStyle by remember {
        mutableStateOf(runCatching {
            TokenXUiStyle.valueOf(prefs.getString(TokenXAppearanceKeys.UI_STYLE, TokenXUiStyle.MATERIAL.name)!!)
        }.getOrDefault(TokenXUiStyle.MATERIAL))
    }
    var colorStyle by remember {
        mutableStateOf(runCatching {
            PaletteStyle.valueOf(prefs.getString(TokenXAppearanceKeys.COLOR_STYLE, PaletteStyle.TonalSpot.name)!!)
        }.getOrDefault(PaletteStyle.TonalSpot))
    }
    var colorSpec by remember { mutableStateOf(prefs.getString(TokenXAppearanceKeys.COLOR_SPEC, "SPEC_2025") ?: "SPEC_2025") }
    var glass by remember { mutableStateOf(prefs.getBoolean(TokenXAppearanceKeys.GLASS_ENABLED, true)) }
    var opacity by remember { mutableFloatStateOf(prefs.getFloat(TokenXAppearanceKeys.GLASS_OPACITY, .72f)) }
    var blur by remember { mutableFloatStateOf(prefs.getFloat(TokenXAppearanceKeys.GLASS_BLUR, 22f)) }
    var radius by remember { mutableFloatStateOf(prefs.getFloat(TokenXAppearanceKeys.GLASS_RADIUS, 28f)) }
    var border by remember { mutableFloatStateOf(prefs.getFloat(TokenXAppearanceKeys.GLASS_BORDER, .18f)) }
    var textContrast by remember { mutableFloatStateOf(prefs.getFloat(TokenXAppearanceKeys.TEXT_CONTRAST, 1f)) }
    var glassPreset by remember { mutableStateOf(prefs.getString(TokenXAppearanceKeys.GLASS_PRESET, "Frosted") ?: "Frosted") }
    var barOpacity by remember { mutableFloatStateOf(prefs.getFloat(TokenXAppearanceKeys.FLOATING_BAR_OPACITY, .82f)) }
    var barStyle by remember {
        mutableStateOf(runCatching {
            FloatingBarStyle.valueOf(prefs.getString(TokenXAppearanceKeys.FLOATING_BAR_STYLE, FloatingBarStyle.FROSTED.name)!!)
        }.getOrDefault(FloatingBarStyle.FROSTED))
    }
    var barShape by remember { mutableStateOf(runCatching { FloatingBarShape.valueOf(prefs.getString(TokenXAppearanceKeys.FLOATING_BAR_SHAPE, FloatingBarShape.ONE_UI.name)!!) }.getOrDefault(FloatingBarShape.ONE_UI)) }
    var barSelection by remember { mutableStateOf(runCatching { FloatingBarSelection.valueOf(prefs.getString(TokenXAppearanceKeys.FLOATING_BAR_SELECTION, FloatingBarSelection.GLASS.name)!!) }.getOrDefault(FloatingBarSelection.GLASS)) }
    var barWidth by remember { mutableFloatStateOf(prefs.getFloat(TokenXAppearanceKeys.FLOATING_BAR_WIDTH, .86f)) }
    var barHeight by remember { mutableFloatStateOf(prefs.getFloat(TokenXAppearanceKeys.FLOATING_BAR_HEIGHT, 64f)) }
    var barBottomGap by remember { mutableFloatStateOf(prefs.getFloat(TokenXAppearanceKeys.FLOATING_BAR_BOTTOM_GAP, 8f)) }
    var barBlur by remember { mutableFloatStateOf(prefs.getFloat(TokenXAppearanceKeys.FLOATING_BAR_BLUR, 22f)) }
    var barBorder by remember { mutableFloatStateOf(prefs.getFloat(TokenXAppearanceKeys.FLOATING_BAR_BORDER, .18f)) }
    var barElevation by remember { mutableFloatStateOf(prefs.getFloat(TokenXAppearanceKeys.FLOATING_BAR_ELEVATION, 8f)) }
    var dim by remember { mutableFloatStateOf(prefs.getFloat(TokenXAppearanceKeys.BACKGROUND_DIM, .18f)) }
    var colorHex by remember { mutableStateOf(String.format("#%08X", prefs.getLong(TokenXAppearanceKeys.BACKGROUND_COLOR, 0xFF090A0FFF))) }
    var seslSwitch by remember { mutableStateOf(true) }
    var hapticStrength by remember { mutableStateOf(runCatching { TokenXHapticStrength.valueOf(prefs.getString(TokenXAppearanceKeys.HAPTIC_STRENGTH, TokenXHapticStrength.STANDARD.name)!!) }.getOrDefault(TokenXHapticStrength.STANDARD)) }
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
        Text("Theme", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text("Preview and tune the complete TokenX interface.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        TokenXThemePreview(themePreset = themePreset, glass = glass, uiStyle = uiStyle, prefs = prefs)

        Text("UI framework", style = MaterialTheme.typography.titleMedium)
        Text("Framework and color theme are independent.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TokenXUiStyle.entries.forEach { candidate ->
                val available = true
                FilterChip(
                    selected = uiStyle == candidate,
                    enabled = available,
                    onClick = {
                        uiStyle = candidate
                        prefs.edit().putString(TokenXAppearanceKeys.UI_STYLE, candidate.name).apply()
                        refresh()
                    },
                    label = { Text(candidate.label) }
                )
            }
        }
        TokenXGlassCard {
            Row(
                Modifier.fillMaxWidth().padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(uiStyle.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (uiStyle) {
                            TokenXUiStyle.MATERIAL -> "Material 3 component renderer"
                            TokenXUiStyle.GHOST -> "TokenX translucent glass renderer"
                            TokenXUiStyle.MIUIX -> "Native Miuix framework enabled"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("Framework", color = MaterialTheme.colorScheme.primary)
            }
        }

        Text("Theme styles", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text("Whole-app presets inspired by KernelSU's centralized light/dark theme model. Pick a base, then fine-tune anything below.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TokenXThemePreset.entries.forEach { preset ->
                FilterChip(
                    selected = themePreset == preset,
                    onClick = {
                        themePreset = preset
                        TokenXThemeEngine.applyPreset(prefs, preset)
                        glass = prefs.getBoolean(TokenXAppearanceKeys.GLASS_ENABLED, true)
                        opacity = prefs.getFloat(TokenXAppearanceKeys.GLASS_OPACITY, .72f)
                        blur = prefs.getFloat(TokenXAppearanceKeys.GLASS_BLUR, 22f)
                        radius = prefs.getFloat(TokenXAppearanceKeys.GLASS_RADIUS, 28f)
                        border = prefs.getFloat(TokenXAppearanceKeys.GLASS_BORDER, .18f)
                        dim = prefs.getFloat(TokenXAppearanceKeys.BACKGROUND_DIM, .18f)
                        mode = runCatching {
                            BackgroundMode.valueOf(prefs.getString(TokenXAppearanceKeys.BACKGROUND_MODE, BackgroundMode.AMOLED_GRADIENT.name)!!)
                        }.getOrDefault(BackgroundMode.AMOLED_GRADIENT)
                        refresh()
                    },
                    label = { Text(preset.label) }
                )
            }
        }

        Text("Advanced customization", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Fine-tune the active TokenX theme. These controls use the same theme engine and preferences as the presets above.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        TokenXGlassCard {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Material color engine", style = MaterialTheme.typography.titleMedium)
                Text("Choose the palette algorithm and Material color specification used across TokenX.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(PaletteStyle.TonalSpot, PaletteStyle.Neutral, PaletteStyle.Vibrant, PaletteStyle.Expressive, PaletteStyle.Fidelity, PaletteStyle.Content, PaletteStyle.Monochrome).forEach { style ->
                        FilterChip(
                            selected = colorStyle == style,
                            onClick = {
                                colorStyle = style
                                prefs.edit().putString(TokenXAppearanceKeys.COLOR_STYLE, style.name).apply()
                                refresh()
                            },
                            label = { Text(style.name) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("SPEC_2021", "SPEC_2025").forEach { spec ->
                        FilterChip(
                            selected = colorSpec == spec,
                            onClick = {
                                colorSpec = spec
                                prefs.edit().putString(TokenXAppearanceKeys.COLOR_SPEC, spec).apply()
                                refresh()
                            },
                            label = { Text(spec.replace('_', ' ')) }
                        )
                    }
                }
            }
        }

        TextButton(shape = RoundedCornerShape(10.dp), onClick = {
            themePreset = TokenXThemePreset.TOKENX
            TokenXThemeEngine.applyPreset(prefs, TokenXThemePreset.TOKENX)
            glass = prefs.getBoolean(TokenXAppearanceKeys.GLASS_ENABLED, true)
            opacity = prefs.getFloat(TokenXAppearanceKeys.GLASS_OPACITY, .72f)
            blur = prefs.getFloat(TokenXAppearanceKeys.GLASS_BLUR, 22f)
            radius = prefs.getFloat(TokenXAppearanceKeys.GLASS_RADIUS, 28f)
            border = prefs.getFloat(TokenXAppearanceKeys.GLASS_BORDER, .18f)
            dim = prefs.getFloat(TokenXAppearanceKeys.BACKGROUND_DIM, .18f)
            mode = BackgroundMode.AMOLED_GRADIENT
            refresh()
        }) { Text("Reset appearance defaults") }

        TokenXGlassCard {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Glass surfaces", color = MaterialTheme.colorScheme.onSurface)
                    Switch(glass, onCheckedChange = {
                        glass = it; prefs.edit().putBoolean(TokenXAppearanceKeys.GLASS_ENABLED, it).apply(); refresh()
                    })
                }
                StudioSlider("Opacity", opacity, .25f..1f) { opacity = it; putFloat(TokenXAppearanceKeys.GLASS_OPACITY, it) }
                StudioSlider("Blur", blur, 0f..48f, " dp") { blur = it; putFloat(TokenXAppearanceKeys.GLASS_BLUR, it) }
                StudioSlider("Corner radius", radius, 8f..40f, " dp") { radius = it; putFloat(TokenXAppearanceKeys.GLASS_RADIUS, it) }
                StudioSlider("Border strength", border, 0f..0.5f) { border = it; putFloat(TokenXAppearanceKeys.GLASS_BORDER, it) }
                StudioSlider("Text contrast", textContrast, .75f..1f) { textContrast = it; putFloat(TokenXAppearanceKeys.TEXT_CONTRAST, it) }
            }
        }

        Text("Advanced • SESL", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
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

        Text("Haptics", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text("Tune tactile feedback for TokenX controls and privileged actions.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        TokenXGlassCard {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TokenXHapticStrength.entries.forEach { strength ->
                        FilterChip(
                            selected = hapticStrength == strength,
                            onClick = {
                                hapticStrength = strength
                                prefs.edit().putString(TokenXAppearanceKeys.HAPTIC_STRENGTH, strength.name).apply()
                            },
                            label = { Text(strength.label) }
                        )
                    }
                }
                Text("Standard is the default. Off disables TokenX-specific tactile feedback.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Text("Floating navigation bar", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text("Choose a floating, frosted, solid or ultra-clear bottom bar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FloatingBarStyle.entries.forEach { candidate ->
                FilterChip(
                    selected = barStyle == candidate,
                    onClick = {
                        barStyle = candidate
                        prefs.edit().putString(TokenXAppearanceKeys.FLOATING_BAR_STYLE, candidate.name).apply()
                        refresh()
                    },
                    label = { Text(candidate.name.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }
        Text("Shape", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FloatingBarShape.entries.forEach { candidate ->
                FilterChip(
                    selected = barShape == candidate,
                    onClick = {
                        barShape = candidate
                        prefs.edit().putString(TokenXAppearanceKeys.FLOATING_BAR_SHAPE, candidate.name).apply()
                        refresh()
                    },
                    label = { Text(candidate.label) }
                )
            }
        }
        StudioSlider("Width", barWidth, .62f..1f) { barWidth = it; putFloat(TokenXAppearanceKeys.FLOATING_BAR_WIDTH, it) }
        StudioSlider("Height", barHeight, 52f..78f, " dp") { barHeight = it; putFloat(TokenXAppearanceKeys.FLOATING_BAR_HEIGHT, it) }
        StudioSlider("Bottom gap", barBottomGap, 0f..28f, " dp") { barBottomGap = it; putFloat(TokenXAppearanceKeys.FLOATING_BAR_BOTTOM_GAP, it) }
        StudioSlider("Bar opacity", barOpacity, .20f..1f) { barOpacity = it; putFloat(TokenXAppearanceKeys.FLOATING_BAR_OPACITY, it) }
        StudioSlider("Blur strength", barBlur, 0f..48f, " dp") { barBlur = it; putFloat(TokenXAppearanceKeys.FLOATING_BAR_BLUR, it) }
        StudioSlider("Border strength", barBorder, 0f..0.5f) { barBorder = it; putFloat(TokenXAppearanceKeys.FLOATING_BAR_BORDER, it) }
        StudioSlider("Elevation", barElevation, 0f..18f, " dp") { barElevation = it; putFloat(TokenXAppearanceKeys.FLOATING_BAR_ELEVATION, it) }
        Text("Selected item", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FloatingBarSelection.entries.forEach { candidate ->
                FilterChip(
                    selected = barSelection == candidate,
                    onClick = {
                        barSelection = candidate
                        prefs.edit().putString(TokenXAppearanceKeys.FLOATING_BAR_SELECTION, candidate.name).apply()
                        refresh()
                    },
                    label = { Text(candidate.label) }
                )
            }
        }

        Text("Accent color", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text("Choose a tonal palette for every TokenX renderer. Swipe for more.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        val selectedAccent = prefs.getString(TokenXAppearanceKeys.ACCENT_PRESET, TokenXAccent.TOKEN_PURPLE.name)
        val useSystemAccent = prefs.getBoolean(ShizukuSettings.Keys.KEY_USE_SYSTEM_COLOR, false)
        var showCustomAccent by remember { mutableStateOf(selectedAccent == "CUSTOM") }
        var customAccent by remember {
            mutableStateOf(String.format("#%08X", prefs.getLong(TokenXAppearanceKeys.ACCENT_COLOR, TokenXAccent.TOKEN_PURPLE.argb)))
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            TonalAccentSwatch(
                label = "System",
                base = MaterialTheme.colorScheme.primary,
                selected = useSystemAccent,
                onClick = {
                    showCustomAccent = false
                    prefs.edit()
                        .putBoolean(ShizukuSettings.Keys.KEY_USE_SYSTEM_COLOR, true)
                        .putString(TokenXAppearanceKeys.ACCENT_PRESET, "SYSTEM")
                        .apply()
                    refresh()
                }
            )

            TokenXAccent.entries.forEach { preset ->
                TonalAccentSwatch(
                    label = preset.label,
                    base = Color(preset.argb.toInt()),
                    selected = !useSystemAccent && selectedAccent == preset.name,
                    onClick = {
                        showCustomAccent = false
                        prefs.edit()
                            .putLong(TokenXAppearanceKeys.ACCENT_COLOR, preset.argb)
                            .putString(TokenXAppearanceKeys.ACCENT_PRESET, preset.name)
                            .putBoolean(ShizukuSettings.Keys.KEY_USE_SYSTEM_COLOR, false)
                            .apply()
                        refresh()
                    }
                )
            }

            TonalAccentSwatch(
                label = "Custom",
                base = Color(prefs.getLong(TokenXAppearanceKeys.ACCENT_COLOR, TokenXAccent.TOKEN_PURPLE.argb).toInt()),
                selected = !useSystemAccent && selectedAccent == "CUSTOM",
                onClick = { showCustomAccent = true }
            )
        }

        if (showCustomAccent) {
            TokenXGlassCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = customAccent,
                        onValueChange = { customAccent = it.take(9) },
                        label = { Text("Custom accent (#AARRGGBB)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(shape = RoundedCornerShape(10.dp), onClick = {
                        runCatching { AndroidColor.parseColor(customAccent) }.onSuccess { parsed ->
                            prefs.edit()
                                .putLong(TokenXAppearanceKeys.ACCENT_COLOR, parsed.toLong() and 0xFFFFFFFFL)
                                .putString(TokenXAppearanceKeys.ACCENT_PRESET, "CUSTOM")
                                .putBoolean(ShizukuSettings.Keys.KEY_USE_SYSTEM_COLOR, false)
                                .apply()
                            refresh()
                        }
                    }) { Text("Apply custom accent") }
                }
            }
        }

        Text("Background", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
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
            Button(shape = RoundedCornerShape(10.dp), onClick = {
                runCatching { AndroidColor.parseColor(colorHex) }.onSuccess { parsed ->
                    prefs.edit().putLong(TokenXAppearanceKeys.BACKGROUND_COLOR, parsed.toLong() and 0xFFFFFFFFL).apply()
                    refresh()
                }
            }) { Text("Apply color") }
        }
        if (mode == BackgroundMode.CUSTOM_IMAGE) {
            Button(shape = RoundedCornerShape(10.dp), onClick = { imagePicker.launch(arrayOf("image/*")) }) { Text("Choose background image") }
        }
        StudioSlider("Background dim", dim, 0f..0.8f) { dim = it; putFloat(TokenXAppearanceKeys.BACKGROUND_DIM, it) }

        Text("Glass presets", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text("Neutral glass keeps wallpaper colors intact while the accent stays on controls.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        fun selectGlassPreset(name: String) {
            glassPreset = name
            prefs.edit().putString(TokenXAppearanceKeys.GLASS_PRESET, name).apply()
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = glassPreset == "Clear", shape = RoundedCornerShape(10.dp), onClick = {
                selectGlassPreset("Clear"); glass=true; opacity=.42f; blur=10f; radius=30f; border=.24f
                prefs.edit().putBoolean(TokenXAppearanceKeys.GLASS_ENABLED,true).putFloat(TokenXAppearanceKeys.GLASS_OPACITY,opacity).putFloat(TokenXAppearanceKeys.GLASS_BLUR,blur).putFloat(TokenXAppearanceKeys.GLASS_RADIUS,radius).putFloat(TokenXAppearanceKeys.GLASS_BORDER,border).apply(); refresh()
            }, label = { Text("Clear") })
            FilterChip(selected = glassPreset == "Frosted", shape = RoundedCornerShape(10.dp), onClick = {
                selectGlassPreset("Frosted"); glass=true; opacity=.72f; blur=28f; radius=28f; border=.18f
                prefs.edit().putBoolean(TokenXAppearanceKeys.GLASS_ENABLED,true).putFloat(TokenXAppearanceKeys.GLASS_OPACITY,opacity).putFloat(TokenXAppearanceKeys.GLASS_BLUR,blur).putFloat(TokenXAppearanceKeys.GLASS_RADIUS,radius).putFloat(TokenXAppearanceKeys.GLASS_BORDER,border).apply(); refresh()
            }, label = { Text("Frosted") })
            FilterChip(selected = glassPreset == "AMOLED", shape = RoundedCornerShape(10.dp), onClick = {
                selectGlassPreset("AMOLED"); mode=BackgroundMode.AMOLED; opacity=.58f; border=.12f
                prefs.edit().putString(TokenXAppearanceKeys.BACKGROUND_MODE,mode.name).putFloat(TokenXAppearanceKeys.GLASS_OPACITY,opacity).putFloat(TokenXAppearanceKeys.GLASS_BORDER,border).apply(); refresh()
            }, label = { Text("AMOLED") })
            FilterChip(selected = glassPreset == "Readable", shape = RoundedCornerShape(10.dp), onClick = {
                selectGlassPreset("Readable"); glass=true; opacity=.82f; radius=28f; border=.16f; dim=.32f
                prefs.edit().putBoolean(TokenXAppearanceKeys.GLASS_ENABLED,true).putFloat(TokenXAppearanceKeys.GLASS_OPACITY,opacity).putFloat(TokenXAppearanceKeys.GLASS_RADIUS,radius).putFloat(TokenXAppearanceKeys.GLASS_BORDER,border).putFloat(TokenXAppearanceKeys.BACKGROUND_DIM,dim).apply(); refresh()
            }, label = { Text("Readable") })
            FilterChip(selected = glassPreset == "Crystal", shape = RoundedCornerShape(10.dp), onClick = {
                selectGlassPreset("Crystal"); glass=true; opacity=.34f; radius=32f; border=.22f; dim=.12f
                prefs.edit().putBoolean(TokenXAppearanceKeys.GLASS_ENABLED,true).putFloat(TokenXAppearanceKeys.GLASS_OPACITY,opacity).putFloat(TokenXAppearanceKeys.GLASS_RADIUS,radius).putFloat(TokenXAppearanceKeys.GLASS_BORDER,border).putFloat(TokenXAppearanceKeys.BACKGROUND_DIM,dim).apply(); refresh()
            }, label = { Text("Crystal") })
            FilterChip(selected = glassPreset == "Smoke", shape = RoundedCornerShape(10.dp), onClick = {
                selectGlassPreset("Smoke"); glass=true; opacity=.52f; radius=34f; border=.28f; dim=.20f
                prefs.edit().putBoolean(TokenXAppearanceKeys.GLASS_ENABLED,true).putFloat(TokenXAppearanceKeys.GLASS_OPACITY,opacity).putFloat(TokenXAppearanceKeys.GLASS_RADIUS,radius).putFloat(TokenXAppearanceKeys.GLASS_BORDER,border).putFloat(TokenXAppearanceKeys.BACKGROUND_DIM,dim).apply(); refresh()
            }, label = { Text("Smoke") })
            FilterChip(selected = glassPreset == "One UI", shape = RoundedCornerShape(10.dp), onClick = {
                selectGlassPreset("One UI"); glass=true; opacity=.64f; radius=26f; border=.10f; dim=.26f
                prefs.edit().putBoolean(TokenXAppearanceKeys.GLASS_ENABLED,true).putFloat(TokenXAppearanceKeys.GLASS_OPACITY,opacity).putFloat(TokenXAppearanceKeys.GLASS_RADIUS,radius).putFloat(TokenXAppearanceKeys.GLASS_BORDER,border).putFloat(TokenXAppearanceKeys.BACKGROUND_DIM,dim).apply(); refresh()
            }, label = { Text("One UI") })
        }
    }
}

@Composable
private fun TonalAccentSwatch(
    label: String,
    base: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val darkTone = androidx.compose.ui.graphics.lerp(base, Color.Black, .42f)
    val midTone = androidx.compose.ui.graphics.lerp(base, Color.White, .18f)
    val lightTone = androidx.compose.ui.graphics.lerp(base, Color.White, .62f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.width(76.dp)
    ) {
        Box(
            Modifier
                .size(66.dp)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(4.dp)
                .clip(RoundedCornerShape(15.dp))
                .clickable(onClick = onClick)
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f).background(darkTone))
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(midTone))
                    Box(Modifier.weight(1f).fillMaxHeight().background(lightTone))
                }
            }
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(24.dp)
                    .background(base, RoundedCornerShape(7.dp))
                    .border(1.dp, MaterialTheme.colorScheme.surface.copy(alpha = .65f), RoundedCornerShape(7.dp))
            )
            if (selected) {
                Text(
                    "✓",
                    modifier = Modifier.align(Alignment.TopEnd).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(5.dp)).padding(horizontal = 4.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun StudioSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, suffix: String = "", onChange: (Float) -> Unit) {
    val view = LocalView.current
    var lastDetent by remember(title, range.start, range.endInclusive) { mutableIntStateOf(-1) }
    val span = (range.endInclusive - range.start).coerceAtLeast(0.0001f)
    val fraction = ((value - range.start) / span).coerceIn(0f, 1f)
    val detent = (fraction * 20f).toInt()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title + "  " + "%.2f".format(value) + suffix, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .46f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
        ) {
            Slider(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                value = value,
                onValueChange = { next ->
                    val nextFraction = ((next - range.start) / span).coerceIn(0f, 1f)
                    val nextDetent = (nextFraction * 20f).toInt()
                    if (nextDetent != lastDetent) {
                        ViewCompat.performHapticFeedback(
                            view,
                            if (nextDetent == 0 || nextDetent == 20) HapticFeedbackConstantsCompat.CONFIRM
                            else HapticFeedbackConstantsCompat.CLOCK_TICK
                        )
                        lastDetent = nextDetent
                    }
                    onChange(next)
                },
                onValueChangeFinished = {
                    ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.CONFIRM)
                },
                valueRange = range,
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = .78f),
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .42f),
                    thumbColor = MaterialTheme.colorScheme.primaryContainer,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
private fun TokenXThemePreview(
    themePreset: TokenXThemePreset,
    glass: Boolean,
    uiStyle: TokenXUiStyle,
    prefs: android.content.SharedPreferences,
) {
    var page by remember { mutableIntStateOf(0) }
    var cardActive by remember { mutableStateOf(false) }

    val useSystem = prefs.getBoolean(ShizukuSettings.Keys.KEY_USE_SYSTEM_COLOR, false)
    val accent = if (useSystem) MaterialTheme.colorScheme.primary else Color(
        prefs.getLong(TokenXAppearanceKeys.ACCENT_COLOR, TokenXAccent.TOKEN_PURPLE.argb).toInt()
    )
    val opacity = prefs.getFloat(TokenXAppearanceKeys.GLASS_OPACITY, .72f)
    val radius = prefs.getFloat(TokenXAppearanceKeys.GLASS_RADIUS, 28f)
    val borderStrength = prefs.getFloat(TokenXAppearanceKeys.GLASS_BORDER, .18f)
    val dim = prefs.getFloat(TokenXAppearanceKeys.BACKGROUND_DIM, .18f)
    val barOpacity = prefs.getFloat(TokenXAppearanceKeys.FLOATING_BAR_OPACITY, .82f)
    val barStyle = runCatching {
        FloatingBarStyle.valueOf(prefs.getString(TokenXAppearanceKeys.FLOATING_BAR_STYLE, FloatingBarStyle.FROSTED.name)!!)
    }.getOrDefault(FloatingBarStyle.FROSTED)
    val backgroundMode = runCatching {
        BackgroundMode.valueOf(prefs.getString(TokenXAppearanceKeys.BACKGROUND_MODE, BackgroundMode.AMOLED_GRADIENT.name)!!)
    }.getOrDefault(BackgroundMode.AMOLED_GRADIENT)

    val phoneBackground = when (backgroundMode) {
        BackgroundMode.AMOLED -> Color.Black
        BackgroundMode.AMOLED_GRADIENT -> androidx.compose.ui.graphics.lerp(Color.Black, accent, .14f)
        BackgroundMode.CUSTOM_COLOR -> Color(prefs.getLong(TokenXAppearanceKeys.BACKGROUND_COLOR, 0xFF090A0FFF).toInt())
        BackgroundMode.CUSTOM_IMAGE -> androidx.compose.ui.graphics.lerp(Color.Black, accent, .22f)
        BackgroundMode.SYSTEM -> MaterialTheme.colorScheme.surface
    }
    val previewShape = when (uiStyle) {
        TokenXUiStyle.MATERIAL -> RoundedCornerShape(radius.dp.coerceIn(12.dp, 32.dp))
        TokenXUiStyle.GHOST -> RoundedCornerShape(radius.dp.coerceIn(22.dp, 40.dp))
        TokenXUiStyle.MIUIX -> RoundedCornerShape(18.dp)
    }
    val surfaceAlpha = if (glass) opacity.coerceIn(.18f, 1f) else 1f
    val navShape = when (barStyle) {
        FloatingBarStyle.FLOATING -> RoundedCornerShape(18.dp)
        FloatingBarStyle.FROSTED -> RoundedCornerShape(12.dp)
        FloatingBarStyle.SOLID -> RoundedCornerShape(8.dp)
        FloatingBarStyle.CLEAR -> RoundedCornerShape(0.dp)
    }
    val navColor = when (barStyle) {
        FloatingBarStyle.FLOATING -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = barOpacity.coerceIn(.25f, .92f))
        FloatingBarStyle.FROSTED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = (barOpacity * .66f).coerceIn(.18f, .78f))
        FloatingBarStyle.SOLID -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 1f)
        FloatingBarStyle.CLEAR -> Color.Transparent
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .width(206.dp)
                .height(330.dp)
                .background(phoneBackground, RoundedCornerShape(34.dp))
                .border(1.dp, accent.copy(alpha = (.22f + borderStrength).coerceAtMost(.72f)), RoundedCornerShape(34.dp))
                .padding(12.dp)
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(
                            when (page) { 0 -> "TokenX"; 1 -> "Apps"; 2 -> "Modules"; else -> "Settings" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(themePreset.label + " • " + uiStyle.label, style = MaterialTheme.typography.labelSmall, color = accent)
                    }
                    Box(Modifier.size(9.dp).background(accent, RoundedCornerShape(3.dp)))
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(accent.copy(alpha = if (cardActive) .52f else .24f), previewShape)
                        .border(1.dp, accent.copy(alpha = .42f), previewShape)
                        .clickable { cardActive = !cardActive }
                        .padding(10.dp)
                ) {
                    Text(if (cardActive) "Interactive state" else "Tap preview card", style = MaterialTheme.typography.labelMedium)
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = (surfaceAlpha * (1f - dim * .35f)).coerceIn(.12f, 1f)),
                            previewShape
                        )
                        .clickable { page = (page + 1) % 4 }
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(3) { index ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (index == page % 3) accent.copy(alpha = .22f) else Color.Transparent, RoundedCornerShape(12.dp))
                                .padding(7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(Modifier.size(18.dp).background(if (index == page % 3) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.45f), RoundedCornerShape(5.dp)))
                            Box(Modifier.weight(1f).height(7.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.28f), RoundedCornerShape(4.dp)))
                        }
                    }
                }

                Box(
                    Modifier
                        .then(if (barStyle == FloatingBarStyle.FLOATING) Modifier.padding(horizontal = 18.dp) else Modifier)
                        .fillMaxWidth()
                        .background(navColor, navShape)
                        .then(if (barStyle != FloatingBarStyle.CLEAR) Modifier.border(1.dp, accent.copy(alpha = if (barStyle == FloatingBarStyle.FROSTED) .24f else .12f), navShape) else Modifier)
                        .padding(horizontal = if (barStyle == FloatingBarStyle.FLOATING) 8.dp else 4.dp, vertical = if (barStyle == FloatingBarStyle.FLOATING) 7.dp else 5.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        repeat(4) { index ->
                            Box(
                                Modifier
                                    .size(if (barStyle == FloatingBarStyle.FLOATING) 30.dp else 32.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .clickable { page = index }
                                    .background(if (page == index) accent.copy(alpha = .24f) else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    Modifier
                                        .size(if (page == index) 17.dp else 13.dp)
                                        .background(if (page == index) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.45f), RoundedCornerShape(5.dp))
                                )
                            }
                        }
                    }
                }
            }
        }
        Text(
            barStyle.name.lowercase().replaceFirstChar { it.uppercase() } + " • " +
                backgroundMode.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
