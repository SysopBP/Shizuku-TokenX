package moe.shizuku.manager.tokenx

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.topjohnwu.superuser.Shell

data class TokenXCapabilityProbe(
    val writeSecureSettings: Boolean,
    val writeSettings: Boolean,
    /** Effective Settings.Secure write proved by a reversible runtime round-trip. */
    val secureSettingsRuntimeVerified: Boolean,
    /** Route that satisfied the effective secure-settings probe. */
    val secureSettingsRoute: String?,
    val dumpViaRoot: Boolean,
    val packageManagerViaRoot: Boolean,
    val systemPropertiesViaRoot: Boolean,
    val rootSelinuxContext: String?,
    val rootUid: String?,
    val shellUid: Int,
    val systemServerCapabilityMask: Int = 0,
)

object TokenXCapabilityScanner {
    fun scan(context: Context): TokenXCapabilityProbe {
        val pm = context.packageManager
        fun granted(permission: String) =
            pm.checkPermission(permission, context.packageName) == PackageManager.PERMISSION_GRANTED
        fun rootProbe(command: String): Boolean = runCatching {
            Shell.cmd(command).exec().isSuccess
        }.getOrDefault(false)

        val rootContext = runCatching {
            Shell.cmd("id -Z 2>/dev/null || cat /proc/self/attr/current").exec()
                .out.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrNull()

        val directWriteSecureSettings = granted(android.Manifest.permission.WRITE_SECURE_SETTINGS)
        val writeSettings = Settings.System.canWrite(context)

        // Direct permission and effective privileged capability are intentionally separate.
        // If there is no direct grant, prove the root route with a reversible round trip.
        val secureProbeKey = "tokenx_capability_probe"
        val secureProbeValue = "tokenx"
        val secureSettingsViaRoot = if (!directWriteSecureSettings) {
            rootProbe(
                "settings put secure $secureProbeKey $secureProbeValue && " +
                    "[ \"\$(settings get secure $secureProbeKey)\" = \"$secureProbeValue\" ] && " +
                    "settings delete secure $secureProbeKey >/dev/null 2>&1"
            )
        } else {
            false
        }
        val secureRuntimeVerified = directWriteSecureSettings || secureSettingsViaRoot
        val secureRoute = when {
            directWriteSecureSettings -> "Direct package grant"
            secureSettingsViaRoot -> "Via Root"
            else -> null
        }

        return TokenXCapabilityProbe(
            writeSecureSettings = directWriteSecureSettings,
            writeSettings = writeSettings,
            secureSettingsRuntimeVerified = secureRuntimeVerified,
            secureSettingsRoute = secureRoute,
            dumpViaRoot = rootProbe("dumpsys activity activities >/dev/null"),
            packageManagerViaRoot = rootProbe("cmd package list packages >/dev/null"),
            systemPropertiesViaRoot = rootProbe("getprop ro.build.version.release >/dev/null"),
            rootSelinuxContext = rootContext,
            rootUid = runCatching { Shell.cmd("id").exec().out.firstOrNull() }.getOrNull(),
            shellUid = if (rikka.shizuku.Shizuku.pingBinder()) {
                runCatching { rikka.shizuku.Shizuku.getUid() }.getOrDefault(-1)
            } else {
                -1
            },
        )
    }
}
