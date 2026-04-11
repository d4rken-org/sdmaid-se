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

- Add trailing commas for multi-line parameter lists and collections
- When using `if` that is not single-line, always use brackets
- Use `FlowCombineExtensions` instead of nesting multiple combine statements
- Place `@Suppress` annotations as close as possible to the affected code (e.g., on a constructor or function, not the entire class)

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
- kotlinx.serialization for JSON serialization
- Room for database operations
- Coil for image loading

## Logging

Use `logTag()` to create tags and `log()` with lambda messages:

```kotlin
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*

companion object {
    private val TAG = logTag("AppCleaner")
    // Multi-part: logTag("Review", "Settings", "Gplay") → "SDMSE:Review:Settings:Gplay"
}

log(TAG) { "Processing $item" }           // DEBUG (default)
log(TAG, INFO) { "Scan complete" }         // INFO
log(TAG, WARN) { "Unexpected state" }      // WARN
log(TAG, ERROR) { "Failed: ${e.asLog()}" } // ERROR with stacktrace
```

## DataStore Settings

Two `createValue()` overloads exist.

**Primitive types** (Boolean/String/Int/Long/Float) — no extra argument:

```kotlin
val usePreviews = dataStore.createValue("core.ui.previews.enabled", true)
```

**Complex `@Serializable` types** — take a `json: Json` (kotlinx.serialization) parameter:

```kotlin
val themeMode = dataStore.createValue("core.ui.theme.mode", ThemeMode.SYSTEM, json)
```

Use `fallbackToDefault = true` only when stored JSON may be corrupt or from a legacy schema and should silently fall
back to the default instead of throwing:

```kotlin
val arbiterConfig = dataStore.createValue("arbiter.config", ArbiterConfig(), json, fallbackToDefault = true)
```

Access values with `.value()` (suspend) or `.flow` (reactive):

```kotlin
val current = settings.themeMode.value()     // suspend read
settings.themeMode.value(ThemeMode.DARK)     // suspend write
val reactive = settings.themeMode.flow       // Flow<ThemeMode>
```

