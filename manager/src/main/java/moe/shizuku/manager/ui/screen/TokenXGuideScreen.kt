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
        "Root requirements" to "For the full TokenX setup, use KernelSU or a compatible KernelSU-based root solution and grant TokenX superuser access. KernelSU is required for installing and running the TokenX KernelSU modules used by the System Server and boot integration. TokenX does not require root for every Shizuku feature, but Root mode, module provisioning and the System Server path do.",
        "Magic Mount / metamodule" to "KernelSU uses a metamodule to provide systemless mounting when a module places files under its system directory. Install one compatible metamodule before TokenX modules that require systemless mounts. Meta Magic Mount RS is one supported option for Magic Mount-style systemless mounting. Keep only one metamodule active at a time, reboot after installing or changing it, and verify the TokenX module mounts before enabling System Server integration.",
        "First-time setup" to "For the full rooted setup: install and configure KernelSU first. If the TokenX modules require systemless mounts, install a compatible metamodule such as Meta Magic Mount RS and reboot. Then install the TokenX KernelSU modules, reboot again, and grant TokenX superuser access when prompted. After the root and module layer is working, configure your preferred TokenX start method and allow battery/background operation when you depend on Start on boot or Watchdog. LSPosed is only required for features that explicitly use the Xposed/System Server integration.",
        "Apps tab" to "The Apps area controls which compatible applications may use Shizuku. Grant access only to applications you trust. TokenX also provides management views for permissions, app-ops and other system-controlled app state; availability depends on the active backend and Android version.",
        "Root mode" to "Root mode starts the backend with UID 0 through KernelSU or another compatible configured root solution. It is appropriate for operations that genuinely require root. TokenX checks whether root is available and avoids leaving Root selected when the device can no longer provide it. A metamodule does not itself grant root; it supplies the systemless mounting layer used by modules that modify system paths.",
        "Wireless / USB mode" to "Wireless debugging is the normal computer-free Shizuku path on supported Android versions. USB/ADB is useful for initial setup, recovery and devices where wireless debugging is unavailable. TokenX includes options for port persistence and recovery, but Android and vendor restrictions still apply.",
        "System Server Bridge" to "On the tested Android 17 configuration, the provisioned com.vikram.exp package is attached to Android's persistent system process. Verification requires UID/GID 1000 and the SELinux domain u:r:system_server:s0. This is Android's real framework system_server and must be treated as critical infrastructure.",
        "Serv.apk + Serv.dex provisioning" to "The optional System Server setup uses external Serv.apk and Serv.dex files and is not bundled into TokenX. Provision installs/stages the supplied files through root. Verify checks the live com.vikram.exp package and the actual system process rather than relying on the older synthetic com.vikram.shell PackageManager record. Do not repeatedly reprovision a working setup just to test status.",
        "System Server safety" to "Do not use the framework system_server as an interactive terminal host. A crash, deadlock or unsafe fork there can restart Android's framework or reboot the device. TokenX keeps Android 17 interactive rish execution in an isolated UID-1000 worker outside system_server and reserves the framework process for narrowly scoped integration.",
        "rish / terminal" to "rish is the interactive shell client. With the verified System Server backend, TokenX launches an isolated UID-1000 system worker outside Android's persistent system_server process. Normal commands remain UID 1000. On the tested Android 17 build, SettingsProvider reads work as UID 1000 but settings put/delete/reset mutations use a narrowly scoped KernelSU root compatibility route because SettingsProvider requires calling-package attribution the isolated worker does not provide. This does not move the interactive shell into system_server.",
        "Start on boot & Watchdog" to "Start on boot restores the selected supported backend after startup. Watchdog monitors service availability and can recover unexpected service loss. Battery restrictions can prevent reliable background recovery, so follow the in-app permission prompts when using these features.",
        "Permissions & app-ops" to "TokenX can expose Android permissions and app-ops that normally require ADB, Shizuku, system or root authority. A switch is not a promise that every vendor permits the operation: TokenX should read changes back and report failures rather than pretending a refused platform operation succeeded.",
        "Diagnostics" to "Use diagnostics to establish identity before assuming a backend is privileged. Useful facts include UID/GID, SELinux context, package state, active start method and service availability. System Server verification specifically checks the live package, system/1000 process and system_server SELinux domain.",
        "Troubleshooting" to "If a feature fails, first identify the active backend and whether Shizuku, root and any required LSPosed integration are running. For boot or service failures collect logs before repeatedly changing configuration. If the phone becomes unstable after a System Server experiment, return to the known working Root or Shell path before making further changes.",
        "Recovery principles" to "Keep a known-good build available. Build 173 is the verified UID-1000 rish baseline for the current Android 17 test configuration. Avoid killing system_server during testing. Do not force-stop or repeatedly modify the System Server companion when verification already passes. Keep interactive commands outside system_server; use root compatibility routing only where a platform-specific operation requires it.",
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
