package com.tokenx.bridgetest;

import android.app.Activity;
import android.app.Application;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String TAG = "TokenXBridgeTest";
    private TextView output;
    private final List<String> report = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 28, 28, 28);

        Button run = new Button(this);
        run.setText("RUN FULL BACKEND SCAN");
        output = new TextView(this);
        output.setTextIsSelectable(true);
        output.setTextSize(13f);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(output);
        root.addView(run, new LinearLayout.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        run.setOnClickListener(v -> runScan());
        runScan();
    }

    private void line(String s) {
        report.add(s);
        Log.i(TAG, s);
        output.append(s + "\n");
    }

    private String readFirst(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            String s = r.readLine();
            return s == null ? "(empty)" : s;
        } catch (Throwable t) {
            return "DENIED: " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    private void permission(String p) {
        int v = checkSelfPermission(p);
        line((v == PackageManager.PERMISSION_GRANTED ? "PASS " : "DENY ") + p);
    }

    private void binder(String name) {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Method m = sm.getDeclaredMethod("getService", String.class);
            m.setAccessible(true);
            Object b = m.invoke(null, name);
            line((b != null ? "PASS " : "MISS ") + "binder:" + name +
                    (b == null ? "" : " handle=" + b.getClass().getName()));
        } catch (Throwable t) {
            line("DENY binder:" + name + " " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private void readable(String path) {
        File f = new File(path);
        line((f.exists() ? "EXISTS " : "MISS ") + path +
                " read=" + f.canRead() + " write=" + f.canWrite() + " exec=" + f.canExecute());
    }

    private void runScan() {
        report.clear();
        output.setText("");
        line("========================================");
        line(" TOKENX BRIDGETEST FULL BACKEND SCAN");
        line("========================================");

        line("");
        line("=== IDENTITY ===");
        line("package=" + getPackageName());
        line("uid=" + Process.myUid());
        line("pid=" + Process.myPid());
        line("process=" + Application.getProcessName());
        line("selinux=" + readFirst("/proc/self/attr/current"));
        line("statusUid=" + findStatus("Uid:"));
        line("statusGid=" + findStatus("Gid:"));

        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(getPackageName(), 0);
            line("sourceDir=" + ai.sourceDir);
            line("flags=0x" + Integer.toHexString(ai.flags));
            line("privateFlags=0x" + Integer.toHexString(ai.privateFlags));
            line("systemFlag=" + ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0));
            line("debuggable=" + ((ai.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0));
        } catch (Throwable t) { line("packageInfo=ERROR " + t); }

        line("");
        line("=== PROCESS=SYSTEM VALIDATION ===");
        int ss = findSystemServerPid();
        line("ourPid=" + Process.myPid());
        line("systemServerPid=" + ss);
        line("samePid=" + (ss > 0 && ss == Process.myPid()));

        line("");
        line("=== BINDER HANDLES (IN-PROCESS) ===");
        String[] services = {"activity","package","power","window","user","mount","notification",
                "appops","permission","input","display","connectivity","wifi","device_policy",
                "account","jobscheduler","deviceidle","batteryproperties"};
        for (String s : services) binder(s);

        line("");
        line("=== SETTINGS READS (IN-PROCESS) ===");
        try { line("PASS secure.android_id=" + Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID)); }
        catch (Throwable t) { line("DENY Secure read: " + t); }
        try { line("PASS system.screen_brightness=" + Settings.System.getString(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS)); }
        catch (Throwable t) { line("DENY System read: " + t); }
        try { line("PASS global.airplane_mode_on=" + Settings.Global.getString(getContentResolver(), Settings.Global.AIRPLANE_MODE_ON)); }
        catch (Throwable t) { line("DENY Global read: " + t); }
        line("canWriteSettings=" + Settings.System.canWrite(this));

        line("");
        line("=== DECLARED/EFFECTIVE PERMISSION CHECKS ===");
        String[] perms = {
                "android.permission.WRITE_SETTINGS",
                "android.permission.WRITE_SECURE_SETTINGS",
                "android.permission.DUMP",
                "android.permission.INTERACT_ACROSS_USERS",
                "android.permission.INTERACT_ACROSS_USERS_FULL",
                "android.permission.MANAGE_USERS",
                "android.permission.PACKAGE_USAGE_STATS",
                "android.permission.REBOOT",
                "android.permission.SHUTDOWN",
                "android.permission.DEVICE_POWER"
        };
        for (String p : perms) permission(p);

        line("");
        line("=== FRAMEWORK READ CAPABILITIES ===");
        try {
            ActivityManager am = getSystemService(ActivityManager.class);
            line("PASS runningAppProcesses count=" + (am.getRunningAppProcesses() == null ? -1 : am.getRunningAppProcesses().size()));
            line("PASS memoryClass=" + am.getMemoryClass());
        } catch (Throwable t) { line("DENY ActivityManager: " + t); }
        try {
            PackageInfo self = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS);
            line("PASS PackageManager self version=" + self.versionName);
            line("PASS installedPackages=" + getPackageManager().getInstalledPackages(0).size());
        } catch (Throwable t) { line("DENY PackageManager: " + t); }

        line("");
        line("=== FILESYSTEM / SELINUX ===");
        readable("/system");
        readable("/system/priv-app");
        readable("/data/system");
        readable("/data/adb");
        readable("/proc/1");
        readable("/proc/" + Process.myPid());
        line("getenforceSource=/sys/fs/selinux/enforce value=" + readFirst("/sys/fs/selinux/enforce"));

        line("");
        line("=== BRIDGE READINESS ===");
        line("uid1000=" + (Process.myUid() == 1000));
        line("systemAppDomain=" + readFirst("/proc/self/attr/current").startsWith("u:r:system_app:"));
        line("sameAsSystemServer=" + (ss > 0 && ss == Process.myPid()));
        line("NOTE: binder handle PASS proves discovery only; it does not prove a privileged transaction.");
        line("NOTE: no su, Shizuku, rish, trap_king, or shell fallback is used by this scanner.");
        line("========================================");
    }

    private String findStatus(String prefix) {
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/self/status"))) {
            String s;
            while ((s = r.readLine()) != null) if (s.startsWith(prefix)) return s;
        } catch (Throwable t) { return "ERROR " + t; }
        return "not found";
    }

    private int findSystemServerPid() {
        File proc = new File("/proc");
        File[] entries = proc.listFiles();
        if (entries == null) return -1;
        for (File e : entries) {
            String n = e.getName();
            if (!n.matches("\\d+")) continue;
            String cmd = readFirst(e.getAbsolutePath() + "/cmdline").replace("\u0000", "");
            if ("system_server".equals(cmd)) {
                try { return Integer.parseInt(n); } catch (Throwable ignored) {}
            }
        }
        return -1;
    }
}
