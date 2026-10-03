package rikka.shizuku.server;

import static android.Manifest.permission.WRITE_SECURE_SETTINGS;
import static rikka.shizuku.ShizukuApiConstants.ATTACH_APPLICATION_API_VERSION;
import static rikka.shizuku.ShizukuApiConstants.ATTACH_APPLICATION_PACKAGE_NAME;
import static rikka.shizuku.ShizukuApiConstants.BIND_APPLICATION_PERMISSION_GRANTED;
import static rikka.shizuku.ShizukuApiConstants.BIND_APPLICATION_SERVER_PATCH_VERSION;
import static rikka.shizuku.ShizukuApiConstants.BIND_APPLICATION_SERVER_SECONTEXT;
import static rikka.shizuku.ShizukuApiConstants.BIND_APPLICATION_SERVER_UID;
import static rikka.shizuku.ShizukuApiConstants.BIND_APPLICATION_SERVER_VERSION;
import static rikka.shizuku.ShizukuApiConstants.BIND_APPLICATION_SHOULD_SHOW_REQUEST_PERMISSION_RATIONALE;
import static rikka.shizuku.ShizukuApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED;
import static rikka.shizuku.ShizukuApiConstants.REQUEST_PERMISSION_REPLY_IS_ONETIME;
import static rikka.shizuku.server.ServerConstants.PERMISSION;

import android.content.Context;
import android.content.IContentProvider;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.ddm.DdmHandleAppName;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Objects;
import java.util.stream.Stream;

import kotlin.collections.ArraysKt;
import moe.shizuku.api.BinderContainer;
import moe.shizuku.common.util.BuildUtils;
import moe.shizuku.common.util.OsUtils;
import moe.shizuku.server.IShizukuApplication;
import rikka.hidden.compat.ActivityManagerApis;
import rikka.hidden.compat.DeviceIdleControllerApis;
import rikka.hidden.compat.PackageManagerApis;
import rikka.shizuku.server.util.Android17Compat;
import rikka.hidden.compat.UserManagerApis;
import rikka.parcelablelist.ParcelableListSlice;
import rikka.shizuku.ShizukuApiConstants;
import rikka.shizuku.server.api.IContentProviderUtils;
import rikka.shizuku.server.util.HandlerUtil;
import rikka.shizuku.server.util.InstalledPackagesCompat;
import rikka.shizuku.server.util.UserHandleCompat;

public class ShizukuService extends Service<ShizukuUserServiceManager, ShizukuClientManager, ShizukuConfigManager> {

