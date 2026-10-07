package rikka.shizuku.shell;

import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.system.Os;
import android.text.TextUtils;
import java.util.Objects;
import rikka.hidden.compat.PackageManagerApis;

public final class TokenXTransportClient {
    private static final String DESCRIPTOR = "moe.shizuku.tokenx.ITransport";
    private static final int TX_HELLO = IBinder.FIRST_CALL_TRANSACTION;
    private static final int TX_REQUEST_SESSION = IBinder.FIRST_CALL_TRANSACTION + 1;
    private static final int TX_PING = IBinder.FIRST_CALL_TRANSACTION + 2;
    private static final int TX_STATUS = IBinder.FIRST_CALL_TRANSACTION + 3;
    private static final int TX_CLOSE = IBinder.FIRST_CALL_TRANSACTION + 4;
    private static final int SYSTEM = 1;
    private static final Binder LIFETIME = new Binder();
    private static Handler handler;
    private static String callingPackage;

    private static final Binder RECEIVER = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code != 2) return false;
            IBinder transport = data.readStrongBinder();
            int generation = data.readInt();
            handler.post(() -> runTest(transport, generation));
            return true;
        }
    };

    private static String packageName() {
        var packages = PackageManagerApis.getPackagesForUidNoThrow(Os.getuid());
        if (packages.size() == 1) return packages.get(0);
        String p = System.getenv("RISH_APPLICATION_ID");
        return TextUtils.isEmpty(p) || "PKG".equals(p) ? "com.termux" : p;
    }

    private static void requestTransport() throws Exception {
        Bundle bundle = new Bundle();
        bundle.putBinder("binder", RECEIVER);
        String manager = System.getenv("MANAGER_APPLICATION_ID");
        if (TextUtils.isEmpty(manager) || "MANAGER_PKG".equals(manager)) manager = BuildConfig.MANAGER_APPLICATION_ID;
        Intent intent = new Intent("moe.shizuku.tokenx.intent.action.REQUEST_TRANSPORT")
                .setPackage(manager).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .putExtra("data", bundle).putExtra("tokenx_package", callingPackage);
        IBinder amBinder = android.os.ServiceManager.getService("activity");
        IActivityManager am = Build.VERSION.SDK_INT >= 26 ? IActivityManager.Stub.asInterface(amBinder)
                : ActivityManagerNative.asInterface(amBinder);
        am.broadcastIntent(null, intent, null, null, 0, null, null, null, -1, null, true, false, 0);
    }

    private interface Writer { void write(Parcel p); }
    private static Parcel tx(IBinder remote, int code, Writer w) throws Exception {
        Parcel d=Parcel.obtain(), r=Parcel.obtain();
        d.writeInterfaceToken(DESCRIPTOR); if(w!=null) w.write(d);
        if(!remote.transact(code,d,r,0)) throw new IllegalStateException("transaction rejected "+code);
        d.recycle(); r.readException(); return r;
    }
    private static long readSession(Parcel r, String label) {
        long id=r.readLong(); int backend=r.readInt(), uid=r.readInt(), callerUid=r.readInt(), callerPid=r.readInt();
        String pkg=r.readString();
        System.out.println(label+" sessionId="+id+" backend="+backend+" backendUid="+uid+
                " callerUid="+callerUid+" callerPid="+callerPid+" package="+pkg);
        if(uid!=1000) throw new IllegalStateException("SYSTEM backend UID mismatch: "+uid);
        return id;
    }
    private static void runTest(IBinder remote, int generation) {
        try {
            if(remote==null || !remote.isBinderAlive()) throw new IllegalStateException("transport unavailable");
            Parcel h=tx(remote,TX_HELLO,null); int v=h.readInt(); h.recycle();
            System.out.println("HELLO="+v+" generation="+generation+" binderAlive="+remote.isBinderAlive());
            Parcel o=tx(remote,TX_REQUEST_SESSION,p->{p.writeStrongBinder(LIFETIME);p.writeInt(SYSTEM);p.writeString(callingPackage);});
            long id=readSession(o,"OPEN"); o.recycle();
            Parcel p=tx(remote,TX_PING,x->x.writeLong(id)); readSession(p,"PING"); p.recycle();
            Parcel st=tx(remote,TX_STATUS,x->x.writeLong(id)); readSession(st,"STATUS"); st.recycle();
            Parcel c=tx(remote,TX_CLOSE,x->x.writeLong(id)); boolean closed=c.readInt()!=0;c.recycle();
            System.out.println("CLOSE="+closed);
            System.out.println("TOKENX_SYSTEM_SESSION_TEST=PASS");
            System.exit(0);
        } catch(Throwable t) {
            t.printStackTrace(System.err); System.out.println("TOKENX_SYSTEM_SESSION_TEST=FAIL"); System.exit(1);
        }
    }

    public static void main(String[] args) {
        callingPackage=packageName();
        if(Looper.getMainLooper()==null) Looper.prepareMainLooper();
        handler=new Handler(Looper.getMainLooper());
        try { requestTransport(); }
        catch(Throwable t){t.printStackTrace(System.err);System.out.println("TOKENX_SYSTEM_SESSION_TEST=FAIL");return;}
        handler.postDelayed(()->{System.err.println("TokenX transport request timeout");System.exit(1);},5000);
        Looper.loop();
    }
}
