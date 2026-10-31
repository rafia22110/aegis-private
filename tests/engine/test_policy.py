import importlib.util
import sqlite3
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("policy_reference", ROOT / "packages/engine/policy.py")
policy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(policy)


class PolicyTests(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.addCleanup(self.db.close)
        self.db.executescript((ROOT / "packages/storage/reference/public-schema.sql").read_text())

    def add(self, identifier, action, port=0):
        self.db.execute("INSERT INTO network_policies(policy_id, package_name, domain_rule, port_rule, action) VALUES (?, ?, ?, ?, ?)",
                        (identifier, "test.app", "example.test", port, action))

    def decide(self, **kwargs):
        args = {"package_name": "test.app", "destination": "example.test", "port": 443}
        args.update(kwargs)
        return policy.decide(self.db, **args)

    def test_unknown_route_denied(self):
        self.assertEqual(self.decide()["reason"], "no_matching_policy")

    def test_explicit_allow_and_normalization(self):
        self.add("allow", "ALLOW", 443)
        self.assertEqual(self.decide(destination="EXAMPLE.TEST.")["action"], "ALLOW")
        self.assertEqual(self.decide(port=80)["action"], "DENY")
        self.assertEqual(self.decide(package_name="other.app")["action"], "DENY")

    def test_deny_wins_regardless_of_insert_order(self):
        self.add("allow", "ALLOW")
        self.add("deny", "DENY", 443)
        self.assertEqual(self.decide()["reason"], "policy_denied")

    def test_mock_never_claims_sensor_virtualization(self):
        self.add("mock", "MOCK")
        self.assertEqual(self.decide(), {"action": "DENY", "reason": "unsupported_mock"})

    def test_invalid_input_denied(self):
        for changes in ({"port": True}, {"port": 0}, {"destination": "x' OR 1=1--"}, {"package_name": ""}, {"destination": None}):
            self.assertEqual(self.decide(**changes)["reason"], "invalid_input")

    def test_storage_error_denied(self):
        self.db.execute("DROP TABLE network_policies")
        self.assertEqual(self.decide(), {"action": "DENY", "reason": "storage_error"})


if __name__ == "__main__":
    unittest.main()
