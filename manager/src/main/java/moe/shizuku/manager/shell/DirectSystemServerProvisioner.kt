package moe.shizuku.manager.shell

import android.content.Context
import android.os.Build
import moe.shizuku.manager.BuildConfig
import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest

/** Live native injector, with TokenX's own authenticated Binder payload. */
internal object DirectSystemServerProvisioner {
    private const val DIRECTORY = "/data/system/tokenx_direct"
    private const val REFERENCE_SHA256 = "35bbb34e14cddc2c0c1df23321ac118eab73bdac151d7494f0b76fd33c80140b"

    fun preflight(context: Context): SystemUidProvisioner.Result {
        if (Build.VERSION.SDK_INT < 27 || "arm64-v8a" !in Build.SUPPORTED_64_BIT_ABIS)
            return SystemUidProvisioner.Result(false, 2, "Live native injection requires ARM64 and Android 8.1+.", "direct preflight")
        if (context.packageName != BuildConfig.APPLICATION_ID || context.applicationInfo.uid / 100000 != 0)
            return SystemUidProvisioner.Result(false, 2, "Use the original TokenX APK in the device owner profile.", "direct preflight")
        return SystemUidProvisioner.runRoot("""
            set -e
            [ "$(id -u)" = 0 ] || { echo 'Root access required'; exit 2; }
            command -v timeout >/dev/null || { echo 'Native injector timeout command unavailable'; exit 2; }
            pid=$(pidof system_server)
            case "${'$'}pid" in ''|*[!0-9]*) echo 'Cannot identify System Server'; exit 2;; esac
            [ "$(tr '\000' '\n' < /proc/${'$'}pid/cmdline | head -n 1)" = system_server ] || exit 2
            [ "$(awk '/^Uid:/ {print ${'$'}2}' /proc/${'$'}pid/status)" = 1000 ] || exit 2
            echo "TOKENX_PREVIOUS_PID=${'$'}pid"
            echo TOKENX_DIRECT_PREFLIGHT_OK
        """.trimIndent())
    }

    fun inject(context: Context, targetPid: Int, request: Long, onStatus: (String) -> Unit): SystemUidProvisioner.Result {
        if (targetPid <= 0 || request <= 0)
            return SystemUidProvisioner.Result(false, 2, "Invalid injection request identity.", "direct injection")
        val apk = File(context.applicationInfo.sourceDir)
        val injector = File(context.applicationInfo.nativeLibraryDir, "libtokenx_direct_inject.so")
        val payload = File(context.applicationInfo.nativeLibraryDir, "libtokenx_direct_payload.so")
        return try {
            require(apk.isFile && injector.isFile && payload.isFile) { "Installed APK or native injection files are missing" }
            require(sha256(injector) == REFERENCE_SHA256) { "Native injector checksum mismatch" }
            onStatus("Staging the checked APK and native payload for live injection")
            val apkHash = sha256(apk)
            val payloadHash = sha256(payload)
            val script = """
                set -e
                [ "$(id -u)" = 0 ] || { echo 'Root access required'; exit 2; }
                pid=$(pidof system_server)
                case "${'$'}pid" in ''|*[!0-9]*) echo 'Cannot identify System Server'; exit 2;; esac
                [ "${'$'}pid" = '$targetPid' ] || { echo 'System Server changed before injection'; exit 2; }
                [ "$(tr '\000' '\n' < /proc/${'$'}pid/cmdline | head -n 1)" = system_server ] || exit 2
                for file in '$DIRECTORY' '$DIRECTORY/payload.apk' '$DIRECTORY/injector' '$DIRECTORY/payload.so' '$DIRECTORY/request' '$DIRECTORY/oat'; do
                    test ! -L "${'$'}file" || { echo 'Symlink staging path rejected'; exit 2; }
                done
                if test -e '$DIRECTORY'; then
                    test -d '$DIRECTORY' && [ "$(stat -c %u '$DIRECTORY')" = 0 ] || exit 2
                fi
                mkdir -p '$DIRECTORY'
                chown 0:0 '$DIRECTORY'
                chmod 755 '$DIRECTORY'
                mkdir -p '$DIRECTORY/oat'
                chown 1000:1000 '$DIRECTORY/oat'
                chmod 700 '$DIRECTORY/oat'
                # Temporary files are created inside the root-owned staging directory.
                stage() {
                    src=${'$'}1; dst=${'$'}2; expected=${'$'}3; mode=${'$'}4
                    tmp=$(mktemp '$DIRECTORY/.stage.XXXXXX')
                    if ! cp "${'$'}src" "${'$'}tmp" || [ "$(sha256sum "${'$'}tmp" | cut -d ' ' -f 1)" != "${'$'}expected" ]; then
                        rm -f "${'$'}tmp"; echo 'Staging checksum failed'; exit 3
                    fi
                    chown 0:0 "${'$'}tmp"; chmod "${'$'}mode" "${'$'}tmp"
                    mv -f "${'$'}tmp" "${'$'}dst"
                }
                stage ${q(apk.path)} '$DIRECTORY/payload.apk' '$apkHash' 444
                stage ${q(injector.path)} '$DIRECTORY/injector' '$REFERENCE_SHA256' 755
                stage ${q(payload.path)} '$DIRECTORY/payload.so' '$payloadHash' 755
                tmp=$(mktemp '$DIRECTORY/.request.XXXXXX')
                echo '$request' > "${'$'}tmp"
                chown 0:0 "${'$'}tmp"; chmod 444 "${'$'}tmp"; mv -f "${'$'}tmp" '$DIRECTORY/request'
                echo "TOKENX_PREVIOUS_PID=${'$'}pid"
                echo TOKENX_DIRECT_REQUEST=$request
                echo TOKENX_DIRECT_APK_SHA256=$apkHash
                echo TOKENX_DIRECT_INJECTOR_SHA256=$REFERENCE_SHA256
                echo TOKENX_DIRECT_INJECTION_START
                timeout -s TERM -k 2 20 '$DIRECTORY/injector' "${'$'}pid" '$DIRECTORY/payload.so' entry
                [ "$(pidof system_server)" = "${'$'}pid" ] || { echo 'System Server changed during injection'; exit 4; }
                echo TOKENX_DIRECT_INJECTION_RETURNED
                # The manager verifies identity, build, request and framework services over Binder.
            """.trimIndent()
            SystemUidProvisioner.runRoot(script)
        } catch (e: Exception) {
            SystemUidProvisioner.Result(false, -1, "Live injection preparation failed: ${e.message}", "direct injection")
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(file.inputStream(), digest).use { input ->
            val buffer = ByteArray(65536)
            while (input.read(buffer) != -1) { /* streaming digest */ }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun q(value: String) = ProvisionPolicy.quote(value)
}
