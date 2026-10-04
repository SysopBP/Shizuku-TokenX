package com.tokenx.bridgetest;

import android.app.Service;
import android.app.ActivityManager;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Arrays;

/**
 * Identity-only TokenX bridge backend.
 *
 * This component intentionally exposes no privileged operations. The exported Binder
 * only answers the identity probe, and only to the TokenX manager package.
 */
public final class IdentityService extends Service {
    public static final int TRANSACTION_GET_IDENTITY = IBinder.FIRST_CALL_TRANSACTION;
    public static final int TRANSACTION_RUN_FUNCTIONAL_CHECKS = IBinder.FIRST_CALL_TRANSACTION + 1;
    private static final String TOKENX_MANAGER = "moe.shizuku.privileged.api";

    private final Binder binder = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code != TRANSACTION_GET_IDENTITY && code != TRANSACTION_RUN_FUNCTIONAL_CHECKS) return false;

            final int callerUid = Binder.getCallingUid();
            String[] callerPackages = getPackageManager().getPackagesForUid(callerUid);
            if (callerPackages == null || !Arrays.asList(callerPackages).contains(TOKENX_MANAGER)) {
                throw new SecurityException("TokenX identity bridge caller not allowed: uid=" + callerUid);
            }

            try {
                if (code == TRANSACTION_RUN_FUNCTIONAL_CHECKS) {
                    reply.writeNoException();
                    reply.writeInt(Process.myPid());
                    reply.writeInt(Process.myUid());
                    reply.writeString(readFirst("/proc/self/attr/current"));
                    reply.writeString(runFunctionalChecks());
                    return true;
                }

                reply.writeNoException();
                reply.writeInt(Process.myPid());
                reply.writeInt(Process.myUid());
                reply.writeString(readFirst("/proc/self/attr/current"));
                reply.writeString(readFirst("/proc/self/cmdline").replace("\u0000", ""));
                return true;
            } catch (Throwable t) {
                reply.writeException(new RuntimeException("identity probe failed", t));
                return true;
            }
        }
    };

    @Override public IBinder onBind(Intent intent) { return binder; }

    /**
     * Read-only capability verification executed by the Binder endpoint itself.
     * No writes, reboots, settings changes, package mutations, or shell fallbacks.
     */
    private String runFunctionalChecks() {
        StringBuilder out = new StringBuilder();
        check(out, "ActivityManager", () -> {
            ActivityManager am = getSystemService(ActivityManager.class);
            if (am == null) throw new IllegalStateException("service=null");
            java.util.List<ActivityManager.RunningAppProcessInfo> p = am.getRunningAppProcesses();
            return "runningProcesses=" + (p == null ? -1 : p.size());
        });
        check(out, "PackageManager", () ->
                "installedPackages=" + getPackageManager().getInstalledPackages(0).size());
        check(out, "PowerManager", () -> {
            PowerManager pm = getSystemService(PowerManager.class);
            if (pm == null) throw new IllegalStateException("service=null");
            return "interactive=" + pm.isInteractive();
        });
        check(out, "WindowManager", () -> {
            WindowManager wm = getSystemService(WindowManager.class);
            if (wm == null) throw new IllegalStateException("service=null");
            return "displayId=" + wm.getDefaultDisplay().getDisplayId();
        });
        check(out, "SecureSettingsRead", () ->
                "android_id=" + Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));
        return out.toString();
    }

    private interface ReadCheck { String run() throws Exception; }

    private static void check(StringBuilder out, String name, ReadCheck check) {
        try {
            out.append("PASS ").append(name).append(" • ").append(check.run()).append('\n');
        } catch (Throwable t) {
            out.append("DENY ").append(name).append(" • ")
                    .append(t.getClass().getSimpleName()).append(": ")
                    .append(t.getMessage()).append('\n');
        }
    }

    private static String readFirst(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            String s = r.readLine();
            return s == null ? "(empty)" : s;
        } catch (Throwable t) {
            return "ERROR " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }
}
