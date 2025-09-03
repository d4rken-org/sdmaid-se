# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## About SD Maid SE

SD Maid SE (AKA SD Maid 2) is an Android file management tool that specializes in maintenance and system cleaning.
Its core purpose is freeing up space and removing unwanted data.

SD Maid SE provides several cleaning and maintenance tools:

- **AppCleaner**: Deleting expendable files, e.g. caches and junk data
- **CorpseFinder**: Removing data that belongs to apps that are no longer installed
- **SystemCleaner**: User configurable filters for random files and system cleanup
- **Deduplicator**: Find and remove duplicate data
- **Analyzer**: Storage overview and analysis
- **AppControl**: Controlling/disabling apps and components
- **Scheduler**: Scheduling cleaning actions

## Development Commands

### Building the Project

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

### Testing

```bash
# Run unit tests
./gradlew test

# Run unit tests for specific flavor
./gradlew testFossDebugUnitTest
```

### Code Quality

```bash
# Run lint checks
./gradlew lint

# Run lint and auto-fix issues where possible
./gradlew lintFix

# Run lint checks for specific variant (used in CI)
./gradlew lintVitalFossRelease
```

### Debugging & Installation

```bash
# Install debug APK on connected device
adb install app/build/outputs/apk/foss/debug/app-foss-debug.apk

# Check logs for SD Maid SE
adb logcat | grep -i sdmaid

# Clear app data for testing
adb shell pm clear eu.darken.sdmse
```

### Fastlane Deployment

```bash
# Deploy beta version
fastlane android beta

# Deploy production version
fastlane android production
```

## Commit Message Guidelines

### Format
```
<module>: <user-friendly title>

<detailed technical description>

<optional additional context>

<issue references>
```

### Module Prefixes
Use these prefixes to categorize commits by SD Maid SE's cleaning tools:
- **AppCleaner**: App cache and junk cleaning functionality
- **CorpseFinder**: Finding and removing data from uninstalled apps  
- **SystemCleaner**: System-wide file cleaning with configurable filters
- **Deduplicator**: Duplicate file detection and removal
- **Analyzer**: Storage analysis and overview tools
- **AppControl**: App management and control features
- **Scheduler**: Task scheduling and automation
- **General**: Cross-cutting concerns, architecture, build system
- **Fix**: Bug fixes that don't fit a specific module

### Title Guidelines
- **Keep user-friendly**: Titles appear in changelogs, so make them understandable to end users
- **Be specific but concise**: Describe what the user will experience, not internal implementation details
- **Use action words**: "Fix", "Add", "Improve", "Update", "Remove"

### Examples

#### ✅ Good Examples
```
AppCleaner: Fix automation issues on HyperOS/MIUI devices

When checking if the Security Center app has PACKAGE_USAGE_STATS permission,
MODE_DEFAULT was incorrectly treated as "permission denied"...

Closes #1827
```

```
CorpseFinder: Improve detection of leftover app data

Enhanced the detection algorithm to better identify remnant files...

Fixes #1234
```

#### ❌ Bad Examples
```
Fix: Correct API level check in RealmeSpecs
```
*Too technical for changelog, should be "AppCleaner: Fix cache deletion on Realme devices"*

```
Handle AppOpsManager MODE_DEFAULT in security center permission check
```
*No module prefix, too technical for users*

### Technical Details
- **Body**: Include technical implementation details that developers need
- **Issue references**: Use "Closes #123", "Fixes #123", or "Resolves #123"
- **Breaking changes**: Mark with "BREAKING:" prefix if applicable
- **Co-authors**: Use "Co-authored-by:" for pair programming

## Architecture Overview

### Build Flavors

- **FOSS**: Open source version without Google Play dependencies.
- **GPLAY**: Google Play version with additional features.

### Module Structure

#### Core Application

- `app`: Main application module with entry point, flavor-specific implementations, and setup flow.

#### Foundation Modules

- `app-common`: Core shared utilities, base architecture components, custom ViewModel hierarchy, theming system.
- `app-common-test`: Testing utilities, helpers, and base test classes for all modules.

#### Platform Integration Modules

- `app-common-io`: File I/O operations, abstract path system (APath), gateway pattern for file access methods.
- `app-common-root`: Root access functionality and root-based file operations.
- `app-common-adb`: Android Debug Bridge integration via Shizuku API.
- `app-common-shell`: Shell operations and reactive command execution with FlowShell.
- `app-common-pkgs`: Package management utilities and package event handling.

#### Cleaning Tool Modules

SD Maid SE's cleaning tools are implemented within the main app module under `eu.darken.sdmse`:

- `appcleaner`: App cache and junk cleaning functionality
- `corpsefinder`: Finding and removing data from uninstalled apps
- `systemcleaner`: System-wide file cleaning with configurable filters
- `deduplicator`: Duplicate file detection and removal
- `analyzer`: Storage analysis and overview tools
- `appcontrol`: App management and control features
- `scheduler`: Task scheduling and automation

