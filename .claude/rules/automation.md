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

## Development Tips

- Debug builds include additional logging for automation debugging
- Debug builds have relaxed ProGuard rules for easier debugging
- Test automation on multiple device manufacturers (Samsung, Xiaomi, etc.)
- Be aware of manufacturer-specific UI variations
- Use built-in debug tools rather than external debugging when possible
- SD Maid SE includes memory monitoring tools for development
