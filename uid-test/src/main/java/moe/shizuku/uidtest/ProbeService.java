package moe.shizuku.uidtest;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

/** Read-only identity endpoint. No shell, settings writer, or framework-hosted component. */
public final class ProbeService extends Service {
    private static final int TRANSACTION = 0x5f544b4e;
    private static final String DESCRIPTOR = "moe.shizuku.tokenx.uidtest.IProbe";
    private final Binder endpoint = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code != TRANSACTION) return super.onTransact(code, data, reply, flags);
            if (reply == null || (flags & IBinder.FLAG_ONEWAY) != 0) return false;
            try {
                data.enforceInterface(DESCRIPTOR);
                String[] packages = getPackageManager().getPackagesForUid(Binder.getCallingUid());
                if (packages == null || !Arrays.asList(packages).contains("moe.shizuku.privileged.api"))
                    throw new SecurityException("TokenX manager required");
                // The test certificate is intentionally different from the manager certificate.
                // The UID is resolved by Package Manager, never supplied by the client.
                long nonce = data.readLong();
                data.enforceNoDataAvail();
                reply.writeNoException();
                reply.writeInt(1);
                reply.writeInt(BuildConfig.VERSION_CODE);
                reply.writeLong(nonce);
                reply.writeInt(Process.myUid());
                reply.writeInt(Process.myPid());
                reply.writeString(self("/proc/self/cmdline"));
                reply.writeString(self("/proc/self/attr/current"));
                reply.writeString(BuildConfig.FLAVOR);
            } catch (Exception e) {
                reply.setDataSize(0); reply.setDataPosition(0); reply.writeException(e);
            }
            return true;
        }
    };

    static String self(String path) {
        try {
            return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                    .replace(String.valueOf((char) 0), "").trim();
        } catch (Exception e) { return "unavailable"; }
    }
    @Override public IBinder onBind(Intent intent) { return endpoint; }
}
