package moe.shizuku.manager.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.launch
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ui.component.TokenXGlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneUIXLabsScreen(onBack: () -> Unit) {
    val prefs = ShizukuSettings.getPreferences()
    val scope = rememberCoroutineScope()
    var master by remember { mutableStateOf(prefs.getBoolean("tokenx_oneuix_labs", false)) }
    var status by remember { mutableStateOf(prefs.getBoolean("tokenx_oneuix_statusbar", false)) }
    var qs by remember { mutableStateOf(prefs.getBoolean("tokenx_oneuix_qs", false)) }
    var notifications by remember { mutableStateOf(prefs.getBoolean("tokenx_oneuix_notifications", false)) }
    var framework by remember { mutableStateOf(prefs.getBoolean("tokenx_oneuix_framework", false)) }

    LabsScaffold("OneUIX Labs", onBack) {
        Text("Samsung / SystemUI experiments", style = MaterialTheme.typography.titleMedium)
        Text("Isolated from TokenX privilege routing. All experiments are opt-in and fail-open.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LabSwitch("Enable OneUIX Labs", "Master gate", master) {
            master = it; prefs.edit().putBoolean("tokenx_oneuix_labs", it).apply()
            scope.launch { Shell.cmd("setprop persist.tokenx.labs.oneuix " + if (it) "1" else "0").exec() }
        }
        HorizontalDivider()
        LabSwitch("Status bar", "Bluetooth icon restoration is the first active One UI 9 experiment.", status) {
            status = it; prefs.edit().putBoolean("tokenx_oneuix_statusbar", it).apply()
            scope.launch { Shell.cmd("setprop persist.tokenx.labs.statusbar " + if (it) "1" else "0").exec() }
        }
        LabSwitch("Quick Settings", "Staged; no hook activated yet.", qs) { qs = it; prefs.edit().putBoolean("tokenx_oneuix_qs", it).apply() }
        LabSwitch("Notifications", "Staged; no hook activated yet.", notifications) { notifications = it; prefs.edit().putBoolean("tokenx_oneuix_notifications", it).apply() }
        LabSwitch("Framework", "Highest-risk staging area; no framework hook activated yet.", framework) { framework = it; prefs.edit().putBoolean("tokenx_oneuix_framework", it).apply() }
        Text("Architecture inspired by SoClear/OneUIX (AGPL-3.0).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorePatchLabsScreen(onBack: () -> Unit) {
    val prefs = ShizukuSettings.getPreferences()
    var master by remember { mutableStateOf(prefs.getBoolean("tokenx_corepatch_labs", false)) }
    var downgrade by remember { mutableStateOf(prefs.getBoolean("tokenx_corepatch_downgrade", false)) }

    LabsScaffold("CorePatch Labs", onBack) {
        Text("Package Manager experiments", style = MaterialTheme.typography.titleMedium)
        Text("Separate from OneUIX and TokenX privilege routing. OFF by default.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LabSwitch("Enable CorePatch Labs", "Master gate for package-manager experiments.", master) {
            master = it; prefs.edit().putBoolean("tokenx_corepatch_labs", it).apply()
        }
        HorizontalDivider()
        LabSwitch("Allow downgrade", "UI staging only in this build; no Package Manager hook is active yet.", downgrade) {
            downgrade = it; prefs.edit().putBoolean("tokenx_corepatch_downgrade", it).apply()
        }
        Text("Signature and verification bypasses are intentionally not enabled by this foundation.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("CorePatch-derived code will retain GPL-2.0 attribution and licensing.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabsScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            windowInsets = WindowInsets(0.dp)
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TokenXGlassCard { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
        }
    }
}

@Composable
private fun LabSwitch(title: String, detail: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
