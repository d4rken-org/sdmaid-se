---
paths:
  - "buildSrc/**"
  - "**/*.gradle"
  - "**/*.gradle.kts"
  - "gradle/**"
  - "gradle.properties"
---

# Build System Notes

Flavors are `Foss` and `Gplay`. Local development uses `assembleFossDebug`, unit tests `testFossDebugUnitTest`,
CI lint `lintVitalFossRelease` / `lintVitalGplayRelease`.

## Pitfalls

### Dependency Updates

When updating Kotlin or other core dependencies:

- Update versions in `buildSrc/src/main/java/Versions.kt`
- **IMPORTANT**: Also update hardcoded versions in `buildSrc/build.gradle.kts`
  - Kotlin Gradle Plugin version must match the version in Versions.kt
  - This is a common source of build errors if forgotten
- The KSP plugin version in root `build.gradle.kts` must be compatible with the Kotlin version
