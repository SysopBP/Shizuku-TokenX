package rikka.shizuku.server;

import android.content.Context;
import android.content.IContentProvider;
import android.content.pm.ApplicationInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import moe.shizuku.manager.BuildConfig;
import rikka.hidden.compat.ActivityManagerApis;
import rikka.shizuku.server.api.IContentProviderUtils;

/**
 * Loaded by TokenX's Zygisk module only after system_server specialization.
 * The endpoint is independent of Root and isolated UID-1000 Shizuku workers.
 * It exposes bounded, read-only framework operations and the existing _TKN rendezvous.
 */
public final class TokenXSystemServerInjection {
    private static final String TAG = "TokenX/Injection";
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static volatile Endpoint endpoint;
    private static volatile IBinder managerLifetime;
    private static final AtomicBoolean PUBLISHING = new AtomicBoolean();
    private static volatile long directRequest;

    /** Fresh request identity is supplied by the root-staged live injector payload. */
    public static void startDirect(long request) {
        if (request <= 0) return;
        directRequest = request;
        start();
    }

    public static void start() {
        if (Process.myUid() != Process.SYSTEM_UID ||
                !self("/proc/self/attr/current").equals("u:r:system_server:s0")) {
            Log.e(TAG, "SYSTEM_SERVER_INJECTION_IDENTITY_REJECTED");
            return;
        }
        if (STARTED.compareAndSet(false, true)) publishAsync();
    }

    private static void publishAsync() {
        if (!PUBLISHING.compareAndSet(false, true)) return;
        Thread worker = new Thread(() -> {
            try {
                // Never block Zygisk's specialization callback or the framework main thread.
                for (int attempt = 0; attempt < 120; attempt++) {
                    try {
                        if (!"system_server".equals(self("/proc/self/cmdline")) ||
                                ServiceManager.getService("activity") == null ||
                                ServiceManager.getService("package") == null) {
                            Thread.sleep(1000);
                            continue;
                        }
                        Context context = systemContext();
                        ApplicationInfo manager = context.getPackageManager().getApplicationInfo(BuildConfig.APPLICATION_ID, 0);
                        if (endpoint == null) endpoint = new Endpoint(manager.uid, context);
                        if (publish()) return;
                    } catch (Exception e) {
                        if (attempt == 119) Log.e(TAG, "SYSTEM_SERVER_INJECTION_HANDOFF_FAILED", e);
                    }
                    Thread.sleep(1000);
                }
            } catch (Throwable e) {
                Log.e(TAG, "SYSTEM_SERVER_INJECTION_FAILED; Android continues", e);
            } finally {
                PUBLISHING.set(false);
            }
        }, "TokenX-Injection-Handoff");
        worker.setDaemon(true);
        worker.start();
    }

    private static Context systemContext() throws Exception {
        Class<?> thread = Class.forName("android.app.ActivityThread");
        Object current = thread.getDeclaredMethod("currentActivityThread").invoke(null);
        if (current == null) throw new IllegalStateException("System ActivityThread unavailable");
        return (Context) thread.getDeclaredMethod("getSystemContext").invoke(current);
    }

    private static boolean publish() {
        String name = BuildConfig.APPLICATION_ID + ".shizuku";
        IContentProvider provider = null;
        try {
            provider = ActivityManagerApis.getContentProviderExternal(name, 0, null, name);
            if (provider == null || !provider.asBinder().isBinderAlive()) return false;
            Bundle extra = new Bundle();
            extra.putBinder("binder", endpoint);
            Bundle reply = IContentProviderUtils.callCompat(provider, null, name, "tokenx.injectedBackend", null, extra);
            IBinder lifetime = reply == null ? null : reply.getBinder("managerLifetime");
            if (lifetime == null) return false;
            if (managerLifetime != lifetime) {
                managerLifetime = lifetime;
                lifetime.linkToDeath(() -> {
                    if (managerLifetime == lifetime) {
                        managerLifetime = null;
                        publishAsync();
                    }
                }, 0);
            }
            Log.i(TAG, "SYSTEM_SERVER_INJECTION_BINDER_READY uid=" + Process.myUid() + " pid=" + Process.myPid());
            return true;
        } catch (Throwable e) {
            Log.w(TAG, "Injection Binder publication unavailable: " + e.getClass().getSimpleName());
            return false;
        } finally {
            if (provider != null) {
                try { ActivityManagerApis.removeContentProviderExternal(name, null); }
                catch (Throwable ignored) { }
            }
        }
    }

