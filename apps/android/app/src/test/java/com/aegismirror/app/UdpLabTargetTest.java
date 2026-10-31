package com.aegismirror.app;

import org.junit.Test;
import static org.junit.Assert.*;

public final class UdpLabTargetTest {
    @Test public void acceptsNumericPrivateEndpoint() {
        UdpLabTarget target = new UdpLabTarget("192.168.1.10", 12345);
        assertEquals(0xc0a8010a, target.address); assertEquals(12345, target.port);
    }
    @Test public void rejectsHostnamesAndWhitespace() {
        for (String host : new String[]{"example.test", " 192.168.1.10", "192.168.1.10 "})
            assertThrows(IllegalArgumentException.class, () -> new UdpLabTarget(host, 12345));
    }
    @Test public void rejectsUnsupportedOrMalformedAddresses() {
        for (String host : new String[]{"0.0.0.0", "127.0.0.1", "224.0.0.1", "255.255.255.255", "169.254.1.1", "192.168.01.10", "256.1.1.1", "1.2.3", "::1", "1.2.3.999999999999"})
            assertThrows(IllegalArgumentException.class, () -> new UdpLabTarget(host, 12345));
    }
    @Test public void rejectsInvalidPorts() {
        for (int port : new int[]{-1, 0, 65536})
            assertThrows(IllegalArgumentException.class, () -> new UdpLabTarget("192.168.1.10", port));
    }
    @Test public void rejectsNullHost() {
        assertThrows(IllegalArgumentException.class, () -> new UdpLabTarget(null, 12345));
    }
}
