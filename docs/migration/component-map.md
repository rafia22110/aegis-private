# Component migration map - batch 1

This is a component-level map of inspected paths, not a complete recursive audit or completed consolidation. Existing runtime paths and deployments remain unchanged. The reference import is not a production migration and is not loaded by any application.

## Source snapshots
- aegis-public: 2d268bbfca02e5924eaf806eade9299993437664.
- Aegis-MirrorP: 4919c944f8265aaba4a3c33864b3f66afebb49d9.
- aegis-private main baseline: 4f52ec461a8370e2b1284fb4a8d1a4313571fb29.
- aegis-private feature/production-ui-enhancements-12208211190608945933: 739fd2c7f69374451a4b34a1c796aa6152bab58b.

## Decisions
| Source component | Decision | Target or blocker |
| --- | --- | --- |
| aegis-public src/database/schema.sql | Copy unchanged as reference in this batch | packages/storage/reference/public-schema.sql; blob 4b430df4b913df61cd51acc3423981f1b1b34731 |
| MirrorP tests/schema.test.ts | Reuse schema-validation intent, not source code | New Python stdlib tests execute SQL instead of merely searching text |
| Private main src/database/schema.sql | Retain in place pending migration design | Blob 4541586c7a1493acfba6cfd370af023c1daf1f36 matches MirrorP; 16 tables including lora_nodes |
| Private feature db/schema.sql | Review before any migration | Compact table names differ from underscore names; vec0 virtual tables need an extension |
| Private feature server/perseus_proxy.py | Do not activate or copy as a production service yet | Uses generatedaliases and vpssyncqueue; requires authentication, identity ownership and schema review; alias generation is not verified mail delivery |
| Public pb_public/index.html | Preserve visual design; UI extraction pending | Remove inferred secure state and decorative QR behavior before production use; full-file extraction still required |
| Private feature src/ui/index.html and video assets | Inventory found; transfer deferred | Full UI and asset review not completed |
| Public/private main Android VPN | Replace forwarding implementation, do not activate | Identical baseline source; no verified packet forwarding |
| Private feature Android VPN | Selective review only | Forwards packets back to TUN; watchdog latency uses random values |
| MirrorP Android VPN | Do not import into a build target | Missing referenced classes and invalid numeric literal in inspected source |
| MirrorP src/api and api | Consolidation pending | Demo fallbacks, volatile state and duplicate handler layers need removal or explicit separation |
| Private vps and deployment workflows | Retain unchanged | No deployment or infrastructure activation in this batch |

## Storage limitations
The imported 15-table SQL file is a historical reference, not the final canonical schema. Its seed values are preserved for reproducibility; they are not validated emergency network destinations. Tests do not demonstrate emergency-call safety, sensor virtualization, encrypted storage or sqlite-vec support. Foreign keys are explicitly enabled by the test connection; production connections must configure them independently. No live database is read or changed.

## Verification
Run from repository root:

```sh
python3 -m unittest discover -s tests/storage -p 'test_*.py' -v
```

The eight tests cover source blob integrity, exact table set, reapplication, foreign-key rejection, action constraints, policy/log round trips, historical seeds and SQLite integrity. The prepared batch passed all eight in the assistant execution environment using SQLite 3.46.1. This is not a GitHub Actions result or an Android device test.

## Next batches
1. Extract complete web source and assets into apps/web without copying simulated protection claims.
2. Define canonical device storage and explicit migrations from both naming conventions.
3. Build one Android Gradle application with a real forwarding implementation and device acceptance tests.
4. Select and harden the optional control plane; connect authenticated fresh engine status to the UI.
