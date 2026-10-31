# Engine migration - batch 2

## Scope
New offline Python reference logic derived from the routing responsibility of `pb_hooks/main.pb.js` (blob `82d1eb3ba1d06a3cbf803295df2f7e6efd881fc8`). This is a reviewed rewrite, not a verbatim transfer. It is not wired into Android, PocketBase or the web interface and does not enforce device traffic.

## Implemented reference behavior
`packages/engine/policy.py` queries the imported underscore-named SQLite reference schema. The caller owns the connection and must provide a trusted native package identity, a canonical destination and the actual port; browser-supplied package names are not proof of identity. Stored domains must already be lowercase without a trailing dot. Matches are exact, with port 0 meaning all ports. DENY wins across matching policies. Unknown routes, invalid input, storage errors, invalid actions and unsupported MOCK requests return DENY. No packet forwarding, emergency bypass, sensor replacement or alias delivery is claimed.

Default-deny is a reference decision only, not an Android kill switch or a production-ready policy. Do not activate this on a user's phone until connectivity, emergency behavior, IPv6/DNS and crash handling are independently designed and tested. It does not support wildcard domains, CIDR, IPv6 literals or user/device policy isolation yet. Each caller must supply an appropriately isolated store.

## Mapped next components
| Component | Current finding | Next action |
| --- | --- | --- |
| Android VPN and manifest | src/android has two source files, no complete Gradle application | Create a buildable project before importing a service into production |
| Native traffic forwarding | Existing service loops packets back to TUN | Select and test a forwarding implementation; do not activate the old loop |
| Packet-to-app attribution | Policy needs a trustworthy package identity | Design native attribution and test limits |
| PocketBase hook | Database errors can fall back to ALLOW; empty journal shows synthetic protection claims | Replace with validated API contracts and real events |
| Storage | Underscore and compact naming conventions coexist | Define versioned migrations; do not point the old proxy at the reference schema |
| Status channel | JSON contract exists but is not connected | Add authentication, device isolation and freshness checks |
| Build/assets | Binary files exceed connector display limits | New CI builds from checkout, compares copied bytes and decodes real PNG files |

## Verification boundaries
Existing 13 tests were rerun locally against source text whose seven Git blob hashes matched commit `51e89bbdfd7db9365b12b3e4b570bf1f4c7dc586`. Six new offline policy tests passed locally. Real image hashes and sizes were read from GitHub metadata, but the binary files were not decoded locally. The proposed workflow runs all 19 unit tests and the real image/build check on the integration branch; its result is pending until execution. It does not build an APK, run a browser, deploy, use production secrets, modify main or push generated files.

## Commands
```sh
python3 -m unittest discover -s tests/engine -p 'test_*.py' -v
python3 -m pip install Pillow==11.3.0
python3 scripts/verify_web_build.py
```

The workflow is push-triggered only on integration/unified-aegis. The existing deployment workflow is unchanged and targets main/master. Workflow logs contain build/image results; no successful CI run is asserted by this document.
