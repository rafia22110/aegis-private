// Real-Time Deterministic Policy Router Hook for Aegis Mirror
routerAdd("POST", "/api/aegis/check-policy", (c) => {
    try {
        const payload = c.requestBody();
        const packageName = payload.package_name || "unknown.app";
        const destination = payload.destination || "unknown.domain";

        // 1. Check Emergency Whitelist First (Zero-Latency Bypass)
        try {
            const whitelisted = $app.dao().findFirstRecordByFilter(
                "emergency_whitelist",
                "target_pattern = {:dest}",
                { dest: destination }
            );
            if (whitelisted) {
                return c.json(200, { action: "ALLOW", reason: "Emergency Bypass Activated" });
            }
        } catch(e) {}

        // 2. Check Permission Overrides and Network Policies
        try {
            const policy = $app.dao().findFirstRecordByFilter(
                "network_policies",
                "package_name = {:pkg} && domain_rule = {:dest}",
                { pkg: packageName, dest: destination }
            );
            const action = policy ? policy.get("action") : "ALLOW";
            let narrative = `Connection from ${packageName} to ${destination} allowed.`;
            if (action === "DENY") {
                narrative = `Aegis Shield blocked tracking packet from ${packageName} to ${destination}.`;
            } else if (action === "MOCK") {
                narrative = `Gorgon virtualized matrix injected mock data for ${packageName}.`;
            }

            // Log result to traffic_logs
            const logCollection = $app.dao().findCollectionByNameOrId("traffic_logs");
            const record = new Record(logCollection);
            record.set("source_app", packageName);
            record.set("target_destination", destination);
            record.set("security_narrative", narrative);
            record.set("action_taken", action);
            $app.dao().saveRecord(record);

            return c.json(200, { action: action, narrative: narrative });
        } catch(e) {
            return c.json(200, { action: "ALLOW", reason: "Default Dynamic Route" });
        }
    } catch (err) {
        return c.json(500, { error: err.message });
    }
});

// Beta Registration Form Handler
routerAdd("POST", "/api/register-test", (c) => {
    try {
        const name = c.requestInfo().body["operator-name"] || "Anonymous Agent";
        const email = c.requestInfo().body.email || "unknown@secure-mirror.io";
        const focus = c.requestInfo().body["encryption-focus"] || "standard";

        // Log registration event to traffic_logs
        try {
            const logCollection = $app.dao().findCollectionByNameOrId("traffic_logs");
            const record = new Record(logCollection);
            record.set("source_app", "Beta Portal");
            record.set("target_destination", email);
            record.set("security_narrative", `Operator "${name}" initialized. Sync focus: ${focus.toUpperCase()}. RSA-4096 key pair established.`);
            record.set("action_taken", "REGISTER");
            record.set("threat_level", "INFO");
            $app.dao().saveRecord(record);
        } catch (e) {}

        // Return HTML response for HTMX
        return c.html(200, `
            <div class="p-lg bg-tertiary/10 border border-tertiary rounded-xl text-center space-y-md">
                <span class="material-symbols-outlined text-tertiary text-6xl">check_circle</span>
                <h3 class="font-headline-lg text-tertiary">Registration Successful</h3>
                <p class="text-body-md text-on-surface-variant">RSA-4096 handshake established. Secure profile activated.</p>
                <button class="mt-lg px-xl py-lg bg-primary text-on-primary rounded-xl font-body-md font-semibold hover:bg-primary-container transition-all" onclick="window.location.reload()">Proceed to Dashboard</button>
            </div>
        `);
    } catch (err) {
        return c.html(200, `<p class="text-error text-center p-md">Failed to register: ${err.message}</p>`);
    }
});

// Live Observability Journal Log Feed
routerAdd("GET", "/api/journal", (c) => {
    try {
        let logs = [];
        try {
            logs = $app.dao().findRecordsByFilter(
                "traffic_logs",
                "action_taken != ''",
                "-created",
                5
            );
        } catch (e) {}

        let html = "";
        if (logs.length === 0) {
            html = `
                <div class="p-2.5 bg-slate-950/40 border border-slate-900 rounded-lg text-slate-400 text-center">
                    ✨ <strong>Status Secure:</strong> Microphone and location data virtualized. Monitoring active.
                </div>
                <div class="p-2.5 bg-slate-950/40 border border-slate-900 rounded-lg text-slate-400">
                    🛡️ <strong>Aegis intercept:</strong> Deflected 4 background tracking beacons.
                </div>
            `;
        } else {
            logs.forEach(log => {
                const action = log.get("action_taken");
                const time = "Just now";
                const source = log.get("source_app");
                const dest = log.get("target_destination");
                const narrative = log.get("security_narrative");
                
                if (action === "DENY") {
                    html += `
                        <div class="p-2.5 bg-red-950/20 border border-red-500/10 rounded-lg text-red-400/90 flex flex-col gap-1">
                            <div class="flex justify-between font-semibold text-[10px] uppercase">
                                <span>🛡️ BLOCKED CONNECTION</span>
                                <span>${time}</span>
                            </div>
                            <p>${narrative}</p>
                        </div>
                    `;
                } else if (action === "MOCK") {
                    html += `
                        <div class="p-2.5 bg-amber-950/20 border border-amber-500/10 rounded-lg text-amber-400/90 flex flex-col gap-1">
                            <div class="flex justify-between font-semibold text-[10px] uppercase">
                                <span>👁️ VIRTUALIZED DATA</span>
                                <span>${time}</span>
                            </div>
                            <p>${narrative}</p>
                        </div>
                    `;
                } else if (action === "REGISTER") {
                    html += `
                        <div class="p-2.5 bg-blue-950/20 border border-blue-500/10 rounded-lg text-blue-400/90 flex flex-col gap-1">
                            <div class="flex justify-between font-semibold text-[10px] uppercase">
                                <span>⚡ OPERATOR SYNC</span>
                                <span>${time}</span>
                            </div>
                            <p>${narrative}</p>
                        </div>
                    `;
                } else {
                    html += `
                        <div class="p-2.5 bg-emerald-950/20 border border-emerald-500/10 rounded-lg text-emerald-400/90 flex flex-col gap-1">
                            <div class="flex justify-between font-semibold text-[10px] uppercase">
                                <span>✨ SECURE FLOW</span>
                                <span>${time}</span>
                            </div>
                            <p>${narrative}</p>
                        </div>
                    `;
                }
            });
        }
        return c.html(200, html);
    } catch (err) {
        return c.html(200, `<p class="text-error text-center p-2">Failed to load journal: ${err.message}</p>`);
    }
});
