"""
Perseus Proxy — Private backend service.
Handles: key exchange, alias email generation, database sync receiving.
This file must NEVER be copied into the public repository.
All secrets are loaded from environment variables (.env), never hardcoded.
"""

import os
import sqlite3
import secrets
import string
from datetime import datetime
from flask import Flask, request, jsonify

app = Flask(__name__)

DB_PATH = os.environ.get("SQLITE_DB_PATH", "./db/aegis.db")
SIGNING_SECRET = os.environ.get("PROXY_SIGNING_SECRET")
SYNC_TOKEN = os.environ.get("SYNC_ENDPOINT_TOKEN")

if not SIGNING_SECRET or not SYNC_TOKEN:
    raise RuntimeError("Missing required secrets. Check your .env file.")


def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def generate_alias_email(user_id: str) -> str:
    suffix = "".join(secrets.choice(string.ascii_lowercase + string.digits) for _ in range(10))
    alias = f"{suffix}@relay.aegismirror.app"
    conn = get_db()
    conn.execute(
        "INSERT INTO generatedaliases (user_id, alias_email, created_at) VALUES (?, ?, ?)",
        (user_id, alias, datetime.utcnow().isoformat()),
    )
    conn.commit()
    conn.close()
    return alias


@app.route("/api/alias/generate", methods=["POST"])
def api_generate_alias():
    token = request.headers.get("X-Sync-Token")
    if token != SYNC_TOKEN:
        return jsonify({"error": "unauthorized"}), 401

    user_id = request.json.get("user_id")
    if not user_id:
        return jsonify({"error": "missing user_id"}), 400

    alias = generate_alias_email(user_id)
    return jsonify({"alias": alias})


@app.route("/api/sync/receive", methods=["POST"])
def api_sync_receive():
    token = request.headers.get("X-Sync-Token")
    if token != SYNC_TOKEN:
        return jsonify({"error": "unauthorized"}), 401

    payload = request.json
    conn = get_db()
    conn.execute(
        "INSERT INTO vpssyncqueue (user_id, payload, status) VALUES (?, ?, 'received')",
        (payload.get("user_id"), str(payload)),
    )
    conn.commit()
    conn.close()
    return jsonify({"status": "ok"})


@app.route("/api/health", methods=["GET"])
def api_health():
    return jsonify({"status": "healthy", "checked_at": datetime.utcnow().isoformat()})


if __name__ == "__main__":
    app.run(host="127.0.0.1", port=8080)
