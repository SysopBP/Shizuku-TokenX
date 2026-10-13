package moe.shizuku.manager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ui.component.TokenXBackground
import moe.shizuku.manager.ui.screen.*
import moe.shizuku.manager.ui.theme.*

private val NavigationSaver = listSaver<TokenXNavigationState, String>(
    save = { listOf(it.current) + it.history },
    restore = { saved -> TokenXNavigationState(saved.first().takeIf(::validTokenXRoute) ?: "home",
        saved.drop(1).filter(::validTokenXRoute)) }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShizukuApp() {
    ShizukuTheme {
        TokenXBackground {
            Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
                var navigation by rememberSaveable(stateSaver = NavigationSaver) { mutableStateOf(TokenXNavigationState()) }
                val stateHolder = rememberSaveableStateHolder()
                val detail = navigation.current.takeIf { it.startsWith("detail:") }
                    ?.let { Detail.valueOf(it.substringAfter(':')) }
                val open: (Detail) -> Unit = { navigation = navigation.navigate(it.route()) }
                val back: () -> Unit = { navigation = navigation.back() }
                BackHandler(enabled = navigation.current != "home" || navigation.history.isNotEmpty(), onBack = back)
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))) {
                    Box(Modifier.align(Alignment.TopCenter).widthIn(max = 600.dp).fillMaxSize()) {
                        val prefs = ShizukuSettings.getPreferences()
                        val bottomPadding = dockReservedHeight(
                            prefs.getFloat(TokenXAppearanceKeys.FLOATING_BAR_HEIGHT, 64f),
                            prefs.getFloat(TokenXAppearanceKeys.FLOATING_BAR_BOTTOM_GAP, 8f), LocalDensity.current.fontScale).dp +
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        stateHolder.SaveableStateProvider(navigation.current) {
                            if (detail != null) {
                                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
                                    // These existing editors do not have their own navigation bar.
                                    if (detail in listOf(Detail.CONNECTIONS, Detail.INTEGRATION, Detail.DEVICE, Detail.APPEARANCE)) {
                                        TopAppBar(title = { Text(detailTitle(detail)) }, navigationIcon = {
                                            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                                        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                                            windowInsets = WindowInsets(0.dp))
                                    }
                                    Box(Modifier.weight(1f)) {
                                        when (detail) {
                                            Detail.CONNECTIONS -> HomeScreen(16.dp, HomeSection.CONNECTIONS, open)
                                            Detail.INTEGRATION -> HomeScreen(16.dp, HomeSection.INTEGRATION, open)
                                            Detail.DEVICE -> HomeScreen(16.dp, HomeSection.DEVICE, open)
                                            Detail.STARTUP -> SettingsScreen(16.dp, open, SettingsSection.STARTUP, back)
                                            Detail.WIRELESS -> SettingsScreen(16.dp, open, SettingsSection.WIRELESS, back)
                                            Detail.ACCESS -> SettingsScreen(16.dp, open, SettingsSection.TOOLS, back)
                                            Detail.THEME -> SettingsScreen(16.dp, open, SettingsSection.APPEARANCE, back)
                                            Detail.ABOUT -> SettingsScreen(16.dp, open, SettingsSection.ABOUT, back)
                                            Detail.SEARCH -> TokenXSearchScreen(back, open)
                                            Detail.APP_OPS -> ManageScreen(bottomPadding = 0.dp, onBack = back)
                                            Detail.SHELL -> ShellScreen(bottomPadding = 0.dp, onBack = back)
                                            Detail.FIREWALL -> LabsToggleScreen(AppToggleFeature.FIREWALL, 0.dp, back)
                                            Detail.AUTOSTART -> LabsToggleScreen(AppToggleFeature.AUTOSTART, 0.dp, back)
                                            Detail.STEALTH -> StealthScreen(onBack = back)
                                            Detail.TERMINAL -> TerminalScreen(onBack = back)
                                            Detail.INTENTS -> IntentsScreen(onBack = back)
                                            Detail.PERMISSIONS -> PermissionsScreen(onBack = back)
                                            Detail.APPEARANCE -> AppearanceStudioScreen()
                                            Detail.TOKENX -> TokenXControlCenterScreen(onBack = back)
                                            Detail.GUIDE -> TokenXGuideScreen(onBack = back)
                                        }
                                    }
                                }
                            } else {
                                when (navigation.current) {
                                    "home" -> HomeScreen(bottomPadding, onOpenDetail = open)
                                    "apps" -> AppsScreen(bottomPadding, active = true, warmUp = true)
                                    else -> TokenXHubScreen(navigation.current, bottomPadding, open)
                                }
                            }
                        }
                    }
                    if (detail == null) TokenXDock(navigation.current,
                        onSelect = { navigation = navigation.navigate(it) }, onSearch = { open(Detail.SEARCH) },
                        modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}

private fun detailTitle(detail: Detail) = when (detail) {
    Detail.CONNECTIONS -> "Connections"
    Detail.INTEGRATION -> "System integration"
    Detail.DEVICE -> "Device & runtime"
    Detail.APPEARANCE -> "Appearance Studio"
    else -> detail.name
}

private fun pageIcon(page: TokenXPage): ImageVector = when (page) {
    TokenXPage.HOME -> Icons.Outlined.Home
    TokenXPage.APPS -> Icons.Outlined.Apps
    TokenXPage.SYSTEM -> Icons.Outlined.Bolt
    TokenXPage.TOOLS -> Icons.Outlined.Build
    TokenXPage.STYLE -> Icons.Outlined.Palette
}

@Composable
private fun TokenXDock(selected: String, onSelect: (String) -> Unit, onSearch: () -> Unit, modifier: Modifier) {
    val prefs = ShizukuSettings.getPreferences()
    val height = dockHeight(prefs.getFloat(TokenXAppearanceKeys.FLOATING_BAR_HEIGHT, 64f), LocalDensity.current.fontScale).dp
    val opacity = prefs.getFloat(TokenXAppearanceKeys.FLOATING_BAR_OPACITY, .82f).coerceIn(.20f, 1f)
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val style = runCatching { FloatingBarStyle.valueOf(prefs.getString(TokenXAppearanceKeys.FLOATING_BAR_STYLE, FloatingBarStyle.FROSTED.name)!!) }
        .getOrDefault(FloatingBarStyle.FROSTED)
    val shape = when (prefs.getString(TokenXAppearanceKeys.FLOATING_BAR_SHAPE, FloatingBarShape.ONE_UI.name)) {
        FloatingBarShape.PILL.name -> CircleShape
        FloatingBarShape.SQUIRCLE.name -> RoundedCornerShape(20.dp)
        FloatingBarShape.ROUNDED.name -> RoundedCornerShape(16.dp)
        else -> RoundedCornerShape(24.dp)
    }
    val container = when (style) {
        FloatingBarStyle.CLEAR -> Color.Transparent
        FloatingBarStyle.SOLID -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = opacity)
    }
    val selection = runCatching { FloatingBarSelection.valueOf(prefs.getString(TokenXAppearanceKeys.FLOATING_BAR_SELECTION, FloatingBarSelection.GLASS.name)!!) }
        .getOrDefault(FloatingBarSelection.GLASS)
    val selectedColor = when (selection) {
        FloatingBarSelection.GLASS -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .72f)
        FloatingBarSelection.TILE -> MaterialTheme.colorScheme.secondaryContainer
        FloatingBarSelection.INDICATOR -> MaterialTheme.colorScheme.primary.copy(alpha = .30f)
        FloatingBarSelection.MINIMAL -> Color.Transparent
    }
    val border = prefs.getFloat(TokenXAppearanceKeys.FLOATING_BAR_BORDER, .18f).coerceIn(0f, .5f)
    BoxWithConstraints(modifier.widthIn(max = 600.dp).fillMaxWidth()
        .windowInsetsPadding(WindowInsets.navigationBars)
        .padding(horizontal = 8.dp, vertical = 8.dp + prefs.getFloat(TokenXAppearanceKeys.FLOATING_BAR_BOTTOM_GAP, 8f).coerceIn(0f, 28f).dp)) {
        val coreWidth = if (maxWidth < 340.dp) 48.dp else 56.dp
        val dockWidth = dockWidth(maxWidth.value,
            prefs.getFloat(TokenXAppearanceKeys.FLOATING_BAR_WIDTH, .86f), coreWidth.value).dp
        Row(Modifier.width(dockWidth).align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.weight(1f).height(height), shape = shape, color = container,
                border = BorderStroke(1.dp, (if (dark) Color.White else MaterialTheme.colorScheme.outline).copy(alpha = border)),
                shadowElevation = prefs.getFloat(TokenXAppearanceKeys.FLOATING_BAR_ELEVATION, 8f).coerceIn(0f, 18f).dp) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    TokenXPage.entries.forEach { page ->
                        val active = page.route == selected
                        Surface(onClick = { onSelect(page.route) }, modifier = Modifier.weight(1f).fillMaxHeight().semantics { role = Role.Tab; this.selected = active },
                            shape = shape, color = if (active) selectedColor else Color.Transparent) {
                            Column(Modifier.fillMaxSize().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)) {
                                Icon(pageIcon(page), page.title, Modifier.size(22.dp),
                                    tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(page.title, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Surface(onClick = onSearch, modifier = Modifier.width(coreWidth).height(height), shape = shape,
                color = MaterialTheme.colorScheme.primaryContainer, shadowElevation = 6.dp) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
                    Icon(Icons.Outlined.Search, "Search TokenX", Modifier.size(22.dp))
                    Text("CORE", fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1)
                }
            }
        }
    }
}