## Coding Standards

- Package by feature, not by layer.
- All user facing strings should be extract to `values/strings.xml` and translated for all other languages too.
- Prefer adding to existing files unless creating new logical components.
- Write tests for web APIs and serialized data.
- No UI tests required.
- Use FOSS debug flavor for local testing.
- Don't add code comments for obvious code.
- Write minimalistic and concise code (omit comments).
- Prefer flow based solutions.
- Prefer reactive programming.
- When using `if` that is not single-line, always use brackets.
- Always add trailing commas.
- Follow the tool-based architecture where each cleaning tool (AppCleaner, CorpseFinder, etc.) has core logic, tasks,
  and UI components.
- Use the established error handling patterns with `ErrorEventHandler`.
- Follow the existing navigation patterns using Jetpack Navigation.
- Use ViewBinding for layout inflation and view access.
- Follow Fragment-based navigation patterns.

## Agent instructions

- Reminder: Our core principle is to maintain focused contexts for both yourself (the orchestrator/main agent) and each
  sub-agent. Therefore, please use the Task tool to delegate suitable tasks to sub-agents to improve task efficiency and
  optimize token usage.
- Be critical.
- Challenge suggestions.

## Development Guidelines

### General

- Single Activity architecture with Fragment-based navigation.
- Reactive programming with Kotlin Flow and StateFlow.
- Centralized error handling with `ErrorEventHandler`.
- DataStore-based settings with kotlinx serialization.
- XML layouts with ViewBinding for UI.
- Hilt for dependency injection.
- Kotlin Coroutines & Flow for async operations.
- Moshi for JSON serialization.
- Coil for image loading.
- Room for database operations.
- Use `FlowCombineExtensions` instead of nesting multiple combine statements.

### Dependency Updates

When updating Kotlin or other core dependencies:

- Update versions in `buildSrc/src/main/java/Versions.kt`
- **IMPORTANT**: Also update hardcoded versions in `buildSrc/build.gradle.kts`
    - Kotlin Gradle Plugin version must match the version in Versions.kt
    - This is a common source of build errors if forgotten
- The KSP plugin version in root `build.gradle.kts` must be compatible with the Kotlin version

#### Dependency Injection

- Hilt/Dagger throughout the application.
- `@AndroidEntryPoint` for Activities/Fragments.
- `@HiltViewModel` for ViewModels.
- Modular DI setup across different modules.

### User Interface

- XML layouts with ViewBinding for UI components.
- Material 3 theming and design system.
- Edge-to-edge display support.
- Traditional Android UI patterns with RecyclerView, Fragments, and Activities.
- Use Material Design icons and follow Material 3 design guidelines.

#### Localization

- All user-facing texts need to be extracted to a `strings.xml` resources file to be localizable.
- UI components should access strings using `getString(R.string.my_string)` or `context.getString(R.string.my_string)`.
- Backend classes (those in the `core`) packages and other non-UI classes should use `CAString` to provide localized
  strings.
    - `R.string.xxx.toCaString()`
    - `R.string.xxx.toCaString("Argument")`
    - `caString { getString(R.plurals.xxx, count, count) }`
- Localized strings with multiple arguments should use ordered placeholders (i.e. `%1$s is %2$d`).
- Use ellipsis characters (`…`) instead of 3 manual dots (`...`).
- Use the `strings.xml` file that belongs to respective feature module.
- General texts that are used through-out multiple modules should be placed in the `strings.xml` file of the
  `app-common` module.
- Before creating a new entry, check if `strings.xml` file in the `app-common` module already contains a general
  version.
- String IDs should be prefixed with their respective module name. Re-used strings should be prefixed with `general` or
  `common`.
- Where possible string IDs should not contain implementation details.
    - Postfix with `_action` instead of prefixing with `button_`.
    - Instead of `module_screen_button_open` it should be `module_screen_open_action`

#### MVVM with Custom ViewModel Hierarchy

- `ViewModel1` → `ViewModel2` → `ViewModel3` → `ViewModel4`.
- `ViewModel4` adds navigation capabilities.
- Uses Hilt for assisted injection.

### Business Logic

#### General

- Abstract path system (`APath`, `RawPath`).
    - `APath` offers path segment infos via `segments`. Use that instead of path splitting.
- Gateway pattern for different file access methods (normal, root, ADB/Shizuku).
- Support for root, ADB, and shell operations.

#### Cleaning Tools Architecture

- Each tool follows a consistent pattern: core logic, task definitions, scanner/detector, and UI components.
- Tools use `BaseTool` and implement `SDMTool` interface.
- Tasks extend appropriate base classes and use dependency injection.
- Forensics and filtering systems for intelligent file detection.
- Progress reporting and cancellation support for long-running operations.

#### Automation System

- SD Maid SE uses an accessibility service for automation features (AppCleaner automation)
- `AutomationManager` handles accessibility service lifecycle and permissions
- `AutomationService` extends AccessibilityService for UI automation
- Common automation errors: `AutomationNoConsentException`, `AutomationNotEnabledException`,
  `AutomationNotRunningException`
