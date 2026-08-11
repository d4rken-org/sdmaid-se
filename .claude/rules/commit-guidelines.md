# Commit & Pull Request Guidelines

## Commit Message Format

```
<module>: <title>

<detailed technical description>

<optional additional context>

<issue references>
```

## Module Prefixes

Use these prefixes to categorize commits and PR titles:
- **AppCleaner**: App cache and junk cleaning functionality
- **CorpseFinder**: Finding and removing data from uninstalled apps
- **SystemCleaner**: System-wide file cleaning with configurable filters
- **Deduplicator**: Duplicate file detection and removal
- **Analyzer**: Storage analysis and overview tools
- **AppControl**: App management and control features
- **Scheduler**: Task scheduling and automation
- **General**: Cross-cutting concerns, architecture, build system
- **Fix**: Bug fixes that don't fit a specific module

## Commit Title Guidelines

Commit titles are for **developers** reading `git log`. They can be technical and reference internal names.

- **Be clear and descriptive**: Describe what was actually changed in the code
- **Use action words**: "Fix", "Add", "Improve", "Update", "Remove", "Refactor"
- **Technical references are fine**: Class names, method names, and implementation details are acceptable

### Commit Examples

```
Fix: Use vmScope instead of viewModelScope for error handling

Replace viewModelScope with vmScope in PickerViewModel and 5 other
ViewModels using DynamicStateFlow. The vmScope includes a
CoroutineExceptionHandler that routes errors to errorEvents instead
of crashing the app.
```

```
AppCleaner: Fix MODE_DEFAULT handling in RealmeSpecs

When checking if the Security Center app has PACKAGE_USAGE_STATS permission,
MODE_DEFAULT was incorrectly treated as "permission denied"...

Closes #1827
```

## Pull Request Titles

PR titles use the same module prefixes as commits. Title rules (ELI5, user-facing language) are enforced by the devtools
PR skill. The PR body format ("What changed" + "Technical Context", no Validation section) is defined in the global
instructions, not here.

## Pull Request Labels

Apply labels that match the change. Run `gh label list` to see what's available — do not invent new labels.

- **Component labels** (`c: AppCleaner`, `c: CorpseFinder`, `c: SystemCleaner`, `c: Deduplicator`, `c: StorageAnalyzer`,
  `c: AppControl`, `c: Scheduler`, `c: IO`, `c: PKGS`, `c: CSI`, `c: Exclusions`, `c: Setup`, `c: Stats`, `c: Debug`,
  `c: ClutterDB`): apply for every tool/module the PR touches, not just the one in the title prefix
- **Type labels**: `bug` for fixes, `enhancement` for new features/improvements, `Chore` for refactors/tests/cleanup,
  `documentation` for docs-only changes
- **Platform/scope labels**: `Root`, `ADB`, `SAF`, `Automation`, `FOSS`, `Google Play`, `Translation`, `Build process`,
  `General UI/UX`, `MOTD` — apply when the PR specifically targets that area
- **Device-specific labels**: `Device specific` plus the relevant ROM label (`ROM: OneUI`, `ROM: LOS`, `ROM: MIUI`,
  `ROM: OxygenOS`, `ROM: Android TV`) when fixing manufacturer/ROM-specific behavior
- **API level labels** (`api: 26 A8.0 (Oreo)` through `api: 35 A15 (Vanilla Ice Cream)`): apply when the change targets
  behavior specific to certain Android versions

Skip labels that don't fit. A PR with no matching labels is fine — better than wrong labels.

## Conventions

- **Issue references**: Use "Closes #123", "Fixes #123", or "Resolves #123"
- **Breaking changes**: Mark with "BREAKING:" prefix if applicable
- **Co-authors**: Use "Co-authored-by:" for pair programming
