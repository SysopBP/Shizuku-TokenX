package moe.shizuku.tokenx.xposed

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * TokenX's modern LSPosed entry point.
 *
 * This stage intentionally installs no framework method hooks. It only proves
 * that LSPosed loaded TokenX into system_server and exposes framework metadata
 * in the Xposed log. The authenticated TokenX Binder bridge is layered on top
 * of this lifecycle point rather than modifying framework behavior.
 */
class TokenXXposedEntry : XposedModule() {

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        if (!param.isSystemServer) return
        log(
            Log.INFO,
            TAG,
            "module loaded in system_server; framework=${frameworkName} ${frameworkVersion}; api=${apiVersion}",
        )
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        log(Log.INFO, TAG, "system_server starting; TokenX bridge lifecycle ready")
    }

    private companion object {
        const val TAG = "TokenX/Xposed"
    }
}
