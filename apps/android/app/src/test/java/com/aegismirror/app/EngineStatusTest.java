package com.aegismirror.app;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public final class EngineStatusTest {
    @Test
    public void initialStatusDoesNotClaimProtection() {
        assertFalse(EngineStatus.UNKNOWN.isProtectionVerified());
    }

    @Test
    public void onlyUnknownStateExistsInBuildScaffold() {
        assertEquals(1, EngineStatus.values().length);
        assertEquals("UNKNOWN", EngineStatus.values()[0].name());
    }
}
