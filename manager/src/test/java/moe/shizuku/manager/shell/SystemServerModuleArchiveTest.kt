package moe.shizuku.manager.shell

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class SystemServerModuleArchiveTest {
    private fun fixture(dexNames: List<String> = listOf("classes.dex", "classes2.dex"), test: (File, File) -> Unit) {
        val directory = Files.createTempDirectory("tokenx-module-test").toFile()
        try {
            val apk = File(directory, "installed.apk")
            ZipOutputStream(apk.outputStream()).use { zip ->
                for (name in dexNames + listOf("lib/arm64-v8a/libtokenx_injector.so", "assets/unrelated.txt")) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write("fixture payload for $name".toByteArray())
                    zip.closeEntry()
                }
            }
            test(apk, File(directory, "module.zip"))
        } finally { directory.deleteRecursively() }
    }

    @Test fun installedManifestSurvivesKernelSuInstallerCleanup() = fixture { apk, archive ->
        val manifest = SystemServerModuleArchive.create(apk, archive, "arm64-v8a", "test", 700,
            "#!/system/bin/sh\nsha256sum -c payload.sha256\n".byteInputStream())
        ZipFile(archive).use { zip ->
            val installed = zip.entries().asSequence().associate { it.name to zip.getInputStream(it).use { stream -> stream.readBytes() } }.toMutableMap()
            assertNotNull(installed.remove("customize.sh")) // Actual KernelSU installer cleanup.
            assertEquals(manifest, installed.getValue("payload.sha256").toString(Charsets.UTF_8))
            assertFalse(manifest.contains("customize.sh"))
            for (line in manifest.lineSequence().filter { it.isNotBlank() }) {
                val (expected, name) = line.split("  ", limit = 2)
                val bytes = installed.getValue(name)
                val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                assertEquals("Installed checksum for $name", expected, actual)
            }
            assertEquals("2\n", installed.getValue("payload/dex.count").toString(Charsets.UTF_8))
            assertTrue(installed.containsKey("zygisk/arm64-v8a.so"))
            assertFalse(installed.containsKey("assets/unrelated.txt"))
        }
    }

    @Test fun missingDexSequenceIsRejected() = fixture(listOf("classes.dex", "classes3.dex")) { apk, archive ->
        assertThrows(IllegalArgumentException::class.java) {
            SystemServerModuleArchive.create(apk, archive, "arm64-v8a", "test", 700, "installer".byteInputStream())
        }
    }

    @Test fun missingNativeAbiIsRejected() = fixture { apk, archive ->
        assertThrows(IllegalStateException::class.java) {
            SystemServerModuleArchive.create(apk, archive, "x86_64", "test", 700, "installer".byteInputStream())
        }
    }
}
