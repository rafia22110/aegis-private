# Aegis web migration preview

This is a static, non-protective migration preview. No device is connected. No API, phone registration, VPN activation, demo events or service worker is enabled. The engine-status contract is not integrated yet.

## Provenance
Adapted from the fully inspected private `pb_public/index.html` at commit `10a5e17084055372659188db09560bb01cabec0f`, blob `87a71956e35bbecbaeea1b10eb2ae299bb8a23b8`. This is not a complete migration of the larger public interface. The dark palette, shield ring, card layout and branding are retained; the original HTML, scripts and Tailwind dependency are not copied unchanged.

The build copies the existing repository images from `pb_public` after checking their Git blob hashes. Both hashes match the reviewed public snapshot: logo `0e350bedd459fe35f65f69b12310c4ad396f72df`, medusa `6b4c1e98afb5e39268a44b0580834bb311e1c072`. Binary assets remain in their original tracked location for now; the generated site contains local copies. Do not delete `pb_public` before moving these assets in a later batch.

## Build and preview
From repository root, with Python 3:

```sh
python3 apps/web/build.py
python3 -m http.server 8080 --bind 127.0.0.1 --directory apps/web/dist
```

Open the local server in a browser. Serve the generated directory, not the source directory. Relative asset and manifest URLs support a subdirectory deployment. No production routing or deployment configuration changes in this batch. Do not serve this on an origin already controlled by the legacy root service worker without explicitly removing that worker and its cache first.

## Tests
```sh
python3 -m unittest discover -s tests/web -p 'test_*.py' -v
```

Tests cover markup, disabled controls, local URLs, source hash checking and build behavior with fixture assets. Fixture builds do not verify actual PNG rendering. Browser/device testing and a real-repository asset build remain required. The manifest does not imply offline support or native VPN capability.

## Next
Migrate the remaining public screens and assets, implement an authenticated device-status channel with freshness checks, and only then enable engine-dependent controls. Do not relax the preview CSP to accept arbitrary scripts or endpoints.