- Automation tasks are built using a stepper pattern for complex UI interactions
- Supports different automation specs per app and Android version

## Testing Guidelines

### Framework Selection

- **JUnit 5**: Use for normal unit tests (must extend `BaseTest`)
- **JUnit 4**: Use for Robolectric tests (Robolectric doesn't support JUnit 5)

### Base Test Classes

- **`BaseTest`**: Base class for all unit tests using JUnit 5
  - Location: `app-common-test/src/main/java/testhelpers/BaseTest.kt`
  - Provides custom logging, test cleanup, and `IO_TEST_BASEDIR` constant
- **`BaseTestInstrumentation`**: Base class for Android instrumented tests (JUnit 4)
- **`BaseUITest`**: Specialized base class for UI testing
- **`BaseCSITest`**: Abstract base class for forensics/CSI tests with extensive MockK setup

### Testing Patterns

#### Normal Unit Tests (JUnit 5)

```kotlin
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import testhelpers.BaseTest
import io.kotest.matchers.shouldBe

class ExampleTest : BaseTest() {
    @BeforeEach
    fun setup() {
        // Test setup
    }
    
    @Test
    fun `descriptive test name with backticks`() {
        // Arrange
        val input = "test"
        
        // Act
        val result = functionUnderTest(input)
        
        // Assert
        result shouldBe "expected"
    }
}
```

#### Robolectric Tests (JUnit 4)

```kotlin
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class AndroidDependentTest : BaseTest() {
    @Test
    fun `test requiring Android framework`() {
        // Test implementation using Android APIs
    }
}
```

### Testing Libraries

- **Assertions**: Use Kotest matchers (`io.kotest.matchers.shouldBe`, `shouldThrow`, etc.)
- **Mocking**: Use MockK (`mockk<Class>()`, `every { ... } returns ...`)
- **Flow Testing**: Use provided `FlowTest` utilities from `app-common-test`
- **Coroutine Testing**: Use enhanced `runTest2` from test extensions
- **JSON Testing**: Use `toComparableJson()` for JSON comparisons
- **DataStore Testing**: Use `mockDataStoreValue()` helper

### Test Organization

- **Package by feature**: Tests mirror the main source structure
- **Extend base classes**: Always extend appropriate base test classes
- **Use test utilities**: Leverage shared test helpers from `app-common-test`
- **Descriptive names**: Use backtick syntax for readable test names

### Common Test Utilities

```kotlin
// Flow testing
flow.test("testTag", scope).apply {
    await { values, latest -> condition }
    assertNoErrors()
    cancelAndJoin()
}

// Enhanced coroutine testing
runTest2(expectedError = IllegalArgumentException::class) {
    // Test code that should throw
}

// MockK with cleanup (handled by BaseTest)
@MockK lateinit var dependency: SomeDependency
```

## Important File Locations

### Database & Schemas

- `app/schemas/`: Room database schema files for migrations
- Database implementations in each tool's `core` package

### Localization & Resources

- `app/src/main/res/values/strings.xml`: Base English strings
- `app/src/main/res/values-*/strings.xml`: Translated strings for other languages
- `app/src/foss/res/values-*/strings.xml`: FOSS flavor specific strings
- `app/src/gplay/res/values-*/strings.xml`: Google Play flavor specific strings

### Test Data & Tooling

- `tooling/testdata-generator/`: Tools for generating test data
- `tooling/translation/`: Translation automation tools

### CI/CD & Build

- `.github/workflows/code-checks.yml`: GitHub Actions for code quality checks
- `gradle.properties`: Project-wide Gradle configuration
- `buildSrc/`: Custom Gradle build logic and dependency management

## CI/CD Pipeline

The project uses GitHub Actions for continuous integration:

### Code Quality Checks

- **Lint Vital**: Runs `lintVitalFossRelease` and `lintVitalGplayRelease` to catch critical issues
- **Build Apps**: Builds both FOSS and Google Play flavors in debug and release variants
- **Tests**: Runs unit tests across all modules

### Build Matrix

- **Flavors**: FOSS, Google Play
- **Variants**: Beta, Release
- **Modules**: All app and library modules

### Quality Gates

- All lint-vital checks must pass
- All unit tests must pass
- Build must succeed for all flavor/variant combinations

## Development & Debugging Tips

### Debug Builds

- Debug builds include additional logging and debug tools
- Debug recorder available for capturing automation sessions

### Common Development Patterns

- Use FOSS debug flavor for local development and testing
- Debug builds have relaxed ProGuard rules for easier debugging
- Test data generators in `tooling/` for creating reproducible test scenarios
- Use the built-in debug tools rather than external debugging when possible

### Performance & Memory

- SD Maid SE includes memory monitoring tools for development
- Large file operations are chunked to prevent memory issues
- Progress reporting is essential for long-running operations
- Cancel-able operations should be implemented for good UX