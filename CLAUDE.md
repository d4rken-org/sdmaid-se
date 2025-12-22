# CLAUDE.md

This file provides guidance to AI coding assistants when working with code in this repository.

## About SD Maid SE

SD Maid SE (AKA SD Maid 2) is an Android file management tool that specializes in maintenance and system cleaning. Its core purpose is freeing up space and removing unwanted data.

### Cleaning Tools

- **AppCleaner**: Deleting expendable files, e.g. caches and junk data
- **CorpseFinder**: Removing data that belongs to apps that are no longer installed
- **SystemCleaner**: User configurable filters for random files and system cleanup
- **Deduplicator**: Find and remove duplicate data
- **Analyzer**: Storage overview and analysis
- **AppControl**: Controlling/disabling apps and components
- **Scheduler**: Scheduling cleaning actions

## Build Flavors

- **FOSS**: Open source version without Google Play dependencies
- **GPLAY**: Google Play version with additional features

## Quick Commands

```bash
# Build
./gradlew assembleFossDebug

# Test
./gradlew test

# Lint
./gradlew lintVitalFossRelease
```

## Important File Locations

### Database & Schemas
- `app/schemas/`: Room database schema files for migrations

### Localization
- `app/src/main/res/values/strings.xml`: Base English strings
- `app/src/main/res/values-*/strings.xml`: Translated strings

### Build Configuration
- `buildSrc/src/main/java/Versions.kt`: Dependency versions
- `buildSrc/build.gradle.kts`: Build plugin versions (keep in sync!)
- `.github/workflows/code-checks.yml`: CI configuration

### Test Data & Tooling
- `tooling/testdata-generator/`: Test data generation tools
- `tooling/translation/`: Translation automation tools

## CI/CD Pipeline

The project uses GitHub Actions:
- **Lint Vital**: Runs `lintVitalFossRelease` and `lintVitalGplayRelease`
- **Build Apps**: Builds both FOSS and Google Play flavors
- **Tests**: Runs unit tests across all modules

All lint-vital checks, unit tests, and builds must pass.

## Development Tips

- Use FOSS debug flavor for local development
- Debug builds include additional logging and debug tools
- Large file operations are chunked to prevent memory issues
- Progress reporting is essential for long-running operations

## Detailed Guidelines

See `.claude/rules/` for detailed guidelines on:
- `code-style.md`: Kotlin conventions, UI patterns
- `testing.md`: Test frameworks, base classes, patterns
- `localization.md`: String extraction, CAString usage
- `architecture.md`: Module structure, patterns, DI
- `commit-guidelines.md`: Commit message format
- `build-commands.md`: Full build command reference
- `automation.md`: Accessibility service patterns
- `agent-instructions.md`: Agent delegation principles
