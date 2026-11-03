package com.aegismirror.app;

import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public final class UdpSelfTestTest {
    @Test(timeout = 10000) public void loopbackAllowDenyAndCleanupComplete() throws Exception {
        try (UdpSelfTest test = new UdpSelfTest()) { test.run(); }
    }
    @Test public void closedTestCannotStart() {
        UdpSelfTest test = new UdpSelfTest();
        test.close();
        assertThrows(IOException.class, test::run);
    }
    @Test(timeout = 10000) public void completedTestCannotRunAgain() throws Exception {
        try (UdpSelfTest test = new UdpSelfTest()) {
            test.run();
            assertThrows(IOException.class, test::run);
        }
    }
    @Test public void closeIsIdempotent() {
        UdpSelfTest test = new UdpSelfTest();
        test.close(); test.close();
    }
}
