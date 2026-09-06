---
paths:
  - "app-common-automation/**"
  - "**/automation/**"
  - "**/*Automation*.kt"
---

# Automation System

SD Maid SE uses an accessibility service for automation features (AppCleaner automation).

## Core Components

- `AutomationManager`: Handles accessibility service lifecycle and permissions
- `AutomationService`: Extends AccessibilityService for UI automation

## Common Automation Errors

- `AutomationNoConsentException`: User hasn't consented to automation
- `AutomationNotEnabledException`: Accessibility service not enabled
- `AutomationNotRunningException`: Service enabled but not running

## Implementation Patterns

- Automation tasks are built using a stepper pattern for complex UI interactions
- Supports different automation specs per app and Android version
- Debug recorder available for capturing automation sessions

## AppCleaner: where automation actually runs

The scan path contains no automation at all. `AppCleaner.performScan` never touches the accessibility
path. ACS work happens in `performProcessing`, gated on `AppCleanerProcessingTask.includeInaccessible`,
which builds the `ClearCacheTask` via `InaccessibleDeleter`. `AutomationCompatibilityException`
("Automation compatibility") has a single throw site, in `ClearCacheModule`, raised once
`FAILURE_LIMIT` targets have failed with an unusable-automation error and nothing has succeeded.

Consequences:

- A debug log recorded while only scanning contains no automation activity. Reproducing an automation
  error requires running the actual clean/delete step.
- With the accessibility service off, the same path raises "Service isn't enabled" instead, so a report
  naming the compatibility error implies the service was on.

## Debugging: the service sees a different tree

A `uiautomator dump` is not evidence about what `AutomationService` sees. It shows nodes the app's own
service cannot reach: clear-cache buttons that are marked `NAF="true"`, or that carry a
`content-desc` and are clickable in the dump, while `findClearCacheCandidate` still returns null. The
DPAD fallback exists because the service gets a different tree.

To learn what the service sees, capture the app's own ACS-DEBUG dump with debug recording enabled
during a run. Use uiautomator only for questions about platform behaviour, such as where DPAD focus
lands.
