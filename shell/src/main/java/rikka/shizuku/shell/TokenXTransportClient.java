package rikka.shizuku.shell;

import android.app.ActivityThread;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.ContextWrapper;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Looper;
import android.system.Os;

import rikka.hidden.compat.PackageManagerApis;

/**
 * Standalone TokenX transport diagnostic client. It uses the manager provider only
 * for Binder discovery; HELLO/session/status/ping/close are direct Binder calls.
 */
public final class TokenXTransportClient {
    private static final String AUTHORITY = "moe.shizuku.privileged.api.shizuku";
    private static final String METHOD = "tokenx.getTransport";
    private static final String EXTRA_BINDER = "tokenx.binder";
    private static final String DESCRIPTOR = "moe.shizuku.tokenx.ITransport";
    private static final int TX_HELLO = IBinder.FIRST_CALL_TRANSACTION;
    private static final int TX_REQUEST_SESSION = IBinder.FIRST_CALL_TRANSACTION + 1;
    private static final int TX_PING = IBinder.FIRST_CALL_TRANSACTION + 2;
    private static final int TX_STATUS = IBinder.FIRST_CALL_TRANSACTION + 3;
    private static final int TX_CLOSE = IBinder.FIRST_CALL_TRANSACTION + 4;
    private static final int SYSTEM = 1;
    private static final Binder LIFETIME = new Binder();

    private static String packageName() {
        var packages = PackageManagerApis.getPackagesForUidNoThrow(Os.getuid());
        if (packages.size() == 1) return packages.get(0);
        String p = System.getenv("RISH_APPLICATION_ID");
        if (p == null || p.isEmpty() || "PKG".equals(p)) p = "com.termux";
        return p;
    }

    private static IBinder discover(Context context) throws Exception {
        ContentProviderClient provider = context.getContentResolver()
                .acquireUnstableContentProviderClient(Uri.parse("content://" + AUTHORITY));
        if (provider == null) throw new IllegalStateException("TokenX manager provider unavailable");
        try {
            Bundle result = provider.call(METHOD, null, new Bundle());
            if (result == null) throw new IllegalStateException("tokenx.getTransport returned null");
            IBinder binder = result.getBinder(EXTRA_BINDER);
            if (binder == null || !binder.isBinderAlive()) throw new IllegalStateException("TokenX transport Binder unavailable");
            return binder;
        } finally {
            provider.close();
        }
    }

    private static Parcel transact(IBinder remote, int code, Writer writer) throws Exception {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            if (writer != null) writer.write(data);
            if (!remote.transact(code, data, reply, 0)) throw new IllegalStateException("transaction rejected code=" + code);
            reply.readException();
            return reply;
        } finally {
            data.recycle();
        }
    }

    private static long[] readSession(Parcel r) {
        long id = r.readLong();
        int backend = r.readInt();
        int backendUid = r.readInt();
        int callerUid = r.readInt();
        int callerPid = r.readInt();
        String pkg = r.readString();
        System.out.println("sessionId=" + id + " backend=" + backend + " backendUid=" + backendUid +
                " callerUid=" + callerUid + " callerPid=" + callerPid + " package=" + pkg);
        return new long[]{id, backendUid};
    }

    public static void main(String[] args) {
        try {
            if (Looper.getMainLooper() == null) {
                Looper.prepareMainLooper();
            }
            Context systemContext = ActivityThread.systemMain().getSystemContext();
            String pkg = packageName();
            Context packageContext = systemContext.createPackageContext(
                    pkg,
                    Context.CONTEXT_IGNORE_SECURITY
            );
            Context context = new ContextWrapper(packageContext) {
                @Override public String getOpPackageName() { return pkg; }
                @Override public String getAttributionTag() { return null; }
            };
            IBinder remote = discover(context);
            Parcel hello = transact(remote, TX_HELLO, null);
            int version = hello.readInt(); hello.recycle();
            System.out.println("HELLO=" + version + " binderAlive=" + remote.isBinderAlive());

            Parcel opened = transact(remote, TX_REQUEST_SESSION, d -> {
                d.writeStrongBinder(LIFETIME); d.writeInt(SYSTEM); d.writeString(pkg);
            });
            long[] session = readSession(opened); opened.recycle();
            if (session[1] != 1000) throw new IllegalStateException("SYSTEM backend UID mismatch: " + session[1]);

            Parcel ping = transact(remote, TX_PING, d -> d.writeLong(session[0]));
            System.out.print("PING "); readSession(ping); ping.recycle();

            Parcel status = transact(remote, TX_STATUS, d -> d.writeLong(session[0]));
            System.out.print("STATUS "); readSession(status); status.recycle();

            Parcel close = transact(remote, TX_CLOSE, d -> d.writeLong(session[0]));
            boolean closed = close.readInt() != 0; close.recycle();
            System.out.println("CLOSE=" + closed);
            System.out.println("TOKENX_SYSTEM_SESSION_TEST=PASS");
        } catch (Throwable t) {
            t.printStackTrace(System.err);
            System.out.println("TOKENX_SYSTEM_SESSION_TEST=FAIL");
            System.exit(1);
        }
    }

    private interface Writer { void write(Parcel parcel); }
}
