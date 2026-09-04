# CLAUDE.md

This file provides guidance to AI coding assistants when working with code in this repository.

## About SD Maid SE

SD Maid SE (AKA SD Maid 2) is an Android file management tool that specializes in maintenance and system cleaning. Its core purpose is freeing up space and removing unwanted data.

### Cleaning Tools

- **AppCleaner**: Deleting expendable files, e.g. caches and junk data
- **CorpseFinder**: Removing data that belongs to apps that are no longer installed
- **SystemCleaner**: User configurable filters for random files and system cleanup
- **Deduplicator**: Find and remove duplicate data
- **Analyzer**: Storage overview and analysis
- **AppControl**: Controlling/disabling apps and components
- **Scheduler**: Scheduling cleaning actions
- **Swiper**: Swipe-to-declutter workflow targeting old files
- **Squeezer**: Storage optimization / squeezing

## Build Flavors

- **FOSS**: Open source version without Google Play dependencies
- **GPLAY**: Google Play version with additional features

## Development Tips

- Use FOSS debug flavor for local development

## Rules Loading

Some files in `.claude/rules/` are path-scoped (`paths:` frontmatter) and only load into context when matching files
are touched: testing, localization, automation, release, build system, DataStore settings. Two rules that must apply
*before* their files would ever be touched:

- All user-facing text must be extracted into the owning module's `strings.xml` — never hardcode UI strings
  (details: `.claude/rules/localization.md`).
- Before writing a test from scratch, read a similar existing test first — that also pulls the testing rules into
  context (`.claude/rules/testing.md`: JUnit 4/5 split, base classes, MockK/coroutine traps).
