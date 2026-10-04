package moe.shizuku.manager.starter

import android.util.Log
import java.io.File
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
        // Boot can race the sticky binder callback on newer Samsung builds: the server
        // process is already alive, but no new callback reaches this manager process.
        // Do not depend on a single callback. Actively re-query pingBinder through the
        // state machine until the deadline so an already-published binder is recovered.
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
        do {
            ShizukuStateMachine.update()
            if (ShizukuStateMachine.isRunning()) {
                // Confirm once more after a short settle period. This rejects a stale or
                // transient binder while still accepting a server that came up before us.
                delay(150)
                ShizukuStateMachine.update()
                if (ShizukuStateMachine.isRunning()) return true
            }
            delay(250)
        } while (android.os.SystemClock.elapsedRealtime() < deadline)

        ShizukuStateMachine.update()
        return ShizukuStateMachine.isRunning()
    }

    suspend fun waitForBinder(log: ((String) -> Unit)? = null) {
        log?.invoke("\nWaiting for service binder...")

        // The old implementation waited a full minute even when the agent process was
        // already present but had failed to publish a usable binder. Probe in two stages:
        // an initial window and one refreshed recovery window. The caller can then retry
        // its normal start/fallback path instead of being trapped for 60 seconds.
        var running = awaitRunning(20_000)
        if (!running) {
            Log.w(AppConstants.TAG, "Starter: agent may be alive without binder; refreshing state")
            log?.invoke("\nAgent did not publish a binder; refreshing and retrying...")
            delay(500)
            running = awaitRunning(20_000)
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
