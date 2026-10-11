package moe.shizuku.manager.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.shell.ManagerUidMigration

@Composable
fun SystemServerProvisionCard() {
    val context = LocalContext.current
    val status by ManagerUidMigration.status.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    TokenXGlassCard {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("TokenX app • System UID 1000", style = MaterialTheme.typography.titleMedium)
            Text("Provision this same TokenX app as UID 1000. System Server communication uses the existing Xposed Binder.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (status.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (status.detail.isNotBlank()) Text(status.detail,
                color = if (status.phase == "failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall)
            Button(onClick = { confirm = true }, enabled = !status.busy && android.os.Process.myUid() != 1000,
                modifier = Modifier.fillMaxWidth()) { Text("Provision this app as UID 1000") }
            OutlinedButton(onClick = { ManagerUidMigration.start(context, "verify") }, enabled = !status.busy,
                modifier = Modifier.fillMaxWidth()) { Text("Verify app UID & Xposed connection") }
            if (android.os.Process.myUid() >= 10000) {
                TextButton(onClick = { ManagerUidMigration.start(context, "restoreSigning") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Restore signing after rollback")
                }
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Provision this TokenX app?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Requires root and a connected TokenX Xposed System Framework module. The matching signed system APK uses the same package name.")
                Text("TokenX will close while a detached process backs up app data, reinstalls this package as UID 1000, restores data and reopens it. Installation failure triggers rollback to the ordinary APK.")
                Text("Android Keystore entries are tied to the old UID and may need to be recreated. Re-enable root authorization and check LSPosed after migration. Soft reboot using Restart Controls, then verify again.",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { confirm = false; ManagerUidMigration.start(context) }) { Text("Provision same app") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
}
