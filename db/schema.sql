-- Aegis Mirror — Local State Schema (Private, real user data)
-- SQLite-vec Schema with 15 core tables, merging template and production specifications.

PRAGMA foreign_keys = ON;

-- ==========================================
-- STATE & REGISTRY
-- ==========================================

-- 1. users
CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    username TEXT UNIQUE,
    hashed_password TEXT,
    trust_score REAL DEFAULT 1.0,
    created_at TEXT NOT NULL,
    display_alias TEXT
);

-- 2. activeprofile
CREATE TABLE IF NOT EXISTS activeprofile (
    user_id TEXT PRIMARY KEY,
    profile_name TEXT CHECK(profile_name IN ('Paranoid','Emergency','Standard','SocialDiet','Custom')),
    updated_at TEXT NOT NULL,
    custom_rules_json TEXT DEFAULT '{}',
    siren_triggers_enabled INTEGER CHECK(siren_triggers_enabled IN (0, 1)) DEFAULT 0,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 3. appregistry
CREATE TABLE IF NOT EXISTS appregistry (
    app_id TEXT PRIMARY KEY,
    display_name TEXT,
    category TEXT,
    signature_hash TEXT,
    is_trusted INTEGER CHECK(is_trusted IN (0, 1)) DEFAULT 0
);

-- 4. sandboxstate
CREATE TABLE IF NOT EXISTS sandboxstate (
    user_id TEXT PRIMARY KEY,
    wasm_module_version TEXT,
    last_health_check TEXT,
    wasm_engine_status TEXT CHECK(wasm_engine_status IN ('IDLE', 'RUNNING', 'CRASHED', 'BLOCKED')) DEFAULT 'IDLE',
    active_restrictions_count INTEGER DEFAULT 0,
    watchdog_latency_ms INTEGER DEFAULT 0,
    last_heartbeat TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- ==========================================
-- DYNAMIC POLICIES
-- ==========================================

-- 5. networkpolicies
CREATE TABLE IF NOT EXISTS networkpolicies (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL,
    app_id TEXT NOT NULL,
    action TEXT CHECK(action IN ('allow','block','bypass','MOCK')) DEFAULT 'allow',
    domain_rule TEXT NOT NULL,
    port_rule TEXT DEFAULT '*',
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (app_id) REFERENCES appregistry(app_id) ON DELETE CASCADE
);

-- 6. permissionoverrides
CREATE TABLE IF NOT EXISTS permissionoverrides (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL,
    app_id TEXT NOT NULL,
    permission TEXT CHECK(permission IN ('CAMERA', 'MICROPHONE', 'CONTACTS', 'LOCATION', 'STORAGE')) NOT NULL,
    override_value TEXT CHECK(override_value IN ('SILENT', 'NOISE', 'RANDOM', 'STATIC')) DEFAULT 'STATIC',
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (app_id) REFERENCES appregistry(app_id) ON DELETE CASCADE,
    UNIQUE(user_id, app_id, permission)
);

-- 7. emergencywhitelist
CREATE TABLE IF NOT EXISTS emergencywhitelist (
    app_id TEXT PRIMARY KEY,
    reason TEXT,
    target_pattern TEXT UNIQUE,
    description TEXT
);

-- 8. customuserrules
CREATE TABLE IF NOT EXISTS customuserrules (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL,
    rule_name TEXT,
    regex_pattern TEXT,
    action TEXT CHECK(action IN ('ALLOW', 'DENY', 'MOCK')) DEFAULT 'DENY',
    rule_json TEXT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- ==========================================
-- VIRTUALIZATION & IDENTITY MASKING
-- ==========================================

-- 9. generatedaliases
CREATE TABLE IF NOT EXISTS generatedaliases (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL,
    alias_email TEXT,
    created_at TEXT,
    alias_id TEXT,
    real_identity_id TEXT,
    alias_type TEXT CHECK(alias_type IN ('EMAIL', 'PHONE', 'NAME')),
    generated_value TEXT UNIQUE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 10. mockcontacts
CREATE TABLE IF NOT EXISTS mockcontacts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL,
    mock_json TEXT,
    contact_id TEXT,
    display_name TEXT,
    phone_number TEXT,
    email TEXT,
    organization TEXT,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 11. mocklocations
CREATE TABLE IF NOT EXISTS mocklocations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL,
    mock_json TEXT,
    location_id TEXT,
    latitude REAL,
    longitude REAL,
    offset_bearing REAL DEFAULT 0.0,
    offset_distance_meters REAL DEFAULT 0.0,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- ==========================================
-- OBSERVABILITY & MANAGEMENT
-- ==========================================

-- 12. trafficlogs
CREATE TABLE IF NOT EXISTS trafficlogs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL,
    summary TEXT,
    logged_at TEXT,
    log_id TEXT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    source_app TEXT,
    target_destination TEXT,
    security_narrative TEXT,
    action_taken TEXT CHECK(action_taken IN ('ALLOW', 'DENY', 'MOCK')) DEFAULT 'ALLOW',
    threat_level TEXT CHECK(threat_level IN ('INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL')) DEFAULT 'INFO',
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 13. vpssyncstate
CREATE TABLE IF NOT EXISTS vpssyncstate (
    user_id TEXT PRIMARY KEY,
    last_sync TEXT,
    sync_id TEXT,
    last_sync_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sync_checksum TEXT,
    pending_records_count INTEGER DEFAULT 0,
    sync_mode TEXT CHECK(sync_mode IN ('DELTA', 'FULL')) DEFAULT 'DELTA',
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 14. vpssyncqueue
CREATE TABLE IF NOT EXISTS vpssyncqueue (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL,
    payload TEXT,
    status TEXT DEFAULT 'pending',
    queue_id TEXT,
    payload_type TEXT,
    payload_data TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 15. systemhealth
CREATE TABLE IF NOT EXISTS systemhealth (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    checked_at TEXT,
    status TEXT,
    cpu_usage_pct REAL DEFAULT 0.0,
    ram_usage_mb REAL DEFAULT 0.0,
    network_rx_bytes INTEGER DEFAULT 0,
    network_tx_bytes INTEGER DEFAULT 0,
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ==========================================
-- SQLITE-VEC VIRTUAL TABLES FOR VECTOR SEARCH
-- ==========================================

CREATE VIRTUAL TABLE IF NOT EXISTS vec_traffic_logs USING vec0(
    log_id TEXT PRIMARY KEY,
    security_narrative_embedding float[1536]
);

CREATE VIRTUAL TABLE IF NOT EXISTS vec_mock_locations USING vec0(
    location_id TEXT PRIMARY KEY,
    location_embedding float[2]
);
