#!/system/bin/sh
# TokenX Build 633: non-destructive UID-1000 Settings attribution regression probe.
# Run in Termux: sh tools/tokenx-settings-attribution-probe.sh
set -u
export RISH_APPLICATION_ID="${RISH_APPLICATION_ID:-com.termux}"
RISH="${RISH:-./rish}"
KEY="tokenx_633_probe_$$"
VALUE="tokenx_probe_633"
echo "TOKENX_SETTINGS_ATTRIBUTION_PROBE=1"
echo "RISH_APPLICATION_ID=$RISH_APPLICATION_ID"
"$RISH" --system -c "echo IDENTITY; id; echo PACKAGE_MAPPING; cmd package list packages --uid 1000; echo SETTINGS_READ; settings get system screen_brightness; echo SETTINGS_PUT; settings put system $KEY $VALUE; echo PUT_EXIT=\$?; echo SETTINGS_GET; settings get system $KEY; echo SETTINGS_DELETE; settings delete system $KEY; echo DELETE_EXIT=\$?"
echo "NOTE: A UID 1000 identity is not proof of valid Binder package attribution."
echo "NOTE: No privileged policy or AppOps bypass is performed by this probe."
