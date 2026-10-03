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
import moe.shizuku.manager.ui.theme.FloatingBarStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
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
    var barOpacity by remember { mutableFloatStateOf(prefs.getFloat(TokenXAppearanceKeys.FLOATING_BAR_OPACITY, .82f)) }
    var barStyle by remember {
        mutableStateOf(runCatching {
            FloatingBarStyle.valueOf(prefs.getString(TokenXAppearanceKeys.FLOATING_BAR_STYLE, FloatingBarStyle.FROSTED.name)!!)
        }.getOrDefault(FloatingBarStyle.FROSTED))
    }
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
        Text("Theme", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text("Preview and tune the complete TokenX interface.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        TokenXThemePreview(themePreset = themePreset, glass = glass)

        Text("Color palette", style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            TokenXAccent.entries.forEach { preset ->
                val selected = prefs.getString(TokenXAppearanceKeys.ACCENT_PRESET, TokenXAccent.TOKEN_PURPLE.name) == preset.name
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(54.dp)
                            .background(Color(preset.argb.toInt()).copy(alpha = .32f), CircleShape)
                            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable {
                                prefs.edit()
                                    .putLong(TokenXAppearanceKeys.ACCENT_COLOR, preset.argb)
                                    .putString(TokenXAppearanceKeys.ACCENT_PRESET, preset.name)
                                    .putBoolean(ShizukuSettings.Keys.KEY_USE_SYSTEM_COLOR, false)
                                    .apply()
                                refresh()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(26.dp).background(Color(preset.argb.toInt()), CircleShape))
                    }
                    Text(preset.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Text("Quick appearance", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "System" to TokenXThemePreset.SYSTEM,
                "Ghost" to TokenXThemePreset.GHOST,
                "Dark" to TokenXThemePreset.TOKENX,
                "AMOLED" to TokenXThemePreset.AMOLED
            ).forEach { (label, preset) ->
                FilterChip(
                    selected = themePreset == preset,
                    onClick = {
                        themePreset = preset
                        prefs.edit().putString(TokenXAppearanceKeys.THEME_PRESET, preset.name).apply()
                        refresh()
                    },
                    label = { Text(label) }
                )
            }
        }

        Text("UI framework", style = MaterialTheme.typography.titleMedium)
        Text("Framework and color theme are independent.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TokenXUiStyle.entries.forEach { candidate ->
                val available = candidate != TokenXUiStyle.MIUIX
                FilterChip(
                    selected = uiStyle == candidate,
                    enabled = available,
                    onClick = {
                        uiStyle = candidate
                        prefs.edit().putString(TokenXAppearanceKeys.UI_STYLE, candidate.name).apply()
                        refresh()
                    },
                    label = { Text(if (available) candidate.label else candidate.label + " • next") }
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
                            TokenXUiStyle.MIUIX -> "Native Miuix renderer pending"
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
                        val editor = prefs.edit().putString(TokenXAppearanceKeys.THEME_PRESET, preset.name)
                        when (preset) {
                            TokenXThemePreset.SYSTEM -> {
                                glass = true; opacity = .72f; blur = 22f; radius = 28f; border = .18f; dim = .18f
                                mode = BackgroundMode.SYSTEM
                                editor.putBoolean(ShizukuSettings.Keys.KEY_USE_SYSTEM_COLOR, true)
                                    .putBoolean(ShizukuSettings.Keys.KEY_BLACK_NIGHT_THEME, false)
                            }
                            TokenXThemePreset.TOKENX -> {
                                glass = true; opacity = .72f; blur = 28f; radius = 28f; border = .18f; dim = .18f
                                mode = BackgroundMode.AMOLED_GRADIENT
                                editor.putBoolean(ShizukuSettings.Keys.KEY_USE_SYSTEM_COLOR, false)
                                    .putBoolean(ShizukuSettings.Keys.KEY_BLACK_NIGHT_THEME, false)
                                    .putLong(TokenXAppearanceKeys.ACCENT_COLOR, TokenXAccent.TOKEN_PURPLE.argb)
                                    .putString(TokenXAppearanceKeys.ACCENT_PRESET, TokenXAccent.TOKEN_PURPLE.name)
                            }
                            TokenXThemePreset.GHOST -> {
                                glass = true; opacity = .26f; blur = 38f; radius = 34f; border = .24f; dim = .08f
                                mode = BackgroundMode.AMOLED_GRADIENT
                                editor.putBoolean(ShizukuSettings.Keys.KEY_USE_SYSTEM_COLOR, false)
                                    .putBoolean(ShizukuSettings.Keys.KEY_BLACK_NIGHT_THEME, false)
                                    .putLong(TokenXAppearanceKeys.ACCENT_COLOR, TokenXAccent.ICE.argb)
                                    .putString(TokenXAppearanceKeys.ACCENT_PRESET, TokenXAccent.ICE.name)
                            }
                            TokenXThemePreset.AMOLED -> {
                                glass = true; opacity = .58f; blur = 20f; radius = 28f; border = .12f; dim = .12f
                                mode = BackgroundMode.AMOLED
                                editor.putBoolean(ShizukuSettings.Keys.KEY_USE_SYSTEM_COLOR, false)
                                    .putBoolean(ShizukuSettings.Keys.KEY_BLACK_NIGHT_THEME, true)
                            }
                            TokenXThemePreset.ONE_UI -> {
                                glass = true; opacity = .64f; blur = 26f; radius = 26f; border = .10f; dim = .26f
                                mode = BackgroundMode.AMOLED_GRADIENT
                                editor.putBoolean(ShizukuSettings.Keys.KEY_USE_SYSTEM_COLOR, false)
                                    .putBoolean(ShizukuSettings.Keys.KEY_BLACK_NIGHT_THEME, false)
                                    .putLong(TokenXAppearanceKeys.ACCENT_COLOR, TokenXAccent.SAMSUNG_BLUE.argb)
                                    .putString(TokenXAppearanceKeys.ACCENT_PRESET, TokenXAccent.SAMSUNG_BLUE.name)
                            }
                            TokenXThemePreset.CRYSTAL -> {
                                glass = true; opacity = .34f; blur = 34f; radius = 32f; border = .22f; dim = .12f
                                mode = BackgroundMode.AMOLED_GRADIENT
                                editor.putBoolean(ShizukuSettings.Keys.KEY_USE_SYSTEM_COLOR, false)
                                    .putBoolean(ShizukuSettings.Keys.KEY_BLACK_NIGHT_THEME, false)
                                    .putLong(TokenXAppearanceKeys.ACCENT_COLOR, TokenXAccent.ICE.argb)
                                    .putString(TokenXAppearanceKeys.ACCENT_PRESET, TokenXAccent.ICE.name)
                            }
                            TokenXThemePreset.SMOKE -> {
                                glass = true; opacity = .52f; blur = 24f; radius = 34f; border = .28f; dim = .20f
                                mode = BackgroundMode.AMOLED_GRADIENT
                                editor.putBoolean(ShizukuSettings.Keys.KEY_USE_SYSTEM_COLOR, false)
                                    .putBoolean(ShizukuSettings.Keys.KEY_BLACK_NIGHT_THEME, false)
                                    .putLong(TokenXAppearanceKeys.ACCENT_COLOR, TokenXAccent.GRAPHITE.argb)
                                    .putString(TokenXAppearanceKeys.ACCENT_PRESET, TokenXAccent.GRAPHITE.name)
                            }
                        }
                        editor.putBoolean(TokenXAppearanceKeys.GLASS_ENABLED, glass)
                            .putFloat(TokenXAppearanceKeys.GLASS_OPACITY, opacity)
                            .putFloat(TokenXAppearanceKeys.GLASS_BLUR, blur)
                            .putFloat(TokenXAppearanceKeys.GLASS_RADIUS, radius)
                            .putFloat(TokenXAppearanceKeys.GLASS_BORDER, border)
                            .putFloat(TokenXAppearanceKeys.BACKGROUND_DIM, dim)
                            .putString(TokenXAppearanceKeys.BACKGROUND_MODE, mode.name)
                            .apply()
                        refresh()
                    },
                    label = { Text(preset.label) }
                )
            }
        }

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

        TextButton(onClick = {
            themePreset = TokenXThemePreset.TOKENX
            glass = true; opacity = .72f; blur = 22f; radius = 28f; border = .18f; dim = .18f
            barOpacity = .82f; barStyle = FloatingBarStyle.FROSTED; mode = BackgroundMode.AMOLED_GRADIENT
            prefs.edit()
                .putString(TokenXAppearanceKeys.THEME_PRESET, TokenXThemePreset.TOKENX.name)
                .putBoolean(TokenXAppearanceKeys.GLASS_ENABLED, glass)
                .putFloat(TokenXAppearanceKeys.GLASS_OPACITY, opacity)
                .putFloat(TokenXAppearanceKeys.GLASS_BLUR, blur)
                .putFloat(TokenXAppearanceKeys.GLASS_RADIUS, radius)
                .putFloat(TokenXAppearanceKeys.GLASS_BORDER, border)
                .putFloat(TokenXAppearanceKeys.BACKGROUND_DIM, dim)
                .putFloat(TokenXAppearanceKeys.FLOATING_BAR_OPACITY, barOpacity)
                .putString(TokenXAppearanceKeys.FLOATING_BAR_STYLE, barStyle.name)
                .putString(TokenXAppearanceKeys.BACKGROUND_MODE, mode.name)
                .putLong(TokenXAppearanceKeys.ACCENT_COLOR, TokenXAccent.TOKEN_PURPLE.argb)
                .putString(TokenXAppearanceKeys.ACCENT_PRESET, TokenXAccent.TOKEN_PURPLE.name)
                .putBoolean(ShizukuSettings.Keys.KEY_USE_SYSTEM_COLOR, false)
                .putBoolean(ShizukuSettings.Keys.KEY_BLACK_NIGHT_THEME, false)
                .apply()
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
        StudioSlider("Bar opacity", barOpacity, .20f..1f) {
            barOpacity = it
            putFloat(TokenXAppearanceKeys.FLOATING_BAR_OPACITY, it)
        }

        Text("Accent color", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
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

        Text("Glass presets", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text("Neutral glass keeps wallpaper colors intact while the accent stays on controls.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Button(onClick = {
                glass=true; opacity=.82f; radius=28f; border=.16f; dim=.32f
                prefs.edit().putBoolean(TokenXAppearanceKeys.GLASS_ENABLED,true).putFloat(TokenXAppearanceKeys.GLASS_OPACITY,opacity).putFloat(TokenXAppearanceKeys.GLASS_RADIUS,radius).putFloat(TokenXAppearanceKeys.GLASS_BORDER,border).putFloat(TokenXAppearanceKeys.BACKGROUND_DIM,dim).apply(); refresh()
            }) { Text("Readable") }
            Button(onClick = {
                glass=true; opacity=.34f; radius=32f; border=.22f; dim=.12f
                prefs.edit().putBoolean(TokenXAppearanceKeys.GLASS_ENABLED,true).putFloat(TokenXAppearanceKeys.GLASS_OPACITY,opacity).putFloat(TokenXAppearanceKeys.GLASS_RADIUS,radius).putFloat(TokenXAppearanceKeys.GLASS_BORDER,border).putFloat(TokenXAppearanceKeys.BACKGROUND_DIM,dim).apply(); refresh()
            }) { Text("Crystal") }
            Button(onClick = {
                glass=true; opacity=.52f; radius=34f; border=.28f; dim=.20f
                prefs.edit().putBoolean(TokenXAppearanceKeys.GLASS_ENABLED,true).putFloat(TokenXAppearanceKeys.GLASS_OPACITY,opacity).putFloat(TokenXAppearanceKeys.GLASS_RADIUS,radius).putFloat(TokenXAppearanceKeys.GLASS_BORDER,border).putFloat(TokenXAppearanceKeys.BACKGROUND_DIM,dim).apply(); refresh()
            }) { Text("Smoke") }
            Button(onClick = {
                glass=true; opacity=.64f; radius=26f; border=.10f; dim=.26f
                prefs.edit().putBoolean(TokenXAppearanceKeys.GLASS_ENABLED,true).putFloat(TokenXAppearanceKeys.GLASS_OPACITY,opacity).putFloat(TokenXAppearanceKeys.GLASS_RADIUS,radius).putFloat(TokenXAppearanceKeys.GLASS_BORDER,border).putFloat(TokenXAppearanceKeys.BACKGROUND_DIM,dim).apply(); refresh()
            }) { Text("One UI") }
        }
    }
}

@Composable
private fun StudioSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, suffix: String = "", onChange: (Float) -> Unit) {
    Column {
        Text(title + "  " + "%.2f".format(value) + suffix, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun TokenXThemePreview(themePreset: TokenXThemePreset, glass: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.width(190.dp).height(300.dp),
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = if (themePreset == TokenXThemePreset.AMOLED) Color.Black else MaterialTheme.colorScheme.surface.copy(alpha = if (glass) .62f else 1f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("TokenX", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Box(Modifier.fillMaxWidth().height(42.dp).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .72f), RoundedCornerShape(12.dp)))
                Box(Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f), RoundedCornerShape(14.dp)))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    repeat(4) {
                        Box(Modifier.size(if (it == 0) 24.dp else 18.dp).background(if (it == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.55f), CircleShape))
                    }
                }
            }
        }
    }
}
