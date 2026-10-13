---
title: "Storage Analyzer"
lang: en
translation_key: storageanalyzer
permalink: /storageanalyzer
---
# Storage Analyzer

The storage analyzer answers "What takes up so much space on my storage?"

It may look like a file browser, but it is not a file browser. Everything is sorted by size, so you can follow the largest items down to what is actually filling your storage.

## Storages

The first screen shows each storage of your device:

* **Primary storage**: your device's built-in storage, including apps and system data.
* **Secondary storage**: usually a removable SD card.
* **Portable storage**: e.g. a flash drive connected via USB.

Each card shows free and used space, and how much the storage changed over the last 7 days, see [Storage Trend](#storage-trend).

## Categories

Opening a storage scans it and splits the used space into categories:

* **Apps**: installed apps with their code, data, caches and media.
* **User files**: photos, videos, downloads and any other data that doesn't belong to a specific app.
* **System data**: the operating system and everything that couldn't be assigned elsewhere.
* **Other users**: apps and files of other users or work profiles on this device.

System data and Other users only exist for the primary storage.

### Apps

Apps are sorted by size and can be searched by name. System apps can be listed here too, including their data. For system apps without an installed update, the built-in app code counts as System data. Opening an app shows where its space goes:

* **App code**: the app itself. Uninstalling or archiving the app frees this space.
* **App data**: data, settings and caches.
* **App media**: media files of this app, e.g. received WhatsApp photos.
* **Extra app files**: data the app placed outside of its own folders, e.g. photos or downloads in a top-level folder.

`Show settings` opens the app's system settings page.

### Why the numbers don't match Android's settings

The analyzer's categories won't exactly match the breakdown in Android's settings.

**System data** is what remains of the used space after Apps, User files and Other users are counted. Anything SD Maid can't see or assign ends up there: protected system areas, storage of other users that couldn't be measured, filesystem overhead, and the difference between the advertised and the actual size of the storage.

Without the [App inventory](/setup#app-inventory) and [Usage statistics](/setup#usage-statistics) permissions, apps can't be measured. Their space then shows up as System data.

## Access

What the analyzer can see depends on the access SD Maid has:

* **Without root**, app code and private app data are shown as totals only and can't be browsed into. On Android 13 and newer, `Android/data` also needs root or ADB access.
* **With [root](/setup#root-access) or [ADB access](/setup#shizuku)**, you can browse System data. Root also allows browsing app code and private app data, and fully measuring other users.
* **Without the App inventory permission**, files can't be matched to the apps that own them. User files are then shown read-only: you can see sizes, but deleting, excluding, creating filters and Swiper sessions are disabled.

Items SD Maid can't access show `No direct access available` when tapped.

Folders with more than 100,000 items are only totalled and can't be browsed.

## Actions

Tap a file to open it in another app. Long-press items to select them, then:

* **Delete**: deletes the selected items after a confirmation. Free to use.
* **Exclude**: creates an [exclusion](/exclusions) for the selected paths. These exclusions apply to all tools. The analyzer itself doesn't use exclusions, as it needs to see everything to calculate correct totals.
* **Filter**: creates a [SystemCleaner custom filter](/systemcleaner#custom-filters) for the selected items. Requires the upgrade.
* **Create Swiper session**: starts a [Swiper](/swiper) session with the selected items.

System data and other users' content can only be browsed. Deleting there is not possible.

## Storage Trend

SD Maid records the free space of your storages every few hours and after each cleanup. `Storage Trend` shows this as a chart, with markers for how much each cleanup freed.

* The storage cards show how much each storage changed in the last 7 days.
* The dashboard shows the combined change of all storages, or instead an estimate of the days left when storage is filling up.
* How long snapshots are kept is set under `Settings > History > Storage history`. Default 90 days, up to 365.

The Storage Trend chart requires the upgrade. The 7-day change on the storage cards and the dashboard, and the days-left estimate, are free.

## Settings

### Low storage threshold

When free space drops below this, SD Maid treats the storage as running out. Default `Automatic`: 5% of the storage size, at most 2 GB.

### Low space warning

Requires the upgrade. Warns when free space on your primary storage reaches the low storage threshold. With at least 5 days of consistent history, it can also warn earlier: when storage is on track to reach the threshold within 14 days and free space is already at most 20% of the storage, capped at 25 GB. Off by default.
