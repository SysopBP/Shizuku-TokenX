# System UID Backend — Installation

> **Experimental / advanced:** this setup requires root and writes directly under `/data/app`. Back up the device before testing.

The TokenX **System UID backend is optional**. The companion APK and provisioning DEX are deliberately kept separate from the main TokenX APK and are **not bundled in this repository build**.

## Requirements

- Root access (KernelSU or equivalent)
- Shizuku TKN Boot / TokenX installed
- `Serv.apk`
- `Serv.dex`
- A reboot after initial provisioning is recommended

## 1. Place the external files

Copy the files to:

```text
Serv.apk -> /sdcard/Download/Serv.apk
Serv.dex -> /sdcard/Serv.dex
```

## 2. Enter a root shell

For example, from Termux:

```sh
su
```

Confirm the shell is root:

```sh
id
```

The output should report `uid=0(root)`.

## 3. Create the companion APK directory

```sh
mkdir -p /data/app/com.android.settings/vikram_shell
cat /sdcard/Download/Serv.apk > /data/app/com.android.settings/vikram_shell/base.apk
chmod -R 755 /data/app/com.android.settings/vikram_shell
```

## 4. Stage the provisioning DEX

```sh
cp /sdcard/Serv.dex /data/local/tmp/Serv.dex
chmod 644 /data/local/tmp/Serv.dex
```

## 5. Run the provisioning DEX

The following is the currently tested provisioning invocation. Keep it unchanged for the first installation:

```sh
export CLASSPATH=/data/local/tmp/Serv.dex
app_process -cp /sdcard/Serv.dex /system/bin Serv
```

Allow the command to finish before rebooting.

## 6. Reboot and verify

After reboot, verify the installed APK:

```sh
su -c 'ls -l /data/app/com.android.settings/vikram_shell/base.apk'
```

Then inspect the package metadata:

```sh
su -c 'dumpsys package com.vikram.shell | grep -E "userId=|sharedUserId=|codePath="'
```

Confirm that the companion remains installed and reports the expected System UID configuration before enabling the TokenX System UID backend.

## Architecture

```text
KernelSU / Root
       |
       +-- Serv.dex provisioning
                 |
                 v
              Serv.apk
               UID 1000
                 |
          ProcessBuilder
                 |
                 v
       TokenX System UID Backend
```

The companion files remain independent from the other TokenX execution paths:

```text
System Server -> existing LSPosed / System Server bridge
System UID    -> Serv.apk / ProcessBuilder
Root          -> KernelSU
Shell         -> UID 2000
```

## TokenX provisioning helper

TokenX contains a root-side provisioning helper that reproduces the procedure above. It expects the external files at the paths documented above. It does **not** bundle `Serv.apk` or `Serv.dex`.

Provisioning and normal command execution are intentionally separate. The DEX should not be rerun for every terminal command or automatically on every boot.

## Troubleshooting

If provisioning fails, verify the source files first:

```sh
su -c 'ls -l /sdcard/Download/Serv.apk /sdcard/Serv.dex'
```

Verify the staged files:

```sh
su -c 'ls -ld /data/app/com.android.settings/vikram_shell && ls -l /data/app/com.android.settings/vikram_shell/base.apk /data/local/tmp/Serv.dex'
```

If the companion is installed but a command is denied, check its actual process identity and SELinux domain. UID 1000 and the SELinux domain are separate security controls:

```sh
id
cat /proc/self/attr/current
getenforce
```

Do not repeatedly rerun the provisioning DEX while diagnosing a normal command-execution failure.
