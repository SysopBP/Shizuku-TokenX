package moe.shizuku.manager.xposed;

import android.os.Process;
import android.os.ServiceManager;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import rikka.shizuku.server.ShizukuService;

/**
 * LSPosed entry point for the TokenX System Server backend.
 *
 * The APK itself remains a normal application UID. LSPosed loads this class into the
 * Android framework process; code executed here therefore has system_server's UID 1000.
 * We deliberately start the existing Shizuku service in-process instead of attempting to
 * install the manager APK with android:sharedUserId or a platform signature.
 */
public final class SystemServerEntry implements IXposedHookLoadPackage {

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final String TAG = "TokenX-SystemServer";
    private static final int START_ATTEMPTS = 6;
    private static final long START_RETRY_DELAY_MS = 1500L;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"android".equals(lpparam.packageName) || !"android".equals(lpparam.processName)) {
            return;
        }

        checkpoint("TOKENX_HOOK_ENTER uid=" + Process.myUid() + " pid=" + Process.myPid());

        // Never block LSPosed's package-load callback. PackageManager/ActivityManager may
        // not all be published at this exact instant, so a tiny worker waits for the same
        // framework services the standalone server requires.
        Thread worker = new Thread(() -> {
            try {
                waitForService("package");
                waitForService("activity");
                waitForService("user");
                waitForService("appops");

                if (Process.myUid() != Process.SYSTEM_UID) {
                    XposedBridge.log("TokenX: refusing System Server backend; uid=" + Process.myUid());
                    return;
                }

                if (!STARTED.compareAndSet(false, true)) {
                    checkpoint("TOKENX_START_SKIPPED already started");
                    return;
                }

                Throwable lastFailure = null;
                for (int attempt = 1; attempt <= START_ATTEMPTS; attempt++) {
                    try {
                        checkpoint("TOKENX_START_ATTEMPT " + attempt + "/" + START_ATTEMPTS);
                        ShizukuService.startEmbeddedSystemServer();
                        checkpoint("TOKENX_SERVICE_REGISTERED uid=" + Process.myUid()
                                + " pid=" + Process.myPid());
                        return;
                    } catch (Throwable t) {
                        lastFailure = t;
                        checkpoint("TOKENX_START_FAILED attempt=" + attempt + " error=" + t);
                        XposedBridge.log(t);
                        if (attempt < START_ATTEMPTS) {
                            Thread.sleep(START_RETRY_DELAY_MS);
                        }
                    }
                }

                STARTED.set(false);
                throw new IllegalStateException("TokenX system_server backend failed after "
                        + START_ATTEMPTS + " attempts", lastFailure);
            } catch (Throwable t) {
                STARTED.set(false);
                checkpoint("TOKENX_BACKEND_FAILED " + t);
                XposedBridge.log(t);
            }
        }, "TokenX-SystemServer");
        worker.setDaemon(true);
        worker.start();
    }

    private static void waitForService(String name) throws InterruptedException {
        while (ServiceManager.getService(name) == null) {
            Thread.sleep(250L);
        }
        checkpoint("TOKENX_DEP_READY " + name);
    }

    private static void checkpoint(String message) {
        XposedBridge.log("TokenX: " + message);
        Log.i(TAG, message);
    }
}
