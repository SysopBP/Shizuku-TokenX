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

    private static void runSystemUidWorkerOrRootFallback(String[] args) {
        final boolean systemWorkerReady = canLaunchSystemUidWorker();
        final List<String> command;

        System.err.println("TokenX: DIRECT BINDER connected to System Server backend (UID 1000).");
        if (systemWorkerReady) {
            command = new ArrayList<>();
            /*
             * Android 17 SettingsProvider accepts UID-1000 reads but rejects mutating
             * settings shell calls because the isolated worker has no ContentProvider
             * calling-package attribution. Root is the verified safe compatibility
             * route for those mutations; keep every other command on the build-173
             * UID-1000 worker.
             */
            // RISH normally passes shell commands as ["-c", "settings put ..."].
            // Match that form as well as direct argv. Never treat a general shell
            // script as safe to rewrite: only a single, leading settings command
            // is eligible for the documented Root compatibility route.
            boolean settingsMutation = args.length > 1
                    && "settings".equals(args[0])
                    && ("put".equals(args[1]) || "delete".equals(args[1]) || "reset".equals(args[1]));
            if (!settingsMutation && args.length == 2 && "-c".equals(args[0])) {
                String shellCommand = args[1].trim();
                settingsMutation = shellCommand.matches(
                        "(?s)^settings\\s+(?:put|delete|reset)\\s+[^;\\n\\r&|]+$");
            }
            // Experimental native UID-1000 settings probe. This is opt-in and
            // deliberately does not change AppOps, Binder identity or system_server.
            // If SettingsProvider rejects the caller, the command fails as UID 1000
            // rather than silently escalating to root.
            boolean nativeSettingsProbe = settingsMutation
                    && "1".equals(System.getenv("TOKENX_NATIVE_SETTINGS_TEST"));
            if (settingsMutation && !nativeSettingsProbe) {
                command.add("su");
                System.err.println("TokenX: Android 17 Settings mutation -> KernelSU root compatibility route.");
            } else {
                command.add("su");
                command.add("1000");
                if (nativeSettingsProbe) {
                    System.err.println("TokenX: EXPERIMENTAL native UID-1000 settings test (no root fallback); AppOps may reject the mutation.");
                }
                System.err.println("TokenX: launching isolated UID-1000 shell worker outside system_server.");
            }
            command.addAll(Arrays.asList(args));
        } else {
            command = new ArrayList<>();
            command.add("su");
            command.addAll(Arrays.asList(args));
            System.err.println("TokenX: UID-1000 worker preflight failed; using KernelSU ROOT fallback.");
        }
        System.err.flush();

        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectInput(ProcessBuilder.Redirect.INHERIT);
            builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            java.lang.Process process = builder.start();
            int exitCode = process.waitFor();
            System.exit(exitCode);
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
