# VPN setup 0.3: consent, not protection

This increment changes the launcher to VpnSetupActivity and retains MainActivity as the internal profile/rule editor. PolicyStore, existing rules, profiles and database version are unchanged. Version code is 3 and version name is 0.3.0-vpn-setup. No new uses-permission declarations, network dependencies or native libraries are added.

VpnService.prepare is checked on resume and immediately before requesting the system dialog. The callback is not trusted as persistent authorization: prepare is checked again. Cancellation, exceptions, settings navigation and activity recreation are handled. No authorization result is persisted as a durable boolean. System approval can replace another prepared VPN and can interrupt an existing VPN; the UI warns before requesting it.

VpnPreparationService is declared with the system BIND_VPN_SERVICE guard and opts out of always-on. It is a discoverable consent component only. The app does not start it; if externally started, it immediately stops and returns START_NOT_STICKY. It does not create a TUN, routes, sockets or a foreground session. Granting consent therefore does not create a VPN connection or a protection guarantee. It must not be advertised as an enforcement engine or used with lockdown.

Manual acceptance on a test device, without another required VPN:
1. Install as an update only with a compatible signing key. Do not uninstall or clear data to hide an update failure.
2. Verify the new setup screen explicitly says VPN is inactive. Open the policy editor and confirm the saved profile and rules remain.
3. Request consent and cancel. Confirm cancellation, no Aegis VPN connection and unchanged connectivity.
4. Request again and approve. Confirm the app reports consent present but VPN inactive. No Aegis active-connection key or ongoing service notification should be created.
5. Rotate/reopen and verify current consent is checked again, not inferred from a saved flag.
6. Forget the app in system VPN settings, return and confirm approval is requested again.
7. Confirm no traffic-blocking claims appear in either screen.

CI must build assembleDebug, run the existing JVM suite and lintDebug. These do not validate the Android consent UI; the device checklist is required. The existing workflow starts on a PR targeting main, not a push to this feature branch. No CI success or APK is claimed by this commit alone.

Next delivery gate: choose and validate the packet-forwarding implementation, integrate a foreground-service lifecycle with the appropriate Android service type and notification, close resources on stop/revoke, and test allowed and denied traffic against an observable destination. Do not establish a default route without a working forwarder. Do not claim per-app/domain enforcement from manually entered package names or unverified DNS-to-IP associations. No server address, key, dependency version or native engine is invented in this increment.
