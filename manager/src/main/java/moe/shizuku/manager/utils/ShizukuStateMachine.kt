package moe.shizuku.manager.utils

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import moe.shizuku.manager.ShizukuApplication
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.start.grantWriteSecureSettingsIfNeeded
import rikka.shizuku.Shizuku
import moe.shizuku.manager.tokenx.TokenXBootOwner
import moe.shizuku.manager.tokenx.TokenXBootSession
import moe.shizuku.manager.tokenx.TokenXBootState

private val appContext = ShizukuApplication.appContext

object ShizukuStateMachine {

    enum class State { STARTING, RUNNING, STOPPING, STOPPED, CRASHED }

    private var state = AtomicReference<State>(State.STOPPED)
    private val listeners = CopyOnWriteArrayList<(State) -> Unit>()

    init {
        Shizuku.addBinderReceivedListenerSticky(
            Shizuku.OnBinderReceivedListener { set(State.RUNNING) }
        )
        Shizuku.addBinderDeadListener(
            Shizuku.OnBinderDeadListener { setDead() }
        )
    }

    fun get(): State = state.get()

    private fun transition(transform: (State) -> State) {
        val oldState = state.getAndUpdate(transform)
        val newState = transform(oldState)
        if(oldState != newState) {
            // A confirmed running server lifts manual-stop suppression, so a
            // later crash is auto-restarted by the watchdog.
            if (newState == State.RUNNING) {
                ShizukuSettings.setManuallyStopped(false)

                // Binder receipt is the authoritative completion signal for a TokenX
                // boot claim. Only confirm a Root-owned session when the live server
                // itself reports UID 0; this prevents an unrelated/fallback server
                // from falsely confirming the Root generation.
                runCatching {
                    val boot = TokenXBootSession.current()
                    if (boot.owner == TokenXBootOwner.ROOT && Shizuku.getUid() == 0) {
                        when (boot.state) {
                            TokenXBootState.SERVER_STARTING -> {
                                TokenXBootSession.markBinderReady(TokenXBootOwner.ROOT)
                                TokenXBootSession.confirm(TokenXBootOwner.ROOT)
                            }
                            TokenXBootState.BINDER_READY ->
                                TokenXBootSession.confirm(TokenXBootOwner.ROOT)
                            TokenXBootState.RECOVERY_CLAIMED ->
                                TokenXBootSession.confirmRecovery(TokenXBootOwner.ROOT)
                            else -> Unit
                        }
                    }
                }.onFailure {
                    Log.w("TokenXBoot", "Unable to confirm Root boot session", it)
                }
                // The server is up, so it can hand us the ADB-only permission the wireless
                // flow needs the user shouldn't have to reach for a computer for it.
                grantWriteSecureSettingsIfNeeded()
                // Remember how the server was launched so later background starts
                // know whether to use root or wireless debugging (previously done by
                // the removed HomeViewModel).
                runCatching {
                    ShizukuSettings.setLastLaunchMode(
                        if (Shizuku.getUid() == 0) ShizukuSettings.LaunchMethod.ROOT
                        else ShizukuSettings.LaunchMethod.ADB
                    )
                }
            }
            // A deliberate stop (STOPPING -> STOPPED) is where the debugging toggles get
            // switched off, if the settings ask for it.
            if (oldState == State.STOPPING && newState == State.STOPPED) {
                disableDebuggingTogglesIfAsked()
            }

            // Deliberately NOT clearing the recorded transport when the server stops.
            // It describes how the server was launched, so it stays true after the
            // launch ends and clearing it here also wiped it on every transient
            // STOPPED while a start was still coming up (the binder is not up yet),
            // which left a running server being reported as "Unknown".
            listeners.forEach { it(newState) }
            Log.d("ShizukuStateMachine", newState.toString())
            when (newState) {
                State.RUNNING, State.STOPPED, State.CRASHED -> sendShizukuChangedBroadcast(newState)
                else -> Unit
            }
        }
    }

    // Broadcast so automation apps (e.g. MacroDroid/Tasker) can react to
    // Shizuku starting or stopping.
    private fun sendShizukuChangedBroadcast(newState: State) {
        val intent = Intent("${appContext.packageName}.SHIZUKU_CHANGED").apply {
            putExtra("status", if (newState == State.RUNNING) 1 else 0)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        appContext.sendBroadcast(intent)
    }

    fun set(newState: State) = transition { newState }

    fun setDead() = transition {
        when (it) {
            State.RUNNING -> State.CRASHED
            State.STOPPING -> State.STOPPED
            else -> it
        }
    }

    /**
     * Turns the debugging toggles off after a deliberate stop, when the user asked for
     * that in settings.
     *
     * Called from the transition rather than from [setDead] because the binder dying can
     * be noticed by [update] first which reaches STOPPED from STOPPING just the same,
     * and used to skip this entirely.
     */
    private fun disableDebuggingTogglesIfAsked() {
        try {
            val granted = appContext.checkSelfPermission(WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return

            if (ShizukuSettings.getAutoDisableUsbDebugging()) {
                Settings.Global.putInt(appContext.contentResolver, Settings.Global.ADB_ENABLED, 0)
            }
            // Wireless debugging is kept on by default that is what lets Shizuku restart
            // with no Wi-Fi so turning it off with Shizuku is opt-in. The opt-in is
            // ignored while the experiment that keeps it on without a network is in use:
            // stopping Shizuku would otherwise undo the state the whole trick exists to
            // hold, and the next start would have to win it all over again.
            if (ShizukuSettings.getAutoDisableWirelessDebugging() &&
                !ShizukuSettings.getForceWirelessDebugging()
            ) {
                Settings.Global.putInt(appContext.contentResolver, "adb_wifi_enabled", 0)
            }
        } catch (e: Exception) {
            Log.w("ShizukuStateMachine", "Failed to disable the debugging toggles", e)
        }
    }

    fun update(): State {
        val state = if (Shizuku.pingBinder()) State.RUNNING else State.STOPPED
        set(state)
        // Also covers a server that was already running when this process started, or a
        // permission that was revoked behind our back: there is no transition to hook then.
        if (state == State.RUNNING) grantWriteSecureSettingsIfNeeded()
        return state
    }

    fun isRunning(): Boolean {
        return get() == State.RUNNING
    }

    fun isDead(): Boolean {
        return (get() == State.STOPPED || get() == State.CRASHED) 
    }

    fun addListener(listener: (State) -> Unit) {
        listeners.add(listener)
        listener(state.get())
    }

    fun removeListener(listener: (State) -> Unit) {
        listeners.remove(listener)
    }

    fun asFlow(): Flow<State> = callbackFlow {
        val listener: (State) -> Unit = { trySend(it).isSuccess }
        addListener(listener)
        awaitClose { removeListener(listener) }
    }

}