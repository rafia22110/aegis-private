package com.aegismirror.app;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public final class LocalPolicyTest {
    private LocalPolicy.Rule rule(LocalPolicy.Action action, int port) {
        return new LocalPolicy.Rule(1, "com.example.app", "example.test", port, action);
    }
    @Test public void profileDefaultsAreExplicit() {
        assertEquals(LocalPolicy.Action.DENY, LocalPolicy.evaluate(LocalPolicy.Profile.PARANOID, Collections.emptyList(), "com.example.app", "example.test", 443));
        assertEquals(LocalPolicy.Action.ALLOW, LocalPolicy.evaluate(LocalPolicy.Profile.STANDARD, Collections.emptyList(), "com.example.app", "example.test", 443));
    }
    @Test public void denyWinsInBothOrders() {
        LocalPolicy.Rule allow = rule(LocalPolicy.Action.ALLOW, 443);
        LocalPolicy.Rule deny = rule(LocalPolicy.Action.DENY, 0);
        assertEquals(LocalPolicy.Action.DENY, LocalPolicy.evaluate(LocalPolicy.Profile.STANDARD, Arrays.asList(allow, deny), "com.example.app", "EXAMPLE.TEST.", 443));
        assertEquals(LocalPolicy.Action.DENY, LocalPolicy.evaluate(LocalPolicy.Profile.STANDARD, Arrays.asList(deny, allow), "com.example.app", "example.test", 443));
    }
    @Test public void specificPortsAndPackagesStayIsolated() {
        assertEquals(LocalPolicy.Action.ALLOW, LocalPolicy.evaluate(LocalPolicy.Profile.STANDARD, Collections.singletonList(rule(LocalPolicy.Action.DENY, 443)), "com.example.app", "example.test", 80));
        assertEquals(LocalPolicy.Action.ALLOW, LocalPolicy.evaluate(LocalPolicy.Profile.STANDARD, Collections.singletonList(rule(LocalPolicy.Action.DENY, 0)), "com.other.app", "example.test", 443));
    }
    @Test public void hostIsCanonical() { assertEquals("example.test", LocalPolicy.host("EXAMPLE.TEST.")); }
    @Test(expected=IllegalArgumentException.class) public void emptyLabelIsRejected() { LocalPolicy.host("a..test"); }
    @Test(expected=IllegalArgumentException.class) public void nullRulesAreRejected() { LocalPolicy.evaluate(LocalPolicy.Profile.STANDARD, null, "com.example.app", "example.test", 443); }
    @Test(expected=IllegalArgumentException.class) public void zeroRequestPortIsRejected() { LocalPolicy.evaluate(LocalPolicy.Profile.STANDARD, Collections.emptyList(), "com.example.app", "example.test", 0); }
}
