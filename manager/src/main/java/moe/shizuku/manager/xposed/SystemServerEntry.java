package moe.shizuku.manager.xposed;

import android.os.Process;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import rikka.shizuku.server.ShizukuService;

/**
 * TokenX LSPosed bridge.
 *
 * Loaded only into the Android framework process. The Shizuku server is embedded directly
 * in system_server after SystemServer.startOtherServices() completes, so there is no Starter
 * activity, copied command, su process, or hard-coded /data/app path on this path.
 */
public final class SystemServerEntry implements IXposedHookLoadPackage {

    private static final String TAG = "TokenX-Xposed";
    private static final AtomicBoolean START_CLAIMED = new AtomicBoolean(false);

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"android".equals(lpparam.packageName)
                || !"android".equals(lpparam.processName)
                || Process.myUid() != Process.SYSTEM_UID) {
            return;
        }

        try {
            Class<?> systemServer = XposedHelpers.findClass(
                    "com.android.server.SystemServer", lpparam.classLoader);

            XposedBridge.hookAllMethods(systemServer, "startOtherServices", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    startOnce();
                }
            });

            Log.i(TAG, "system_server hook installed");
        } catch (Throwable t) {
            Log.e(TAG, "Unable to install system_server hook", t);
            XposedBridge.log(TAG + ": " + Log.getStackTraceString(t));
        }
    }

    private static void startOnce() {
        if (!START_CLAIMED.compareAndSet(false, true)) {
            return;
        }

        try {
            Log.i(TAG, "BOOT_TOKEN CLAIMED: XPOSED/SYSTEM_SERVER UID 1000");
            ShizukuService.startEmbeddedSystemServer();
            Log.i(TAG, "BOOT_TOKEN CONFIRMED: XPOSED/SYSTEM_SERVER UID 1000");
        } catch (Throwable t) {
            // Never crash system_server for an optional backend. Leaving the binder absent
            // lets BootCompleteReceiver continue with root, then ADB/shell UID 2000.
            START_CLAIMED.set(false);
            Log.e(TAG, "Embedded Shizuku start failed; app fallback remains available", t);
            XposedBridge.log(TAG + ": embedded start failed: " + Log.getStackTraceString(t));
        }
    }
}
