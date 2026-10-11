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
            Text("Load TokenX inside Android's running System Server using root and the JBCompanion native injector.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (status.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (status.detail.isNotBlank()) Text(status.detail,
                color = if (status.phase == "failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall)
            Button(onClick = { confirm = true }, enabled = !status.busy, modifier = Modifier.fillMaxWidth()) {
                Text("Provision System Server UID 1000")
            }
            TextButton(onClick = {
                context.startActivity(android.content.Intent(context, moe.shizuku.manager.ui.UidTestActivity::class.java))
            }, modifier = Modifier.fillMaxWidth()) { Text("UID 1000 test companion") }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        icon = { Icon(Icons.Outlined.RestartAlt, contentDescription = null) },
        title = { Text("Inject into System Server?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("TokenX will load its native payload into the running System Server, then verify its UID 1000 Binder connection.")
                Text("Requires root and an ARM64 device. Uses the supplied JBCompanion native injector with the TokenX payload.")
                Text("This action does not request a soft reboot or change the Zygisk module state.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = { confirm = false; SystemUidProvisioning.start(context) }) { Text("Inject & verify") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
}
