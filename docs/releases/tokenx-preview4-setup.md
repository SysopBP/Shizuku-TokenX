# TokenX Preview 4 setup and validation
## Install
Install TokenX-Preview4-Build670-debug.apk over a compatible, identically signed installation. A signature mismatch requires a separate migration decision; do not uninstall solely to bypass the warning.
Allow notifications for the pairing notification. Grant KernelSU access when using Root. Configure the packaged TokenX Xposed integration in a compatible LSPosed environment for its System Server hooks.
The included KernelSU ZIP is an optional diagnostic tracer, not a System backend installer. Do not flash old bridge ZIPs on the assumption they match this APK.

## RISH
Extract the two files from TokenX-Preview4-Build670-RISH.zip into your terminal app's private directory. Keep them as a pair from this APK.
For Termux:
```sh
chmod 700 rish
chmod 400 rish_shizuku.dex
export RISH_APPLICATION_ID=com.termux
export MANAGER_APPLICATION_ID=moe.shizuku.privileged.api
./rish --root -c 'id; echo ROOT_OK'
./rish --system -c 'id; echo SYSTEM_OK'
./rish --shell -c 'id; echo SHELL_OK'
./rish -c 'id; echo DEFAULT_OK'
```
Expected identities are UID 0, 1000, and 2000 for explicit Root, System, and Shell requests. An unavailable backend is a failed check; a success marker alone does not certify identity. Default follows the configured available route.

## Wireless pairing
Enable Developer options and Wireless debugging. Open Pair device with pairing code, then use TokenX's Enter code notification action. Enter the pairing code while that Android dialog remains open.
Use the pairing endpoint, not the ordinary wireless debugging connection port. Record if the notification is absent, the action fails to accept input, or pairing/start fails. This exact candidate's OEM behavior remains unverified.

## Release validation
Run all four RISH checks with supported backends active. Verify client authorization and independent backend status.
Perform a full reboot; if D2 is installed, unlock it normally before checking recovery. Repeat routing checks, confirm saved authorizations and heartbeat state, and verify System detach/rebind without a framework restart.
Save test output and relevant diagnostics in /storage/emulated/0/Download. Include device, Android/One UI version, APK build, root/LSPosed versions, steps and logs when reporting failures.
