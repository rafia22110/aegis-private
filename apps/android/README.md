# Aegis Android build scaffold

Development-only native Android application. It displays UNKNOWN and never claims protection. It requests no permissions, starts no VPN and sends no network traffic. The legacy Kotlin service and Python policy reference are not imported into this application.

## Toolchain
- Android Gradle Plugin 8.9.0
- Gradle 8.11.1, JDK 17
- compileSdk/targetSdk 35, Build Tools 35.0.0, minSdk 26
- Java framework Activity; no AndroidX, Compose or Kotlin runtime dependency

Compatibility source: https://developer.android.com/build/releases/agp-8-9-0-release-notes
This is a development toolchain, not a claim of current Google Play submission compliance.

## Local build
Install the declared JDK, Gradle and Android SDK packages. Set ANDROID_HOME or create an untracked local.properties with sdk.dir. From repository root:

```sh
gradle -p apps/android --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Output: app/build/outputs/apk/debug/app-debug.apk inside this project. Debug signing uses an automatically generated development key, not a production signing key.

A Gradle wrapper JAR and wrapper scripts are not committed in this batch. Use the exact installed Gradle version above; do not assume ./gradlew exists. A maintainer with that Gradle installed can later generate a standard wrapper using `gradle -p apps/android wrapper --gradle-version 8.11.1 --distribution-type bin` and review its files separately.

## CI
The new Android workflow runs only on pushes to integration/unified-aegis, installs the declared toolchain, runs unit tests/lint, builds a debug APK and uploads it as a seven-day workflow artifact. No production secrets, release signing, deployment, emulator or device test is included. A committed workflow is not proof that a build succeeded: inspect the actual run result.

## Migration boundary
The existing src/android files remain untouched and are outside this project's Gradle source sets. Next steps are a reviewed native policy implementation, real traffic forwarding, trusted app attribution, VPN permission/lifecycle support and device tests. Do not enable a full-route tunnel until forwarding and failure behavior are verified.
