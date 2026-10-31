# ADR 0001: Unified Aegis repository

Status: proposed implementation baseline.

## Scope
Consolidate selected components from rafia22110/aegis-public, rafia22110/aegis-private and rafia22110/Aegis-MirrorP into one maintained repository. This decision does not claim that migration, Android builds or network protection have been completed or verified.

## Target layout
- apps/android: complete Gradle application, local policy enforcement and device events.
- apps/web: installation, QR onboarding and authorized status views.
- services/control-plane: optional account management and policy synchronization.
- contracts: versioned schemas for status, policies and events.
- infra: deployment configuration without credentials.
- tests: integration and security acceptance tests.

## Boundaries
The Android engine owns enforcement. The web interface must not infer protection from a button click, registration or a successful API response. Server connectivity is not proof of device protection. Network enforcement must not depend on a remote request for each packet.

Production credentials, live databases, personal data and APK signing keys must stay outside Git, including private repositories. Do not change visibility, archive source repositories, merge to main or deploy as part of this baseline.

## Migration strategy
Preserve source repositories. Inventory source files and their revisions before copying. Review the additional feature branch in aegis-private before declaring the inventory complete. Reuse useful public UI components and MirrorP test infrastructure selectively; do not copy simulated protection claims into production. Select one web rendering approach and one authoritative schema per data store. Keep device-local database migrations distinct from control-plane collection migrations.

## Capability gates
The current VPN sources are not accepted as a verified forwarding engine. Sensor virtualization, wearable integration and alias delivery remain unverified until independently implemented and tested. Demo events must be visibly labeled and never represented as device activity.

## Acceptance criteria
1. CI builds the Android application from a clean checkout.
2. Device tests prove allowed traffic reaches its destination and denied traffic is blocked.
3. IPv4, IPv6, DNS, restart and crash behavior have explicit test results.
4. The UI displays a fresh authenticated device status, or explicitly displays unknown/stale status.
5. Failure behavior and Android always-on/lockdown prerequisites are documented and tested.
6. Multi-device state is isolated; no global wizard record represents all users.
7. No merge to main or production deployment occurs without a separate review and approval.
