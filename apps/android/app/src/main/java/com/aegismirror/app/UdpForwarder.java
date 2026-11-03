package com.aegismirror.app;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** One request/reply exchange at a time; NOT a general UDP flow/NAT implementation. */
public final class UdpForwarder implements Closeable {
    public interface SocketProtector {
        /** Android integration must protect AND bind to the intended underlying network as needed. */
        boolean protect(DatagramSocket socket) throws IOException;
    }
    public interface Policy { boolean allow(Ipv4UdpPacket packet); }
    private final SocketProtector protector;
    private final Policy policy;
    private final int timeoutMillis;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<DatagramSocket> active = new AtomicReference<>();
    public UdpForwarder(SocketProtector protector, Policy policy, int timeoutMillis) {
        this.protector = Objects.requireNonNull(protector);
        this.policy = Objects.requireNonNull(policy);
        if (timeoutMillis < 1 || timeoutMillis > 10000) throw new IllegalArgumentException("invalid_timeout");
        this.timeoutMillis = timeoutMillis;
    }
    /** Null means policy DENY. Malformed input throws; I/O failures never fall back to unprotected send. */
    public byte[] forward(byte[] frame, int length) throws IOException {
        if (closed.get()) throw new SocketException("forwarder_closed");
        Ipv4UdpPacket packet = Ipv4UdpPacket.parse(frame, length);
        try {
            if (!policy.allow(packet)) return null;
        } catch (RuntimeException error) { throw new IOException("policy_failed", error); }
        try (DatagramSocket socket = new DatagramSocket()) {
            if (!active.compareAndSet(null, socket)) throw new SocketException("forwarder_busy");
            try {
                if (closed.get()) throw new SocketException("forwarder_closed");
                if (!protector.protect(socket)) throw new SocketException("socket_protection_failed");
                socket.setSoTimeout(timeoutMillis);
                socket.connect(InetAddress.getByAddress(Ipv4UdpPacket.address(packet.destination)), packet.destinationPort);
                byte[] payload = packet.payload();
                socket.send(new DatagramPacket(payload, payload.length));
                byte[] buffer = new byte[Ipv4UdpPacket.MAX_PAYLOAD + 1];
                DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                socket.receive(response);
                if (closed.get()) throw new SocketException("forwarder_closed");
                if (response.getLength() > Ipv4UdpPacket.MAX_PAYLOAD) throw new IOException("reply_exceeds_supported_mtu");
                return packet.reply(Arrays.copyOf(buffer, response.getLength()));
            } finally { active.compareAndSet(socket, null); }
        } catch (RuntimeException error) { throw new IOException("forwarding_failed", error); }
    }
    @Override public void close() {
        closed.set(true);
        DatagramSocket socket = active.getAndSet(null);
        if (socket != null) socket.close();
    }
}
