package com.aegismirror.app;

import java.io.Closeable;
import java.io.IOException;
import java.net.*;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Fixed loopback fixture only. Does not exercise Android VPN protection or network binding. */
public final class UdpSelfTest implements Closeable {
    private static final int LOOPBACK = 0x7f000001, APP = 0x0a000002;
    private boolean closed, started;
    private DatagramSocket server;
    private UdpForwarder active;

    private synchronized void checkOpen() throws IOException {
        if (closed) throw new IOException("self_test_closed");
    }
    private synchronized void attach(DatagramSocket socket) throws IOException {
        if (closed) { socket.close(); throw new IOException("self_test_closed"); }
        server = socket;
    }
    private synchronized void attach(UdpForwarder worker) throws IOException {
        if (closed) { worker.close(); throw new IOException("self_test_closed"); }
        active = worker;
    }
    private synchronized void begin() throws IOException {
        checkOpen();
        if (started) throw new IOException("self_test_already_started");
        started = true;
    }
    public void run() throws IOException {
        begin();
        FutureTask<Void> echo = null;
        Thread echoThread = null;
        try {
            byte[] nonce = new byte[32];
            new SecureRandom().nextBytes(nonce);
            InetAddress loopback = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
            DatagramSocket socket = new DatagramSocket(new InetSocketAddress(loopback, 0));
            attach(socket);
            socket.setSoTimeout(2000);
            int port = socket.getLocalPort();
            byte[] frame = Ipv4UdpPacket.encode(APP, LOOPBACK, 40000, port, nonce);
            AtomicInteger denyHooks = new AtomicInteger();
            try (UdpForwarder deny = new UdpForwarder(s -> {
                denyHooks.incrementAndGet(); return true;
            }, p -> false, 2000)) {
                attach(deny);
                if (deny.forward(frame, frame.length) != null || denyHooks.get() != 0)
                    throw new IOException("deny_reached_socket_hook");
            }
            socket.setSoTimeout(150);
            try {
                socket.receive(new DatagramPacket(new byte[64], 64));
                throw new IOException("deny_delivered_datagram");
            } catch (SocketTimeoutException expected) { checkOpen(); }
            socket.setSoTimeout(2000);
            echo = new FutureTask<>(() -> {
                DatagramPacket request = new DatagramPacket(new byte[64], 64);
                socket.receive(request);
                byte[] received = Arrays.copyOfRange(request.getData(), request.getOffset(),
                        request.getOffset() + request.getLength());
                if (!request.getAddress().isLoopbackAddress() || !Arrays.equals(nonce, received))
                    throw new IOException("unexpected_echo_request");
                socket.send(new DatagramPacket(received, received.length, request.getSocketAddress()));
                return null;
            });
            echoThread = new Thread(echo, "aegis-self-test-echo");
            echoThread.setDaemon(true);
            echoThread.start();
            AtomicInteger allowHooks = new AtomicInteger();
            // This hook is deliberately a fixture, NOT VpnService.protect().
            try (UdpForwarder allow = new UdpForwarder(s -> {
                allowHooks.incrementAndGet(); return true;
            }, p -> p.destination == LOOPBACK && p.destinationPort == port, 2000)) {
                attach(allow);
                byte[] output = allow.forward(frame, frame.length);
                if (output == null) throw new IOException("allow_denied");
                Ipv4UdpPacket reply = Ipv4UdpPacket.parse(output, output.length);
                if (allowHooks.get() != 1 || reply.source != LOOPBACK || reply.destination != APP
                        || reply.sourcePort != port || reply.destinationPort != 40000
                        || !Arrays.equals(nonce, reply.payload()))
                    throw new IOException("echo_reply_mismatch");
            }
            echo.get(3, TimeUnit.SECONDS);
            checkOpen();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("self_test_interrupted", error);
        } catch (ExecutionException | TimeoutException | RuntimeException error) {
            throw new IOException("self_test_failed", error);
        } finally {
            close();
            if (echo != null) echo.cancel(true);
            if (echoThread != null) {
                try { echoThread.join(500); }
                catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IOException("self_test_cleanup_interrupted", error);
                }
                if (echoThread.isAlive()) throw new IOException("self_test_cleanup_timeout");
            }
        }
    }
    @Override public synchronized void close() {
        closed = true;
        if (active != null) active.close();
        if (server != null) server.close();
    }
}
