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

## Gplay APK outputs

`assembleGplayRelease` / `assembleGplayBeta` produce two APKs:

- `app/build/outputs/apk/gplay/<type>/...-UPLOAD.apk` — upload key, what the Play AAB is signed with. Not installable
  as an update over a Play install.
- `app/build/outputs/apk_gplay_signed/<type>/....apk` — Play app signing key, the installable one. Produced by the
  `signGplay<Type>Apk` finalizer, which no-ops when `~/.config/projects/eu.darken.sdmse/signing-gplay.properties`
  is absent (CI).

## Pitfalls

### Dependency Updates

When updating Kotlin or other core dependencies:

- Update versions in `buildSrc/src/main/java/Versions.kt`
- **IMPORTANT**: Also update hardcoded versions in `buildSrc/build.gradle.kts`
  - Kotlin Gradle Plugin version must match the version in Versions.kt
  - This is a common source of build errors if forgotten
- The KSP plugin version in root `build.gradle.kts` must be compatible with the Kotlin version
