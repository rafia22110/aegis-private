package com.aegismirror.app;

import java.net.IDN;
import java.util.List;
import java.util.Locale;

/** Local policy decisions only; no network interception. */
public final class LocalPolicy {
    private LocalPolicy() {}
    public enum Action { ALLOW, DENY }
    public enum Profile {
        STANDARD(Action.ALLOW), PARANOID(Action.DENY), EMERGENCY(Action.ALLOW),
        SOCIAL_DIET(Action.ALLOW), CUSTOM(Action.ALLOW);
        public final Action fallback;
        Profile(Action fallback) { this.fallback = fallback; }
    }
    public static String packageName(String value) {
        if (value == null || value.length() > 255 || !value.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")) {
            throw new IllegalArgumentException("invalid_package");
        }
        return value;
    }
    public static String host(String value) {
        if (value == null || !value.equals(value.trim())) throw new IllegalArgumentException("invalid_host");
        if (value.endsWith(".")) value = value.substring(0, value.length() - 1);
        String result = IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        if (result.isEmpty() || result.length() > 253) throw new IllegalArgumentException("invalid_host");
        for (String label : result.split("\\.", -1)) {
            if (!label.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) throw new IllegalArgumentException("invalid_host");
        }
        return result;
    }
    public static final class Rule {
        public final long id;
        public final String app, host;
        public final int port;
        public final Action action;
        public Rule(long id, String app, String host, int port, Action action) {
            if (port < 0 || port > 65535 || action == null) throw new IllegalArgumentException("invalid_rule");
            this.id = id;
            this.app = packageName(app);
            this.host = LocalPolicy.host(host);
            this.port = port;
            this.action = action;
        }
    }
    public static Action evaluate(Profile profile, List<Rule> rules, String app, String destination, int port) {
        if (profile == null || rules == null || port < 1 || port > 65535) throw new IllegalArgumentException("invalid_request");
        packageName(app);
        String host = host(destination);
        boolean allowed = false;
        for (Rule rule : rules) {
            if (rule == null) throw new IllegalArgumentException("invalid_rule");
            if (!rule.app.equals(app) || !rule.host.equals(host) || (rule.port != 0 && rule.port != port)) continue;
            if (rule.action == Action.DENY) return Action.DENY;
            allowed = true;
        }
        return allowed ? Action.ALLOW : profile.fallback;
    }
}
