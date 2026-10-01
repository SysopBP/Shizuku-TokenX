package moe.shizuku.tokenx.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Minimal TokenX LSPosed/Xposed entry point.
 *
 * Phase one deliberately does not hook framework methods. It proves that the
 * module is loaded in android/system_server; the authenticated IPC handshake
 * will be added next. Keeping this inert avoids destabilising system_server
 * while the bridge contract is built.
 */
class TokenXXposedEntry : IXposedHookLoadPackage {
    override fun handleLoadPackage(param: XC_LoadPackage.LoadPackageParam) {
        if (param.packageName != "android" || param.processName != "android") return
        XposedBridge.log("TokenX: system_server module loaded; bridge handshake pending")
    }
}
