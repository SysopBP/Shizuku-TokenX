package moe.shizuku.manager.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Token
import androidx.compose.material.icons.rounded.SystemSecurityUpdateGood
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.shell.SystemUidProvisioner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenXDashboard(
    running: Boolean,
    uid: Int,
    rootAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val hapticView = LocalView.current
    var provisionStatus by remember { mutableStateOf<String?>(null) }
    var provisioning by remember { mutableStateOf(false) }
    var showVaultDialog by remember { mutableStateOf(false) }
    var dialogTitle by remember { mutableStateOf("System Integration • Live") }
    var dialogMode by remember { mutableStateOf("vault") }
    var showTechnicalConsole by remember { mutableStateOf(false) }
    var showVaultDetails by remember { mutableStateOf(false) }
    var showBridgeDetails by remember { mutableStateOf(false) }
    var bridgeVerified by remember { mutableStateOf(false) }
    var liveStage by remember { mutableStateOf<SystemUidProvisioner.Progress?>(null) }
    LaunchedEffect(provisioning) {
        if (provisioning) {
            while (true) {
                ViewCompat.performHapticFeedback(hapticView, HapticFeedbackConstantsCompat.CONFIRM)
                delay(180L)
                ViewCompat.performHapticFeedback(hapticView, HapticFeedbackConstantsCompat.CONFIRM)
                delay(820L)
            }
        }
    }

    fun runRootAction(title: String, command: String) {
        if (provisioning) return
        provisioning = true
        dialogTitle = title
        dialogMode = "bridge"
        showVaultDialog = true
        provisionStatus = "TOKENX • requesting privileged restart…"
        scope.launch {
            val result = withContext(Dispatchers.IO) { SystemUidProvisioner.runPrivilegedAction(command) }
            provisionStatus = buildString {
                append(if (result.success) "SUCCESS" else "FAILED")
                append(" (exit ")
                append(result.exitCode)
                append(")")
                if (result.output.isNotBlank()) {
                    append("\n")
                    append(result.output.trim())
                }
            }
            provisioning = false
        }
    }

    fun runProvision(title: String = "System Integration • Live", action: () -> SystemUidProvisioner.Result) {
        if (provisioning) return
        provisioning = true
        dialogTitle = title
        dialogMode = if (title.startsWith("System Bridge")) "bridge" else "vault"
        showVaultDialog = true
        provisionStatus = "D2 Gate • checking security boundary…"
        scope.launch {
            val result = withContext(Dispatchers.IO) { action() }
            provisionStatus = buildString {
                append(if (result.success) "SUCCESS" else "FAILED")
                append(" (exit ")
                append(result.exitCode)
                append(")")
                if (result.output.isNotBlank()) {
                    append("\n")
                    append(result.output.trim())
                }
            }
            if (dialogMode == "bridge") bridgeVerified = result.success
            provisioning = false
            ViewCompat.performHapticFeedback(hapticView, if (result.success) HapticFeedbackConstantsCompat.CONFIRM else HapticFeedbackConstantsCompat.REJECT)
        }
    }

    val modeColor = when {
        !provisioning && provisionStatus?.startsWith("SUCCESS") == true -> MaterialTheme.colorScheme.tertiary
        !provisioning && provisionStatus?.startsWith("FAILED") == true -> MaterialTheme.colorScheme.error
        dialogMode == "bridge" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }

    if (showVaultDialog) {
        val pulse = rememberInfiniteTransition(label = "token-pulse")
        val pulseScale by pulse.animateFloat(
            initialValue = .88f,
            targetValue = 1.72f,
            animationSpec = infiniteRepeatable(
                animation = tween(620, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse-scale"
        )
        BasicAlertDialog(onDismissRequest = { if (!provisioning) showVaultDialog = false }) {
            TokenXGlassCard {
                Column(
                    Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(104.dp).clickable { showTechnicalConsole = !showTechnicalConsole },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.size(64.dp)
                                .graphicsLayer {
                                    scaleX = pulseScale
                                    scaleY = pulseScale
                                    alpha = (1.72f - pulseScale).coerceIn(.28f, .92f)
                                }
                                .border(3.dp, modeColor, CircleShape)
                        )
                        if (provisioning) CircularProgressIndicator(Modifier.size(48.dp), color = modeColor, strokeWidth = 3.dp)
                        else Icon(Icons.Rounded.VerifiedUser, null, Modifier.size(44.dp), tint = modeColor)
                    }
                    Text(
                        if (provisioning) dialogTitle else dialogTitle.replace(" • Live", " • Complete"),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = modeColor
                    )
                    Text(
                        if (provisioning) "TOKEN PULSE • SECURE CHAIN ACTIVE" else if (provisionStatus?.startsWith("SUCCESS") == true) "TOKENX • CHAIN VERIFIED" else "TOKENX • CHECK FAILED",
                        style = MaterialTheme.typography.labelSmall,
                        color = modeColor
                    )
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        VaultChainRow("D2 Gate", stageLabel(SystemUidProvisioner.Stage.D2_GATE, liveStage, provisioning, "OPEN"), modeColor)
                        VaultChainRow("BridgeTest", stageLabel(SystemUidProvisioner.Stage.BRIDGE_UID, liveStage, provisioning, "REFERENCE • UID 1000"), modeColor)
                        if (dialogMode == "bridge") VaultChainRow("System Bridge", if (provisioning) "HANDSHAKE" else if (provisionStatus?.startsWith("SUCCESS") == true) "VERIFIED" else "FAILED", modeColor)
                    }
                    HorizontalDivider()
                    if (showTechnicalConsole) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .35f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("TOKENX LIVE CONSOLE", style = MaterialTheme.typography.labelMedium, color = modeColor)
                                Text(
                                    provisionStatus ?: "Preparing secure audit…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Text(
                            "Tap the pulse to reveal technical details",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!provisioning) TextButton(onClick = { showVaultDialog = false }) { Text("Done") }
                }
            }
        }
    }

    TokenXGlassCard(modifier) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Token, contentDescription = null)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        "TKN Boot Privilege Engine",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (running) "Privilege engine active" else "Privilege engine stopped",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(Modifier.width(72.dp), contentAlignment = Alignment.CenterEnd) {
                    Text(
                        if (running) "ACTIVE" else "OFFLINE",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (running) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TokenXBackendChip(
                    label = "System",
                    value = if (uid == 1000) "UID 1000" else if (bridgeVerified) "Ready" else "Standby",
                    active = uid == 1000 || bridgeVerified,
                    modifier = Modifier.weight(1f)
                )
                TokenXBackendChip(
                    label = "Root",
                    value = if (uid == 0) "UID 0" else if (rootAvailable) "Ready" else "Unavailable",
                    active = uid == 0,
                    modifier = Modifier.weight(1f)
                )
                TokenXBackendChip(
                    label = "Shell",
                    value = if (uid == 2000) "UID 2000" else "Fallback",
                    active = uid == 2000,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusLine(Icons.Rounded.Token, "Token Boot", "Session coordination", Modifier.weight(1f))
                StatusLine(Icons.Rounded.Security, "Watchdog", "Existing engine", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusLine(Icons.Rounded.AdminPanelSettings, "LSPosed", "Bridge discovery", Modifier.weight(1f))
                StatusLine(Icons.Rounded.Terminal, "TokenX Router", "Multi-backend", Modifier.weight(1f))
            }

            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            Text(
                "Restart Controls",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TokenXGlassButton(
                    onClick = { runRootAction("Soft Reboot • Live", "setprop ctl.restart zygote") },
                    enabled = rootAvailable && !provisioning,
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = null, Modifier.size(18.dp))
                        Text("Soft Reboot", style = MaterialTheme.typography.labelLarge)
                    }
                }
                TokenXGlassButton(
                    onClick = { runRootAction("System UI Restart • Live", "pkill -TERM -f com.android.systemui") },
                    enabled = rootAvailable && !provisioning,
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, Modifier.size(18.dp))
                        Text("System UI", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            Text(
                "System Integration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "D2 protection • BridgeTest reference • LSPosed backend",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusLine(Icons.Rounded.VerifiedUser, "BridgeTest", "Reference", Modifier.weight(1f))
                StatusLine(Icons.Rounded.Extension, "LSPosed RPC", "System", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusLine(Icons.Rounded.Lock, "D2 Gate", "Dual gate", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusLine(Icons.Rounded.Security, "Compatibility", "TokenX native", Modifier.weight(1f))
            }
            TextButton(
                onClick = { showVaultDetails = !showVaultDetails },
                modifier = Modifier.align(Alignment.Start)
            ) {
                Icon(Icons.Rounded.Info, contentDescription = null, Modifier.size(16.dp))
                Text(if (showVaultDetails) " Hide details" else " Details")
            }
            if (showVaultDetails) {
                Text(
                    "Read-only audit of the retained D2 boundary and BridgeTest identity reference. FOTA and Legacy DEX have been removed. Receiver Compatibility is TokenX-native and is no longer part of provisioning.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TokenXGlassButton(
                onClick = { runProvision("System Integration • Live") {
                    SystemUidProvisioner.verifyProvisionedPayloads { progress ->
                        liveStage = progress
                        provisionStatus = "${progress.stage.name} • ${progress.state.name}\n${progress.detail}"
                    }
                } },
                enabled = rootAvailable && !provisioning,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, Modifier.size(18.dp))
                    Text(if (provisioning) "Checking Integration…" else "Check System Integration", style = MaterialTheme.typography.labelLarge)
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            Text(
                "System Server RPC",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "TokenX uses the LSPosed _TKN Binder RPC inside the real system_server. BridgeTest is retained only as an identity reference.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TokenXGlassButton(
                    onClick = { runProvision("System Bridge Verify • Live") { SystemUidProvisioner.verify() } },
                    enabled = rootAvailable && !provisioning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.VerifiedUser, contentDescription = null, Modifier.size(18.dp))
                        Text("Verify System Reference", style = MaterialTheme.typography.labelLarge)
                    }
                }
            TextButton(
                onClick = { showBridgeDetails = !showBridgeDetails },
                modifier = Modifier.align(Alignment.Start)
            ) {
                Icon(Icons.Rounded.Info, contentDescription = null, Modifier.size(16.dp))
                Text(if (showBridgeDetails) " Hide details" else " Details")
            }
            if (showBridgeDetails) {
                Text(
                    "BridgeTest remains a temporary UID 1000 identity reference. The live System Server backend is the LSPosed _TKN RPC; normal Shizuku remains the UID 0/root backend.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!rootAvailable) {
                Text(
                    "Root is required for provisioning and vault verification.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun TokenXBackendChip(
    label: String,
    value: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(40.dp), contentAlignment = Alignment.CenterStart) {
            Icon(icon, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(
            Modifier
                .padding(start = 8.dp)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    value,
                    Modifier.padding(start = 5.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


@Composable
private fun VaultLiveRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    detail: String,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


@Composable
private fun VaultChainRow(
    label: String,
    state: String,
    accent: androidx.compose.ui.graphics.Color,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).border(2.dp, accent, CircleShape))
        Text(label, Modifier.padding(start = 10.dp).weight(1f), fontWeight = FontWeight.Medium)
        Text(state, style = MaterialTheme.typography.labelSmall, color = accent)
    }
}


private fun stageLabel(
    stage: SystemUidProvisioner.Stage,
    current: SystemUidProvisioner.Progress?,
    running: Boolean,
    complete: String,
): String = when {
    !running -> complete
    current == null -> "WAITING"
    current.stage == stage -> current.state.name
    current.stage.ordinal > stage.ordinal -> "VERIFIED"
    else -> "WAITING"
}
