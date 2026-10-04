package moe.shizuku.manager.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ui.component.TokenXGlassCard
import moe.shizuku.manager.tokenx.TokenXBootSession
import moe.shizuku.manager.tokenx.TokenXBootState
import moe.shizuku.manager.tokenx.TokenXRuntime
import moe.shizuku.manager.tokenx.TokenXRuntimeState
import moe.shizuku.manager.tokenx.TokenXCapability
import moe.shizuku.manager.tokenx.TokenXFotaBridge
import moe.shizuku.manager.tokenx.TokenXFotaBridgeState
import moe.shizuku.manager.tokenx.TokenXBackend
import moe.shizuku.manager.tokenx.TokenXRouteState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.topjohnwu.superuser.Shell
import androidx.compose.ui.platform.LocalContext
import moe.shizuku.manager.utils.ShizukuStateMachine

/**
 * TokenX's unified management surface.
 *
 * Only current Shizuku/runtime state is presented as live. Controls for the
 * upcoming native TokenX router, token boot and Xposed bridge are deliberately
 * labelled Preview until their backends are wired.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenXControlCenterScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = ShizukuSettings.getPreferences()
    var runtime by remember { mutableStateOf(TokenXRuntime.snapshot(context)) }
    var boot by remember { mutableStateOf(TokenXBootSession.current()) }
    var fotaBridge by remember { mutableStateOf(TokenXFotaBridge.snapshot(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            runtime = TokenXRuntime.snapshot(context)
            boot = TokenXBootSession.current()
            fotaBridge = TokenXFotaBridge.snapshot(context)
            delay(TokenXRuntime.REFRESH_INTERVAL_MS)
        }
    }
    val running = runtime.backendState.serverRunning
    val uid = runtime.backendState.serverUid
    var routerMode by remember { mutableStateOf(prefs.getString("tokenx_router_mode", "Automatic") ?: "Automatic") }
    var rootFirst by remember { mutableStateOf(prefs.getBoolean("tokenx_root_first", true)) }
    var recovery by remember { mutableStateOf(prefs.getBoolean("tokenx_recovery_preview", true)) }
    // Deliberately session-only: experimental system_server operations reset OFF
    // whenever this screen/app process is recreated, including after reboot.
    var systemServerOperations by remember { mutableStateOf(false) }
    var showSystemServerWarning by remember { mutableStateOf(false) }
    var pendingPowerAction by remember { mutableStateOf<Pair<String, String>?>(null) }
    var rootRequestInFlight by remember { mutableStateOf(false) }
    var rootRequestStatus by remember { mutableStateOf<String?>(null) }
    val powerScope = rememberCoroutineScope()

    pendingPowerAction?.let { (label, command) ->
        AlertDialog(
            onDismissRequest = { pendingPowerAction = null },
            title = { Text("$label?") },
            text = { Text("TokenX will request $label through the root backend. No FOTA agent, recovery command file, update package, or wipe operation is used.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingPowerAction = null
                    powerScope.launch { Shell.cmd(command).exec() }
                }) { Text(label) }
            },
            dismissButton = { TextButton(onClick = { pendingPowerAction = null }) { Text("Cancel") } }
        )
    }

    if (showSystemServerWarning) {
        AlertDialog(
            onDismissRequest = { showSystemServerWarning = false },
            title = { Text("Enable System Server Operations?") },
            text = { Text("Experimental high-trust mode. Only explicitly supported TokenX framework operations may use the verified system_server bridge. The interactive rish shell will remain outside system_server as the isolated UID-1000 worker. This option is session-only and resets OFF after restart or reboot.") },
            confirmButton = {
                TextButton(onClick = {
                    systemServerOperations = true
                    showSystemServerWarning = false
                }) { Text("Enable") }
            },
            dismissButton = {
                TextButton(onClick = { showSystemServerWarning = false }) { Text("Cancel") }
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("TokenX") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            windowInsets = WindowInsets(0.dp)
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TokenXGlassCard {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Token, null)
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text("Unified Privilege Engine", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Shizuku compatibility + TokenX multi-backend architecture", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AssistChip(onClick = {}, label = { Text(if (running) "ACTIVE" else "OFFLINE") })
                    }
                    HorizontalDivider()
                    Text("Current server  •  " + if (uid >= 0) "UID $uid" else "Not connected")
                }
            }

            SectionTitle("Privilege backends")
            TokenXGlassCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    BackendRow(Icons.Outlined.AdminPanelSettings, "Root", "UID 0", if (runtime.backendState.rootAvailable) if (uid == 0) "ACTIVE • current server" else "READY" else rootRequestStatus ?: "Permission required")
                    if (!runtime.backendState.rootAvailable) {
                        Button(
                            enabled = !rootRequestInFlight,
                            onClick = {
                                rootRequestInFlight = true
                                rootRequestStatus = "Requesting KernelSU permission…"
                                powerScope.launch {
                                    val result = runCatching { Shell.getShell() }.getOrNull()
                                    val granted = result?.isRoot == true
                                    rootRequestStatus = if (granted) "GRANTED • UID 0" else "DENIED / unavailable"
                                    runtime = TokenXRuntime.snapshot(context)
                                    rootRequestInFlight = false
                                }
                            }
                        ) {
                            if (rootRequestInFlight) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Requesting…")
                            } else {
                                Icon(Icons.Outlined.AdminPanelSettings, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Request Superuser Access")
                            }
                        }
                        Text(
                            "Requests root through TokenX's existing libsu backend. KernelSU remains the authority that grants or denies access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BackendRow(
                        Icons.Outlined.Security,
                        "TKN Bridge / System Server",
                        "UID 1000",
                        when {
                            runtime.systemServerBridgeActive -> "ACTIVE • live system_server transaction verified"
                            runtime.systemServerBridgeAttached -> "ATTACHED • system_server association verified; awaiting live transaction"
                            else -> "AVAILABLE check pending • no verified system_server attachment"
                        }
                    )
                    BackendRow(Icons.Outlined.Terminal, "Shell", "UID 2000", if (runtime.backendState.shellAvailable) "ACTIVE • compatibility fallback" else "Standby")
                    BackendRow(Icons.Outlined.Extension, "Xposed / LSPosed", "framework integration", when { runtime.xposedBridgeActive -> "ACTIVE • handshake verified"; runtime.xposedFrameworkDetected -> "Framework detected • bridge waiting"; else -> "Not detected" })
                }
            }

            SectionTitle("System Hook Health")
            TokenXGlassCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeatureRow(Icons.Outlined.Extension, "LSPosed framework", if (runtime.xposedFrameworkDetected) "DETECTED" else "Not detected")
                    FeatureRow(Icons.Outlined.Security, "CorePatch", when {
                        runtime.corePatchDetected && runtime.nativeUid1000Verified -> "DETECTED • shared UID bypass behavior verified"
                        runtime.corePatchDetected -> "DETECTED • PM UID1000 effect not yet verified"
                        else -> "Not detected"
                    })
                    FeatureRow(Icons.Outlined.Badge, "Native PM UID1000", if (runtime.nativeUid1000Verified) "VERIFIED • android.uid.system/1000" else "Not verified")
                    FeatureRow(Icons.Outlined.AdminPanelSettings, "TKN Bridge UID1000", if (runtime.systemServerBridgeAttached) "ATTACHED" else if (runtime.backendRegistry.isReady(TokenXBackend.SYSTEM_UID)) "READY" else "Not verified")
                    FeatureRow(Icons.Outlined.Link, "System Server Binder", if (runtime.systemServerBridgeActive) "VERIFIED • live UID1000 handshake" else "Not active")
                    HorizontalDivider()
                    FeatureRow(Icons.Outlined.Android, "Platform", "Android API ${runtime.androidApiLevel} • One UI ${runtime.oneUiVersion}")
                    Text(
                        "Health checks are observational. TokenX does not enable KnoxPatch hooks or alter Knox/attestation behavior.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionTitle("Backend Registry")
            TokenXGlassCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Selected route: ${runtime.backendRegistry.selected.name.replace('_', ' ')}", fontWeight = FontWeight.SemiBold)
                    runtime.backendRegistry.backends.values.forEach { entry ->
                        Surface(
                            onClick = {
                                if (entry.ready) TokenXRouteState.select(entry.backend, "Selected from Privilege Inspector")
                            },
                            enabled = entry.ready,
                            shape = MaterialTheme.shapes.medium,
                            color = if (runtime.backendRegistry.selected == entry.backend) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f) else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .35f)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.backend.name.replace('_', ' '), fontWeight = FontWeight.Medium)
                                    Text("UID ${entry.uid} • ${entry.detail}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(if (entry.verified) "VERIFIED" else if (entry.ready) "READY" else "OFFLINE", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            SectionTitle("Privilege Inspector")
            TokenXGlassCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val selected = runtime.backendRegistry.entry(runtime.backendRegistry.selected)
                    FeatureRow(Icons.Outlined.Route, "Selected backend", runtime.backendRegistry.selected.name.replace('_', ' '))
                    FeatureRow(Icons.Outlined.Badge, "Identity", selected?.let { "UID ${it.uid} • ${if (it.verified) "verified" else "discovered"}" } ?: "Unavailable")
                    FeatureRow(Icons.Outlined.Link, "System Server Binder", if (runtime.systemServerBridgeActive) "VERIFIED • capability mask 0x${runtime.systemServerCapabilities.toString(16)}" else "Not active")
                    FeatureRow(Icons.Outlined.Security, "Native / PM UID 1000", if (runtime.nativeUid1000Verified) "VERIFIED • com.tokenx.bridgetest • android.uid.system" else "Unavailable")
                    FeatureRow(Icons.Outlined.Security, "TKN Bridge / System UID", if (runtime.backendRegistry.isReady(TokenXBackend.SYSTEM_UID)) "READY • UID 1000" else "Unavailable")
                    FeatureRow(Icons.Outlined.Terminal, "Root", if (runtime.backendRegistry.isReady(TokenXBackend.ROOT)) "READY • UID 0" else "Unavailable")
                }
            }

            SectionTitle("Execution Router")
            TokenXGlassCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Router preferences", style = MaterialTheme.typography.titleMedium)
                    Text("Live capability routes are calculated from the backends that are actually available now.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("Automatic", "Capability").forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = routerMode == mode,
                                onClick = { routerMode = mode; prefs.edit().putString("tokenx_router_mode", mode).apply() },
                                shape = SegmentedButtonDefaults.itemShape(index, 2)
                            ) { Text(mode) }
                        }
                    }
                    PreviewSwitch("Prefer Root when capable", "Root-first policy; UID-1000 work can route through TKN Bridge.", rootFirst) {
                        rootFirst = it; prefs.edit().putBoolean("tokenx_root_first", it).apply()
                    }
                    CapabilityLine("Filesystem", runtime.routes.getValue(TokenXCapability.FILESYSTEM).backend.name)
                    CapabilityLine("Process", runtime.routes.getValue(TokenXCapability.PROCESS).backend.name)
                    CapabilityLine("Android framework", runtime.routes.getValue(TokenXCapability.FRAMEWORK).backend.name)
                    CapabilityLine("Shell commands", runtime.routes.getValue(TokenXCapability.SHELL_COMMAND).backend.name)
                    CapabilityLine("General", runtime.routes.getValue(TokenXCapability.GENERAL).backend.name)
                    HorizontalDivider()
                    PreviewSwitch(
                        "Experimental: System Server Operations",
                        if (systemServerOperations)
                            "ON for this session • only explicitly supported framework operations may use the verified system_server bridge; interactive rish stays isolated UID 1000."
                        else
                            "OFF • interactive rish remains isolated UID 1000. Enable only for supported framework operations; resets after restart/reboot.",
                        systemServerOperations
                    ) { enabled ->
                        if (enabled) showSystemServerWarning = true
                        else systemServerOperations = false
                    }
                }
            }

            SectionTitle("Boot Guardian")
            TokenXGlassCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FeatureRow(Icons.Outlined.Token, "Boot Session Token", "Generation ${boot.generation} • ${boot.state.name} • ${boot.token.take(8)}…")
                    FeatureRow(Icons.Outlined.Bolt, "Session owner", if (boot.state == TokenXBootState.NEW) "Waiting for a startup path to claim this generation" else "${boot.owner.name.replace('_', ' ')} • ${boot.state.name.replace('_', ' ')}")
                    FeatureRow(Icons.Outlined.MonitorHeart, "Guardian watchdog", "Existing watchdog remains the health/recovery layer")
                    boot.failure?.let { FeatureRow(Icons.Outlined.Warning, "Last boot failure", it) }
                    PreviewSwitch("Recovery handoff", "Prepare fallback ownership when the preferred backend cannot confirm.", recovery) {
                        recovery = it; prefs.edit().putBoolean("tokenx_recovery_preview", it).apply()
                    }
                }
            }

            SectionTitle("FOTA Bridge")
            TokenXGlassCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeatureRow(
                        Icons.Outlined.SystemUpdate,
                        "Samsung FOTA",
                        fotaBridge.detail
                    )
                    FeatureRow(
                        Icons.Outlined.Badge,
                        "Package identity",
                        if (fotaBridge.installed) "com.sdet.fotaagent • UID ${fotaBridge.uid ?: -1}" else "Not detected"
                    )
                    FeatureRow(
                        Icons.Outlined.Sensors,
                        "Receiver discovery",
                        if (fotaBridge.receiverNames.isNotEmpty())
                            "${fotaBridge.receiverNames.size} declared • ${fotaBridge.exportedReceiverNames.size} exported"
                        else "No receivers visible"
                    )
                    FeatureRow(
                        Icons.Outlined.Shield,
                        "Transmit gate",
                        "LOCKED • passive inspection only"
                    )
                    if (fotaBridge.state == TokenXFotaBridgeState.READY_PASSIVE) {
                        Text(
                            "Passive handshake complete. TokenX will not send CP_FILE, recovery, factory-reset, update-package, or wipe actions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SectionTitle("Power Controls")
            TokenXGlassCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Privileged device controls", style = MaterialTheme.typography.titleMedium)
                    Text("Root backend only • isolated from Samsung FOTA/update paths.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { pendingPowerAction = "Reboot" to "reboot" },
                            enabled = runtime.backendState.rootAvailable,
                            modifier = Modifier.weight(1f)
                        ) { Text("Reboot") }
                        Button(
                            onClick = { pendingPowerAction = "Recovery Reboot" to "reboot recovery" },
                            enabled = runtime.backendState.rootAvailable,
                            modifier = Modifier.weight(1f)
                        ) { Text("Recovery") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { pendingPowerAction = "SystemUI Restart" to "pkill -TERM -f com.android.systemui" },
                            enabled = runtime.backendState.rootAvailable,
                            modifier = Modifier.weight(1f)
                        ) { Text("SystemUI") }
                        OutlinedButton(
                            onClick = { pendingPowerAction = "Soft Reboot" to "setprop ctl.restart zygote" },
                            enabled = runtime.backendState.rootAvailable,
                            modifier = Modifier.weight(1f)
                        ) { Text("Soft Reboot") }
                    }
                    FeatureRow(
                        Icons.Outlined.RestartAlt,
                        "Recovery Reboot",
                        if (runtime.backendState.rootAvailable) "AVAILABLE • root backend" else "Unavailable • root required"
                    )
                }
            }

            SectionTitle("Native TokenX API")
            TokenXGlassCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FeatureRow(Icons.Outlined.Api, "Capability discovery", "LIVE • runtime backend state feeds the router")
                    FeatureRow(Icons.Outlined.Route, "Per-capability routing", "LIVE • Root first, System Server for framework work, Shell fallback")
                    FeatureRow(Icons.Outlined.Code, "Root execution", if (runtime.backendState.rootAvailable) "READY • UID 0 backend available" else "Unavailable")
                    FeatureRow(Icons.Outlined.Badge, "Native UID 1000", if (runtime.nativeUid1000Verified) "VERIFIED • PackageManager shared UID android.uid.system" else "Unavailable • PM admission not verified")
                    FeatureRow(
                        Icons.Outlined.AdminPanelSettings,
                        "UID 1000 operations",
                        when {
                            runtime.systemServerBridgeActive -> "ACTIVE • execution handshake verified in system_server"
                            runtime.systemServerBridgeAttached -> "ATTACHED • TKN Bridge identity received; live system_server verification pending"
                            else -> "Not attached • TKN Bridge unavailable"
                        }
                    )
                    FeatureRow(Icons.Outlined.Link, "Shizuku compatibility", "Preserved • existing Binder model stays intact")
                    runtime.systemServerFunctionalResult?.let { functional ->
                        FeatureRow(
                            Icons.Outlined.VerifiedUser,
                            "System Server functional",
                            if (functional.verified)
                                "VERIFIED • DIRECT BINDER • ${functional.passCount} read-only checks passed"
                            else
                                "PARTIAL • ${functional.passCount} passed • ${functional.denyCount} denied"
                        )
                        Text(
                            "PID ${functional.pid} • UID ${functional.uid} • ${functional.selinux}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        functional.checks.forEach { check ->
                            Text(
                                check,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (check.startsWith("PASS "))
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            SectionTitle("Connection History")
            TokenXGlassCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val events = TokenXRouteState.history()
                    if (events.isEmpty()) Text("No route changes recorded this session.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    events.take(10).forEach { event ->
                        Text("${event.from.name} → ${event.to.name} • ${event.message}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            SectionTitle("About TokenX")
            TokenXGlassCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeatureRow(Icons.Outlined.Security, "D2 Dual Gate", "TKN Bridge + FOTA remain held until the D2 security boundary is released")
                    FeatureRow(Icons.Outlined.Token, "Provisioning Vault", "Token Pulse • live Secure Chain • technical console")
                    FeatureRow(Icons.Outlined.AdminPanelSettings, "Privileged payloads", "TKN Bridge UID 1000 • FOTA system_app")
                    FeatureRow(Icons.Outlined.Extension, "Android 17 compatibility", "Receiver compatibility scoped to the tested FOTA path")
                    Text("System Server contribution: @Vikramaditya015", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            SectionTitle("Interface")
            TokenXGlassCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeatureRow(Icons.Outlined.Palette, "SESL + TokenX Glass", "tribalfs SESL foundation active")
                    FeatureRow(Icons.Outlined.DarkMode, "AMOLED / custom backgrounds", "Appearance Studio")
                    FeatureRow(Icons.Outlined.Tune, "Editable glass surfaces", "Opacity, radius, border and background controls")
                }
            }
            Text(
                "Preview labels are intentional: TokenX will only show a backend as active after the implementation can verify it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
}

@Composable private fun BackendRow(icon: androidx.compose.ui.graphics.vector.ImageVector, name: String, uid: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold)
            Text("$uid • $detail", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun CapabilityLine(label: String, route: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(route, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable private fun PreviewSwitch(title: String, summary: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
