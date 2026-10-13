package moe.shizuku.manager.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.ui.Detail
import moe.shizuku.manager.ui.TokenXSearchCatalog
import moe.shizuku.manager.ui.component.TokenXGlassCard

data class TokenXShortcut(val title: String, val summary: String, val destination: Detail)

@Composable
fun TokenXPageHeading(title: String, summary: String) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TokenXShortcutGroup(title: String, shortcuts: List<TokenXShortcut>, onOpen: (Detail) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 6.dp))
        TokenXGlassCard {
            Column {
                shortcuts.forEachIndexed { index, shortcut ->
                    Row(Modifier.fillMaxWidth().clickable { onOpen(shortcut.destination) }.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(shortcut.title, style = MaterialTheme.typography.titleMedium)
                            Text(shortcut.summary, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (index < shortcuts.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 18.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
                }
            }
        }
    }
}

@Composable
fun TokenXHubScreen(page: String, bottomPadding: Dp, onOpen: (Detail) -> Unit) {
    val groups = when (page) {
        "system" -> listOf(
            "Connections" to listOf(TokenXShortcut("Backend connections", "Root, System, wireless and USB", Detail.CONNECTIONS)),
            "Reliability" to listOf(TokenXShortcut("Startup & recovery", "Boot startup, watchdog and start method", Detail.STARTUP),
                TokenXShortcut("ADB & pairing", "TCP settings, port persistence and pairing", Detail.WIRELESS)),
            "Diagnostics" to listOf(TokenXShortcut("System integration", "Live identity and integration checks", Detail.INTEGRATION),
                TokenXShortcut("Control Center", "Routing, Boot Guardian and framework controls", Detail.TOKENX),
                TokenXShortcut("Device & runtime", "Version, kernel and Android security state", Detail.DEVICE)))
        "style" -> listOf(
            "Personalize" to listOf(TokenXShortcut("Appearance Studio", "Glass, backgrounds, navigation and presets", Detail.APPEARANCE),
                TokenXShortcut("Theme & language", "Brightness, color theme and app language", Detail.THEME)))
        else -> listOf(
            "Run & export" to listOf(TokenXShortcut("Interactive shell", "Open your shell session", Detail.SHELL),
                TokenXShortcut("RISH v3 export", "Matching launcher and DEX for Termux", Detail.TERMINAL)),
            "App controls" to listOf(TokenXShortcut("App operations", "Inspect and manage app operations", Detail.APP_OPS),
                TokenXShortcut("Firewall", "Application network controls", Detail.FIREWALL),
                TokenXShortcut("Autostart", "Application startup controls", Detail.AUTOSTART)),
            "Access & integration" to listOf(TokenXShortcut("Permissions", "Required access and battery optimization", Detail.PERMISSIONS),
                TokenXShortcut("Stealth", "Manager visibility settings", Detail.STEALTH),
                TokenXShortcut("Intents", "Integration actions and commands", Detail.INTENTS)),
            "Help & information" to listOf(TokenXShortcut("Setup guide", "Backend and usage guidance", Detail.GUIDE),
                TokenXShortcut("About TokenX", "Version, package and updates", Detail.ABOUT)))
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, bottomPadding),
        verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            TokenXPageHeading(when (page) { "system" -> "System"; "style" -> "Style"; else -> "Tools" },
                when (page) { "system" -> "Connections, startup and integration"; "style" -> "Make TokenX feel like yours"; else -> "Shells, app controls and useful utilities" })
        }
        items(groups, key = { it.first }) { (title, entries) -> TokenXShortcutGroup(title, entries, onOpen) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenXSearchScreen(onBack: () -> Unit, onOpen: (Detail) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) { TokenXSearchCatalog.search(query) }
    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(title = { Text("CORE search") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0f)),
            windowInsets = WindowInsets(0.dp))
        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Search connections, tools and settings") }, singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, null) })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(if (query.isBlank()) "Explore TokenX" else "${results.size} results",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (results.isEmpty()) item {
                TokenXPageHeading("No matches", "Try root, wireless, RISH, watchdog, theme or permissions.")
            }
            items(results, key = { it.destination.name }) { entry ->
                TokenXShortcutGroup(entry.category, listOf(TokenXShortcut(entry.title, entry.summary, entry.destination)), onOpen)
            }
        }
    }
}
