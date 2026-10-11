package moe.shizuku.manager.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.shell.UidTestCompanion

class UidTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UidTestCompanion.initialize(this)
        setContent {
            MaterialTheme {
                val status by UidTestCompanion.status.collectAsState()
                var confirmation by remember { mutableStateOf<String?>(null) }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("UID 1000 test companion", style = MaterialTheme.typography.headlineMedium)
                        Text("A disposable app for comparing ordinary and System UID permissions. TokenX keeps its current UID and Root/System routes.")
                        Text("Requires root. Provisioning also requires Android 16+ and TokenX's verified native System Server injection.", style = MaterialTheme.typography.bodySmall)
                        if (status.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Card(Modifier.fillMaxWidth()) { Text(status.detail, Modifier.padding(16.dp),
                            color = if (status.phase == "failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) }
                        Button({ confirmation = "ordinary" }, Modifier.fillMaxWidth(), enabled = !status.busy) { Text("Install ordinary test app") }
                        Button({ confirmation = "provision" }, Modifier.fillMaxWidth(), enabled = !status.busy) { Text("Provision test companion UID 1000") }
                        OutlinedButton({ UidTestCompanion.start(this@UidTestActivity, "verify") }, Modifier.fillMaxWidth(), enabled = !status.busy) { Text("Verify actual UID & Binder handshake") }
                        OutlinedButton({ confirmation = "remove" }, Modifier.fillMaxWidth(), enabled = !status.busy) { Text("Remove & restore signing state") }
                        Text("Reopen this screen after a manual reboot and verify again. UID 1000 app identity and System Server injection are checked separately.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                confirmation?.let { operation ->
                    AlertDialog(onDismissRequest = { confirmation = null },
                        title = { Text(when (operation) { "provision" -> "Provision the test companion?"; "remove" -> "Remove & restore?"; else -> "Install the ordinary test app?" }) },
                        text = { Text(when (operation) {
                            "provision" -> "This experiment grants the dedicated test certificate access to android.uid.system, saves a recovery record, and replaces only the test companion. Its test data will be erased. Signing trust persists until you remove and restore it. Other apps signed with that test certificate could also join UID 1000. No reboot is requested."
                            "remove" -> "The test companion and its data will be deleted. TokenX will restore the recorded platform signing lineage only after removal is verified. Recovery stops if another app uses the test certificate or the lineage changed independently."
                            else -> "TokenX will use your root grant to install its source-built ordinary companion. It has no UID 1000 privilege until you explicitly provision it."
                        }) },
                        confirmButton = { Button({ confirmation = null; UidTestCompanion.start(this@UidTestActivity, operation) }) { Text("Continue") } },
                        dismissButton = { TextButton({ confirmation = null }) { Text("Cancel") } })
                }
            }
        }
    }
}
