package moe.shizuku.manager.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.ui.component.TokenXGlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenXGuideScreen(onBack: () -> Unit) {
    val sections = listOf(
        "What is TokenX?" to "TokenX is a Shizuku-compatible unified multi-backend privilege engine for Android. It separates authorization from execution and can expose independent Root / UID 0, System / UID 1000 and Shell / UID 2000 routes. Root and System can remain bound at the same time while the default route is selected independently. TokenX adds RISH Protocol v3, backend-aware diagnostics, persistent app authorization, D2-aware startup, System Server RPC integration and power-user administration tools.",
        "Intended use" to "TokenX is intended for device owners, developers and advanced Android users who need Shizuku services, privileged app administration, diagnostics, automation, testing and controlled access to Android system APIs. Typical uses include authorizing compatible apps, managing permissions and app-ops, inspecting system state, maintaining Shizuku across boots, troubleshooting Android services and testing software on devices you administer.",
        "Security model" to "TokenX is not intended to give arbitrary applications silent privileged access. Apps still require authorization. Root access remains controlled by the root manager, and privileged TokenX interfaces should remain private and authenticated. Treat System Server access as a high-trust capability.",
        "Privilege backends" to "TokenX tracks Root / UID 0, System / UID 1000 and Shell / UID 2000 as independent execution routes. Root and System may be online concurrently; changing the default route does not tear either backend down. Wireless debugging, USB debugging and PC/ADB remain UID-2000 Shell paths. System UID-1000 interactive execution is isolated from Android's persistent system_server process.",
        "System UID 1000 vs System Server RPC" to "./rish --system selects the TokenX SYSTEM_SERVER backend and has been physically verified to return UID 1000. That does not mean the interactive command is running inside Android's persistent system_server process. Interactive System execution remains isolated. Framework-side operations use the separate LSPosed/Xposed _TKN RPC, whose system_server identity is verified independently.",
        "Dual backend operation" to "TokenX can keep Root and System bound simultaneously. The dashboard reports each Binder, heartbeat and client state independently, while Default controls where an unpinned request is routed. Default is routing policy, not backend lifecycle.",
        "RISH Protocol v3" to "The packaged TokenX RISH client supports explicit routes: ./rish --root requests UID 0, ./rish --system requests UID 1000, ./rish --shell requests UID 2000, and plain ./rish follows the configured default. Protocol v3 preserves the manager-classified backend handoff so Android 17 does not lose explicit route identity during the Binder transport.",
        "Global Connections" to "Global Connections exposes the TokenX multi-backend transport plus independent Root and System Binder health, heartbeat, active-client counts and RISH commands. Wireless debugging, USB debugging and PC/ADB are shown separately as UID-2000 routes. Device & Runtime expands the diagnostic view.",
        "New themes • MIUIX + Material" to "TokenX includes MIUIX and Material UI/theme support alongside its configurable glass and floating surfaces. Appearance is intentionally separate from privilege state: changing the design system does not reconnect, replace or alter Root, System or Shell backends.",
        "Root requirements" to "For the full TokenX setup, use KernelSU or a compatible KernelSU-based root solution and grant TokenX superuser access. KernelSU is required for installing and running the TokenX KernelSU modules used by the System Server and boot integration. TokenX does not require root for every Shizuku feature, but Root mode, module provisioning and the System Server path do.",
        "Magic Mount / metamodule" to "KernelSU uses a metamodule to provide systemless mounting when a module places files under its system directory. Install one compatible metamodule before TokenX modules that require systemless mounts. Meta Magic Mount RS is one supported option for Magic Mount-style systemless mounting. Keep only one metamodule active at a time, reboot after installing or changing it, and verify the TokenX module mounts before enabling System Server integration.",
        "First-time setup" to "For the full rooted setup: install and configure KernelSU first. If the TokenX modules require systemless mounts, install a compatible metamodule such as Meta Magic Mount RS and reboot. Then install the TokenX KernelSU modules, reboot again, and grant TokenX superuser access when prompted. After the root and module layer is working, configure your preferred TokenX start method and allow battery/background operation when you depend on Start on boot or Watchdog. LSPosed is only required for features that explicitly use the Xposed/System Server integration.",
        "QUICK START • Full rooted installation" to "1 • Install and configure KernelSU.  2 • Install a compatible systemless-mount layer when required.  3 • Install TokenX and grant superuser access.  4 • Flash the current TokenX D2 module.  5 • Enable the TokenX LSPosed module for system_server.  6 • Open TokenX and run Check System Integration.  7 • Verify the System Server RPC/reference status.  8 • Reboot normally and leave D2 locked briefly to prove the protected startup gate.  9 • Unlock D2 and confirm Root, D2 and the LSPosed _TKN RPC recover cleanly.",
        "STEP 1 • KernelSU" to "Confirm KernelSU is working before installing the bridge. From Termux run su -c id. A working rooted setup should report uid=0(root), with the KernelSU SELinux domain on supported configurations. Grant TokenX superuser access when requested. Do not continue with System Server provisioning until root itself is reliable.",
        "STEP 2 • Systemless mount layer" to "If your KernelSU configuration requires a metamodule for systemless /system mounts, install one compatible metamodule before the TokenX bridge. Keep only one competing mount metamodule active. Reboot after installing or changing the mount layer, then confirm KernelSU still works.",
        "STEP 3 • Install TokenX" to "Install the current TokenX APK normally, open it, complete its setup and grant the required root authorization. The complete rooted configuration can expose Root, System UID and Shizuku-compatible paths. System UID becomes available when the live Shizuku-compatible binder reports UID 1000.",
        "STEP 4 • System Server Integration" to "In KernelSU Modules install the current TokenX D2 module when using the protected D2 startup boundary. Enable the TokenX LSPosed module for system_server. The live framework backend is the LSPosed _TKN RPC.",
        "STEP 5 • Reboot + D2 gate" to "Perform a normal reboot after installing the bridge. If Kiosk D2 Guardian is installed, let its lock screen appear normally and unlock D2 before expecting TokenX provisioning to finish. The D2 gate is designed to keep TokenX from bypassing the boot lock. Devices without D2 use the normal non-D2 path.",
        "STEP 6 • Provisioning Vault" to "Before the first reboot test, open TokenX and run Check System Integration. The audit verifies the D2 boundary. Receiver Compatibility is TokenX-native and outside the provisioning chain. Verify the LSPosed _TKN System Server RPC and live UID-1000 binder identity separately.",
        "STEP 7 • Configure rish" to "Export or place rish and rish_shizuku.dex in the terminal app private directory. Make rish executable and the dex non-writable as required by modern Android app_process restrictions. TokenX RISH protocol v3 supports explicit independent routes: ./rish --root for UID 0, ./rish --system for UID 1000, and ./rish --shell for UID 2000. Plain ./rish follows the configured default route. Root and System may remain bound at the same time; changing the default route does not need to tear either backend down.",
        "EXPECTED • rish identity" to "For ./rish --system, id should report uid=1000(system), gid=1000(system), with the isolated worker context u:r:ksu:s0 on the tested configuration. ./rish --root should report uid=0(root). This is intentional: interactive System execution remains outside the real Android system_server process, which is the component expected to show u:r:system_server:s0.",
        "EXPECTED • Capability routing" to "Normal rish and Binder/service work stays on the isolated UID-1000 route. SettingsProvider reads work as UID 1000 on the tested Android 17 build. settings put/delete/reset use the narrow KernelSU root compatibility route because Android 17 validates ContentProvider calling-package attribution. Root fallback for those mutations does not turn the interactive shell into a root or system_server terminal.",
        "EXPERIMENTAL • System Server Operations" to "The Execution Router includes a session-only Experimental System Server Operations control. It is OFF by default, requires confirmation and resets OFF after app restart or reboot. When enabled, only explicitly supported TokenX framework operations may use the verified bridge. Interactive rish remains outside system_server.",
        "POST-INSTALL HEALTH CHECK" to "D2 Gate ✓ protected • LSPosed _TKN RPC ✓ system_server UID 1000 • live System UID binder ✓ UID 1000 • Root/Shizuku ✓ UID 0 • TokenX-native compatibility ✓ active • Token Boot ✓ ready.",
        "Apps tab" to "The Apps area controls which compatible applications may use Shizuku. Grant access only to applications you trust. TokenX also provides management views for permissions, app-ops and other system-controlled app state; availability depends on the active backend and Android version.",
        "Root mode" to "Root mode starts the backend with UID 0 through KernelSU or another compatible configured root solution. It is appropriate for operations that genuinely require root. TokenX checks whether root is available and avoids leaving Root selected when the device can no longer provide it. A metamodule does not itself grant root; it supplies the systemless mounting layer used by modules that modify system paths.",
        "Wireless / USB mode" to "Wireless debugging is the normal computer-free Shizuku path on supported Android versions. USB/ADB is useful for initial setup, recovery and devices where wireless debugging is unavailable. TokenX includes options for port persistence and recovery, but Android and vendor restrictions still apply.",
        "System Server Bridge" to "On the tested Android 17 configuration, the LSPosed _TKN RPC reports identity directly from Android's persistent system_server process. Verification requires UID 1000, process system_server and SELinux u:r:system_server:s0.",
        "TKN System Server Backend" to "The current System Server setup uses the LSPosed _TKN RPC. Verification requires the live RPC identity to report UID 1000, process system_server and SELinux u:r:system_server:s0.",
        "System Server safety" to "Do not use the framework system_server as an interactive terminal host. A crash, deadlock or unsafe fork there can restart Android's framework or reboot the device. TokenX keeps Android 17 interactive rish execution in an isolated UID-1000 worker outside system_server and reserves the framework process for narrowly scoped integration.",
        "rish / terminal" to "rish is the interactive shell client. With the verified System Server backend, TokenX launches an isolated UID-1000 system worker outside Android's persistent system_server process. Normal commands remain UID 1000. On the tested Android 17 build, SettingsProvider reads work as UID 1000 but settings put/delete/reset mutations use a narrowly scoped KernelSU root compatibility route because SettingsProvider requires calling-package attribution the isolated worker does not provide. This does not move the interactive shell into system_server.",
        "Start on boot & Watchdog" to "Start on boot restores supported TokenX connectivity after startup. The TokenX watchdog observes transport and backend health separately using heartbeat/Binder state and can distinguish healthy, stale and offline conditions. It does not own Android's system_server lifecycle and must never recover System by killing or restarting system_server. Battery restrictions can still affect background recovery.",
        "Apps & routing" to "The Apps screen supports search, sorting, Granted/Revoked views, User/System/Disabled/Hidden filters, batch authorization and backend-aware Root/System route filtering. Authorization is persisted separately from backend state so temporary backend loss is not treated as an intentional revoke.",
        "Labs & advanced tools" to "Preview Labs exposes App Ops, Shell, Firewall, Autostart, TokenX Control Center, Root Console and Secure Chain Monitor. Control Center surfaces Root, D2 and System RPC state, while Secure Chain Monitor focuses on live D2/System RPC health. Experimental features that are not ready are intentionally kept out of Preview navigation.",
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
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent
            ),
            windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Installation • architecture • usage • safety",
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