    private static volatile boolean EMBEDDED_SYSTEM_SERVER = false;
    private static final String TOKENX_SYSTEM_SERVER_SERVICE = "tokenx_system_server";
    private static final String TOKENX_SYSTEM_SERVER_DESCRIPTOR = "moe.shizuku.tokenx.ISystemServerBridge";
    private static final Binder TOKENX_SYSTEM_SERVER_HEALTH_BINDER = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == IBinder.FIRST_CALL_TRANSACTION) {
                data.enforceInterface(TOKENX_SYSTEM_SERVER_DESCRIPTOR);
                reply.writeNoException();
                reply.writeInt(Process.myUid());
                return true;
            }
            if (code == IBinder.FIRST_CALL_TRANSACTION + 1) {
                data.enforceInterface(TOKENX_SYSTEM_SERVER_DESCRIPTOR);
                final String script = data.readString();
                if (script == null || script.length() > 131072) {
                    throw new IllegalArgumentException("invalid TokenX Sserver command");
                }
                Process process = null;
                try {
                    process = new ProcessBuilder("/system/bin/sh", "-c", script)
                            .redirectErrorStream(false)
                            .start();
                    final Process commandProcess = process;
                    final AtomicReference<String> stdout = new AtomicReference<>("");
                    final AtomicReference<String> stderr = new AtomicReference<>("");
                    Thread outThread = new Thread(() -> stdout.set(readTokenXStream(commandProcess.getInputStream())), "TokenX-Sserver-out");
                    Thread errThread = new Thread(() -> stderr.set(readTokenXStream(commandProcess.getErrorStream())), "TokenX-Sserver-err");
                    outThread.setDaemon(true);
                    errThread.setDaemon(true);
                    outThread.start();
                    errThread.start();
                    boolean finished = process.waitFor(30, TimeUnit.SECONDS);
                    if (!finished) process.destroyForcibly();
                    outThread.join(1000);
                    errThread.join(1000);
                    reply.writeNoException();
                    reply.writeInt(finished ? process.exitValue() : 124);
                    reply.writeString(stdout.get());
                    reply.writeString(finished ? stderr.get() : stderr.get() + "\nTokenX Sserver: command timed out.");
                    return true;
                } catch (Throwable tr) {
                    if (process != null) process.destroyForcibly();
                    reply.writeNoException();
                    reply.writeInt(-1);
                    reply.writeString("");
                    reply.writeString(tr.getClass().getSimpleName() + ": " + tr.getMessage());
                    return true;
                }
            }
            return super.onTransact(code, data, reply, flags);
        }
    };

    private static String readTokenXStream(java.io.InputStream input) {
        try {
            byte[] bytes = input.readNBytes(1024 * 1024);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Throwable tr) {
            return "stream error: " + tr.getMessage();
        }
    }

    private static void publishTokenXSystemServerHealthBinder() {
        if (Process.myUid() != Process.SYSTEM_UID) return;
        try {
            IBinder existing = ServiceManager.getService(TOKENX_SYSTEM_SERVER_SERVICE);
            if (existing == null) {
                ServiceManager.addService(TOKENX_SYSTEM_SERVER_SERVICE, TOKENX_SYSTEM_SERVER_HEALTH_BINDER);
                ServerLog.mark("TOKENX_BINDER_PUBLISHED: " + TOKENX_SYSTEM_SERVER_SERVICE
                        + " uid=" + Process.myUid() + " pid=" + Process.myPid());
            } else {
                ServerLog.mark("TOKENX_BINDER_PRESENT: " + TOKENX_SYSTEM_SERVER_SERVICE);
            }
        } catch (Throwable tr) {
            ServerLog.mark("TOKENX_BINDER_PUBLISH_FAILED: " + Log.getStackTraceString(tr));
            LOGGER.e(tr, "failed to publish TokenX system_server health Binder");
        }
    }

    public static final String MANAGER_APPLICATION_ID;

    static {
        String packageName = null;

        // TokenX can run in two very different processes:
        //  1. the normal standalone server, where CLASSPATH points at the manager APK; and
        //  2. LSPosed's system_server hook (UID 1000), where CLASSPATH belongs to Android
        //     framework/services and therefore cannot identify the TokenX manager.
        //
        // The old code parsed system_server's CLASSPATH and later concluded that the manager
        // APK was not installed. Use TokenX's real manager package in the embedded path and
        // retain the upstream CLASSPATH discovery for root/shell standalone launches.
        if (Process.myUid() == Process.SYSTEM_UID) {
            packageName = "moe.shizuku.privileged.api";
            LOGGER.i("Embedded system_server manager package is " + packageName);
            ServerLog.mark("MANAGER_RESOLVED: embedded system_server -> " + packageName);
        } else {
            try {
                String apk = System.getenv("CLASSPATH");
                if (apk == null || apk.isEmpty()) {
                    throw new IllegalStateException("CLASSPATH is empty");
                }

                int lastSlash = apk.lastIndexOf(File.separatorChar);
                String parentDir = apk.substring(0, lastSlash);

                int secondLastSlash = parentDir.lastIndexOf(File.separatorChar);
                String dirName = parentDir.substring(secondLastSlash + 1);

                int dash = dirName.indexOf('-');
                if (dash > 0) {
                    packageName = dirName.substring(0, dash);
                } else {
                    packageName = dirName;
                }

                LOGGER.i("Manager package name is " + packageName);
            } catch (Throwable tr) {
                LOGGER.w("Couldn't get manager package name from CLASSPATH", tr);
            }
        }

        MANAGER_APPLICATION_ID = packageName;
    }


    public static void main(String[] args) {
        // First, and before anything can fail: from here on the manager can read what this
        // process did, which it otherwise cannot tell apart from the server never running.
        ServerLog.mark("main entered, uid=" + Process.myUid() + ", sdk=" + Build.VERSION.SDK_INT);

        try {
            DdmHandleAppName.setAppName("shizuku_server", 0);
            // Keep Rish completely out of the embedded system_server class-link path.
            // LSPosed's module ClassLoader does not expose librish.so to system_server.
            // The standalone root/shell server still configures Rish here when main() runs.
            try {
                Class<?> rishConfig = Class.forName("rikka.rish.RishConfig");
                rishConfig.getMethod("setLibraryPath", String.class)
                        .invoke(null, System.getProperty("shizuku.library.path"));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unable to configure Rish for standalone server", e);
            }

            Looper.prepareMainLooper();
            new ShizukuService();
            ServerLog.mark("service created, entering the main looper");

            Looper.loop();
        } catch (Throwable tr) {
            // Nothing else can report this one: there is no manager connection to report it
            // over yet, and the logcat is not readable by the app that is waiting.
            ServerLog.mark("startup failed: " + Log.getStackTraceString(tr));
            if (tr instanceof RuntimeException) throw (RuntimeException) tr;
            if (tr instanceof Error) throw (Error) tr;
            throw new IllegalStateException("Shizuku server startup failed", tr);
        }
    }

    /** Start the full Shizuku service inside system_server while preserving UID 1000. */
    public static synchronized void startEmbeddedSystemServer() {
        if (Process.myUid() != Process.SYSTEM_UID) {
            throw new SecurityException("Embedded backend requires system_server UID 1000");
        }

        EMBEDDED_SYSTEM_SERVER = true;
        publishTokenXSystemServerHealthBinder();
        ServerLog.mark("SYSTEM_SERVER_HOOK: embedded start uid=" + Process.myUid()
                + ", pid=" + Process.myPid());
        ServerLog.mark("NATIVE_READY: embedded backend does not require librish");

        final Looper mainLooper = Looper.getMainLooper();
        if (mainLooper == null) {
            EMBEDDED_SYSTEM_SERVER = false;
            throw new IllegalStateException("system_server main looper is not ready");
        }

        if (Looper.myLooper() == mainLooper) {
            new ShizukuService();
            ServerLog.mark("SERVER_CREATED: embedded backend ready on main looper");
            ServerLog.mark("BINDER_PUBLISHED: provider binder handoff scheduled");
            return;
        }

        final CountDownLatch ready = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        Runnable start = () -> {
            try {
                ServerLog.mark("embedded service constructing on system_server main looper");
                new ShizukuService();
                ServerLog.mark("SERVER_CREATED: embedded service constructed");
                ServerLog.mark("BINDER_PUBLISHED: provider binder handoff scheduled");
            } catch (Throwable tr) {
                failure.set(tr);
                ServerLog.mark("embedded startup failed: " + Log.getStackTraceString(tr));
                LOGGER.e(tr, "embedded system_server startup failed");
            } finally {
                ready.countDown();
            }
        };

        if (!new Handler(mainLooper).post(start)) {
            EMBEDDED_SYSTEM_SERVER = false;
            throw new IllegalStateException("could not post embedded backend to system_server main looper");
        }

        try {
            if (!ready.await(30, TimeUnit.SECONDS)) {
                EMBEDDED_SYSTEM_SERVER = false;
                throw new IllegalStateException("timed out constructing embedded backend");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            EMBEDDED_SYSTEM_SERVER = false;
            throw new IllegalStateException("interrupted while constructing embedded backend", ex);
        }

        Throwable startupFailure = failure.get();
        if (startupFailure != null) {
            EMBEDDED_SYSTEM_SERVER = false;
            throw new IllegalStateException("embedded backend construction failed", startupFailure);
        }

        ServerLog.mark("CLIENT_HANDOFF: embedded backend ready; provider binder publication active");
    }

    private static void waitSystemService(String name) {
        while (ServiceManager.getService(name) == null) {
            try {
                LOGGER.i("service " + name + " is not started, wait 1s.");
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                LOGGER.w(e.getMessage(), e);
            }
        }
    }

    public static ApplicationInfo getManagerApplicationInfo() {
        return Android17Compat.getApplicationInfo(MANAGER_APPLICATION_ID, 0, 0);
    }

    @SuppressWarnings({"FieldCanBeLocal"})
    private final Handler mainHandler = new Handler(Looper.myLooper());
    //private final Context systemContext = HiddenApiBridge.getSystemContext();
    private final ShizukuClientManager clientManager;
    private final ShizukuConfigManager configManager;
    private final int managerAppId;

    public ShizukuService() {
        super();

        HandlerUtil.setMainHandler(mainHandler);

        LOGGER.i("starting server...");

        waitSystemService("package");
        waitSystemService(Context.ACTIVITY_SERVICE);
        waitSystemService(Context.USER_SERVICE);
        waitSystemService(Context.APP_OPS_SERVICE);

        ApplicationInfo ai = getManagerApplicationInfo();
        if (ai == null) {
            if (EMBEDDED_SYSTEM_SERVER) {
                throw new IllegalStateException("TokenX manager APK is not installed");
            }
            System.exit(ServerConstants.MANAGER_APP_NOT_FOUND);
        }

        assert ai != null;
        managerAppId = ai.uid;

        configManager = getConfigManager();
        clientManager = getClientManager();

        ApkChangedObservers.start(ai.sourceDir, () -> {
            if (getManagerApplicationInfo() == null) {
                if (EMBEDDED_SYSTEM_SERVER) {
                    LOGGER.w("manager app is unavailable; embedded system_server backend stays alive");
                } else {
                    LOGGER.w("manager app is uninstalled in user 0, exiting...");
                    System.exit(ServerConstants.MANAGER_APP_NOT_FOUND);
                }
            }
        });

        BinderSender.register(this);

        mainHandler.post(() -> {
            sendBinderToClient();
            sendBinderToManager();

            // The embedded UID-1000 backend may be ready before the manager provider.
            // Retry Binder publication only; do not launch another Shizuku server.
            if (Process.myUid() == Process.SYSTEM_UID) {
                final long[] retryDelays = {500L, 1500L, 3000L, 5000L, 8000L, 12000L};
                for (long retryDelay : retryDelays) {
                    mainHandler.postDelayed(() -> {
                        try {
                            ServerLog.mark("embedded binder handoff retry after " + retryDelay + "ms");
                            sendBinderToManager();
                        } catch (Throwable tr) {
                            ServerLog.mark("embedded binder handoff retry failed: "
                                    + Log.getStackTraceString(tr));
                            LOGGER.e(tr, "embedded binder handoff retry failed");
                        }
                    }, retryDelay);
                }
            }
        });
    }

    @Override
    public ShizukuUserServiceManager onCreateUserServiceManager() {
        return new ShizukuUserServiceManager();
    }

    @Override
    public ShizukuClientManager onCreateClientManager() {
        return new ShizukuClientManager(getConfigManager());
    }

    @Override
    public ShizukuConfigManager onCreateConfigManager() {
        return new ShizukuConfigManager();
    }

    @Override
    public boolean checkCallerManagerPermission(String func, int callingUid, int callingPid) {
        return UserHandleCompat.getAppId(callingUid) == managerAppId;
    }

    private int checkCallingPermission() {
        try {
            return ActivityManagerApis.checkPermission(ServerConstants.PERMISSION,
                    Binder.getCallingPid(),
                    Binder.getCallingUid());
        } catch (Throwable tr) {
            LOGGER.w(tr, "checkCallingPermission");
            return PackageManager.PERMISSION_DENIED;
        }
    }

    @Override
    public boolean checkCallerPermission(String func, int callingUid, int callingPid, @Nullable ClientRecord clientRecord) {
        if (UserHandleCompat.getAppId(callingUid) == managerAppId) {
            return true;
        }
        if (clientRecord == null && checkCallingPermission() == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        return false;
    }

    @Override
    public void exit() {
        enforceManagerPermission("exit");

        // TokenX can run this service embedded inside Android's real system_server.
        // System.exit() is valid for the traditional standalone Shizuku process, but
        // from UID 1000/system_server it terminates the Android framework and causes
        // an immediate soft reboot. Never allow the normal Shizuku/TokenX Stop action
        // to kill its host process.
        if (Process.myUid() == Process.SYSTEM_UID) {
            LOGGER.w("exit requested for embedded system_server backend; refusing to terminate system_server");
            ServerLog.mark("embedded stop requested: system_server protected; process exit skipped");
            return;
        }

        LOGGER.i("exit");
        System.exit(0);
    }

    @Override
    public void attachUserService(IBinder binder, Bundle options) {
        enforceManagerPermission("func");

        super.attachUserService(binder, options);
    }

    @Override
    public void attachApplication(IShizukuApplication application, Bundle args) {
        if (application == null || args == null) {
            return;
        }

        String requestPackageName = args.getString(ATTACH_APPLICATION_PACKAGE_NAME);
        if (requestPackageName == null) {
            return;
        }

        ServerLog.mark("TOKENX_CLIENT_ATTACH: package=" + requestPackageName
                + ", callingUid=" + Binder.getCallingUid()
                + ", callingPid=" + Binder.getCallingPid()
                + ", backendUid=" + Process.myUid()
                + ", backendPid=" + Process.myPid()
                + ", embedded=" + EMBEDDED_SYSTEM_SERVER);
        int apiVersion = args.getInt(ATTACH_APPLICATION_API_VERSION, -1);

        int callingPid = Binder.getCallingPid();
        int callingUid = Binder.getCallingUid();
        boolean isManager;
        ClientRecord clientRecord = null;

        List<String> packages = PackageManagerApis.getPackagesForUidNoThrow(callingUid);
        if (!packages.contains(requestPackageName)) {
            LOGGER.w("Request package " + requestPackageName + "does not belong to uid " + callingUid);
            throw new SecurityException("Request package " + requestPackageName + "does not belong to uid " + callingUid);
        }

        isManager = MANAGER_APPLICATION_ID.equals(requestPackageName);

        if (clientManager.findClient(callingUid, callingPid) == null) {
            synchronized (this) {
                clientRecord = clientManager.addClient(callingUid, callingPid, application, requestPackageName, apiVersion);
            }
            if (clientRecord == null) {
                LOGGER.w("Add client failed");
                return;
            }
        }

        LOGGER.d("attachApplication: %s %d %d", requestPackageName, callingUid, callingPid);

        int replyServerVersion = ShizukuApiConstants.SERVER_VERSION;
        if (apiVersion == -1) {
            // ShizukuBinderWrapper has adapted API v13 in dev.rikka.shizuku:api 12.2.0, however
            // attachApplication in 12.2.0 is still old, so that server treat the client as pre 13.
            // This finally cause transactRemote fails.
            // So we can pass 12 here to pretend we are v12 server.
            replyServerVersion = 12;
        }

        Bundle reply = new Bundle();
        reply.putInt(BIND_APPLICATION_SERVER_UID, OsUtils.getUid());
        reply.putInt(BIND_APPLICATION_SERVER_VERSION, replyServerVersion);
        reply.putString(BIND_APPLICATION_SERVER_SECONTEXT, OsUtils.getSELinuxContext());
        reply.putInt(BIND_APPLICATION_SERVER_PATCH_VERSION, ShizukuApiConstants.SERVER_PATCH_VERSION);
        if (!isManager) {
            reply.putBoolean(BIND_APPLICATION_PERMISSION_GRANTED, Objects.requireNonNull(clientRecord).allowed);
            reply.putBoolean(BIND_APPLICATION_SHOULD_SHOW_REQUEST_PERMISSION_RATIONALE, false);
        } else {
            // Granted to itself so a fresh install can switch wireless debugging on without
            // anyone at a computer. The compat layer says whether the platform was reached at
            // all: a signature it could not match is not the same thing as a platform that
            // refused, and either way this must not pass in silence.
            if (!Android17Compat.grantRuntimePermission(MANAGER_APPLICATION_ID,
                    WRITE_SECURE_SETTINGS, UserHandleCompat.getUserId(callingUid))) {
                LOGGER.w("could not grant WRITE_SECURE_SETTINGS to the manager");
            }
        }
        try {
            application.bindApplication(reply);
        } catch (Throwable e) {
            LOGGER.w(e, "attachApplication");
        }
    }

    @Override
    public void showPermissionConfirmation(int requestCode, @NonNull ClientRecord clientRecord, int callingUid, int callingPid, int userId) {
        /*
         * TokenX can host Shizuku directly inside system_server (UID 1000). Binder keeps
         * the rish/client identity while this callback runs. Android 17 enforces
         * QUERY_USERS/MANAGE_USERS for getUserInfo(), so perform framework lookups under
         * the service identity while retaining the captured caller uid/pid for the grant.
         */
        final long identity = Binder.clearCallingIdentity();
        try {
            ApplicationInfo ai = Android17Compat.getApplicationInfo(clientRecord.packageName, 0, userId);
            if (ai == null) {
                return;
            }

            PackageInfo pi = Android17Compat.getPackageInfo(MANAGER_APPLICATION_ID, 0, userId);
            UserInfo userInfo = UserManagerApis.getUserInfo(userId);
            boolean isWorkProfileUser = BuildUtils.atLeast30() ?
                    "android.os.usertype.profile.MANAGED".equals(userInfo.userType) :
                    (userInfo.flags & UserInfo.FLAG_MANAGED_PROFILE) != 0;
            if (pi == null && !isWorkProfileUser) {
                LOGGER.w("Manager not found in non work profile user %d. Revoke permission", userId);
                clientRecord.dispatchRequestPermissionResult(requestCode, false);
                return;
            }

            Intent intent = new Intent(ServerConstants.REQUEST_PERMISSION_ACTION)
                    .setPackage(MANAGER_APPLICATION_ID)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                    .putExtra("uid", callingUid)
                    .putExtra("pid", callingPid)
                    .putExtra("requestCode", requestCode)
                    .putExtra("applicationInfo", ai);
            ActivityManagerApis.startActivityNoThrow(intent, null, isWorkProfileUser ? 0 : userId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void dispatchPermissionConfirmationResult(int requestUid, int requestPid, int requestCode, Bundle data) throws RemoteException {
        if (UserHandleCompat.getAppId(Binder.getCallingUid()) != managerAppId) {
            LOGGER.w("dispatchPermissionConfirmationResult called not from the manager package");
            return;
        }

        if (data == null) {
            return;
        }

        boolean allowed = data.getBoolean(REQUEST_PERMISSION_REPLY_ALLOWED);
        boolean onetime = data.getBoolean(REQUEST_PERMISSION_REPLY_IS_ONETIME);

        LOGGER.i("dispatchPermissionConfirmationResult: uid=%d, pid=%d, requestCode=%d, allowed=%s, onetime=%s",
                requestUid, requestPid, requestCode, Boolean.toString(allowed), Boolean.toString(onetime));

        List<ClientRecord> records = clientManager.findClients(requestUid);
        List<String> packages = new ArrayList<>();
        if (records.isEmpty()) {
            LOGGER.w("dispatchPermissionConfirmationResult: no client for uid %d was found", requestUid);
        } else {
            for (ClientRecord record : records) {
                packages.add(record.packageName);
                record.allowed = allowed;
                if (record.pid == requestPid) {
                    record.dispatchRequestPermissionResult(requestCode, allowed);
                }
            }
        }

        if (!onetime) {
            configManager.update(requestUid, packages, ConfigManager.MASK_PERMISSION, allowed ? ConfigManager.FLAG_ALLOWED : ConfigManager.FLAG_DENIED);
        }

        if (!onetime && allowed) {
            int userId = UserHandleCompat.getUserId(requestUid);

            for (String packageName : PackageManagerApis.getPackagesForUidNoThrow(requestUid)) {
                PackageInfo pi = Android17Compat.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS, userId);
                if (pi == null || pi.requestedPermissions == null || !ArraysKt.contains(pi.requestedPermissions, PERMISSION)) {
                    continue;
                }

                int deviceId = 0;//Context.DEVICE_ID_DEFAULT
                // The answer is reported rather than assumed: a permission that was asked for
                // and never changed has to say so, or the app that asked is told it worked.
                if (allowed) {
                    if (!Android17Compat.grantRuntimePermission(packageName, PERMISSION, userId)) {
                        LOGGER.w("could not grant %s to %s", PERMISSION, packageName);
                    }
                } else {
                    if (!Android17Compat.revokeRuntimePermission(packageName, PERMISSION, userId)) {
                        LOGGER.w("could not revoke %s from %s", PERMISSION, packageName);
                    }
                }
            }
        }
    }

    private int  getFlagsForUidInternal(int uid, int mask, boolean allowRuntimePermission) {
        ShizukuConfig.PackageEntry entry = configManager.find(uid);
        if (entry != null) {
            return entry.flags & mask;
        }

        if (allowRuntimePermission && (mask & ConfigManager.MASK_PERMISSION) != 0) {
            int userId = UserHandleCompat.getUserId(uid);
            for (String packageName : PackageManagerApis.getPackagesForUidNoThrow(uid)) {
                PackageInfo pi = Android17Compat.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS, userId);
                if (pi == null || pi.requestedPermissions == null || !ArraysKt.contains(pi.requestedPermissions, PERMISSION)) {
                    continue;
                }

                try {
                    if (Android17Compat.checkPermission(PERMISSION, uid) == PackageManager.PERMISSION_GRANTED) {
                        return ConfigManager.FLAG_ALLOWED;
                    }
                } catch (Throwable e) {
                    LOGGER.w("getFlagsForUid");
                }
            }
        }
        return 0;
    }

    @Override
    public int getFlagsForUid(int uid, int mask) {
        if (UserHandleCompat.getAppId(Binder.getCallingUid()) != managerAppId) {
            LOGGER.w("updateFlagsForUid is allowed to be called only from the manager");
            return 0;
        }
        return getFlagsForUidInternal(uid, mask, true);
    }

    @Override
    public void updateFlagsForUid(int uid, int mask, int value) throws RemoteException {
        if (UserHandleCompat.getAppId(Binder.getCallingUid()) != managerAppId) {
            LOGGER.w("updateFlagsForUid is allowed to be called only from the manager");
            return;
        }

        int userId = UserHandleCompat.getUserId(uid);

        if ((mask & ConfigManager.MASK_PERMISSION) != 0) {
            boolean allowed = (value & ConfigManager.FLAG_ALLOWED) != 0;
            boolean denied = (value & ConfigManager.FLAG_DENIED) != 0;

            List<ClientRecord> records = clientManager.findClients(uid);
            for (ClientRecord record : records) {
                if (allowed) {
                    record.allowed = true;
                } else {
                    record.allowed = false;
                    ActivityManagerApis.forceStopPackageNoThrow(record.packageName, UserHandleCompat.getUserId(record.uid));
                    onPermissionRevoked(record.packageName);
                }
            }

            for (String packageName : PackageManagerApis.getPackagesForUidNoThrow(uid)) {
                PackageInfo pi = Android17Compat.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS, userId);
                if (pi == null || pi.requestedPermissions == null || !ArraysKt.contains(pi.requestedPermissions, PERMISSION)) {
                    continue;
                }

                int deviceId = 0;//Context.DEVICE_ID_DEFAULT
                if (allowed) {
                    Android17Compat.grantRuntimePermission(packageName, PERMISSION, userId);
                } else {
                    Android17Compat.revokeRuntimePermission(packageName, PERMISSION, userId);
                }

                // TODO kill user service using
            }
        }

        configManager.update(uid, null, mask, value);
    }

    private void onPermissionRevoked(String packageName) {
        // TODO add runtime permission listener
        getUserServiceManager().removeUserServicesForPackage(packageName);
    }

    private ParcelableListSlice<PackageInfo> getApplications(int userId) {
        List<PackageInfo> list = new ArrayList<>();
        List<Integer> users = new ArrayList<>();
        if (userId == -1) {
            users.addAll(UserManagerApis.getUserIdsNoThrow());
        } else {
            users.add(userId);
        }

        for (int user : users) {
            for (PackageInfo pi : InstalledPackagesCompat.getInstalledPackagesNoThrow(PackageManager.GET_META_DATA | PackageManager.GET_PERMISSIONS, user)) {
                if (Objects.equals(MANAGER_APPLICATION_ID, pi.packageName)) continue;
                if (pi.applicationInfo == null) continue;

                int uid = pi.applicationInfo.uid;
                int flags = 0;
                ShizukuConfig.PackageEntry entry = configManager.find(uid);
                if (entry != null) {
                    if (entry.packages != null && !entry.packages.contains(pi.packageName))
                        continue;
                    flags = entry.flags & ConfigManager.MASK_PERMISSION;
                }

                if (flags != 0) {
                    list.add(pi);
                } else if (pi.applicationInfo.metaData != null
                        && pi.applicationInfo.metaData.getBoolean("moe.shizuku.client.V3_SUPPORT", false)
                        && pi.requestedPermissions != null
                        && ArraysKt.contains(pi.requestedPermissions, PERMISSION)) {
                    list.add(pi);
                }
            }

        }
        return new ParcelableListSlice<>(list);
    }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        // Rish transactions are handled by server-shared's RishService. When TokenX is
        // embedded directly in system_server, handling them under the incoming app Binder
        // identity makes framework calls inherit the terminal app UID. Android 17 then
        // rejects user queries with QUERY_USERS/MANAGE_USERS before the shell can start.
        //
        // Authenticate the original caller first, then clear only the nested Rish Binder
        // identity. This keeps Shizuku's client permission boundary intact while making
        // framework calls/fork setup execute as the UID-1000 backend.
        if (EMBEDDED_SYSTEM_SERVER && code >= 30000 && code <= 30002) {
            // Android 17 libbinder deliberately aborts when a process that has already
            // initialized ProcessState forks and the child subsequently touches Binder.
            // system_server is permanently Binder-initialized, so rish's native fork/exec
            // host is not a safe execution path here. Fail closed instead of allowing a
            // terminal request to take down a system_server child/process.
            enforceCallingPermission("rish");
            ServerLog.mark("RISH_TKN_BLOCKED: embedded system_server cannot fork rish safely on Android 17; code="
                    + code + ", uid=" + Process.myUid());
            throw new RemoteException(
                    "TokenX embedded system_server rish is disabled: Android 17 forbids Binder use after fork");
        }

        //LOGGER.d("transact: code=%d, calling uid=%d", code, Binder.getCallingUid());
        if (code == ServerConstants.BINDER_TRANSACTION_getApplications) {
            data.enforceInterface(ShizukuApiConstants.BINDER_DESCRIPTOR);
            int userId = data.readInt();
            ParcelableListSlice<PackageInfo> result = getApplications(userId);
            reply.writeNoException();
            result.writeToParcel(reply, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
            return true;
        }
        return super.onTransact(code, data, reply, flags);
    }

    void sendBinderToClient() {
        for (int userId : UserManagerApis.getUserIdsNoThrow()) {
            sendBinderToClient(this, userId);
        }
    }

    private static void sendBinderToClient(Binder binder, int userId) {
        try {
            Stream<PackageInfo> packages =
                InstalledPackagesCompat.getInstalledPackagesNoThrow(
                    PackageManager.GET_PERMISSIONS, userId
                )
                .stream()
                .filter(pi -> pi != null && pi.requestedPermissions != null)
                .filter(pi -> ArraysKt.contains(pi.requestedPermissions, PERMISSION));

            LOGGER.i("sending binders");
            packages
                .parallel()
                .forEach(pi -> {
                    sendBinderToUserApp(binder, pi.packageName, userId);
                });
            LOGGER.i("sent binders");
        } catch (Throwable tr) {
            LOGGER.e("exception when call getInstalledPackages", tr);
        }
    }

    void sendBinderToManager() {
        sendBinderToManager(this);
    }

    private static void sendBinderToManager(Binder binder) {
        for (int userId : UserManagerApis.getUserIdsNoThrow()) {
            sendBinderToManager(binder, userId);
        }
    }

    static void sendBinderToManager(Binder binder, int userId) {
        ServerLog.mark("handing the binder to " + MANAGER_APPLICATION_ID + " in user " + userId);
        boolean success = sendBinderToUserApp(binder, MANAGER_APPLICATION_ID, userId);
        ServerLog.mark(success
                ? "the manager took the binder"
                : "the manager did not take the binder: retrying without force-stop");
        if (!success) {
            // Do NOT force-stop the manager here. On Android 17 a force-stop marks the
            // package stopped, so the next getContentProviderExternal() cannot reliably
            // bring the manager provider back. That turns a recoverable provider race into
            // a permanent Binder handoff timeout while shizuku_server itself stays alive.
            boolean installed;
            try {
                installed = Android17Compat.getApplicationInfo(MANAGER_APPLICATION_ID, 0, userId) != null;
            } catch (Throwable tr) {
                installed = true;
            }
            if (!installed) {
                ServerLog.mark("not retrying in user " + userId + ": the manager is not installed there");
                return;
            }

            final long[] retryDelays = {250L, 750L, 1500L, 3000L};
            for (long retryDelay : retryDelays) {
                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }

                ServerLog.mark("manager binder handoff retry after " + retryDelay
                        + "ms, serverUid=" + Process.myUid());
                success = sendBinderToUserApp(binder, MANAGER_APPLICATION_ID, userId);
                if (success) {
                    ServerLog.mark("manager binder handoff retry succeeded");
                    LOGGER.i("manager binder handoff retry succeeded in user %d", userId);
                    return;
                }
            }

            ServerLog.mark("manager binder handoff retries exhausted; server remains alive");
            LOGGER.e("manager binder handoff retries exhausted in user %d", userId);
        }
    }

    static boolean sendBinderToUserApp(Binder binder, String packageName, int userId) {
        try {
            DeviceIdleControllerApis.addPowerSaveTempWhitelistApp(packageName, 30 * 1000, userId,
                    316/* PowerExemptionManager#REASON_SHELL */, "shell");
        } catch (Throwable tr) {
            LOGGER.e(tr, "Failed to add %d:%s to power save temp whitelist", userId, packageName);
        }

        String name = packageName + ".shizuku";
        IContentProvider provider = null;

        /*
         When we pass IBinder through binder (and really crossed process), the receive side (here is system_server process)
         will always get a new instance of android.os.BinderProxy.

         In the implementation of getContentProviderExternal and removeContentProviderExternal, received
         IBinder is used as the key of a HashMap. But hashCode() is not implemented by BinderProxy, so
         removeContentProviderExternal will never work.

         Luckily, we can pass null. When token is token, count will be used.
         */
        IBinder token = null;

        try {
            provider = ActivityManagerApis.getContentProviderExternal(name, userId, token, name);
            if (provider == null) {
                LOGGER.e("provider is null %s %d", name, userId);
                return false;
            }
            if (!provider.asBinder().pingBinder()) {
                LOGGER.e("provider is dead %s %d", name, userId);
                return false;
            }

            Bundle extra = new Bundle();
            extra.putParcelable("moe.shizuku.privileged.api.intent.extra.BINDER", new BinderContainer(binder));

            Bundle reply = IContentProviderUtils.callCompat(provider, null, name, "sendBinder", null, extra);
            if (reply != null) {
                LOGGER.i("send binder to user app %s in user %d", packageName, userId);
                return true;
            } else {
                LOGGER.w("failed to send binder to user app %s in user %d", packageName, userId);
                return false;
            }
        } catch (Throwable tr) {
            LOGGER.e(tr, "failed to send binder to user app %s in user %d", packageName, userId);
            return false;
        } finally {
            if (provider != null) {
                try {
                    ActivityManagerApis.removeContentProviderExternal(name, token);
                } catch (Throwable tr) {
                    LOGGER.w(tr, "removeContentProviderExternal");
                }
            }
        }
    }

    // ------ Sui only ------

    @Override
    public void dispatchPackageChanged(Intent intent) throws RemoteException {

    }

    @Override
    public boolean isHidden(int uid) throws RemoteException {
        return false;
    }
}
