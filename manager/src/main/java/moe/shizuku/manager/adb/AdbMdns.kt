package moe.shizuku.manager.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.Observer
import java.net.NetworkInterface

@RequiresApi(Build.VERSION_CODES.R)
class AdbMdns(
    context: Context, private val serviceType: String,
    private val observer: Observer<Pair<String, Int>>
) {

    private val handler = Handler(Looper.getMainLooper())
    private val resolveRetries = mutableMapOf<String, Int>()
    private var registered = false
    private var starting = false
    private var stopping = false
    private var generation = 0
    private var discoveryRetries = 0
    private var stopRetries = 0
    private var running = false
    private var serviceName: String? = null
    private val listener = DiscoveryListener(this)
    private val nsdManager: NsdManager = context.getSystemService(NsdManager::class.java)

    // NSD callbacks and caller requests share one looper: a worker may call stop
    // while Android is still delivering onDiscoveryStarted.
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == handler.looper) block() else handler.post { block() }
    }

    fun start() = onMain {
        if (!running) {
            running = true
            generation++
            discoveryRetries = 0
            resolveRetries.clear()
        }
        ensureDiscovery()
    }

    private fun ensureDiscovery() {
        if (!running || registered || starting || stopping) return
        starting = true
        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: RuntimeException) {
            starting = false
            retryDiscovery(e)
        }
    }

    fun stop() = onMain {
        running = false
        generation++
        serviceName = null
        stopRegisteredDiscovery()
    }

    private fun stopRegisteredDiscovery() {
        if (!registered || stopping) return
        stopping = true
        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (e: RuntimeException) {
            stopping = false
            registered = false
            Log.w(TAG, "Could not stop discovery", e)
            ensureDiscovery()
        }
    }

    private fun retryDiscovery(error: Throwable? = null) {
        Log.w(TAG, "Discovery unavailable; retry ${discoveryRetries + 1}", error)
        if (!running) return
        if (++discoveryRetries > 3) {
            running = false
            observer.onChanged("" to -1)
            return
        }
        val session = generation
        handler.postDelayed({ if (running && session == generation) ensureDiscovery() }, 1000)
    }

    private fun onDiscoveryStart() {
        starting = false
        registered = true
        stopRetries = 0
        if (!running) stopRegisteredDiscovery()
    }

    private fun onDiscoveryStop() {
        registered = false
        stopping = false
        // A new start requested during stop must wait for this callback.
        ensureDiscovery()
    }

    private fun onServiceFound(info: NsdServiceInfo) {
        if (!running || stopping || !registered) return
        try {
            nsdManager.resolveService(info, ResolveListener(this, generation))
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not resolve service", e)
        }
    }

    private fun onServiceLost(info: NsdServiceInfo) {
        if (running && info.serviceName == serviceName) {
            serviceName = null
            observer.onChanged("" to -1)
        }
    }

    private fun onServiceResolved(resolvedService: NsdServiceInfo) {
        val host = resolvedService.host?.hostAddress ?: return
        val localHost = runCatching {
            NetworkInterface.getNetworkInterfaces().asSequence().any { networkInterface ->
                networkInterface.inetAddresses.asSequence().any { host == it.hostAddress }
            }
        }.getOrDefault(false)
        if (running && localHost && resolvedService.port in 1..65535) {
            serviceName = resolvedService.serviceName
            observer.onChanged(host to resolvedService.port)
        }
    }

    internal class DiscoveryListener(private val adbMdns: AdbMdns) : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            Log.v(TAG, "onDiscoveryStarted: $serviceType")

            adbMdns.onMain { adbMdns.onDiscoveryStart() }
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(TAG, "onStartDiscoveryFailed: $serviceType, $errorCode")
            adbMdns.onMain {
                adbMdns.starting = false
                adbMdns.registered = false
                adbMdns.retryDiscovery()
            }
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.v(TAG, "onDiscoveryStopped: $serviceType")

            adbMdns.onMain { adbMdns.onDiscoveryStop() }
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(TAG, "onStopDiscoveryFailed: $serviceType, $errorCode")
            adbMdns.onMain {
                // Android may still own the listener. Retry its stop instead of
                // registering it a second time.
                adbMdns.stopping = false
                if (++adbMdns.stopRetries <= 3) {
                    adbMdns.handler.postDelayed({ adbMdns.stopRegisteredDiscovery() }, 1000)
                } else {
                    adbMdns.running = false
                    adbMdns.observer.onChanged("" to -1)
                }
            }
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            Log.v(TAG, "onServiceFound: ${serviceInfo.serviceName}")

            adbMdns.onMain { adbMdns.onServiceFound(serviceInfo) }
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            Log.v(TAG, "onServiceLost: ${serviceInfo.serviceName}")

            adbMdns.onMain { adbMdns.onServiceLost(serviceInfo) }
        }
    }

    internal class ResolveListener(private val adbMdns: AdbMdns, private val session: Int) : NsdManager.ResolveListener {
        override fun onResolveFailed(nsdServiceInfo: NsdServiceInfo, i: Int) {
            Log.w(TAG, "Resolve failed: ${nsdServiceInfo.serviceName}, $i")
            adbMdns.onMain {
                if (!adbMdns.running || session != adbMdns.generation) return@onMain
                val attempts = (adbMdns.resolveRetries[nsdServiceInfo.serviceName] ?: 0) + 1
                adbMdns.resolveRetries[nsdServiceInfo.serviceName] = attempts
                if (attempts <= 3) adbMdns.handler.postDelayed({
                    if (adbMdns.running && session == adbMdns.generation)
                        adbMdns.onServiceFound(nsdServiceInfo)
                }, 1000)
            }
        }

        override fun onServiceResolved(nsdServiceInfo: NsdServiceInfo) {
            adbMdns.onMain {
                if (session == adbMdns.generation) adbMdns.onServiceResolved(nsdServiceInfo)
            }
        }

    }

    companion object {
        const val TLS_CONNECT = "_adb-tls-connect._tcp"
        const val TLS_PAIRING = "_adb-tls-pairing._tcp"
        const val TAG = "AdbMdns"
    }
}
