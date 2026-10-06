package moe.shizuku.manager.starter

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLProtocolException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import moe.shizuku.manager.AppConstants.EXTRA
import moe.shizuku.manager.R
import moe.shizuku.manager.adb.AdbKeyException
import moe.shizuku.manager.adb.AdbPairingHelper
import moe.shizuku.manager.adb.AdbStarter
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.start.StartStatusReporter
import moe.shizuku.manager.utils.ShizukuStateMachine
import moe.shizuku.manager.databinding.StarterActivityBinding
import rikka.lifecycle.Resource
import rikka.lifecycle.Status

private class NotRootedException: Exception()

/**
 * How the system start asks the payload to report how far it got.
 *
 * The starter leaves a log, but only from the moment it runs, and its absence cannot say
 * why: a payload that never executed, a process that could not write either of the two
 * directories, and one that wrote where the app cannot read all look identical from here.
 * The command handed to the agent is ours, so it reports to this app as it goes, one step
 * earlier and with no file involved at all: if the first of these never arrives, the agent
 * never ran the command, and nothing else about the attempt matters.
 */
/** Written by the starter at the top of every run it records. */
private const val STARTER_LOG_DIVIDER = "---- start ----"

private const val STAGE_ACTION = "moe.shizuku.privileged.api.action.SYSTEM_START_STAGE"
private const val STAGE_EXTRA = "stage"
private const val STAGE_AT_SHELL = "payload_reached_the_shell"
private const val STAGE_AFTER_STARTER = "the_starter_returned"

class StarterActivity : AppBarActivity() {

    private val viewModel: ViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close_24)

        val binding = StarterActivityBinding.inflate(layoutInflater, rootView, true)

        viewModel.output.observe(this) {
            val output = it.data!!.trim()
            if (output.endsWith(Starter.serviceStartedMessage)) {
                window?.decorView?.postDelayed({
                    if (!isFinishing) finish()
                }, 3000)
            } else if (it.status == Status.ERROR) {
                var message = 0
                when (it.error) {
                    is AdbKeyException -> {
                        message = R.string.adb_error_key_store
                    }
                    is NotRootedException -> {
                        message = R.string.start_with_root_failed
                    }
                    is SocketTimeoutException -> {
                        message = R.string.cannot_connect_port
                    }
                    is ConnectException -> {
                        message = R.string.cannot_connect_port
                    }
                    // The service never appeared. Nothing in this activity can say why - the
                    // starter runs from the agent, so its output is the agent's - but its own
                    // log is on the device and this is where to look.
                    is TimeoutException -> {
                        message = R.string.start_failed_no_binder
                    }

                    is SSLProtocolException -> {
                        // Not paired yet: run the pairing flow automatically instead of
                        // failing and asking the user to pair manually.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            AdbPairingHelper.handlePairing(this@StarterActivity)
                            finish()
                            return@observe
                        } else {
                            message = R.string.adb_pair_required
                        }
                    }
                }

                if (message != 0) {
                    MaterialAlertDialogBuilder(this)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
            binding.text1.text = output
        }

    }

    private var hasStarted = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !hasStarted) {
            hasStarted = true
            viewModel.start(
                intent.getBooleanExtra(EXTRA_IS_ROOT, false),
                intent.getBooleanExtra(EXTRA_IS_SYSTEM, false),
                intent.getIntExtra(EXTRA_PORT, 0),
                intent.getBooleanExtra(EXTRA_SYSTEM_CUSTOM, false)
            )
        }
    }

    companion object {

        const val EXTRA_IS_ROOT = "$EXTRA.IS_ROOT"
        const val EXTRA_IS_SYSTEM = "$EXTRA.IS_SYSTEM"
        const val EXTRA_SYSTEM_CUSTOM = "$EXTRA.SYSTEM_CUSTOM"
        const val EXTRA_PORT = "$EXTRA.PORT"
    }
}

class ViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = getApplication<Application>().applicationContext

    private val sb = StringBuilder()
    private val _output = MutableLiveData<Resource<StringBuilder>>()

    val output = _output as LiveData<Resource<StringBuilder>>

    /**
     * Where a start's own account of itself can be written.
     *
     * Two places, because they fail for different reasons. The external files directory is
     * the natural one, but another app's `Android/data` is exactly what the storage layer
     * and SELinux deny, and a system start writes this from another app's process;
     * `Android/media/<package>` exists to be reachable from outside the app, so it is the
     * one that survives that. Both are this app's own directories, so it can read either.
     */
    private fun starterLogFiles(): List<File> = listOfNotNull(
        appContext.getExternalFilesDir(null),
        appContext.getExternalMediaDirs()?.firstOrNull()
    ).map { File(it, "starter.log") }

    /**
     * The starter's own account of the attempt, and the file it was read from. That account
     * is the only reason a failed system start can be explained without logcat, so when
     * there is none the caller says where it looked rather than nothing happening.
     */
    private fun starterLog(): Pair<File, String>? {
        for (file in starterLogFiles()) {
            val text = runCatching {
                if (file.exists()) file.readText().trim() else null
            }.getOrNull()
            if (text.isNullOrEmpty()) continue

            // The file keeps every run now, so show the one that just happened: the accounts
            // of earlier attempts are still there but they are not what this timeout is about.
            val last = text.substringAfterLast(STARTER_LOG_DIVIDER).trim()
            return file to last.ifEmpty { text }
        }
        return null
    }

    private val handler = CoroutineExceptionHandler { _, throwable ->
        ShizukuStateMachine.update()
        log(error = throwable)
    }

    private var started = false

    fun start(root: Boolean, isSystem: Boolean, port: Int, systemCustom: Boolean = false) {
        if (started) return
        started = true

        // Recorded here too: this activity is an entry point of its own (the root and
        // system rows start it directly), and the card reports the method that started
        // the running server.
        ShizukuSettings.setRunningStartMethod(
            when {
                root -> ShizukuSettings.StartMethod.ROOT
                isSystem -> ShizukuSettings.StartMethod.SYSTEM
                else -> ShizukuSettings.StartMethod.USB
            }
        )

        viewModelScope.launch(handler) {
            // Whether there is anything to wait for: a system start whose exploit target
            // this device does not ship has already said so, and waiting a minute for a
            // service nobody asked for only hides that.
            val waiting = when {
                root -> { startRoot(); true }
                isSystem -> { startSystemCustom(); true }
                else -> { AdbStarter.startAdb(appContext, port, { log(it) }); true }
            }
            try {
                if (waiting) Starter.waitForBinder({ log(it) })
            } catch (e: TimeoutException) {
                val starter = starterLog()
                if (starter != null) {
                    log(
                        "the starter left this behind, in ${starter.first.absolutePath}:\n" +
                            "${starter.second}\n"
                    )
                } else {
                    log(
                        "the starter left no log at all, so it never got as far as writing " +
                            "one: nothing in " +
                            starterLogFiles().joinToString(" or ") { it.absolutePath } +
                            "\n"
                    )
                }
                throw e
            }
        }
    }

    private suspend fun startSystemCustom() {
        // Two forms of the same thing, because which one works depends on the shell the
        // command is run in: the run-time lookup needs permission to ask the package
        // manager, and the absolute path is the one that works without it. The lookup is
        // the one copied, since it survives an update that moves the install.
        val command = systemStarterCommand()
        val plain = plainStarterCommand()

        log(appContext.getString(R.string.start_system_custom_intro))
        log(appContext.getString(R.string.start_system_custom_lookup))
        log(command)
        log("")
        log(appContext.getString(R.string.start_system_custom_plain))
        log(plain)

        val copied = withContext(Dispatchers.Main) {
            val clipboard =
                appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return@withContext false
            clipboard.setPrimaryClip(ClipData.newPlainText("Shizuku", command))
            true
        }

        log(
            appContext.getString(
                if (copied) R.string.start_system_custom_copied
                else R.string.start_system_custom_copy_failed
            )
        )
    }

    /**
     * The command resolves the installed APK's own lib directory at run time, so it keeps
     * working after an update moves it, and it names this package rather than a fixed one,
     * which matters for stealth mode.
     */
    /**
     * The same executable, named outright.
     *
     * The lookup above asks the package manager where this app is, which a shell that may
     * not query packages is refused. This is the path as this app knows it, right now.
     */
    private fun plainStarterCommand(): String =
        appContext.applicationInfo.nativeLibraryDir + "/libshizuku.so"

    private fun systemStarterCommand(): String {
        val packageName = appContext.packageName

        // The ABI directory this install actually uses, not arm64 by name: the app ships
        // four, and a 32-bit device looks in lib/arm.
        val abi = appContext.applicationInfo.nativeLibraryDir.substringAfterLast('/')

        // grep base.apk first, because pm path prints one line per split: on an install
        // delivered as an App Bundle that is several lines, and sed would return every one
        // of those paths joined together, which the shell then reads as one nonsense path.
        return "STARTER=\$(pm path $packageName | grep base.apk" +
            " | sed -E 's|^package:(.*/)[^/]+\\.apk\$|\\1lib/$abi/libshizuku.so|')" +
            " && \$STARTER"
    }

    /** Set as soon as the payload's shell reports anything at all. */
    @Volatile

    private fun log(line: String? = null, error: Throwable? = null) {
        line?.let { sb.appendLine(it) }
        error?.let { sb.appendLine().appendLine(Log.getStackTraceString(it)) }

        if (error == null) _output.postValue(Resource.success(sb))
        else _output.postValue(Resource.error(error, sb))
    }

    private suspend fun startRoot() {
        log("Starting with root...\n")

        return withContext(Dispatchers.IO) {
            if (!Shell.getShell().isRoot) {
                // Try again just in case
                Shell.getCachedShell()?.close()

                if (!Shell.getShell().isRoot) {
                    Shell.getCachedShell()?.close()
                    throw NotRootedException()
                }
            }

            ShizukuStateMachine.set(ShizukuStateMachine.State.STARTING)
            suspendCancellableCoroutine { cont ->
                Shell.cmd(Starter.internalCommand)
                    .to(object : CallbackList<String?>() {
                        override fun onAddElement(s: String?) { s?.let { log(it) } }
                    })
                    .submit {
                        if (it.isSuccess) {
                            cont.resume(Unit)
                        } else {
                            cont.resumeWithException(Exception("Failed to start with root"))
                        }
                    }
            }
        }
    }
    
}
