package rikka.shizuku.server;

import moe.shizuku.server.BuildConfig;

public class ServerConstants {

    public static final int MANAGER_APP_NOT_FOUND = 50;

    public static final String PERMISSION = "com.sysopbp.tokenx.permission.API_V23";
    // Discovery only: existing clients request this in their own manifests.
    // TokenX never declares or grants this upstream permission.
    public static final String LEGACY_CLIENT_PERMISSION = "moe.shizuku.manager.permission.API_V23";

    public static boolean requestsClientApi(String[] permissions) {
        if (permissions == null) return false;
        for (String permission : permissions) {
            if (PERMISSION.equals(permission) || LEGACY_CLIENT_PERMISSION.equals(permission)) return true;
        }
        return false;
    }

    public static final String REQUEST_PERMISSION_ACTION = BuildConfig.MANAGER_APPLICATION_ID + ".intent.action.REQUEST_PERMISSION";

    public static final int BINDER_TRANSACTION_getApplications = 10001;
}
