---
title: "System Cleaner"
lang: en
translation_key: systemcleaner
permalink: /systemcleaner
---
# System Cleaner

The SystemCleaner tool is basically a general purpose search engine. It will crawl all accessible storage and match found files and folders against a set of filters.

In contrast to other tools like the CorpseFinder or the AppCleaner, it doesn't know about installed apps. It purely works on file paths and attributes. This is why only path and segment exclusions can be applied to the SystemCleaner.

Any found file is passed through the enabled filters, and will be attributed to the first filter that is matched.

## Settings

### General filters

General filters search **across all accessible storage areas** for certain file types that can appear anywhere on the device.

| Filter | Description |
|--------|-------------|
| **Empty folders** | Empty folders from all over the device. Nested folders may require multiple scan passes. |
| **Screenshots** | Screenshots older than a configurable age from multiple apps and locations. |
| **Trashed files** | Files in recycle bins from various apps and locations that are marked for deletion but not yet permanently removed. |
| **Superfluous APKs** | Setup files (APKs) that are older or equal to app versions that are already installed. |
| **LOST.DIR folders** | Temporary files created when transferring data between Android devices. |
| **Linux files** | Files and folders created when storage has been connected to a Linux computer. |
| **Mac files** | Files and folders created when browsing storage with Apple's Finder app (e.g., `.DS_Store`). |
| **Windows files** | Data that the Windows file explorer can create on connected storage (e.g., `Thumbs.db`). |
| **Thumbnail images** | Cached previews for images and videos (e.g., `.thumbnails` folders). |
| **Temporary system files** | System related temporary files, usually short-lived caches for active operations. |
| **Analytics files** | Data related to analytics and bug tracking. |
| **Ad files** | Files that have been created for or by advertisements. |
| **System log files** | System and system service log files (e.g., `*.log`) in various locations. |

### Specific filters

Specific filters target **fixed, known paths** on the device where certain types of data are stored.

| Filter | Description |
|--------|-------------|
| **System crash residue** | If native processes crash (e.g., games), then "tombstone" files are created here. |
| **Usage stats** | Files related to application usage statistics. |
| **ANR errors** | Traces from "Application Not Responding" events. |
| **Local temporary files** | System related temporary files (e.g., from Android Studio). |
| **System log files (datalogger)** | System service specific log files. |
| **Download cache** | System cache folder used for various things (e.g., OTA updates). |
| **Log drop box** | An area for the system to drop log files into. |
| **Installer cache** | Cached data related to installed apps that is used by the system's package management. |
| **Recent tasks** | Data related to the tasks history in the system's task switcher. |

### Custom filters

In addition to general filters that are provided by the app, you can create custom filters with your own criteria. Custom filters can be created from **Settings > SystemCleaner > Filter-Manager**.

> **Warning**: Custom filters can be risky. Always check your scan results before deleting!

#### Available Criteria

| Criterion | Description |
|-----------|-------------|
| **Label** | A name for your filter |
| **Data Areas** | Storage locations to scan (leave empty for all accessible areas) |
| **File Types** | Target files, folders, or symbolic links (leave empty for all) |
| **Path Criteria** | Rules for matching file paths |
| **Path Exclusions** | Paths to exclude from matching |
| **Name Criteria** | Rules for matching filenames |
| **Minimum Size** | Only match files larger than this |
| **Maximum Size** | Only match files smaller than this |
| **Minimum Age** | Only match files older than this |
| **Maximum Age** | Only match files newer than this |
| **Path Regex** | Advanced: Regular expressions for path matching |

#### Path Criteria Modes

| Mode | Description | Example |
|------|-------------|---------|
| **Starts with** | Path begins with the keyword | `Downloads/temp` matches `/sdcard/Downloads/temp/file.txt` |
| **Contains** | Path includes the keyword | `cache` matches `/sdcard/app/cache/data.bin` |
| **Ends with** | Path ends with the keyword | `backup/old` matches `/sdcard/backup/old` |
| **Equals** | Path exactly matches the keyword | `Download/temp` matches only that exact path |

#### Name Criteria Modes

| Mode | Description | Example |
|------|-------------|---------|
| **Starts with** | Filename begins with keyword | `temp_` matches `temp_file.txt` |
| **Contains** | Filename includes keyword | `.bak` matches `document.bak.old` |
| **Ends with** | Filename ends with keyword | `.tmp` matches `cache.tmp` |
| **Equals** | Filename exactly matches | `thumbs.db` matches only `thumbs.db` |

All matching is **case-insensitive** by default.

#### Data Areas

Common areas you can target:

| Area | Description |
|------|-------------|
| **SDCARD** | Primary storage (internal or SD card) |
| **PUBLIC_MEDIA** | Media folders (DCIM, Pictures, Music, etc.) |
| **PUBLIC_DATA** | Shared app data |
| **PRIVATE_DATA** | App-private storage (requires root) |
| **DOWNLOAD_CACHE** | System download cache (requires root) |
| **PORTABLE** | Removable storage (USB/microSD) |

#### Tips

- **Performance**: Define only necessary criteria. Too many regex patterns slow down scanning.
- **Multiple criteria**: All specified criteria must match (AND logic).
- **Long press**: Long press on entries for additional options.
- **Testing**: Use the scan preview before enabling deletion.
- **Import/Export**: Filters can be exported and shared with other users.

#### Deprecated Options

The `rootOnly` option from SD Maid 1 is **no longer available**. Use **Data Areas** to control which storage locations are scanned instead.
