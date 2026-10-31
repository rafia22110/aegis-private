package com.aegismirror.app;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import static org.junit.Assert.*;

public final class UdpForwarderTest {
    private static final int APP = 0x0a000002, LOOPBACK = 0x7f000001;
    private byte[] frame(int port, byte[] data) {
        return Ipv4UdpPacket.encode(APP, LOOPBACK, 40000, port, data);
    }
    @Test public void oddPayloadRoundTripAndReply() {
        byte[] input = frame(5353, new byte[]{1, 2, 3});
        Ipv4UdpPacket packet = Ipv4UdpPacket.parse(input, input.length);
        assertEquals(APP, packet.source);
        assertEquals(5353, packet.destinationPort);
        assertArrayEquals(new byte[]{1, 2, 3}, packet.payload());
        byte[] reply = packet.reply(new byte[]{4});
        Ipv4UdpPacket parsed = Ipv4UdpPacket.parse(reply, reply.length);
        assertEquals(LOOPBACK, parsed.source);
        assertEquals(APP, parsed.destination);
        assertEquals(40000, parsed.destinationPort);
        assertArrayEquals(new byte[]{4}, parsed.payload());
    }
    @Test public void rejectsBadIpChecksum() {
        byte[] input = frame(5353, new byte[]{1}); input[10] ^= 1;
        assertThrows(IllegalArgumentException.class, () -> Ipv4UdpPacket.parse(input, input.length));
    }
    @Test public void rejectsBadUdpChecksum() {
        byte[] input = frame(5353, new byte[]{1}); input[28] ^= 1;
        assertThrows(IllegalArgumentException.class, () -> Ipv4UdpPacket.parse(input, input.length));
    }
    @Test public void acceptsOmittedIpv4UdpChecksum() {
        byte[] input = frame(5353, new byte[]{1}); input[26] = 0; input[27] = 0;
        assertArrayEquals(new byte[]{1}, Ipv4UdpPacket.parse(input, input.length).payload());
    }
    @Test public void rejectsFragments() {
        byte[] input = frame(5353, new byte[]{1}); input[6] = 0x20;
        assertThrows(IllegalArgumentException.class, () -> Ipv4UdpPacket.parse(input, input.length));
    }
    @Test public void rejectsTruncatedPacket() {
        byte[] input = frame(5353, new byte[]{1});
        assertThrows(IllegalArgumentException.class, () -> Ipv4UdpPacket.parse(input, input.length - 1));
    }
    @Test public void rejectsOversizedPayload() {
        assertThrows(IllegalArgumentException.class, () -> frame(5353, new byte[1473]));
    }
    @Test public void denialPrecedesSocketProtection() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        try (UdpForwarder worker = new UdpForwarder(s -> { called.set(true); return true; }, p -> false, 100)) {
            byte[] input = frame(5353, new byte[]{1});
            assertNull(worker.forward(input, input.length));
            assertFalse(called.get());
        }
    }
    @Test public void protectionFailureHasNoFallback() throws Exception {
        try (UdpForwarder worker = new UdpForwarder(s -> false, p -> true, 100)) {
            byte[] input = frame(5353, new byte[]{1});
            IOException error = assertThrows(IOException.class, () -> worker.forward(input, input.length));
            assertEquals("socket_protection_failed", error.getMessage());
        }
    }
    @Test public void policyFailureNeverReachesProtection() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        try (UdpForwarder worker = new UdpForwarder(s -> { called.set(true); return true; }, p -> { throw new IllegalStateException(); }, 100)) {
            byte[] input = frame(5353, new byte[]{1});
            assertThrows(IOException.class, () -> worker.forward(input, input.length));
            assertFalse(called.get());
        }
    }
    @Test public void closedWorkerRejectsRequests() throws Exception {
        UdpForwarder worker = new UdpForwarder(s -> true, p -> true, 100);
        worker.close();
        byte[] input = frame(5353, new byte[]{1});
        assertThrows(IOException.class, () -> worker.forward(input, input.length));
    }
    private byte[] exchange(byte[] response) throws Exception {
        ExecutorService serverThread = Executors.newSingleThreadExecutor();
        InetAddress loopback = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        try (DatagramSocket server = new DatagramSocket(new InetSocketAddress(loopback, 0));
             UdpForwarder worker = new UdpForwarder(s -> true, p -> true, 5000)) {
            server.setSoTimeout(5000);
            Future<?> task = serverThread.submit(() -> {
                try {
                    DatagramPacket request = new DatagramPacket(new byte[64], 64);
                    server.receive(request);
                    assertEquals(3, request.getLength());
                    assertEquals(9, request.getData()[0]);
                    server.send(new DatagramPacket(response, response.length, request.getSocketAddress()));
                } catch (IOException error) { throw new RuntimeException(error); }
            });
            byte[] input = frame(server.getLocalPort(), new byte[]{9, 8, 7});
            byte[] output = worker.forward(input, input.length);
            task.get(6, TimeUnit.SECONDS);
            return output;
        } finally { serverThread.shutdownNow(); }
    }
    @Test public void forwardsPayloadOverRealLoopbackUdpAndBuildsReply() throws Exception {
        byte[] output = exchange(new byte[]{6, 5, 4});
        Ipv4UdpPacket reply = Ipv4UdpPacket.parse(output, output.length);
        assertEquals(LOOPBACK, reply.source);
        assertEquals(APP, reply.destination);
        assertEquals(40000, reply.destinationPort);
        assertArrayEquals(new byte[]{6, 5, 4}, reply.payload());
    }
    @Test public void rejectsOversizedNetworkReply() {
        assertThrows(IOException.class, () -> exchange(new byte[1473]));
    }
}
