package moe.shizuku.manager.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.ui.component.TokenXGlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenXGuideScreen(onBack: () -> Unit) {
    val sections = listOf(
        "What is TokenX?" to "Shizuku TKN Boot / TokenX is an advanced Android privilege and service-management platform. It extends Shizuku with multiple start and execution paths, boot recovery, diagnostics, app administration and experimental system integration. Its goal is to give the device owner one controlled place to choose the privilege level appropriate for a task instead of treating root, ADB shell and Android framework access as the same thing.",
        "Intended use" to "TokenX is intended for device owners, developers and advanced Android users who need Shizuku services, privileged app administration, diagnostics, automation, testing and controlled access to Android system APIs. Typical uses include authorizing compatible apps, managing permissions and app-ops, inspecting system state, maintaining Shizuku across boots, troubleshooting Android services and testing software on devices you administer.",
        "Security model" to "TokenX is not intended to give arbitrary applications silent privileged access. Apps still require authorization. Root access remains controlled by the root manager, and privileged TokenX interfaces should remain private and authenticated. Treat System Server access as a high-trust capability.",
        "Privilege backends" to "Wireless and USB start through Android debugging and normally operate with shell privileges. Root uses the device root solution such as KernelSU. System Server is a separate experimental path for operations that specifically require Android framework identity. Choose the least-privileged backend that can perform the job.",
        "First-time setup" to "Grant the permissions requested by TokenX, configure your preferred start method, and allow battery/background operation when you depend on Start on boot or Watchdog. Root users should approve TokenX in their root manager. LSPosed is only required for features that explicitly use the Xposed/System Server integration.",
        "Apps tab" to "The Apps area controls which compatible applications may use Shizuku. Grant access only to applications you trust. TokenX also provides management views for permissions, app-ops and other system-controlled app state; availability depends on the active backend and Android version.",
        "Root mode" to "Root mode starts the backend with UID 0 through the configured root solution. It is appropriate for operations that genuinely require root. TokenX checks whether root is available and avoids leaving Root selected when the device can no longer provide it.",
        "Wireless / USB mode" to "Wireless debugging is the normal computer-free Shizuku path on supported Android versions. USB/ADB is useful for initial setup, recovery and devices where wireless debugging is unavailable. TokenX includes options for port persistence and recovery, but Android and vendor restrictions still apply.",
        "System Server Bridge" to "On the tested Android 17 configuration, the provisioned com.vikram.exp package is attached to Android's persistent system process. Verification requires UID/GID 1000 and the SELinux domain u:r:system_server:s0. This is Android's real framework system_server and must be treated as critical infrastructure.",
        "Serv.apk + Serv.dex provisioning" to "The optional System Server setup uses external Serv.apk and Serv.dex files and is not bundled into TokenX. Provision installs/stages the supplied files through root. Verify checks the live com.vikram.exp package and the actual system process rather than relying on the older synthetic com.vikram.shell PackageManager record. Do not repeatedly reprovision a working setup just to test status.",
        "System Server safety" to "Do not use the framework system_server as an interactive terminal host. A crash, deadlock or unsafe fork there can restart Android's framework or reboot the device. TokenX deliberately keeps Android 17 interactive rish execution isolated through the root fallback while reserving System Server integration for narrowly scoped framework operations.",
        "rish / terminal" to "rish is the interactive shell client. On Android 17, when TokenX detects the System Server backend, interactive shell execution is routed to the safer root fallback rather than forking a shell inside system_server. This separation is intentional.",
        "Start on boot & Watchdog" to "Start on boot restores the selected supported backend after startup. Watchdog monitors service availability and can recover unexpected service loss. Battery restrictions can prevent reliable background recovery, so follow the in-app permission prompts when using these features.",
        "Permissions & app-ops" to "TokenX can expose Android permissions and app-ops that normally require ADB, Shizuku, system or root authority. A switch is not a promise that every vendor permits the operation: TokenX should read changes back and report failures rather than pretending a refused platform operation succeeded.",
        "Diagnostics" to "Use diagnostics to establish identity before assuming a backend is privileged. Useful facts include UID/GID, SELinux context, package state, active start method and service availability. System Server verification specifically checks the live package, system/1000 process and system_server SELinux domain.",
        "Troubleshooting" to "If a feature fails, first identify the active backend and whether Shizuku, root and any required LSPosed integration are running. For boot or service failures collect logs before repeatedly changing configuration. If the phone becomes unstable after a System Server experiment, return to the known working Root or Shell path before making further changes.",
        "Recovery principles" to "Keep a known-good build available. Avoid killing system_server during testing. Do not force-stop or repeatedly modify the System Server companion when verification already passes. Preserve the Android 17 rish-to-root fallback because it isolates interactive commands from the framework process.",
        "Upstream Shizuku" to "TokenX builds on Shizuku and a later Shizuku fork. Original Shizuku concepts, API behavior and compatible-app authorization still apply. TokenX-specific behavior such as Token Boot, the multi-backend controls and System Server experiments is documented here because upstream documentation cannot describe those additions.",
        "Privacy & responsibility" to "TokenX is powerful local device-management software. Review commands and app grants before applying them, keep privileged interfaces private, and use the software only on devices and applications you are authorized to administer."
    )

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("TokenX Guide") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Purpose, setup, usage and safety",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(sections.size) { index ->
                val (title, body) = sections[index]
                TokenXGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
