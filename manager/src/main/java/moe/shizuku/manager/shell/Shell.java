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
    private static final String TOKENX_SYSTEM_PACKAGE = "com.vikram.exp";

    private static List<String> systemUidWorkerCommand(String[] args) {
        final List<String> command = new ArrayList<>();
        command.add("su");
        command.add("1000");

        /*
         * Keep the proven build-173 UID-1000 worker unchanged for normal commands.
         * Android 17 SettingsProvider additionally validates package attribution on
         * mutations. Export the TokenX system package identity only for the settings
         * CLI so other Binder/service routes retain build-173 behavior.
         */
        if (args.length > 0 && "settings".equals(args[0])) {
            command.add("env");
            command.add("TOKENX_CALLING_PACKAGE=" + TOKENX_SYSTEM_PACKAGE);
            System.err.println("TokenX: SettingsProvider UID-1000 attribution=" + TOKENX_SYSTEM_PACKAGE);
        }

        command.addAll(Arrays.asList(args));
        return command;
    }

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
            command = systemUidWorkerCommand(args);
            System.err.println("TokenX: launching isolated UID-1000 shell worker outside system_server.");
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
