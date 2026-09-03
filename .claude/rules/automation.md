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
