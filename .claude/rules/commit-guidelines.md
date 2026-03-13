# Commit Message Guidelines

## Format

```
<module>: <user-friendly title>

<detailed technical description>

<optional additional context>

<issue references>
```

## Module Prefixes

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

## Title Guidelines

- **Keep user-friendly**: Titles appear in changelogs, so make them understandable to end users
- **Be specific but concise**: Describe what the user will experience, not internal implementation details
- **Use action words**: "Fix", "Add", "Improve", "Update", "Remove"

## Examples

### Good Examples

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

### Bad Examples

```
Fix: Correct API level check in RealmeSpecs
```
*Too technical for changelog, should be "AppCleaner: Fix cache deletion on Realme devices"*

```
Handle AppOpsManager MODE_DEFAULT in security center permission check
```
*No module prefix, too technical for users*

## Technical Details

- **Body**: Include technical implementation details that developers need
- **Issue references**: Use "Closes #123", "Fixes #123", or "Resolves #123"
- **Breaking changes**: Mark with "BREAKING:" prefix if applicable
- **Co-authors**: Use "Co-authored-by:" for pair programming
