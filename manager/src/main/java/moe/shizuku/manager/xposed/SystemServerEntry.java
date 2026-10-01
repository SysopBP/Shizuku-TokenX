package moe.shizuku.manager.xposed;

import android.os.Process;
import android.os.ServiceManager;

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

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"android".equals(lpparam.packageName) || !"android".equals(lpparam.processName)) {
            return;
        }

        XposedBridge.log("TokenX: attached to system_server uid=" + Process.myUid());

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
                    return;
                }

                ShizukuService.startEmbeddedSystemServer();
                XposedBridge.log("TokenX: System Server backend ACTIVE (uid 1000)");
            } catch (Throwable t) {
                STARTED.set(false);
                XposedBridge.log("TokenX: System Server backend failed");
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
    }
}
