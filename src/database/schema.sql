-- Aegis Mirror Core Data Architecture
-- SQLite-vec Schema with 15 production tables and vector search capabilities

-- Enable foreign keys
PRAGMA foreign_keys = ON;

-- ==========================================
-- STATE & REGISTRY
-- ==========================================

-- 1. users
CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    hashed_password TEXT NOT NULL,
    trust_score REAL DEFAULT 1.0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. active_profile
CREATE TABLE IF NOT EXISTS active_profile (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    profile_type TEXT CHECK(profile_type IN ('Paranoid', 'Emergency', 'Standard', 'Social Diet', 'Custom')) DEFAULT 'Standard',
    custom_rules_json TEXT DEFAULT '{}',
    siren_triggers_enabled INTEGER CHECK(siren_triggers_enabled IN (0, 1)) DEFAULT 0,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 3. app_registry
CREATE TABLE IF NOT EXISTS app_registry (
    package_name TEXT PRIMARY KEY,
    app_name TEXT NOT NULL,
    signature_hash TEXT NOT NULL,
    is_trusted INTEGER CHECK(is_trusted IN (0, 1)) DEFAULT 0
);

-- 4. sandbox_state
CREATE TABLE IF NOT EXISTS sandbox_state (
    id TEXT PRIMARY KEY,
    wasm_engine_status TEXT CHECK(wasm_engine_status IN ('IDLE', 'RUNNING', 'CRASHED', 'BLOCKED')) DEFAULT 'IDLE',
    active_restrictions_count INTEGER DEFAULT 0,
    watchdog_latency_ms INTEGER DEFAULT 0,
    last_heartbeat TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ==========================================
-- DYNAMIC POLICIES
-- ==========================================

-- 5. network_policies
CREATE TABLE IF NOT EXISTS network_policies (
    policy_id TEXT PRIMARY KEY,
    package_name TEXT NOT NULL,
    domain_rule TEXT NOT NULL,
    port_rule TEXT DEFAULT '*',
    action TEXT CHECK(action IN ('ALLOW', 'DENY', 'MOCK')) DEFAULT 'ALLOW',
    FOREIGN KEY (package_name) REFERENCES app_registry(package_name) ON DELETE CASCADE
);

-- 6. permission_overrides
CREATE TABLE IF NOT EXISTS permission_overrides (
    id TEXT PRIMARY KEY,
    package_name TEXT NOT NULL,
    permission_type TEXT CHECK(permission_type IN ('CAMERA', 'MICROPHONE', 'CONTACTS', 'LOCATION', 'STORAGE')) NOT NULL,
    mocking_behavior TEXT CHECK(mocking_behavior IN ('SILENT', 'NOISE', 'RANDOM', 'STATIC')) DEFAULT 'STATIC',
    FOREIGN KEY (package_name) REFERENCES app_registry(package_name) ON DELETE CASCADE,
    UNIQUE(package_name, permission_type)
);

-- 7. emergency_whitelist
CREATE TABLE IF NOT EXISTS emergency_whitelist (
    rule_id TEXT PRIMARY KEY,
    target_pattern TEXT NOT NULL UNIQUE, -- e.g., 'gov.alert', '112', '911', 'co.il.redalert'
    description TEXT
);

-- 8. custom_user_rules
CREATE TABLE IF NOT EXISTS custom_user_rules (
    rule_id TEXT PRIMARY KEY,
    rule_name TEXT NOT NULL,
    regex_pattern TEXT NOT NULL,
    action TEXT CHECK(action IN ('ALLOW', 'DENY', 'MOCK')) DEFAULT 'DENY'
);

-- ==========================================
-- VIRTUALIZATION & IDENTITY MASKING
-- ==========================================

-- 9. generated_aliases
CREATE TABLE IF NOT EXISTS generated_aliases (
    alias_id TEXT PRIMARY KEY,
    real_identity_id TEXT NOT NULL,
    alias_type TEXT CHECK(alias_type IN ('EMAIL', 'PHONE', 'NAME')) NOT NULL,
    generated_value TEXT NOT NULL UNIQUE,
    FOREIGN KEY (real_identity_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 10. mock_contacts
CREATE TABLE IF NOT EXISTS mock_contacts (
    contact_id TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    phone_number TEXT NOT NULL,
    email TEXT,
    organization TEXT
);

-- 11. mock_locations
CREATE TABLE IF NOT EXISTS mock_locations (
    location_id TEXT PRIMARY KEY,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    offset_bearing REAL DEFAULT 0.0,
    offset_distance_meters REAL DEFAULT 0.0
);

-- ==========================================
-- OBSERVABILITY & MANAGEMENT
-- ==========================================

-- 12. traffic_logs
CREATE TABLE IF NOT EXISTS traffic_logs (
    log_id TEXT PRIMARY KEY,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    source_app TEXT NOT NULL,
    target_destination TEXT NOT NULL,
    security_narrative TEXT,
    action_taken TEXT CHECK(action_taken IN ('ALLOW', 'DENY', 'MOCK')) DEFAULT 'ALLOW',
    threat_level TEXT CHECK(threat_level IN ('INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL')) DEFAULT 'INFO'
);

-- 13. vps_sync_state
CREATE TABLE IF NOT EXISTS vps_sync_state (
    sync_id TEXT PRIMARY KEY,
    last_sync_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sync_checksum TEXT NOT NULL,
    pending_records_count INTEGER DEFAULT 0,
    sync_mode TEXT CHECK(sync_mode IN ('DELTA', 'FULL')) DEFAULT 'DELTA'
);

-- 14. vps_sync_queue
CREATE TABLE IF NOT EXISTS vps_sync_queue (
    queue_id TEXT PRIMARY KEY,
    payload_type TEXT NOT NULL,
    payload_data TEXT NOT NULL, -- JSON formatted data payload
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 15. system_health
CREATE TABLE IF NOT EXISTS system_health (
    id TEXT PRIMARY KEY,
    cpu_usage_pct REAL DEFAULT 0.0,
    ram_usage_mb REAL DEFAULT 0.0,
    network_rx_bytes INTEGER DEFAULT 0,
    network_tx_bytes INTEGER DEFAULT 0,
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ==========================================
-- SQLITE-VEC VIRTUAL TABLES FOR VECTOR SEARCH
-- ==========================================

-- SQLite-vec extension virtual table for searching semantic security narratives
-- This allows fast vector-based threat detection and query indexing
CREATE VIRTUAL TABLE IF NOT EXISTS vec_traffic_logs USING vec0(
    log_id TEXT PRIMARY KEY,
    security_narrative_embedding float[1536]
);

-- SQLite-vec extension virtual table for mock location vectors
-- Allowing coordinate clustering and geospatial lookup based on 2D embeddings
CREATE VIRTUAL TABLE IF NOT EXISTS vec_mock_locations USING vec0(
    location_id TEXT PRIMARY KEY,
    location_embedding float[2]
);
