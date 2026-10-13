"""Execute the real selector, launcher and Shell with a simulated Binder UID."""
import json
import os
from pathlib import Path
import subprocess
import shutil
import tempfile

with tempfile.TemporaryDirectory() as directory:
    root = Path(directory)
    sources = {
        "moe/shizuku/manager/BuildConfig.java": "package moe.shizuku.manager; public final class BuildConfig { public static final int VERSION_CODE=12345; public static final String VERSION_NAME=\"routing-test\"; }",
        "android/content/pm/PackageManager.java": "package android.content.pm; public class PackageManager { public static final int PERMISSION_GRANTED=0; }",
        "android/os/Handler.java": "package android.os; public class Handler {}",
        "android/os/IBinder.java": "package android.os; public interface IBinder {}",
        "android/os/Process.java": "package android.os; public class Process { public static int myUid(){return 10000;} }",
        "rikka/rish/Rish.java": "package rikka.rish; public class Rish { public void requestPermission(Runnable r){} public void start(String[] a){System.out.println(\"SHELL_STARTED\");} }",
        "rikka/rish/RishConfig.java": "package rikka.rish; public class RishConfig { public static void init(Object b,String d,int t){} }",
        "rikka/shizuku/ShizukuApiConstants.java": "package rikka.shizuku; public class ShizukuApiConstants { public static final String BINDER_DESCRIPTOR=\"test\"; }",
        "rikka/shizuku/Shizuku.java": """package rikka.shizuku; public class Shizuku {
            public interface OnRequestPermissionResultListener { void onRequestPermissionResult(int r,int g); }
            public static int getUid(){return Integer.parseInt(System.getProperty("uid"));}
            public static int getVersion(){return 13;} public static int checkSelfPermission(){return 0;}
            public static boolean shouldShowRequestPermissionRationale(){return false;}
            public static void onBinderReceived(Object b,String p){}
            public static void addBinderReceivedListenerSticky(Runnable r){r.run();}
            public static void addRequestPermissionResultListener(OnRequestPermissionResultListener l){}
            public static void removeRequestPermissionResultListener(OnRequestPermissionResultListener l){}
            public static void requestPermission(int r){}
        }""",
        "RoutingTest.java": """import rikka.shizuku.shell.RishBackendSelector; public class RoutingTest {
            public static void main(String[] a) {
                if (a[0].equals("shell")) {
                    moe.shizuku.manager.shell.Shell.main(new String[]{"-c","id"},"com.termux",null,null,a[1]);
                    return;
                }
                for (String backend : new String[]{"root","system","shell"}) {
                    for (String dash : new String[]{"--","—","–"}) {
                        for (String selected : new String[]{"root","system","shell"}) {
                            var s=RishBackendSelector.select(new String[]{dash+backend,"-c","id"},selected);
                            if (!backend.equals(s.backend) || s.arguments.length!=2 || !s.arguments[1].equals("id")) throw new AssertionError();
                        }
                    }
                }
                var s=RishBackendSelector.select(new String[]{"-c","echo --root"},"shell");
                if (!s.backend.equals("shell") || s.arguments.length!=2) throw new AssertionError();
                if (RishBackendSelector.select(new String[]{"-c","id"},null).backend!=null) throw new AssertionError();
                try { RishBackendSelector.select(new String[]{},"invalid"); throw new AssertionError(); }
                catch (IllegalArgumentException expected) {}
            }
        }""",
    }
    for name, content in sources.items():
        target = root / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content)
    compiler = ["javac"] if shutil.which("javac") else ["java", "com.sun.tools.javac.Main"]
    subprocess.run([*compiler, "-d", str(root), *map(str, root.rglob("*.java")),
                    "shell/src/main/java/rikka/shizuku/shell/RishBackendSelector.java",
                    "manager/src/main/java/moe/shizuku/manager/shell/Shell.java"], check=True)
    subprocess.run(["java", "-cp", str(root), "RoutingTest", "selector"], check=True)
    for backend, expected in [("root", 0), ("shell", 2000)]:
        for actual in [0, 1000, 2000]:
            result = subprocess.run(["java", f"-Duid={actual}", "-cp", str(root), "RoutingTest", "shell", backend], capture_output=True, text=True)
            assert (result.returncode == 0) == (actual == expected), result.stderr
            assert ("SHELL_STARTED" in result.stdout) == (actual == expected)

    launcher = root / "rish"
    dex = root / "rish_shizuku.dex"
    dex.write_bytes(b"test")
    capture = root / "capture.py"
    capture.write_text("#!/usr/bin/env python3\nimport json,os,sys\nprint(json.dumps([os.getenv('TOKENX_RISH_BACKEND'),sys.argv[1:]]))\n")
    capture.chmod(0o755)
    getprop = root / "getprop"
    getprop.write_text("#!/bin/sh\necho 33\n")
    getprop.chmod(0o755)
    launcher.write_text(Path("manager/src/main/assets/rish").read_text().replace("/system/bin/app_process", str(capture)))
    environment = dict(os.environ, PATH=str(root) + ":" + os.environ["PATH"], RISH_APPLICATION_ID="com.termux", TOKENX_RISH_BACKEND="system")
    for backend in ["root", "shell", "system"]:
        for dash in ["--", "—", "–"]:
            output = subprocess.check_output(["sh", str(launcher), dash + backend, "-c", "echo --shell"], env=environment, text=True)
            selected, arguments = json.loads(output)
            assert selected == backend
            assert arguments[-2:] == ["-c", "echo --shell"]
    print("RISH tests passed: selector precedence, Unicode dashes, argument forwarding, and wrong-UID execution rejection.")
