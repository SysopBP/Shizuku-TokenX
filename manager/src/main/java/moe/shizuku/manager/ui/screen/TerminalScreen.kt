package moe.shizuku.manager.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import java.security.MessageDigest
import android.widget.Toast
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.ui.component.SegmentedColumn
import moe.shizuku.manager.ui.component.SegmentedListItem
import moe.shizuku.manager.utils.CustomTabsHelper
import rikka.compatibility.DeviceCompatibility

private const val SH_NAME = "rish"
private const val DEX_NAME = "rish_shizuku.dex"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val status = remember { mutableStateOf("Export both files from this installed APK.") }
    val build = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "unknown" }
    val assetHashes = remember { listOf(SH_NAME, DEX_NAME).map { name ->
        runCatching { name + " SHA-256: " + MessageDigest.getInstance("SHA-256").digest(context.assets.open(name).use { it.readBytes() }).joinToString("") { "%02x".format(it) } }.getOrElse { "$name missing" }
    } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) { status.value = writeRish(context, tree); Toast.makeText(context, status.value, Toast.LENGTH_LONG).show() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.tools_terminal)) },
            windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            }
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            item {
                SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("RISH v3 Export", style = MaterialTheme.typography.titleMedium)
                            Text("Installed TokenX: $build", style = MaterialTheme.typography.bodyMedium)
                            assetHashes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                            Text(status.value, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { picker.launch(null) }) { Text("Export matching RISH + DEX") }
                            OutlinedButton(onClick = {
                                val commands = listOf(
                                    "termux-setup-storage",
                                    "cd ~",
                                    "cp /sdcard/Download/rish /sdcard/Download/rish_shizuku.dex ~/",
                                    "chmod 700 ~/rish",
                                    "chmod 400 ~/rish_shizuku.dex",
                                    "export RISH_APPLICATION_ID=com.termux",
                                    "./rish --root -c 'id; echo ROOT_OK'",
                                    "./rish --system -c 'id; echo SYSTEM_OK'",
                                    "./rish --shell -c 'id; echo SHELL_OK'"
                                ).joinToString("\n")
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                    .setPrimaryClip(ClipData.newPlainText("TokenX Termux commands", commands))
                                Toast.makeText(context, "Termux commands copied. Export to Download first.", Toast.LENGTH_LONG).show()
                            }) { Text("Copy Termux setup and tests") }
                        }
                    }
                }
            }
            // The export action uses the matching launcher and DEX bundled in the installed APK.
            // What rish is lives on the settings row; the screen goes straight to
            // the steps.
            item {
                SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.terminal_tutorial_1)) },
                            supportingContent = {
                                Text(
                                    stringResource(
                                        R.string.terminal_tutorial_1_description,
                                        SH_NAME,
                                        DEX_NAME
                                    )
                                )
                            },
                            onClick = { picker.launch(null) }
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.terminal_tutorial_2)) },
                            supportingContent = {
                                Column {
                                    Text(
                                        stringResource(
                                            R.string.terminal_tutorial_2_description,
                                            SH_NAME,
                                            SH_NAME,
                                            ".bashrc"
                                        )
                                    )
                                    Text(
                                        text = "cp /sdcard/chosen-folder/* /data/data/terminal.package.name/files",
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.terminal_tutorial_3)) },
                            supportingContent = {
                                Text(
                                    text = "sh /path/to/$SH_NAME",
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        )
                    }
                    item {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.settings_help)) },
                            onClick = {
                                CustomTabsHelper.launchUrlOrCopy(context, Helps.RISH.get())
                            }
                        )
                    }
                }
            }

            if (runCatching { DeviceCompatibility.isMiui() }.getOrDefault(false)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.terminal_tutorial_miui),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.terminal_tutorial_miui_2),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun writeRish(context: Context, tree: Uri): String {
    // Confirm the installed APK has both matching assets before touching exported files.
    val available = listOf(SH_NAME, DEX_NAME).all { name ->
        runCatching { context.assets.open(name).use { it.read() >= 0 } }.getOrDefault(false)
    }
    if (!available) return "Export unavailable: matching RISH or DEX missing from this APK"
    val cr = context.contentResolver
    val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    val child = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    runCatching {
        cr.query(
            child,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val name = cursor.getString(1)
                if (name == SH_NAME || name == DEX_NAME) {
                    runCatching {
                        DocumentsContract.deleteDocument(
                            cr,
                            DocumentsContract.buildDocumentUriUsingTree(tree, id)
                        )
                    }
                }
            }
        }
    }

    var exported = 0
    fun writeToDocument(name: String) {
        runCatching {
            val created = DocumentsContract.createDocument(cr, doc, "application/octet-stream", name) ?: return
            cr.openOutputStream(created)?.use { output ->
                context.assets.open(name).use { input ->
                    if (name == SH_NAME) {
                        input.bufferedReader().use {
                            output.write(it.readText().replace("MANAGER_PKG", context.packageName).toByteArray())
                        }
                    } else {
                        input.copyTo(output)
                    }
                }
            }
            exported++
        }
    }

    writeToDocument(SH_NAME)
    writeToDocument(DEX_NAME)
    return if (exported == 2) "Exported matching rish and rish_shizuku.dex from installed TokenX" else "Export incomplete: $exported of 2 files written"
}
