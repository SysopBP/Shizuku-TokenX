package moe.shizuku.manager.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ui.component.TokenXGlassCard
import moe.shizuku.manager.utils.ShizukuStateMachine

/**
 * TokenX's unified management surface.
 *
 * Only current Shizuku/runtime state is presented as live. Controls for the
 * upcoming native TokenX router, token boot and Xposed bridge are deliberately
 * labelled Preview until their backends are wired.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenXControlCenterScreen(onBack: () -> Unit) {
    val running = ShizukuStateMachine.isRunning()
    val uid = runCatching { rikka.shizuku.Shizuku.getUid() }.getOrDefault(-1)
    val prefs = ShizukuSettings.getPreferences()
    var routerMode by remember { mutableStateOf(prefs.getString("tokenx_router_mode", "Automatic") ?: "Automatic") }
    var rootFirst by remember { mutableStateOf(prefs.getBoolean("tokenx_root_first", true)) }
    var recovery by remember { mutableStateOf(prefs.getBoolean("tokenx_recovery_preview", true)) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("TokenX") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            windowInsets = WindowInsets(0.dp)
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TokenXGlassCard {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Token, null)
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text("Unified Privilege Engine", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Shizuku compatibility + TokenX multi-backend architecture", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AssistChip(onClick = {}, label = { Text(if (running) "ACTIVE" else "OFFLINE") })
                    }
                    HorizontalDivider()
                    Text("Current server  •  " + if (uid >= 0) "UID $uid" else "Not connected")
                }
            }

            SectionTitle("Privilege backends")
            TokenXGlassCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    BackendRow(Icons.Outlined.AdminPanelSettings, "Root", "UID 0", if (uid == 0) "Active now" else "Primary TokenX backend")
                    BackendRow(Icons.Outlined.Security, "System Server", "UID 1000", if (uid == 1000) "Active now" else "Framework-specialized backend")
                    BackendRow(Icons.Outlined.Terminal, "Shell", "UID 2000", if (uid == 2000) "Active now" else "Compatibility / fallback")
                    BackendRow(Icons.Outlined.Extension, "Xposed / LSPosed", "system_server bridge", "Preview • backend not wired yet")
                }
            }

            SectionTitle("Execution Router")
            TokenXGlassCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Router preferences", style = MaterialTheme.typography.titleMedium)
                    Text("These preferences prepare the UI for the native TokenX router. They do not change execution until the router backend lands.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("Automatic", "Capability").forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = routerMode == mode,
                                onClick = { routerMode = mode; prefs.edit().putString("tokenx_router_mode", mode).apply() },
                                shape = SegmentedButtonDefaults.itemShape(index, 2)
                            ) { Text(mode) }
                        }
                    }
                    PreviewSwitch("Prefer Root when capable", "Root-first policy; framework-only calls can route to System Server.", rootFirst) {
                        rootFirst = it; prefs.edit().putBoolean("tokenx_root_first", it).apply()
                    }
                    CapabilityLine("Filesystem / process", "Root → Shell")
                    CapabilityLine("Android framework", "System Server → Root")
                    CapabilityLine("Shell commands", "Root → Shell")
                    CapabilityLine("General Binder compatibility", "Current Shizuku server")
                }
            }

            SectionTitle("Boot Guardian")
            TokenXGlassCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FeatureRow(Icons.Outlined.Token, "Boot Session Token", "Preview • NEW → CLAIMED → BINDER_READY → CONFIRMED")
                    FeatureRow(Icons.Outlined.Bolt, "Early boot handoff", "Preview • System Server → Root → Shell recovery")
                    FeatureRow(Icons.Outlined.MonitorHeart, "Guardian watchdog", "Existing watchdog remains the health/recovery layer")
                    PreviewSwitch("Recovery handoff", "Prepare fallback ownership when the preferred backend cannot confirm.", recovery) {
                        recovery = it; prefs.edit().putBoolean("tokenx_recovery_preview", it).apply()
                    }
                }
            }

            SectionTitle("Native TokenX API")
            TokenXGlassCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FeatureRow(Icons.Outlined.Api, "Capability discovery", "Preview • apps ask what the active backends can do")
                    FeatureRow(Icons.Outlined.Route, "Per-capability routing", "Preview • choose backend by operation, not one global mode")
                    FeatureRow(Icons.Outlined.Code, "Root execution", "Preview • explicit UID 0 execution path")
                    FeatureRow(Icons.Outlined.AdminPanelSettings, "Framework operations", "Preview • System Server / Xposed path")
                    FeatureRow(Icons.Outlined.Link, "Shizuku compatibility", "Preserved • existing Binder model stays intact")
                }
            }

            SectionTitle("Interface")
            TokenXGlassCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeatureRow(Icons.Outlined.Palette, "SESL + TokenX Glass", "tribalfs SESL foundation active")
                    FeatureRow(Icons.Outlined.DarkMode, "AMOLED / custom backgrounds", "Appearance Studio")
                    FeatureRow(Icons.Outlined.Tune, "Editable glass surfaces", "Opacity, radius, border and background controls")
                }
            }
            Text(
                "Preview labels are intentional: TokenX will only show a backend as active after the implementation can verify it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
}

@Composable private fun BackendRow(icon: androidx.compose.ui.graphics.vector.ImageVector, name: String, uid: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold)
            Text("$uid • $detail", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun CapabilityLine(label: String, route: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(route, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable private fun PreviewSwitch(title: String, summary: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
