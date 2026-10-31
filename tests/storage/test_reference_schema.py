import hashlib
import sqlite3
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCHEMA = ROOT / "packages/storage/reference/public-schema.sql"
TABLES = {
    "users", "active_profile", "app_registry", "sandbox_state",
    "network_policies", "permission_overrides", "emergency_whitelist",
    "custom_user_rules", "generated_aliases", "mock_contacts", "mock_locations",
    "traffic_logs", "vps_sync_state", "vps_sync_queue", "system_health",
}


class ReferenceSchemaTests(unittest.TestCase):
    def setUp(self):
        self.raw = SCHEMA.read_bytes()
        self.sql = self.raw.decode("utf-8")
        self.db = sqlite3.connect(":memory:")
        self.addCleanup(self.db.close)
        self.db.execute("PRAGMA foreign_keys = ON")
        self.db.executescript(self.sql)

    def test_source_blob_matches(self):
        header = b"blob " + str(len(self.raw)).encode() + b"\0"
        self.assertEqual(hashlib.sha1(header + self.raw).hexdigest(),
                         "4b430df4b913df61cd51acc3423981f1b1b34731")

    def test_exact_table_set(self):
        rows = self.db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")
        self.assertEqual({row[0] for row in rows}, TABLES)

    def test_reapply_is_idempotent(self):
        self.db.executescript(self.sql)
        self.assertEqual(self.db.execute("SELECT count(*) FROM emergency_whitelist").fetchone()[0], 4)

    def test_foreign_keys_reject_unknown_user(self):
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("INSERT INTO active_profile(user_id) VALUES (?)", ("missing",))

    def test_invalid_policy_action_is_rejected(self):
        self.db.execute("INSERT INTO app_registry VALUES (?, ?, ?, ?)",
                        ("test.app", "Test", "test-signature", 0))
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("INSERT INTO network_policies(policy_id, package_name, domain_rule, action) VALUES (?, ?, ?, ?)",
                            ("p1", "test.app", "example.test", "INVALID"))

    def test_policy_and_log_round_trip(self):
        self.db.execute("INSERT INTO app_registry VALUES (?, ?, ?, ?)",
                        ("test.app", "Test", "test-signature", 0))
        self.db.execute("INSERT INTO network_policies(policy_id, package_name, domain_rule, action) VALUES (?, ?, ?, ?)",
                        ("p1", "test.app", "example.test", "DENY"))
        self.assertEqual(self.db.execute("SELECT action FROM network_policies WHERE policy_id = ?", ("p1",)).fetchone()[0], "DENY")
        self.db.execute("INSERT INTO traffic_logs(source_app, target_destination, security_narrative, action_taken) VALUES (?, ?, ?, ?)",
                        ("test.app", "example.test", "TEST FIXTURE ONLY", "DENY"))
        self.assertEqual(self.db.execute("SELECT action_taken FROM traffic_logs").fetchone()[0], "DENY")

    def test_seed_values_are_preserved_not_validated_routes(self):
        rows = self.db.execute("SELECT target_pattern FROM emergency_whitelist")
        self.assertEqual({row[0] for row in rows}, {"gov.alert", "co.il.redalert", "112", "911"})

    def test_integrity(self):
        self.assertEqual(self.db.execute("PRAGMA integrity_check").fetchone()[0], "ok")
        self.assertEqual(self.db.execute("PRAGMA foreign_key_check").fetchall(), [])


if __name__ == "__main__":
    unittest.main()
