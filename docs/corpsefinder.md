---
title: "Corpse Finder"
lang: en
translation_key: corpsefinder
permalink: /corpsefinder
---
# Corpse Finder

The `CorpseFinder` tool finds files belonging to apps that are no longer installed.


## Risklevel

By default all risklevel options are off. Only enable them if you know what you are doing and before deleting anything, check the results first.
Enabling a risklevel option increases the chance that files are deleted that you didn't want to delete.

Risklevels mostly apply to whitelist locations, such as the root of your public storage (`/storage/emulated/0`). Such locations have no common naming pattern and SD Maid can only determine ownership through a manually maintained database.


### Include desirable remnants

Desirable remanents are files with known owners that are not installed, but are internally flagged by SD Maid as `KEEPER`. Usually these are user created files, e.g. photos you took.

### Include common names

Some apps just use very common name. There are probably hundreds of note taking apps that create a folder `/sdcard/Notes` to save data in. SD Maid may know one of those app and it is not installed on your phone, but SD Maid can't be sure that there isn't still another app on your phone using that folder and SD Maid doesn't know that this app also uses that folder. 

## Filter

Each filter is responsible for a specific data area. By enabling a filter, the CorpseFinder will search that additional area.

### Sdcard

* Usually where the user places files
* Public storage (built-in as well as removable), excluding specific sub-directories in `Android/data`.
* Files here have no naming scheme.

Examples:

```bash
/storage/emulated/0/SDMaid
/storage/DEAD-BEEF/SDMaid
/sdcard/SDMaid
```

### Public media

* Stores public app media, e.g. media received via WhatsApp. Folders are named after the packagenames of apps.
* Sub directory of public storage with special meaning to the OS. 

Examples:

```bash
/storage/emulated/0/Android/media/eu.darken.sdmse
/storage/DEAD-BEEF/Android/media/eu.darken.sdmse
/sdcard/Android/media/eu.darken.sdmse
```

### Public app data

* Extra files of apps.
* Folders are named after the packagenames of apps.
* Sub directory of public storage with special meaning to the OS.

Examples:

```bash
/storage/emulated/0/Android/data/eu.darken.sdmse
/storage/DEAD-BEEF/Android/data/eu.darken.sdmse
/sdcard/Android/data/eu.darken.sdmse
```

### OBB resources

* Usually extra resource files for games.
* Folders & files are named after the packagenames of apps.
* Sub directory of public storage with special meaning to the OS.

Examples:

```bash
/storage/emulated/0/Android/obb/eu.darken.sdmse
/storage/DEAD-BEEF/Android/obb/eu.darken.sdmse
/sdcard/Android/obb/eu.darken.sdmse
```

### Private app data

* Folders & files are named after the packagenames of apps.
* Sub directory of public storage with special meaning to the OS.

Examples:

```bash
/data/data/eu.darken.sdmse
```

### Dalvik cache

* Stores optimized versions of app code for faster execution.
* Located in the system partition, often under specific architecture directories.
* Files are named using the app's package name.

Examples:

```bash
/data/dalvik-cache/arm64/data@app@eu.darken.sdmse@base.apk@classes.dex
/data/dalvik-cache/arm/data@app@eu.darken.sdmse@base.apk@classes.dex
```

### App libraries

* Contains native libraries used by apps, such as `.so` files.
* Typically located in the app's data directory under a `lib` folder.
* Files are named as per the architecture and library requirements.

Examples:

```bash
/data/app/eu.darken.sdmse-1/lib/arm64/libexample.so
/data/app/eu.darken.sdmse-2/lib/arm/libexample.so
```

### App sources

* Refers to the APK files that serve as the source code for apps.
* Stored in specific directories for system, user, or vendor apps.
* Files are named after the app's package name and version.

Examples:

```bash
/data/app/eu.darken.sdmse-1/base.apk
/system/app/ExampleApp/ExampleApp.apk
```

### Private app sources

* Similar to app sources but stored in protected directories for enhanced security.
* Only accessible by the system or the respective app.
* Files are named after the app's package name.

Examples:

```bash
/data/app-private/eu.darken.sdmse/base.apk
```
