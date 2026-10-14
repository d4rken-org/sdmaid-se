---
title: "Exclusions"
lang: en
translation_key: exclusions
permalink: /exclusions
---
# Exclusions
{: #exclusions }

SD Maid 2/SE's exclusion functionality allows users to define specific folders or files that the app should ignore during system scans and maintenance tasks. This is useful if there is data you want to keep unaffected by the app's processes. By setting exclusions, SD Maid 2/SE will skip these directories or files during tasks like corpse finder, app cleaner, and other system management features.

The easiest way to add an exclusion is directly from the results. You can either use the "exclude" button on individual items or long press/multi-select entries, then choose the "exclude" option. Additionally, exclusions can be manually added through the app settings by specifying paths or wildcard patterns for more control.

Exclusions can be exported and imported from the menu. Exclusion import is only supported on similar versions of SD Maid. If there are too many updates between the import and export versions, an error may occur. In that case, you can try to import on an earlier version, then export the exclusions again.

<img src="/assets/images/exclusions/overview-1.webp" width="150">
<img src="/assets/images/exclusions/overview-2.webp" width="150">
<img src="/assets/images/exclusions/overview-3.webp" width="150">
<img src="/assets/images/exclusions/overview-4.webp" width="150">

## Types
{: #types }

SD Maid supports 3 different types of exclusions:

### App exclusions
{: #app-exclusions }

App exclusions are based on the package name of each app. These can be created via the AppControl tool. Just pick an app from the list and use the related `Exclude` action.

### Path exclusions
{: #path-exclusions }

Path exclusions can be created by selecting (long-pressing) paths in the StorageAnalyzer tool.

### Segment exclusions 
{: #segment-exclusions }
Segment exclusions are a type of path exclusion that doesn't require an exact match. A segment is a part of a path, such as a folder. The path `/sdcard/DCIM/Camera` consists of 3 segments. You could exclude all your camera pictures for both your internal storage (`/storage/emulated/0/DCIM/Camera`) and external storage (`/sdcard/DCIM/Camera`) by creating a segment exclusion for `DCIM/Camera`.

If you tick the option `Allow partial matches`, you could create an exclusion for `DCIM/Cam` to match both the folder `Cam` and the folder `Camera`.

## Tools
{: #tools }

Each tool of SD Maid has a different view of data. Not all exclusions affect all tools equally.

### CorpseFinder
{: #corpsefinder }

The CorpseFinder tool knows what an "app" is and what files belong to an app. It respects all exclusion types. Creating an app exclusion means any files the CorpseFinder finds that it thinks belong to an excluded app will be excluded.

Supported exclusion types: `APP`, `PATH`, `SEGMENTS`

### SystemCleaner
{: #systemcleaner }

The SystemCleaner tool only works on path data. It does not know what an "app" is or which files belong to an app.

Supported exclusion types: `PATH`, `SEGMENTS`

### AppCleaner
{: #appcleaner }

The AppCleaner tool respects all exclusion types. You can use exclusions to speed up scans if there are apps with many files to scan.

Supported exclusion types: `APP`, `PATH`, `SEGMENTS`

### Deduplicator
{: #deduplicator }

The Deduplicator tool does not know what "apps" are and only works with file data.

Supported exclusion types: `PATH`, `SEGMENTS`

### Media Squeeze
{: #media-squeeze }

Media Squeeze only works with file data. Excluded files are not compressed.

Supported exclusion types: `PATH`, `SEGMENTS`

### Swiper
{: #swiper }

Swiper only works with file data. Excluded files are not included in new sessions. Long-pressing `Skip` while swiping excludes the current file.

Supported exclusion types: `PATH`, `SEGMENTS`

### StorageAnalyzer
{: #storageanalyzer }

The StorageAnalyzer does not support exclusions, as all content needs to be scanned to calculate the correct total sizes.
