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

Backend classes (those in the `core` packages) and other non-UI classes should use `CAString`:

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

## Resource Locations

- `app/src/main/res/values/strings.xml`: Base English strings
- `app/src/main/res/values-*/strings.xml`: Translated strings for other languages
- `app/src/foss/res/values-*/strings.xml`: FOSS flavor specific strings
- `app/src/gplay/res/values-*/strings.xml`: Google Play flavor specific strings
