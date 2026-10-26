"""Offline policy reference; not a VPN or an Android runtime component."""
import re
import sqlite3


def decide(connection, package_name, destination, port):
    if (not isinstance(package_name, str) or len(package_name) > 255
            or not re.fullmatch(r"[A-Za-z0-9_.]+", package_name)
            or not isinstance(destination, str) or len(destination) > 253
            or not re.fullmatch(r"[A-Za-z0-9.-]+", destination)
            or type(port) is not int or not 1 <= port <= 65535):
        return {"action": "DENY", "reason": "invalid_input"}
    try:
        rows = connection.execute(
            "SELECT action FROM network_policies "
            "WHERE package_name = ? AND domain_rule = ? AND port_rule IN (0, ?)",
            (package_name, destination.lower().rstrip("."), port),
        ).fetchall()
    except sqlite3.Error:
        return {"action": "DENY", "reason": "storage_error"}
    actions = {row[0] for row in rows}
    if not actions:
        return {"action": "DENY", "reason": "no_matching_policy"}
    if actions - {"ALLOW", "DENY", "MOCK"}:
        return {"action": "DENY", "reason": "invalid_policy"}
    if "DENY" in actions:
        return {"action": "DENY", "reason": "policy_denied"}
    if "MOCK" in actions:
        return {"action": "DENY", "reason": "unsupported_mock"}
    return {"action": "ALLOW", "reason": "policy_allowed"}
