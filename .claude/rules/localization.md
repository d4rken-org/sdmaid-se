---
paths:
  - "**/res/values*/strings.xml"
  - "tooling/translation/**"
  - "crowdin.yml"
---

# Localization Guidelines

## String Extraction

- All user-facing texts need to be extracted to a `strings.xml` resources file to be localizable
- Use the `strings.xml` file that belongs to the respective feature module
- General texts used throughout multiple modules should be placed in the `strings.xml` file of the `app-common` module
- Before creating a new entry, check if `strings.xml` file in the `app-common` module already contains a general version

## Accessing Strings

### UI Components
```kotlin
getString(R.string.my_string)
context.getString(R.string.my_string)
```

### Backend/Core Classes

Backend classes (those in the `core` packages) and other non-UI classes should use `CaString`:

```kotlin
R.string.xxx.toCaString()
R.string.xxx.toCaString("Argument")
caString { getString(R.plurals.xxx, count, count) }
```

## String Format Conventions

- Localized strings with multiple arguments should use ordered placeholders: `%1$s is %2$d`
- Use ellipsis characters (`…`) instead of 3 manual dots (`...`)

## String ID Naming

- String IDs should be prefixed with their respective module name
- Re-used strings should be prefixed with `general` or `common`
- Where possible, string IDs should not contain implementation details:
  - Postfix with `_action` instead of prefixing with `button_`
  - Instead of `module_screen_button_open` it should be `module_screen_open_action`

## Copy Style

Rules for the English source text of user-facing strings:

- No em dashes. Use a period or a comma.
- A settings description never states the default ("Off by default: …"). The switch next to it already
  shows the state.
- Descriptions are two or three plain sentences: what the option covers, what happens to those files.
  No justification clauses ("to keep memory use in check"), no "Enable to …" instructions.
- Captions and hints must not repeat what the button label next to them already says.
- Never over-promise on the one-time purchase. No "lifetime" or "forever" phrasing; the approved
  wording is "One payment, no renewals."
- Whimsy is welcome where it fits, e.g. the mascot mood variants reacting to app state.
- Section headers get a translatability check before they are proposed. Puns rarely survive; prefer
  wording that maps 1:1 into other languages.
- Modelling a new string on an existing one is not a defence. Older strings predate these rules; new
  strings are held to them regardless.

## Translator Context on Crowdin

String context, character limits and file context are managed on Crowdin through the android-translation
plugin's `crowdin-annotate` skill. XML comments in `values/strings.xml` no longer reach translators once a
string's context has been written on Crowdin; change it there.
