package com.tokenx.bridgetest;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;

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
    private static final String TOKENX_MANAGER = "moe.shizuku.privileged.api";

    private final Binder binder = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code != TRANSACTION_GET_IDENTITY) return false;

            final int callerUid = Binder.getCallingUid();
            String[] callerPackages = getPackageManager().getPackagesForUid(callerUid);
            if (callerPackages == null || !Arrays.asList(callerPackages).contains(TOKENX_MANAGER)) {
                throw new SecurityException("TokenX identity bridge caller not allowed: uid=" + callerUid);
            }

            try {
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

    private static String readFirst(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            String s = r.readLine();
            return s == null ? "(empty)" : s;
        } catch (Throwable t) {
            return "ERROR " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }
}
