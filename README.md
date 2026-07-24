# Aegis Mirror - Digital Sovereignty Engine (Private Repository)

Aegis Mirror is a digital sovereignty and network virtualization framework designed to secure personal telemetry, mask identities, and prevent background device tracking at the kernel/network level.

This is the **Private Engine** repository, containing the full backend logic, VPS deployment orchestration, local database schema, Android VPN service implementation, and the single-page admin dashboard.

## Key Features

1. **Cognitive Data Layer (SQLite-vec)**: 15 relational tables managing users, active policies, sandboxing profiles, location/contact virtualization, and sync queues.
2. **Deterministic Routing Hooks (PocketBase)**: Real-time route verification, instant emergency whitelisting (zero-latency bypass), and packet log translation into narrative alerts.
3. **Fail-Closed VPN Subsystem (Kotlin)**: Custom VpnService loopback routing with absolute packet drop on daemon crashes to protect against DNS leakages.
4. **Stitched User Interface (Tailwind + Alpine.js + HTMX)**: Responsive, accessible single-page interface with real-time status control, mock alias redirectors, and vigilance logs.

---

## Directory Structure

```
.
├── .github/workflows/deploy.yml   # Production CI/CD workflow
├── pb_hooks/main.pb.js            # PocketBase policy checking and form hooks
├── pb_public/                     # Statically built PWA interface files
│   ├── index.html                 # Unified single-page web app
│   ├── manifest.json              # PWA manifest configurations
│   ├── sw.js                      # Service worker caching files
│   ├── logo.png                   # Brand asset
│   └── medusa-core.png            # Holographic medallion visual asset
├── src/
│   ├── database/
│   │   └── schema.sql             # On-device SQLite schema
│   └── android/
│       ├── MirrorVpnService.kt    # Android Kotlin VPN service
│       └── AndroidManifest.xml    # Android VPN permissions configuration
└── vps/
    ├── docker-compose.yml         # PocketBase & Caddy network stack setup
    └── Caddyfile                  # Caddy reverse proxy configurations
```

---

## Deployment & Setup

### 1. Backend Orchestration (VPS)
Deploy PocketBase and Caddy proxy locally or to a VPS using Docker:
```bash
cd vps
docker-compose up -d --build
```
This maps local volumes (`pb_data/`, `pb_hooks/`, and `pb_public/`) into the alpine containers, making the hooks and frontend instantly active.

### 2. Frontend Deployment (Vercel)
The static web app is located in `pb_public/`. Run deployment command using Vercel CLI:
```bash
cd pb_public
vercel --prod
```

### 3. Database Schema Verification
Run local verification on the SQLite schema:
```bash
sqlite3 test_verify.db ".read src/database/schema.sql"
```

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
