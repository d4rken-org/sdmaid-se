# Architecture

## Module Structure

Modules follow two naming conventions: `app-common-*` for shared infrastructure and `app-tool-*` for the cleaning
tools (one module per tool). The authoritative list lives in `settings.gradle`.

Non-obvious module facts:

- `app`: entry point, flavor-specific implementations, setup flow wiring
- `app-common-test`: JVM test utilities and base test classes (a `src/main` library consumed by other modules' tests)
- `app-common-data`: Room database, type converters; hosts `BaseCSITest`
- `app-common-adb`: ADB integration goes through Shizuku
- `app-common-shell`: shell operations via reactive `FlowShell`

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
- **`ViewModel2`** (extends `ViewModel1`): Adds `DispatcherProvider`, `vmScope`, `launch()`, `Flow<T>.launchInViewModel()` for coroutine management
- **`ViewModel4`** (extends `ViewModel2`): Base for all Compose screens. Adds error handling (`errorEvents`, a `SingleEventFlow<Throwable>`), navigation via `NavigationEventSource` (`navTo()` / `navUp()`), and `safeStateIn()` for collector-safe render state.

> There is no `ViewModel3` — what older docs split across `ViewModel3` (errors) and `ViewModel4` (navigation) is now merged into a single `ViewModel4`.

New ViewModels extend **`ViewModel4`** (use it whether or not the screen navigates). Uses `@HiltViewModel` with Hilt injection

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
