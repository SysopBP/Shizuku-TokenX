package moe.shizuku.manager.shell

import java.io.File
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Builds only the installed APK's injector and DEX; the archive hash also protects its installer. */
internal object SystemServerModuleArchive {
    fun create(apkFile: File, archive: File, abi: String, versionName: String, versionCode: Int,
               customize: InputStream): String {
        val checksums = StringBuilder()
        ZipFile(apkFile).use { apk ->
            val dex = apk.entries().asSequence().filter { it.name.matches(Regex("classes([2-9]|[1-9][0-9]+)?\\.dex")) }
                .sortedBy { it.name.removePrefix("classes").removeSuffix(".dex").toIntOrNull() ?: 1 }.toList()
            require(dex.isNotEmpty() && dex.size <= 16 && dex.sumOf { it.size } <= 128L * 1024 * 1024)
            dex.forEachIndexed { i, entry ->
                require(entry.size > 8 && entry.name == if (i == 0) "classes.dex" else "classes${i + 1}.dex")
            }
            val library = apk.getEntry("lib/$abi/libtokenx_injector.so")
                ?: error("Packaged System Server injector missing for $abi")
            ZipOutputStream(archive.outputStream()).use { zip ->
                fun add(name: String, input: InputStream, persists: Boolean = true) {
                    val digest = MessageDigest.getInstance("SHA-256")
                    zip.putNextEntry(ZipEntry(name))
                    DigestInputStream(input, digest).use { it.copyTo(zip) }
                    zip.closeEntry()
                    if (persists) checksums.append(digest.digest().joinToString("") { "%02x".format(it) })
                        .append("  ").append(name).append('\n')
                }
                fun text(name: String, value: String) = add(name, value.byteInputStream())
                val id = SystemUidProvisioner.MODULE_ID
                text("module.prop", "id=$id\nname=TokenX System Server\nversion=$versionName\nversionCode=$versionCode\nauthor=SysopBP\ndescription=TokenX native UID 1000 Binder injection\ntokenxInstaller=kernel-v1\n")
                text("skip_mount", "")
                // KernelSU sources then deletes customize.sh. Its bytes are covered by the ZIP hash,
                // while the installed manifest covers only files that survive installer cleanup.
                add("customize.sh", customize, persists = false)
                add("zygisk/$abi.so", apk.getInputStream(library))
                text("payload/dex.count", "${dex.size}\n")
                for (entry in dex) add("payload/${entry.name}", apk.getInputStream(entry))
                zip.putNextEntry(ZipEntry("payload.sha256"))
                zip.write(checksums.toString().toByteArray())
                zip.closeEntry()
            }
        }
        return checksums.toString()
    }
}
