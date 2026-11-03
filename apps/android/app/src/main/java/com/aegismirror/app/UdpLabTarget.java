package com.aegismirror.app;

/** Numeric test endpoint only. No hostname lookup and no default destination. */
public final class UdpLabTarget {
    public final int address, port;
    public UdpLabTarget(String host, int port) {
        if (host == null || host.length() > 15 || !host.matches("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+")
                || port < 1 || port > 65535) throw new IllegalArgumentException("invalid_target");
        String[] labels = host.split("\\.");
        int value = 0;
        for (String label : labels) {
            if (label.length() > 3 || (label.length() > 1 && label.charAt(0) == '0'))
                throw new IllegalArgumentException("invalid_target");
            int octet = Integer.parseInt(label);
            if (octet > 255) throw new IllegalArgumentException("invalid_target");
            value = (value << 8) | octet;
        }
        int first = value >>> 24;
        if (first == 0 || first == 127 || first >= 224 || (value & 0xffff0000) == 0xa9fe0000)
            throw new IllegalArgumentException("unsupported_target");
        address = value;
        this.port = port;
    }
}
