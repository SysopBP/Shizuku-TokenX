package moe.shizuku.manager.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.shell.SystemUidProvisioning

@Composable
fun SystemServerProvisionCard() {
    val context = LocalContext.current
    val status by SystemUidProvisioning.status.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    TokenXGlassCard {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("System Server injection", style = MaterialTheme.typography.titleMedium)
            Text("Load TokenX inside Android's System Server using KernelSU and a Zygisk API v4 runtime.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (status.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (status.detail.isNotBlank()) Text(status.detail,
                color = if (status.phase == "failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall)
            Button(onClick = { confirm = true }, enabled = !status.busy, modifier = Modifier.fillMaxWidth()) {
                Text("Provision System Server UID 1000")
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        icon = { Icon(Icons.Outlined.RestartAlt, contentDescription = null) },
        title = { Text("Provision & soft reboot?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Android will soft reboot after TokenX's injection module is installed and verified. Open apps will close and unsaved work may be lost.")
                Text("Save your work before continuing. TokenX will check KernelSU and Zygisk first, then verify the new UID 1000 backend after Android restarts.")
                Text("If preparation or installation fails, TokenX will stop without rebooting.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = { confirm = false; SystemUidProvisioning.start(context) }) { Text("Provision & soft reboot") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
}
