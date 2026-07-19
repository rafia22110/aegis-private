// Aegis Mirror - Deterministic Real-Time Guardrail Router Hook
// PocketBase JS VM Hook System

// Hook: Register Custom API Routes
routerAdd("POST", "/api/telemetry", (c) => {
    // Read and parse request body data
    // In PocketBase, requestInfo().data contains parsed fields
    let reqData = c.requestInfo().data || {};
    
    let package_name = reqData.package_name || "com.unknown.app";
    let target_destination = reqData.target_destination || "telemetry.tracker.com";
    let permission_type = reqData.permission_type || "";
    
    // Default action and threat assessment
    let action = "ALLOW";
    let reason = "Default policy validation";
    let narrative = "Aegis verified outbound socket connection for " + package_name;
    let threat_level = "INFO";
    let payload = null;

    try {
        // 1. Emergency Whitelist Check
        // Query database to see if target_destination matches any pattern (e.g. redalert, 911)
        let whitelistRecords = [];
        $app.db().newQuery("SELECT * FROM emergency_whitelist")
            .all(whitelistRecords);

        let isWhitelisted = false;
        for (let i = 0; i < whitelistRecords.length; i++) {
            let pattern = whitelistRecords[i].target_pattern;
            // Simple regex/glob matching for target pattern
            if (target_destination.indexOf(pattern) !== -1 || new RegExp(pattern).test(target_destination)) {
                isWhitelisted = true;
                reason = "Emergency Whitelist Match: " + whitelistRecords[i].description;
                break;
            }
        }

        if (isWhitelisted) {
            action = "ALLOW";
            narrative = "Aegis bypassed VPN tunnel loopback for emergency signal (" + target_destination + ")";
            threat_level = "LOW";
        } else {
            // 2. Custom User Rules & Block Policies
            let blockRecords = [];
            $app.db().newQuery("SELECT * FROM network_policies WHERE package_name = {:pkg} AND (domain_rule = {:dest} OR domain_rule = '*')")
                .bind({ pkg: package_name, dest: target_destination })
                .all(blockRecords);

            if (blockRecords.length > 0) {
                let policy = blockRecords[0];
                action = policy.action; // 'ALLOW', 'DENY', or 'MOCK'
                
                if (action === "DENY") {
                    narrative = "Aegis safely deflected a background beacon from " + package_name + " targeting " + target_destination;
                    threat_level = "MEDIUM";
                } else if (action === "MOCK") {
                    narrative = "Aegis fed virtualized shielding parameters to " + package_name + " requesting " + target_destination;
                    threat_level = "LOW";
                }
            }

            // 3. Permission Overrides & Identity Masking Virtualization
            if (permission_type) {
                let overrides = [];
                $app.db().newQuery("SELECT * FROM permission_overrides WHERE package_name = {:pkg} AND permission_type = {:perm}")
                    .bind({ pkg: package_name, perm: permission_type })
                    .all(overrides);

                if (overrides.length > 0) {
                    let override = overrides[0];
                    action = "MOCK";
                    threat_level = "LOW";
                    let behavior = override.mocking_behavior; // SILENT, NOISE, RANDOM, STATIC

                    if (permission_type === "MICROPHONE") {
                        payload = {
                            vector_type: "audio_stream",
                            data: "SILENT_LOOP_RAW_PCM_8KHZ",
                            behavior: behavior
                        };
                        narrative = "Aegis spoofed microphone input for " + package_name + " with a silent stream.";
                    } else if (permission_type === "CONTACTS") {
                        let mockContacts = [];
                        $app.db().newQuery("SELECT * FROM mock_contacts LIMIT 5").all(mockContacts);
                        payload = {
                            vector_type: "contacts_list",
                            data: mockContacts,
                            behavior: behavior
                        };
                        narrative = "Aegis returned a secure virtualized contact directory to " + package_name;
                    } else if (permission_type === "LOCATION") {
                        let mockLocs = [];
                        $app.db().newQuery("SELECT * FROM mock_locations LIMIT 1").all(mockLocs);
                        let loc = mockLocs[0] || { latitude: 32.0853, longitude: 34.7818, offset_bearing: 90.0, offset_distance_meters: 150 };
                        payload = {
                            vector_type: "location_vector",
                            latitude: loc.latitude,
                            longitude: loc.longitude,
                            bearing: loc.offset_bearing,
                            distance: loc.offset_distance_meters,
                            behavior: behavior
                        };
                        narrative = "Aegis masked GPS coordinates for " + package_name + " using drift-offset vectors.";
                    } else {
                        payload = {
                            vector_type: "generic_stub",
                            status: "MOCKED",
                            behavior: behavior
                        };
                        narrative = "Aegis spoofed " + permission_type + " permissions for " + package_name;
                    }
                }
            }
        }

        // 4. Record security log inside traffic_logs
        let logId = "log_" + String(new Date().getTime()) + "_" + Math.random().toString(36).substring(2, 6);
        $app.db().newQuery("INSERT INTO traffic_logs (log_id, source_app, target_destination, security_narrative, action_taken, threat_level) VALUES ({:id}, {:src}, {:dest}, {:narr}, {:act}, {:threat})")
            .bind({
                id: logId,
                src: package_name,
                dest: target_destination,
                narr: narrative,
                act: action,
                threat: threat_level
            })
            .execute();

    } catch (e) {
        // Fallback logging in case of system exceptions
        narrative = "Aegis System exception: " + e.message;
        threat_level = "HIGH";
    }

    return c.json(200, {
        action: action,
        reason: reason,
        payload: payload,
        narrative: narrative,
        timestamp: new Date().toISOString()
    });
});

