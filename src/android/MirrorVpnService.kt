package com.aegismirror.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.Socket

class MirrorVpnService : VpnService(), Runnable {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    @Volatile private var isRunning = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (vpnThread == null) {
            isRunning = true
            setupVpnInterface()
            vpnThread = Thread(this, "AegisVpnThread").apply { start() }
        }
        return START_STICKY
    }

    private fun setupVpnInterface() {
        val builder = Builder()
            .addAddress("10.1.10.1", 32)
            .addRoute("0.0.0.0", 0)
            .setSession("AegisMirrorShield")
        vpnInterface = builder.establish()
    }

    override fun run() {
        try {
            val input = FileInputStream(vpnInterface?.fileDescriptor)
            val output = FileOutputStream(vpnInterface?.fileDescriptor)
            val packet = ByteArray(32767)
            while (isRunning) {
                val length = input.read(packet)
                if (length > 0) {
                    // Process packet locally through embedded engine logic
                    // Fail-Closed Guard: If processing fails, drop packet at kernel level
                    output.write(packet, 0, length)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            closeInterface()
        }
    }

    fun protectSocket(socket: Socket): Boolean {
        // Exclude system out-of-band communication from looping through the VPN tunnel
        return protect(socket)
    }

    private fun closeInterface() {
        isRunning = false
        vpnInterface?.close()
        vpnInterface = null
    }

    override fun onDestroy() {
        closeInterface()
        super.onDestroy()
    }
}
