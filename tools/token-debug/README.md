# Token Debug Module

Passive diagnostics for Shizuku-TokenX token/boot/privilege failures.

## Captures
- UID/PID/PPID and Android build/boot state
- relevant Shizuku/TokenX/system_server/zygote/LSPosed processes
- Binder/service visibility
- Shizuku/token system properties
- package UID, permission and enabled/stopped state
- filtered recent logcat for token, Binder, boot, UID 1000/2000, denials and exceptions
- kernel/SELinux denial lines when readable

## Safety
This collector is read-only by design. It does **not** print token secret values from app storage, mint/request tokens, alter permissions, restart Shizuku, or mutate boot state.

## Run
From an Android shell:
```sh
sh tools/token-debug/token_debug.sh
```
Or supply an output path as argument 1. The default report is written to Download.
