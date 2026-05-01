# Architecture

## Module Structure

Modules follow two naming conventions: `app-common-*` for shared infrastructure and `app-tool-*` for the cleaning tools.
The authoritative list lives in `settings.gradle`.

### Core Application

- `app`: Main application module — entry point, flavor-specific implementations, setup flow wiring

### Foundation Modules

- `app-common`: Core shared utilities, logging, DataStore helpers, theming, common base classes
- `app-common-test`: Testing utilities, helpers, and base test classes for JVM unit tests

### Platform Integration Modules

- `app-common-io`: File I/O, abstract path system (`APath`), gateway pattern for file access
- `app-common-root`: Root access and root-based file operations
- `app-common-adb`: ADB integration via Shizuku
- `app-common-shell`: Shell operations with reactive `FlowShell`
- `app-common-pkgs`: Package management and package event handling
- `app-common-data`: Room database, type converters, shared persisted data; hosts `BaseCSITest`

### UI & Feature Modules

- `app-common-ui`: Custom ViewModel hierarchy (`ViewModel1` → `ViewModel2` → `ViewModel3`), base fragments, navigation
- `app-common-coil`: Coil-based image loading and request pipeline
- `app-common-automation`: Accessibility-service automation engine
- `app-common-exclusion`: Shared exclusion rules across tools
- `app-common-picker`: File / path picker UI
- `app-common-setup`: Onboarding and setup flow
- `app-common-stats`: Statistics tracking

### Cleaning Tool Modules

Each cleaning tool is its own Gradle module under `app-tool-*`:

- `app-tool-corpsefinder`: Data from uninstalled apps
- `app-tool-systemcleaner`: System-wide configurable file filters
- `app-tool-appcleaner`: App cache / junk cleaning
- `app-tool-deduplicator`: Duplicate file detection and removal
- `app-tool-squeezer`: Storage squeezing / optimization
- `app-tool-analyzer`: Storage analysis and overview
- `app-tool-swiper`: Swipe-to-declutter old files
- `app-tool-appcontrol`: App management and control
- `app-tool-scheduler`: Task scheduling and automation

## Cleaning Tools Architecture

- Each tool lives in its own `app-tool-*` Gradle module following a consistent pattern: core logic, task definitions,
  scanner/detector, UI components
- Tools implement the `SDMTool` interface directly (the older `BaseTool` abstract class is no longer used — do not
  introduce new subclasses of it)
- Tasks extend appropriate base classes and use Hilt injection
- Forensics and filtering systems for intelligent file detection
- Progress reporting and cancellation support for long-running operations

## Path System

- Abstract path system using `APath` and `RawPath`
- Gateway pattern for different file access methods (normal, root, ADB/Shizuku)
- Support for root, ADB, and shell operations

## MVVM with Custom ViewModel Hierarchy

Layered ViewModel hierarchy (defined in `app-common-ui/.../common/uix/`):

- **`ViewModel1`** (extends `androidx.lifecycle.ViewModel`): Debug logging on init/clear, `tag` system for log identification
- **`ViewModel2`** (extends `ViewModel1`): Adds `DispatcherProvider`, `vmScope`, `launch()`, `Flow<T>.asStateFlow()` for coroutine management
- **`ViewModel3`** (extends `ViewModel2`): Adds error handling via `ErrorEventSource` with `SingleEventFlow<Throwable>`
- **`ViewModel4`** (extends `ViewModel3`): Adds navigation via `NavigationEventSource` with `navTo()` and `navUp()`

New ViewModels should extend **`ViewModel3`** (no navigation) or **`ViewModel4`** (with navigation). Uses `@HiltViewModel` with Hilt injection

## Dependency Injection

- Hilt/Dagger throughout the application
- `@AndroidEntryPoint` for Activities/Fragments
- `@HiltViewModel` for ViewModels
- Modular DI setup across different modules

## Pitfalls

- Use `APath.segments` for path segment access — do NOT manually split path strings
- `android.nonTransitiveRClass=true` is enabled — app's `R.attr` only contains attrs defined by the app module itself
  - Theme attrs from dependencies must use their declaring module's R class (e.g. `com.google.android.material.R.attr.colorSecondary`)
  - Widget attrs like `errorTextColor` are NOT theme attrs — using `MaterialColors.getColor()` with them crashes at runtime
