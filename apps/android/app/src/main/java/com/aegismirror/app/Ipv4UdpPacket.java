package com.aegismirror.app;

import java.util.Arrays;

/** Deliberately limited to unfragmented IPv4/UDP, no IP options, maximum 1500 bytes. */
public final class Ipv4UdpPacket {
    public static final int MAX_PAYLOAD = 1472;
    public final int source, destination, sourcePort, destinationPort;
    private final byte[] data;
    private Ipv4UdpPacket(int source, int destination, int sourcePort, int destinationPort, byte[] data) {
        this.source = source;
        this.destination = destination;
        this.sourcePort = sourcePort;
        this.destinationPort = destinationPort;
        this.data = data;
    }
    public byte[] payload() { return data.clone(); }
    public static byte[] address(int value) {
        return new byte[]{(byte)(value >>> 24), (byte)(value >>> 16), (byte)(value >>> 8), (byte)value};
    }
    private static boolean unicast(int address) {
        int first = address >>> 24;
        return first > 0 && first < 224;
    }
    private static int u16(byte[] b, int offset) {
        return ((b[offset] & 255) << 8) | (b[offset + 1] & 255);
    }
    private static int u32(byte[] b, int offset) {
        return (u16(b, offset) << 16) | u16(b, offset + 2);
    }
    private static void put16(byte[] b, int offset, int value) {
        b[offset] = (byte)(value >>> 8);
        b[offset + 1] = (byte)value;
    }
    private static void put32(byte[] b, int offset, int value) {
        put16(b, offset, value >>> 16);
        put16(b, offset + 2, value);
    }
    private static int checksum(byte[] b, int offset, int length, long sum) {
        int end = offset + length;
        while (offset + 1 < end) { sum += u16(b, offset); offset += 2; }
        if (offset < end) sum += (b[offset] & 255) << 8;
        while ((sum >>> 16) != 0) sum = (sum & 65535) + (sum >>> 16);
        return ((int)~sum) & 65535;
    }
    private static long pseudo(byte[] b, int udpLength) {
        return (long)u16(b, 12) + u16(b, 14) + u16(b, 16) + u16(b, 18) + 17 + udpLength;
    }
    public static Ipv4UdpPacket parse(byte[] frame, int length) {
        if (frame == null || length < 28 || length > 1500 || length > frame.length)
            throw new IllegalArgumentException("invalid_size");
        if ((frame[0] & 255) != 0x45 || (frame[9] & 255) != 17)
            throw new IllegalArgumentException("unsupported_protocol_or_options");
        if (u16(frame, 2) != length || (u16(frame, 6) & 0xbfff) != 0 || (frame[8] & 255) == 0)
            throw new IllegalArgumentException("invalid_length_fragment_or_ttl");
        if (checksum(frame, 0, 20, 0) != 0) throw new IllegalArgumentException("invalid_ip_checksum");
        int udpLength = u16(frame, 24);
        if (udpLength != length - 20 || udpLength < 8) throw new IllegalArgumentException("invalid_udp_length");
        if (u16(frame, 26) != 0 && checksum(frame, 20, udpLength, pseudo(frame, udpLength)) != 0)
            throw new IllegalArgumentException("invalid_udp_checksum");
        int source = u32(frame, 12), destination = u32(frame, 16);
        int sourcePort = u16(frame, 20), destinationPort = u16(frame, 22);
        if (!unicast(source) || !unicast(destination) || sourcePort == 0 || destinationPort == 0)
            throw new IllegalArgumentException("unsupported_address_or_port");
        return new Ipv4UdpPacket(source, destination, sourcePort, destinationPort, Arrays.copyOfRange(frame, 28, length));
    }
    public static byte[] encode(int source, int destination, int sourcePort, int destinationPort, byte[] data) {
        if (!unicast(source) || !unicast(destination) || sourcePort < 1 || sourcePort > 65535
                || destinationPort < 1 || destinationPort > 65535 || data == null || data.length > MAX_PAYLOAD)
            throw new IllegalArgumentException("unsupported_packet");
        byte[] frame = new byte[28 + data.length];
        frame[0] = 0x45;
        put16(frame, 2, frame.length);
        frame[8] = 64;
        frame[9] = 17;
        put32(frame, 12, source);
        put32(frame, 16, destination);
        put16(frame, 20, sourcePort);
        put16(frame, 22, destinationPort);
        int udpLength = 8 + data.length;
        put16(frame, 24, udpLength);
        System.arraycopy(data, 0, frame, 28, data.length);
        int udpChecksum = checksum(frame, 20, udpLength, pseudo(frame, udpLength));
        put16(frame, 26, udpChecksum == 0 ? 65535 : udpChecksum);
        put16(frame, 10, checksum(frame, 0, 20, 0));
        return frame;
    }
    public byte[] reply(byte[] payload) {
        return encode(destination, source, destinationPort, sourcePort, payload);
    }
}