    private static String self(String path) {
        try {
            return new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8)
                    .replace(String.valueOf((char) 0), "").trim();
        } catch (Exception e) { return "unknown"; }
    }

    private static final class Endpoint extends Binder {
        private final int managerUid;
        private final Context context;
        private IBinder rendezvous;
        private IBinder.DeathRecipient rendezvousDeath;
        Endpoint(int managerUid, Context context) { this.managerUid = managerUid; this.context = context; }

        @Override
        protected synchronized boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code != 0x5f544b4e) return super.onTransact(code, data, reply, flags);
            if (reply == null || (flags & IBinder.FLAG_ONEWAY) != 0) return false;
            try {
                data.enforceInterface("android.app.IActivityManager");
                if (Binder.getCallingUid() != managerUid) throw new SecurityException("TokenX manager required");
                int action = data.readInt();
                if (action == 1) {
                    reply.writeNoException();
                    reply.writeInt(Process.myPid());
                    reply.writeInt(Process.myUid());
                    reply.writeString(self("/proc/self/attr/current"));
                    reply.writeString(self("/proc/self/cmdline"));
                } else if (action == 2) {
                    IBinder candidate = data.readStrongBinder();
                    if (candidate == null || !candidate.pingBinder()) throw new IllegalArgumentException("Dead rendezvous Binder");
                    if (rendezvous != null && rendezvousDeath != null) rendezvous.unlinkToDeath(rendezvousDeath, 0);
                    rendezvousDeath = () -> { synchronized (Endpoint.this) { if (rendezvous == candidate) rendezvous = null; } };
                    candidate.linkToDeath(rendezvousDeath, 0);
                    rendezvous = candidate;
                    reply.writeNoException();
                } else if (action == 3) {
                    reply.writeNoException();
                    reply.writeStrongBinder(rendezvous != null && rendezvous.isBinderAlive() ? rendezvous : null);
                } else if (action == 4) {
                    ArrayList<String> services = new ArrayList<>();
                    for (String name : new String[] {"activity", "package", "power", "window"}) {
                        IBinder service = ServiceManager.getService(name);
                        if (service != null && service.isBinderAlive()) services.add(name);
                    }
                    reply.writeNoException();
                    reply.writeInt(Process.myUid());
                    reply.writeInt(Process.myPid());
                    reply.writeString(self("/proc/self/attr/current"));
                    reply.writeString(String.join(",", services));
                } else if (action == 5) {
                    String packageName = data.readString();
                    if (packageName == null || packageName.length() > 255) throw new IllegalArgumentException("Invalid package");
                    int uid = -1;
                    // Resolve as system_server, never under the manager's Binder calling identity.
                    long token = Binder.clearCallingIdentity();
                    try { uid = context.getPackageManager().getApplicationInfo(packageName, 0).uid; }
                    catch (android.content.pm.PackageManager.NameNotFoundException ignored) { }
                    finally { Binder.restoreCallingIdentity(token); }
                    reply.writeNoException(); reply.writeInt(uid);
                } else if (action == 6) {
                    // Version + challenge echo prove the installed payload participated in this call.
                    long challenge = data.readLong();
                    reply.writeNoException();
                    reply.writeInt(4);
                    reply.writeInt(BuildConfig.VERSION_CODE);
                    reply.writeLong(challenge);
                } else if (action == 7) {
                    reply.writeNoException();
                    reply.writeLong(directRequest);
                } else if (action >= 20 && action <= 22) {
                    byte[] certificate = data.createByteArray();
                    if (data.dataAvail() != 0) throw new IllegalArgumentException("Unexpected signing arguments");
                    long token = Binder.clearCallingIdentity();
                    Bundle result;
                    try { result = TokenXUidTestSigning.run(context, action, certificate); }
                    finally { Binder.restoreCallingIdentity(token); }
                    reply.writeNoException(); reply.writeBundle(result);
                } else throw new IllegalArgumentException("Unsupported TokenX action");
                return true;
            } catch (Throwable e) {
                reply.setDataSize(0); reply.setDataPosition(0);
                reply.writeException(e instanceof Exception ? (Exception) e : new IllegalStateException(e));
                return true;
            }
        }
    }
}
