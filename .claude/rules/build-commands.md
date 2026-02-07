# Build Commands

## Building the Project

```bash
# Build debug version (FOSS flavor) - main app
./gradlew assembleFossDebug

# Build specific modules
./gradlew :app-common:assembleDebug
./gradlew :app-common-io:assembleDebug
./gradlew :app-common-root:assembleDebug
./gradlew :app-common-adb:assembleDebug
./gradlew :app-common-shell:assembleDebug
./gradlew :app-common-pkgs:assembleDebug

# Build release version
./gradlew bundleFossRelease

# Clean build
./gradlew clean
```

## Testing

```bash
# Run unit tests
./gradlew test

# Run unit tests for specific flavor
./gradlew testFossDebugUnitTest
```

## Code Quality

```bash
# Run lint checks
./gradlew lint

# Run lint and auto-fix issues where possible
./gradlew lintFix

# Run lint checks for specific variant (used in CI)
./gradlew lintVitalFossRelease
```

## Debugging & Installation

```bash
# Install debug APK on connected device
adb install app/build/outputs/apk/foss/debug/app-foss-debug.apk

# Check logs for SD Maid SE
adb logcat | grep -i sdmaid

# Clear app data for testing
adb shell pm clear eu.darken.sdmse
```

## Fastlane Deployment

```bash
# Deploy beta version
fastlane android beta

# Deploy production version
fastlane android production
```

## Context Management

When running gradle builds or tests, use the Task tool with a sub-agent to keep verbose output isolated from the main conversation context. The sub-agent should report back only:
- Success or failure
- Compilation errors with file paths and line numbers
- Warning counts

Run gradle directly in the main context only when the user explicitly requests full output.

## Pitfalls

### Dependency Updates

When updating Kotlin or other core dependencies:

- Update versions in `buildSrc/src/main/java/Versions.kt`
- **IMPORTANT**: Also update hardcoded versions in `buildSrc/build.gradle.kts`
  - Kotlin Gradle Plugin version must match the version in Versions.kt
  - This is a common source of build errors if forgotten
- The KSP plugin version in root `build.gradle.kts` must be compatible with the Kotlin version
