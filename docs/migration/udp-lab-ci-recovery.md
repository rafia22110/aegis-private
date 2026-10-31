# UDP lab CI recovery checkpoint

## Observed state on 2026-10-31

PR 7 remains open and draft. Its source head before this documentation commit is 76982880c9631ef0d9b5c5a3bddcf56da3f93de4. The Java core and UDP lab files are present; no new source deletion was observed. The latest Android check reported cancelled, not failure. The responsible actor and reason were not established. The verify check reported success. Earlier Android success must not be substituted for a fresh result after cancellation.

## Recovery scope

This commit changes documentation only. It does not restore the legacy production/VPS deploy.yml, alter application code, permissions or workflows, merge the PR, activate a VPN, or send a UDP probe. The normal pull_request synchronization event is expected to trigger the existing Android and unified verification workflows. This is a new commit-triggered validation, not an API rerun of the cancelled job. Vercel may also create a normal Git preview.

## Acceptance gates

Confirm checks belong to the new head and wait for Android assembleDebug, JVM tests, lintDebug and unified verification to complete successfully. Inspect actual errors if a job fails; cancellation alone is not a source-code diagnosis. Verify the resulting APK artifact exists before delivery. Install only with compatible signing and preserve app data. Device validation of VPN consent, socket protection, network binding, cancellation and the controlled UDP Echo exchange remains required; CI success is not proof of full-device protection. Do not enable TUN/default routing or merge based on an older green result.
