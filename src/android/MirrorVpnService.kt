package com.aegis.mirror.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MirrorVpnService handles the local Android VPN lifecycle, network encapsulation,
 * deterministic rules engine hook, and failsafe watchdogs.
 */
class MirrorVpnService : VpnService() {

    private val tag = "MirrorVpnService"
    private var vpnInterface: ParcelFileDescriptor? = null
    private val isRunning = AtomicBoolean(false)
    private var vpnThread: Thread? = null

    // Latency Watchdog configurations
    private val watchdogScope = CoroutineScope(Dispatchers.Default + Job())
    private val latencyCeilingMs = 50L
    private val failOpenActive = AtomicBoolean(false)

    // Configuration flags
    private var isAlwaysOnFailsafe = false // Corresponds to fail-closed configuration
    private var activeProfile = "Standard"  // Standard, Paranoid, Emergency, Social Diet

    override fun onCreate() {
        super.onCreate()
        Log.i(tag, "Aegis Mirror VPN Service created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val action = intent.action
            if ("START" == action) {
                isAlwaysOnFailsafe = intent.getBooleanExtra("ALWAYS_ON_FAILSAFE", false)
                activeProfile = intent.getStringExtra("PROFILE") ?: "Standard"
                startVpn()
            } else if ("STOP" == action) {
                stopVpn()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopVpn()
        watchdogScope.cancel()
        super.onDestroy()
    }

    /**
     * Start the VPN tunnel and start checking network boundaries.
     */
    private synchronized fun startVpn() {
        if (isRunning.get()) return
        isRunning.set(true)
        failOpenActive.set(false)

        Log.i(tag, "Starting VPN with profile: $activeProfile, Fail-Closed Always-on: $isAlwaysOnFailsafe")

        vpnThread = Thread({
            try {
                establishVpn()
                runTunnelLoop()
            } catch (e: Exception) {
                Log.e(tag, "Fatal loop crash: ${e.message}", e)
                handleEngineCrash()
            }
        }, "AegisVpnTunnelThread").apply { start() }

        // Start hardware latency loopback watchdog
        startWatchdog()
    }

    /**
     * Stop the VPN and release allocations.
     */
    private synchronized fun stopVpn() {
        if (!isRunning.get()) return
        isRunning.set(false)
        
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(tag, "Error closing VPN interface: ${e.message}")
        }
        vpnInterface = null
        vpnThread = null
        Log.i(tag, "Aegis Mirror VPN Service stopped.")
    }

    /**
     * Configure virtual network interface parameters.
     */
    private fun establishVpn() {
        val builder = Builder()
            .setSession("AegisMirrorShield")
            .addAddress("10.0.0.1", 24)
            .addDnsServer("1.1.1.1")
            .addRoute("0.0.0.0", 0) // Route all IPv4 traffic
            .setMtu(1500)

        // Android VpnService builder flags for Always-on VPN blocking
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        vpnInterface = builder.establish()
        Log.i(tag, "VPN Interface established.")
    }

    /**
     * Main network loop that reads packets and routes them.
     */
    private fun runTunnelLoop() {
        val fd = vpnInterface?.fileDescriptor ?: return
        val inputStream = FileInputStream(fd)
        val outputStream = FileOutputStream(fd)
        val packetBuffer = ByteArray(32767)

        while (isRunning.get()) {
            val startTime = System.currentTimeMillis()
            val readBytes = inputStream.read(packetBuffer)

            if (readBytes > 0) {
                // If the Latency Watchdog triggers a fail-open state (for standard profiles only)
                // we bypass policy enforcement and forward packets directly to maintain baseline access.
                if (failOpenActive.get() && activeProfile != "Paranoid") {
                    // Fail-Open Bypass: forward packets without applying rules
                    forwardPacketDirectly(packetBuffer, readBytes, outputStream)
                } else {
                    // Process packet, check local rules, intercept policies
                    val processingTime = System.currentTimeMillis() - startTime
                    if (processingTime > latencyCeilingMs) {
                        Log.w(tag, "Policy processing exceeded latency ceiling ($processingTime ms)")
                    }
                    
                    // Inspect and apply policies
                    processAppTelemetryPacket(packetBuffer, readBytes, outputStream)
                }
            }
        }
    }

    private fun processAppTelemetryPacket(packet: ByteArray, size: Int, outStream: FileOutputStream) {
        // Enforce policies, mocking behavior, sandbox rules
        // Standard packet routing...
        outStream.write(packet, 0, size)
    }

    private fun forwardPacketDirectly(packet: ByteArray, size: Int, outStream: FileOutputStream) {
        // Direct routing bypass
        outStream.write(packet, 0, size)
    }

    /**
     * Exempt outbound connections from the VPN looping pipelines (such as reporting telemetry
     * back to PocketBase in Repository 1 or secure sync channels).
     */
    fun exemptSocketFromVpn(socket: Socket) {
        try {
            // CALLING NATIVE vpnService.protect(socket) command
            val success = protect(socket)
            Log.d(tag, "TCP Socket protected: $success")
        } catch (e: Exception) {
            Log.e(tag, "Failed to protect TCP Socket: ${e.message}")
        }
    }

    fun exemptSocketFromVpn(socket: DatagramSocket) {
        try {
            // CALLING NATIVE vpnService.protect(socket) command
            val success = protect(socket)
            Log.d(tag, "UDP Socket protected: $success")
        } catch (e: Exception) {
            Log.e(tag, "Failed to protect UDP Socket: ${e.message}")
        }
    }

    /**
     * Monitor policy latencies via a loopback timer.
     */
    private fun startWatchdog() {
        watchdogScope.launch {
            while (isRunning.get()) {
                delay(1000) // Pulse check every second
                
                val currentLatency = measureLoopbackLatency()
                if (currentLatency > latencyCeilingMs) {
                    if (activeProfile == "Paranoid" || activeProfile == "Emergency") {
                        // Strict profiles do not allow fail-open. Under strict mode, we remain closed.
                        Log.w(tag, "Strict profile active ($activeProfile). Latency is $currentLatency ms, holding Closed enforcement.")
                    } else {
                        // Fail-Open Trigger: Standard/Diet profiles drop constraints to prevent network interruption
                        if (!failOpenActive.get()) {
                            failOpenActive.set(true)
                            Log.e(tag, "Watchdog triggered: latency ceiling exceeded ($currentLatency ms). Failing open.")
                        }
                    }
                } else if (failOpenActive.get() && currentLatency <= latencyCeilingMs) {
                    // Recover back to enforcement once latency returns to normal
                    failOpenActive.set(false)
                    Log.i(tag, "Latency stabilized ($currentLatency ms). Resuming policy enforcement.")
                }
            }
        }
    }

    private fun measureLoopbackLatency(): Long {
        // Mock checking rules latency execution cycle
        return (10..40).random().toLong()
    }

    /**
     * Critical Failsafe Handler.
     * Handles local engine crashes.
     */
    private fun handleEngineCrash() {
        Log.e(tag, "CRITICAL: Local VPN engine has crashed!")
        
        if (isAlwaysOnFailsafe) {
            // FAIL-CLOSED ARCHITECTURE
            // We shut down the interface but DO NOT clean up parameters. Because Android Always-On VPN
            // has "Block connections without VPN" active, the operating system blocks all internet access
            // at the kernel socket layer. Cellular voice and standard carrier SMS continue working natively out-of-band.
            Log.e(tag, "FAIL-CLOSED: Kernel lock down active. Dropping sockets to prevent structural data leaks.")
            vpnInterface?.close()
            vpnInterface = null
        } else {
            // Standard recovery - attempt restart or shutdown clean
            stopVpn()
        }
    }
}
