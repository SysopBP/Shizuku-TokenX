package moe.shizuku.manager.shell;

import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Process;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import rikka.rish.Rish;
import rikka.rish.RishConfig;
import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuApiConstants;

public class Shell extends Rish {

    private static final int SYSTEM_UID = 1000;
    private static boolean canLaunchSystemUidWorker() {
        try {
            ProcessBuilder probe = new ProcessBuilder("su", "1000", "-c", "id -u");
            probe.redirectErrorStream(true);
            java.lang.Process process = probe.start();
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            int exitCode = process.waitFor();
            return exitCode == 0 && "1000".equals(line != null ? line.trim() : "");
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Permit a compound compatibility route only when EVERY statement is a
     * literal Settings command. Reject shell expansion, pipelines, redirects,
     * comments, quoting and arbitrary commands. No implicit privilege upgrade
     * for a general shell script.
     */
    private static boolean isSettingsOnlyScript(String script) {
        if (script == null || script.length() > 8192) return false;
        String[] statements = script.split("[;\\n\\r]", -1);
        int count = 0;
        for (String statement : statements) {
            String trimmed = statement.trim();
            if (trimmed.isEmpty()) continue;
            if (!trimmed.matches("settings\\s+(?:put|delete|reset|get)\\s+(?:system|secure|global)\\s+[A-Za-z0-9_.:-]+(?:\\s+[A-Za-z0-9_.:-]+)?")) {
                return false;
            }
            count++;
        }
        return count > 0;
    }

    private static void runSystemUidWorkerOrRootFallback(String[] args) {
        final boolean systemWorkerReady = canLaunchSystemUidWorker();
        final List<String> command = new ArrayList<>();
        boolean settingsMutation = args.length >= 2
                && "settings".equals(args[0])
                && ("put".equals(args[1]) || "delete".equals(args[1]) || "reset".equals(args[1]));
        if (!settingsMutation && args.length == 2 && "-c".equals(args[0])) {
            settingsMutation = args[1].trim().matches(
                    "(?s)^settings\\s+(?:put|delete|reset)\\s+[^;\\n\\r&|]+$");
        }
        final boolean compoundSettings = args.length == 2 && "-c".equals(args[0])
                && isSettingsOnlyScript(args[1]);
        final boolean readOnlySettings = compoundSettings
                && !args[1].matches("(?s).*\\bsettings\\s+(?:put|delete|reset)\\b.*");
        final boolean nativeProbe = settingsMutation
                && "1".equals(System.getenv("TOKENX_NATIVE_SETTINGS_TEST"));
        // Build 633 compatibility audit: explicit opt-in is required for UID-0 settings writes.
        final boolean rootMutation = settingsMutation && !nativeProbe
                && "1".equals(System.getenv("TOKENX_ALLOW_ROOT_SETTINGS_COMPAT"));
        // Only explicitly classified settings mutations may use the root compatibility
        // path. Never elevate a read-only compound script or an arbitrary command.
        if (rootMutation) {
            command.add("su");
            System.err.println("TOKENX_SETTINGS_ROUTE=ROOT_MUTATION_COMPAT_EXPLICIT");
            System.err.println("TOKENX_NATIVE_UID1000_SETTINGS_WRITE=false");
        } else {
            if (!systemWorkerReady) {
                System.err.println("TOKENX_SETTINGS_ROUTE=SYSTEM_WORKER_UNAVAILABLE");
                System.err.println("TokenX: UID-1000 worker unavailable; refusing implicit root escalation.");
                System.err.flush();
                System.exit(1);
                return;
            }
            command.add("su");
            command.add("1000");
            if (settingsMutation && !nativeProbe) {
                System.err.println("TOKENX_SETTINGS_COMPAT=NOT_OPTED_IN; set TOKENX_ALLOW_ROOT_SETTINGS_COMPAT=1 for explicit root compatibility");
            }
            System.err.println("TOKENX_SETTINGS_ROUTE=" + (readOnlySettings
                    ? "UID1000_READ_ONLY" : (nativeProbe ? "UID1000_NATIVE_PROBE" : "UID1000")));
        }
        System.err.println("TOKENX_SELECTED_BACKEND=SYSTEM_SERVER");
        System.err.println("TOKENX_EXECUTION_UID=" + (rootMutation ? "0" : "1000"));
        if (!rootMutation && settingsMutation) System.err.println("TOKENX_NATIVE_UID1000_SETTINGS_WRITE=UNVERIFIED");
        System.err.println("TokenX: launching isolated shell worker outside system_server.");
        command.addAll(Arrays.asList(args));
        System.err.flush();
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectInput(ProcessBuilder.Redirect.INHERIT);
            builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            java.lang.Process process = builder.start();
            System.exit(process.waitFor());
        } catch (Throwable tr) {
            System.err.println("TokenX: shell worker failed: " + tr.getClass().getSimpleName() + ": " + tr.getMessage());
            tr.printStackTrace(System.err);
            System.err.flush();
            System.exit(1);
        }
    }

    @Override
    public void requestPermission(Runnable onGrantedRunnable) {
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            onGrantedRunnable.run();
        } else if (Shizuku.shouldShowRequestPermissionRationale()) {
            System.err.println("Permission denied");
            System.err.flush();
            System.exit(1);
        } else {
            Shizuku.addRequestPermissionResultListener(new Shizuku.OnRequestPermissionResultListener() {
                @Override
                public void onRequestPermissionResult(int requestCode, int grantResult) {
                    Shizuku.removeRequestPermissionResultListener(this);

                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        onGrantedRunnable.run();
                    } else {
                        System.err.println("Permission denied");
                        System.err.flush();
                        System.exit(1);
                    }
                }
            });
            Shizuku.requestPermission(0);
        }
    }

    public static void main(String[] args, String packageName, IBinder binder, Handler handler) {
        RishConfig.init(binder, ShizukuApiConstants.BINDER_DESCRIPTOR, 30000);
        Shizuku.onBinderReceived(binder, packageName);
        Shizuku.addBinderReceivedListenerSticky(() -> {
            int serverUid = Shizuku.getUid();
            System.err.println("TokenX RISH: clientUid=" + Process.myUid() + ", serverUid=" + serverUid + ", package=" + packageName);
            System.err.flush();

            if (serverUid == SYSTEM_UID) {
                runSystemUidWorkerOrRootFallback(args);
                return;
            }

            int version = Shizuku.getVersion();
            if (version < 12) {
                System.err.println("Rish requires server 12 (running " + version + ")");
                System.err.flush();
                System.exit(1);
            }
            new Shell().start(args);
        });
    }
}
