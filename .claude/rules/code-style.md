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

- Jetpack Compose with Material 3 (`SdmSeTheme`)
- Edge-to-edge display support
- Single Activity architecture with Navigation3 (`NavDisplay`)
- Legacy Fragments still exist (unconverted screens) — new screens must be Compose

### Host/Page Pattern (mandatory for all Compose screens)

Every screen splits into two composables:

**`<Feature>ScreenHost`** — ViewModel wiring + side effects. The only place that touches `hiltViewModel()`, collects one-shot events, launches activity results, and starts intents. Must call `ErrorEventHandler(vm)` and `NavigationEventHandler(vm)`.

**`<Feature>Screen`** — Pure presentation. Accepts `Flow<State>` or simple parameters. Previewable with mock `flowOf()` data. Marked `internal`.

```kotlin
@Composable
fun MyScreenHost(vm: MyViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)
    MyScreen(stateSource = vm.state, onAction = vm::doThing)
}

@Composable
internal fun MyScreen(
    stateSource: Flow<MyViewModel.State> = flowOf(MyViewModel.State()),
    onAction: () -> Unit = {},
) { ... }
```

### Compose Conventions

- `modifier: Modifier = Modifier` must be the **first parameter** in `@Composable` functions
- All composables must have `@Preview2` previews wrapped in `PreviewWrapper`
- Preview functions: `private`, named `ComponentNamePreview()`, placed below the composable
- Extract composables to separate files when the file exceeds ~200 lines
- Reusable composables (e.g., `SdmMascot`) belong in `app-common-ui/.../compose/`
- In `when` expressions, omit braces for single-expression branches; a composable's trailing lambda does not require wrapper braces

### Shared Compose Components

- **`SdmMascot`** (`app-common-ui/.../compose/SdmMascot.kt`): Lottie-based animated mascot with seasonal hat overlays. Modes: `SdmMascotMode.Animated` (default looping), `SdmMascotMode.Party` (forces party hat)
- **Settings toolkit** (`app-common-ui/.../compose/settings/`): `SettingsPreferenceItem`, `SettingsSwitchItem`, `SettingsBaseItem`
- **`Preview2`/`PreviewWrapper`** (`app-common-ui/.../compose/preview/`): Multi-preview annotation (light+dark) and themed wrapper

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

