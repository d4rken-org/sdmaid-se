---
paths:
  - "**/*Settings.kt"
  - "**/datastore/**"
---

# DataStore Settings

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
