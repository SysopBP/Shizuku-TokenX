package moe.shizuku.manager.tokenx

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

enum class TokenXFotaBridgeState {
    NOT_INSTALLED,
    DETECTED,
    SYSTEM_UID_VERIFIED,
    INTERFACE_DISCOVERED,
    READY_PASSIVE
}

data class TokenXFotaSnapshot(
    val state: TokenXFotaBridgeState,
    val installed: Boolean,
    val uid: Int?,
    val systemApp: Boolean,
    val receiverNames: List<String>,
    val exportedReceiverNames: List<String>,
    val detail: String,
)

object TokenXFotaBridge {
    const val PACKAGE_NAME = "com.sdet.fotaagent"

    /**
     * Passive discovery only.
     *
     * This code never sends a broadcast, starts an activity/service, writes recovery
     * command files, or invokes an update/wipe/reboot path. It only inspects package
     * metadata already exposed by PackageManager.
     */
    fun snapshot(context: Context): TokenXFotaSnapshot {
        val pm = context.packageManager
        val flags = PackageManager.GET_RECEIVERS or PackageManager.GET_PERMISSIONS
        val info = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(PACKAGE_NAME, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(PACKAGE_NAME, flags)
            }
        }.getOrNull() ?: return TokenXFotaSnapshot(
            state = TokenXFotaBridgeState.NOT_INSTALLED,
            installed = false,
            uid = null,
            systemApp = false,
            receiverNames = emptyList(),
            exportedReceiverNames = emptyList(),
            detail = "Samsung FOTA package not visible"
        )

        val app = info.applicationInfo
        val uid = app?.uid
        val systemApp = app?.let {
            (it.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
        } ?: false
        val receivers = info.receivers?.map { it.name }.orEmpty().sorted()
        val exported = info.receivers?.filter { it.exported }?.map { it.name }.orEmpty().sorted()
        val fotaReceiver = receivers.any { it.endsWith(".FotaAgentReceiver") || it.endsWith("FotaAgentReceiver") }

        val state = when {
            uid != 1000 -> TokenXFotaBridgeState.DETECTED
            !fotaReceiver -> TokenXFotaBridgeState.SYSTEM_UID_VERIFIED
            else -> TokenXFotaBridgeState.READY_PASSIVE
        }
        val detail = when (state) {
            TokenXFotaBridgeState.NOT_INSTALLED -> "Not installed"
            TokenXFotaBridgeState.DETECTED -> "Detected • UID ${uid ?: -1}; system UID not verified"
            TokenXFotaBridgeState.SYSTEM_UID_VERIFIED -> "UID 1000 verified • FotaAgentReceiver not discovered"
            TokenXFotaBridgeState.INTERFACE_DISCOVERED -> "Receiver interface discovered"
            TokenXFotaBridgeState.READY_PASSIVE -> "READY • UID 1000 + FotaAgentReceiver discovered • transmit disabled"
        }
        return TokenXFotaSnapshot(state, true, uid, systemApp, receivers, exported, detail)
    }
}
