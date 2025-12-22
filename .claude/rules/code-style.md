# Code Style

## General Principles

- Package by feature, not by layer
- Prefer adding to existing files unless creating new logical components
- Write minimalistic and concise code
- Don't add code comments for obvious code
- Prefer flow-based solutions
- Prefer reactive programming
- Cancel-able operations should be implemented for good UX

## Kotlin Conventions

- Always add trailing commas
- When using `if` that is not single-line, always use brackets
- Use `FlowCombineExtensions` instead of nesting multiple combine statements

## UI Patterns

- XML layouts with ViewBinding for UI components
- Material 3 theming and design system
- Edge-to-edge display support
- Use Material Design icons and follow Material 3 design guidelines
- Single Activity architecture with Fragment-based navigation
- Follow Fragment-based navigation patterns using Jetpack Navigation

## Error Handling

- Use the established error handling patterns with `ErrorEventHandler`
- Centralized error handling approach

## Data & State

- Reactive programming with Kotlin Flow and StateFlow
- DataStore-based settings with kotlinx serialization
- Moshi for JSON serialization
- Room for database operations
- Coil for image loading

## Testing Requirements

- Write tests for web APIs and serialized data
- No UI tests required
- Use FOSS debug flavor for local testing
