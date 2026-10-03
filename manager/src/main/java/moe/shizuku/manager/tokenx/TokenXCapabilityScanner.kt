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
            val result = Shell.cmd(command).exec()
            result.isSuccess
        }.getOrDefault(false)

        val rootContext = runCatching {
            Shell.cmd("id -Z 2>/dev/null || cat /proc/self/attr/current").exec()
                .out.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrNull()

        val directWriteSecureSettings = granted(android.Manifest.permission.WRITE_SECURE_SETTINGS)
        val writeSettings = Settings.System.canWrite(context)

        // Keep the direct PackageManager grant separate from effective capability.
        // When no direct grant is present, prove the privileged route with a reversible
        // Settings.Secure write/delete instead of inferring capability from UID/root alone.
        val secureProbeKey = "tokenx_capability_probe"
        val secureProbeValue = "tokenx"
        val secureSettingsViaRoot = if (!directWriteSecureSettings) runCatching {
            val result = Shell.cmd(
                "settings put secure $secureProbeKey $secureProbeValue && " +
                    "test \"$(settings get secure $secureProbeKey)\" = \"$secureProbeValue\"; " +
                    "rc=$?; settings delete secure $secureProbeKey >/dev/null 2>&1; exit $rc"
            ).exec()
            result.isSuccess
        }.getOrDefault(false) else false
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
            shellUid = if (rikka.shizuku.Shizuku.pingBinder()) runCatching { rikka.shizuku.Shizuku.getUid() }.getOrDefault(-1) else -1,
        )
    }
}
