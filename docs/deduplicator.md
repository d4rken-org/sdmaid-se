---
title: "Deduplicator"
lang: en
translation_key: deduplicator
permalink: /deduplicator
---
# Deduplicator

The Deduplicator finds duplicate files on your device: exact copies, and optionally images, videos or audio that look or sound alike. It groups them into sets and deletes all but one file per set.

Scanning is free. Deleting requires the upgrade.

## Detection methods

You can enable each method under `Settings > Deduplicator`.

### Content checksum

On by default. Finds files with exactly the same content, byte for byte, regardless of their names. This is the safe method: every file in such a set is a true copy.

### Perceptual hash

Off by default. Finds images that look alike, e.g. the same photo saved in a different format or size, or with small edits. Images are compared in grayscale, so images that only differ in color can match.

### Media fingerprint

Off by default. Finds videos and audio files that sound alike, and silent videos that look alike. Only files of roughly the same length are compared.

Similar files are not identical. Burst shots, edited versions or resized copies can end up in the same set. **Check sets found by perceptual hash or media fingerprint before deleting them.** Each similar file shows how closely it matches.

## What gets scanned

By default the Deduplicator searches all accessible storage: internal storage, SD cards, USB storage, `Android/data` and `Android/media`. `Android/obb` is not included. You can limit the search under [Search locations](#search-locations).

Access to `Android/data` on Android 13 and newer requires [root](/setup#root-access) or [ADB access](/setup#shizuku).

Only files above the [minimum size](#minimum-size) are considered. By default only common file types are included, see [Skip uncommon file types](#skip-uncommon-file-types).

## Which file is kept

When deleting a set, SD Maid keeps one file and deletes the rest. Which one it keeps is decided by the deletion strategy under `Settings > Deduplicator > Deletion strategy`.

SD Maid checks each rule in order. The first rule that produces a clear winner decides. Drag rules to reorder them, tap a rule to change its mode.

| Rule | Default |
|---|---|
| Duplicate type | Keep exact matches over similar files |
| Preferred locations | Keep files in folders you choose. No folders are set by default. |
| Media indexing | Keep files that your gallery and other media apps know about |
| Storage location | Keep files on internal storage. SD cards and USB storage count as external. |
| Folder depth | Keep files in shallow folders |
| Modification date | Keep older files |
| File size | Keep larger files |

**Preferred locations** helps choose which file is kept within a group, together with the other rules. It doesn't choose between groups. In a set with both exact copies and similar files, Duplicate type decides which group is kept: by default, every file in the similar group is deleted, even in a preferred location. For example, if your camera photo and two identical, resized copies from a messenger end up in one set, the default keeps one messenger copy and deletes the camera original. Check such sets before deleting them, and [exclude](/exclusions) folders whose files must never be deleted.

There is no way to mark a single file as the one to keep. To keep a different file, change the deletion strategy, or select the files to delete yourself.

Changing the deletion strategy discards the current results. Scan again afterwards.

## Deleting

### Deleting a whole set

Deleting a set keeps one file and deletes **every other file in that set**. In a set with similar files, this includes files that are only similar to the kept one, not identical.

Switch the result list to the list layout to see a delete icon on each file that deleting the whole set removes. The default grid layout doesn't show these icons.

### Deleting individual files

Select files with a long-press and delete them. At least one file per set has to remain, unless you enable [Make "Delete all" possible](#make-delete-all-possible).

In the set details, the `Folders` view groups the files by folder. Deleting a folder there deletes the duplicates in that folder. If the folder holds every file of a set, the file that deleting the whole set would keep is left out. With [Make "Delete all" possible](#make-delete-all-possible) enabled, the confirmation lets you delete all of them.

### Deleting from the dashboard

Deleting from the Deduplicator's dashboard card deletes all but one file in every set, after a confirmation. Whether the dashboard's main button also runs the Deduplicator is set under [One-Tap](#one-tap).

## Exclusions

Paths excluded for the Deduplicator, or in general, are not scanned. Excluding a set from the results excludes every file in it, including the one that would have been kept. See [Exclusions](/exclusions).

The Deduplicator can't be run by the [Scheduler](/scheduler).

## One-Tap

`Settings > General > One-Tap Tools` decides whether the dashboard's main button, the `Scan + Delete` shortcut and the widget include the Deduplicator. It's off by default. The shortcut and the widget scan and delete without a confirmation, using your deletion strategy. On the dashboard, `One-Tap Mode` does the same, but if results are already waiting, Delete still asks first. Only include the Deduplicator with [Content checksum](#content-checksum) as the only detection method.

## Settings

### Search locations

The folders to search. Defaults to all accessible areas. Long-press to reset it.

### Deletion strategy

Which file in a set is kept, see [Which file is kept](#which-file-is-kept).

### Make "Delete all" possible

Shows options to delete every file in a set, so that no copy is left. Off by default.

### Minimum size

Skip files below this size. Default 512 KB.

### Skip uncommon file types

Only include common images, videos, audio files, documents and archives. On by default. The type is determined by the file extension.

### Detection method

Enable [Content checksum](#content-checksum), [Perceptual hash](#perceptual-hash) and [Media fingerprint](#media-fingerprint).
