-- State and Registry Tables
CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    username TEXT NOT NULL,
    hashed_password TEXT NOT NULL,
    trust_score REAL DEFAULT 1.0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS active_profile (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL,
    profile_type TEXT CHECK(profile_type IN ('Paranoid', 'Emergency', 'Standard', 'Social Diet', 'Custom')) DEFAULT 'Standard',
    custom_rules_json TEXT,
    siren_triggers_enabled INTEGER DEFAULT 1,
    FOREIGN KEY(user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS app_registry (
    package_name TEXT PRIMARY KEY,
    app_name TEXT NOT NULL,
    signature_hash TEXT NOT NULL,
    is_trusted INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sandbox_state (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    wasm_engine_status TEXT NOT NULL,
    active_restrictions_count INTEGER DEFAULT 0,
    watchdog_latency_ms REAL DEFAULT 0.0,
    last_heartbeat DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Dynamic Policy Tables
CREATE TABLE IF NOT EXISTS network_policies (
    policy_id TEXT PRIMARY KEY,
    package_name TEXT NOT NULL,
    domain_rule TEXT NOT NULL,
    port_rule INTEGER DEFAULT 0,
    action TEXT CHECK(action IN ('ALLOW', 'DENY', 'MOCK')) DEFAULT 'ALLOW',
    FOREIGN KEY(package_name) REFERENCES app_registry(package_name)
);

CREATE TABLE IF NOT EXISTS permission_overrides (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    package_name TEXT NOT NULL,
    permission_type TEXT NOT NULL,
    mocking_behavior TEXT DEFAULT 'SILENT',
    FOREIGN KEY(package_name) REFERENCES app_registry(package_name)
);

CREATE TABLE IF NOT EXISTS emergency_whitelist (
    rule_id TEXT PRIMARY KEY,
    target_pattern TEXT UNIQUE NOT NULL,
    description TEXT
);

CREATE TABLE IF NOT EXISTS custom_user_rules (
    rule_id TEXT PRIMARY KEY,
    rule_name TEXT NOT NULL,
    regex_pattern TEXT NOT NULL,
    action TEXT CHECK(action IN ('ALLOW', 'DENY', 'MOCK')) DEFAULT 'ALLOW'
);

-- Virtualization & Identity Masking
CREATE TABLE IF NOT EXISTS generated_aliases (
    alias_id TEXT PRIMARY KEY,
    real_identity_id TEXT NOT NULL,
    alias_type TEXT CHECK(alias_type IN ('EMAIL', 'PHONE', 'NAME')) NOT NULL,
    generated_value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS mock_contacts (
    contact_id TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    phone_number TEXT NOT NULL,
    email TEXT,
    organization TEXT
);

CREATE TABLE IF NOT EXISTS mock_locations (
    location_id TEXT PRIMARY KEY,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    offset_bearing REAL DEFAULT 0.0,
    offset_distance_meters REAL DEFAULT 0.0
);

-- Observability & Management
CREATE TABLE IF NOT EXISTS traffic_logs (
    log_id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    source_app TEXT NOT NULL,
    target_destination TEXT NOT NULL,
    security_narrative TEXT NOT NULL,
    action_taken TEXT NOT NULL,
    threat_level TEXT DEFAULT 'LOW'
);

CREATE TABLE IF NOT EXISTS vps_sync_state (
    sync_id TEXT PRIMARY KEY,
    last_sync_time DATETIME,
    sync_checksum TEXT,
    pending_records_count INTEGER DEFAULT 0,
    sync_mode TEXT DEFAULT 'PASSIVE'
);

CREATE TABLE IF NOT EXISTS vps_sync_queue (
    queue_id INTEGER PRIMARY KEY AUTOINCREMENT,
    payload_type TEXT NOT NULL,
    payload_data TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS system_health (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    cpu_usage_pct REAL,
    ram_usage_mb REAL,
    network_rx_bytes INTEGER,
    network_tx_bytes INTEGER
);

-- Pre-seed Emergency Whitelist Routes
INSERT OR IGNORE INTO emergency_whitelist (rule_id, target_pattern, description) VALUES
('em_1', 'gov.alert', 'National Emergency Alert System'),
('em_2', 'co.il.redalert', 'Rocket and Missile Defense Siren System'),
('em_3', '112', 'Universal Emergency Services'),
('em_4', '911', 'Emergency Services Bypass');
