# Architecture

## Module Structure

### Core Application
- `app`: Main application module with entry point, flavor-specific implementations, and setup flow

### Foundation Modules
- `app-common`: Core shared utilities, base architecture components, custom ViewModel hierarchy, theming system
- `app-common-test`: Testing utilities, helpers, and base test classes for all modules

### Platform Integration Modules
- `app-common-io`: File I/O operations, abstract path system (APath), gateway pattern for file access methods
- `app-common-root`: Root access functionality and root-based file operations
- `app-common-adb`: Android Debug Bridge integration via Shizuku API
- `app-common-shell`: Shell operations and reactive command execution with FlowShell
- `app-common-pkgs`: Package management utilities and package event handling

### Cleaning Tool Packages

SD Maid SE's cleaning tools are implemented within the main app module under `eu.darken.sdmse`:
- `appcleaner`: App cache and junk cleaning functionality
- `corpsefinder`: Finding and removing data from uninstalled apps
- `systemcleaner`: System-wide file cleaning with configurable filters
- `deduplicator`: Duplicate file detection and removal
- `analyzer`: Storage analysis and overview tools
- `appcontrol`: App management and control features
- `scheduler`: Task scheduling and automation

## Cleaning Tools Architecture

- Each tool follows a consistent pattern: core logic, task definitions, scanner/detector, and UI components
- Tools use `BaseTool` and implement `SDMTool` interface
- Tasks extend appropriate base classes and use dependency injection
- Forensics and filtering systems for intelligent file detection
- Progress reporting and cancellation support for long-running operations

## Path System

- Abstract path system using `APath` and `RawPath`
- Gateway pattern for different file access methods (normal, root, ADB/Shizuku)
- Support for root, ADB, and shell operations

## MVVM with Custom ViewModel Hierarchy

- `ViewModel1` → `ViewModel2` → `ViewModel3` → `ViewModel4`
- `ViewModel4` adds navigation capabilities
- Uses Hilt for assisted injection

## Dependency Injection

- Hilt/Dagger throughout the application
- `@AndroidEntryPoint` for Activities/Fragments
- `@HiltViewModel` for ViewModels
- Modular DI setup across different modules

## Pitfalls

- Use `APath.segments` for path segment access — do NOT manually split path strings
