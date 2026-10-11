package moe.shizuku.manager.starter

import android.util.Log
import androidx.lifecycle.asFlow
import java.io.File
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuApplication
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.start.StartStatusReporter
import moe.shizuku.manager.utils.ShizukuStateMachine

private val app = ShizukuApplication.application

object Starter {

    private val starterFile = File(app.applicationInfo.nativeLibraryDir, "libshizuku.so")

    val userCommand: String = starterFile.absolutePath
    val adbCommand = "adb shell $userCommand"
    val internalCommand = "$userCommand --apk=${app.applicationInfo.sourceDir}"

    val serviceStartedMessage = "Service started, this window will be automatically closed in 3 seconds"

    private suspend fun awaitRunning(timeoutMs: Long): Boolean {
        // Refresh before subscribing. This is important when a previous agent process is
        // still alive but its binder was never published (or has already died).
        ShizukuStateMachine.update()
        if (ShizukuStateMachine.isRunning()) return true

        return try {
            withTimeout(timeoutMs) {
                ShizukuStateMachine.asFlow()
                    .first { it == ShizukuStateMachine.State.RUNNING }
            }
            // Do not accept a transient RUNNING emission as success. Re-query the binder
            // and require it to still be alive before the starter reports success.
            ShizukuStateMachine.update()
            ShizukuStateMachine.isRunning()
        } catch (_: TimeoutCancellationException) {
            false
        }
    }

    suspend fun waitForBinder(log: ((String) -> Unit)? = null) {
        log?.invoke("\nWaiting for service binder...")

        // The old implementation waited a full minute even when the agent process was
        // already present but had failed to publish a usable binder. Probe in two stages:
        // an initial window and one refreshed recovery window. The caller can then retry
        // its normal start/fallback path instead of being trapped for 60 seconds.
        var running = awaitRunning(15_000)
        if (!running) {
            Log.w(AppConstants.TAG, "Starter: agent may be alive without binder; refreshing state")
            log?.invoke("\nAgent did not publish a binder; refreshing and retrying...")
            delay(500)
            running = awaitRunning(15_000)
        }

        if (!running) {
            StartStatusReporter.failed("Agent is running but no live Shizuku binder was received")
            throw TimeoutException(
                "Failed to receive a live Shizuku binder after recovery retry"
            )
        }

        log?.invoke(serviceStartedMessage)

        CoroutineScope(Dispatchers.IO).launch {
            delay(10_000)
            ShizukuStateMachine.update()

            when {
                ShizukuStateMachine.isRunning() ->
                    log?.invoke("\nThe service is still running.\n")

                ShizukuSettings.getManuallyStopped() ->
                    log?.invoke("\nThe service was stopped again.\n")

                log == null -> Log.i(
                    AppConstants.TAG,
                    "The service started and then went away again"
                )

                else -> {
                    log.invoke("\nThe service started and then went away again.\n")
                    StartStatusReporter.failed(
                        app.getString(R.string.start_failed_binder_went_away)
                    )
                }
            }
        }
    }
}
