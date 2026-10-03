package moe.shizuku.manager.tokenx

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.topjohnwu.superuser.Shell

data class TokenXCapabilityProbe(
    val writeSecureSettings: Boolean,
    val writeSettings: Boolean,
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

        return TokenXCapabilityProbe(
            writeSecureSettings = granted(android.Manifest.permission.WRITE_SECURE_SETTINGS),
            writeSettings = Settings.System.canWrite(context),
            dumpViaRoot = rootProbe("dumpsys activity activities >/dev/null"),
            packageManagerViaRoot = rootProbe("cmd package list packages >/dev/null"),
            systemPropertiesViaRoot = rootProbe("getprop ro.build.version.release >/dev/null"),
            rootSelinuxContext = rootContext,
            rootUid = runCatching { Shell.cmd("id").exec().out.firstOrNull() }.getOrNull(),
            shellUid = if (rikka.shizuku.Shizuku.pingBinder()) runCatching { rikka.shizuku.Shizuku.getUid() }.getOrDefault(-1) else -1,
        )
    }
}
