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
PR skill.

## Pull Request Description Format

### What changed

User-friendly explanation of what this PR does. Describe the problem that was fixed or the feature that was added from
the user's perspective. No internal class or method names.

For non-user-facing PRs (refactors, tests, CI, dependency bumps): write "No user-facing behavior change" followed by a
brief internal description.

### Technical Context

Explain what's hard to extract from the diff alone. Focus on:

- **Why** this approach was chosen (and alternatives considered/rejected)
- **Root cause** for bug fixes (the diff shows the fix, not what caused it)
- **Non-obvious side effects** or behavioral changes not apparent from reading the code
- **Review guidance** — what's tricky or deserves close attention

Keep it scannable with bullet points. Don't restate what's visible in the diff (file names, class renames, line-level
changes).

### Example

```markdown
## What changed

Fixed a crash that could happen when browsing files on devices without root or ADB access.

## Technical Context

- Root cause: `viewModelScope` lacks a CoroutineExceptionHandler, so unhandled exceptions in DynamicStateFlow crash the app instead of routing to errorEvents
- Chose `vmScope` (existing project convention) over adding a custom supervisor scope to stay consistent with other ViewModels
- Narrowing `catch(Exception)` to `catch(IOException)` changes behavior: `CancellationException` is no longer silently swallowed, which is correct but may surface previously-hidden cancellation bugs
```

## Conventions

- **Issue references**: Use "Closes #123", "Fixes #123", or "Resolves #123"
- **Breaking changes**: Mark with "BREAKING:" prefix if applicable
- **Co-authors**: Use "Co-authored-by:" for pair programming
