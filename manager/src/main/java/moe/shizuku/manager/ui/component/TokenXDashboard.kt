package moe.shizuku.manager.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Token
import androidx.compose.material.icons.rounded.SystemSecurityUpdateGood
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.shell.SystemUidProvisioner

@Composable
fun TokenXDashboard(
    running: Boolean,
    uid: Int,
    rootAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var provisionStatus by remember { mutableStateOf<String?>(null) }
    var provisioning by remember { mutableStateOf(false) }

    fun runProvision(action: () -> SystemUidProvisioner.Result) {
        if (provisioning) return
        provisioning = true
        provisionStatus = "Working…"
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
            provisioning = false
        }
    }

    TokenXGlassCard(modifier) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                Text(
                    if (running) "ACTIVE" else "OFFLINE",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (running) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TokenXBackendChip(
                    label = "System",
                    value = if (uid == 1000) "UID 1000" else "Standby",
                    active = uid == 1000,
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

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusLine(Icons.Rounded.Token, "Token Boot", "Session coordination", Modifier.weight(1f))
                StatusLine(Icons.Rounded.Security, "Watchdog", "Existing engine", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusLine(Icons.Rounded.AdminPanelSettings, "LSPosed", "Bridge discovery", Modifier.weight(1f))
                StatusLine(Icons.Rounded.Terminal, "TokenX Router", "Multi-backend", Modifier.weight(1f))
            }

            Text(
                "Provisioning Vault",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "D2-protected privileged payload inspector",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusLine(Icons.Rounded.VerifiedUser, "Serv", "UID 1000", Modifier.weight(1f))
                StatusLine(Icons.Rounded.SystemSecurityUpdateGood, "FOTA", "UID 1000", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusLine(Icons.Rounded.Lock, "D2 Gate", "Dual gate", Modifier.weight(1f))
                StatusLine(Icons.Rounded.Security, "A17 Fix", "Audit", Modifier.weight(1f))
            }
            Text(
                "The audit is read-only: it checks Serv, FOTA, shared-system identity, live FOTA SELinux state, and Receiver Flag Fix presence without launching FOTA or invoking update_engine.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { runProvision { SystemUidProvisioner.verifyProvisionedPayloads() } },
                enabled = rootAvailable && !provisioning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (provisioning) "Scanning Vault…" else "Scan Provisioning Vault")
            }

            Text(
                "System Server Bridge",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Serv.apk provides the current TokenX System Server Bridge for com.vikram.exp (UID 1000). Interactive rish remains isolated through the Android 17 root fallback.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { runProvision { SystemUidProvisioner.verify() } },
                    enabled = rootAvailable && !provisioning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Verify")
                }
            }
            if (!rootAvailable) {
                Text(
                    "Root is required for provisioning and vault verification.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            provisionStatus?.let { status ->
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.padding(start = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