// Hook: HTMX Live Security Journal Logs
routerAdd("GET", "/api/journal", (c) => {
    try {
        let logs = [];
        $app.db().newQuery("SELECT * FROM traffic_logs ORDER BY timestamp DESC LIMIT 10")
            .all(logs);

        if (logs.length === 0) {
            // Seed a default narrative if logs are empty
            return c.html(200, `
                <div class="py-3 px-4 rounded border border-emerald-950/20 bg-emerald-950/5 text-emerald-400 text-sm flex items-center gap-3">
                    <span class="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
                    Aegis Active: Guardian system initialized. No packets dropped.
                </div>
            `);
        }

        // Render HTML for HTMX swapping
        let htmlResult = logs.map(log => {
            let badgeColor = "bg-emerald-500/10 text-emerald-400 border-emerald-500/20";
            let actionIndicator = "● ALLOW";
            
            if (log.action_taken === "DENY") {
                badgeColor = "bg-rose-500/10 text-rose-400 border-rose-500/20";
                actionIndicator = "■ DEFLECTED";
            } else if (log.action_taken === "MOCK") {
                badgeColor = "bg-amber-500/10 text-amber-400 border-amber-500/20";
                actionIndicator = "▲ SHIELDED";
            }

            let appName = log.source_app.split('.').pop() || log.source_app;
            let timeStr = new Date(log.timestamp).toLocaleTimeString();

            return `
                <div class="flex flex-col md:flex-row md:items-center justify-between py-3 border-b border-amber-900/10 hover:bg-amber-950/5 transition duration-150 gap-2">
                    <div class="flex items-start gap-3">
                        <div class="mt-1 px-1.5 py-0.5 text-[10px] font-mono tracking-wider rounded border ${badgeColor}">
                            ${actionIndicator}
                        </div>
                        <div>
                            <p class="text-stone-200 text-sm font-medium">${log.security_narrative}</p>
                            <p class="text-stone-500 text-xs mt-0.5">App: <span class="font-mono text-stone-400">${log.source_app}</span> | Destination: <span class="text-amber-700/80">${log.target_destination}</span></p>
                        </div>
                    </div>
                    <span class="text-stone-500 font-mono text-xs whitespace-nowrap self-end md:self-center">${timeStr}</span>
                </div>
            `;
        }).join("");

        return c.html(200, htmlResult);
    } catch(e) {
        return c.html(500, `<p class="text-rose-400 text-xs">Error loading journals: ${e.message}</p>`);
    }
});

// Hook: Register Beta Testers from Page 2
routerAdd("POST", "/api/register-test", (c) => {
    let reqData = c.requestInfo().data || {};
    let email = reqData.email || "";

    if (!email || email.indexOf("@") === -1) {
        return c.html(400, `
            <div class="p-3 rounded border border-rose-900/30 bg-rose-950/20 text-rose-400 text-xs font-mono">
                Error: A valid cryptographic email structure is required.
            </div>
        `);
    }

    try {
        // Save to users table or log registering
        let userId = "usr_" + String(new Date().getTime());
        // Generate a random temporary password
        let pwd = Math.random().toString(36).substring(2, 10);
        
        $app.db().newQuery("INSERT INTO users (id, username, hashed_password, trust_score) VALUES ({:id}, {:username}, {:pwd}, 1.0)")
            .bind({ id: userId, username: email, pwd: pwd })
            .execute();

        return c.html(200, `
            <div class="p-4 rounded border border-emerald-500/20 bg-emerald-950/20 text-emerald-400 text-sm animate-fade-in">
                <p class="font-semibold text-emerald-300">Beta Authorization Granted</p>
                <p class="text-xs text-stone-400 mt-1">Aegis credentials compiled for ${email}. Access keys generated successfully.</p>
            </div>
        `);
    } catch(e) {
        return c.html(500, `
            <div class="p-3 rounded border border-rose-900/30 bg-rose-950/20 text-rose-400 text-xs font-mono">
                System error or duplicate registration: ${e.message}
            </div>
        `);
    }
});
