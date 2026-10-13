---
title: "Swiper"
lang: en
translation_key: swiper
permalink: /swiper
---
# Swiper

Swiper helps you declutter specific folders faster than a file explorer. You go through files one at a time and swipe each one to keep, delete or skip it. Nothing is deleted until you review your choices and confirm.

## Starting a session

1. Open Swiper from the dashboard and tap `Select folders`. You can pick folders, single files, or both.
2. Optionally set a file type filter and a sort order on the session card.
3. Tap `Scan`. Folders are searched including all subfolders.
4. Tap `Start swiping`.

You can also create a session from the [StorageAnalyzer](/storageanalyzer): select items there and choose `Create Swiper session`.

Good places to start are your camera roll, the Downloads folder and screenshots.

### File types

`Only show these file types` limits the session to images, videos, audio, documents or archives. You can also enter your own extensions, e.g. `apk, apks`. If nothing is checked, all files are shown. Files are matched by their extension only.

### Sort order

Files are shown oldest first by default, based on their last modified date. You can also sort by newest first, name, or largest first.

The filter and sort order can't be changed after a scan found files. To change them, start a new session.

### Scanning a whole storage

If you pick the top level of a storage, e.g. all of your internal storage, every file on it ends up in the session, including photos and app media. SD Maid asks you to confirm before scanning. When you later delete files from such a session, you have to tick `I understand these files will be permanently deleted.` first.

## Swiping

| Gesture | Default action |
|---|---|
| Swipe right | Keep |
| Swipe left | Delete |
| Swipe up | Skip |
| Swipe down | Undo the last decision |

The buttons at the bottom do the same. With [Swap swipe directions](#swap-swipe-directions) enabled, left and right are reversed.

* **Skip** leaves a file undecided. Skipped files come back around after the rest.
* **Undo** steps back through your last decisions, up to 50.
* **Long-press Skip** to exclude a file from future Swiper scans. This creates a path [exclusion](/exclusions) and removes the file from the session.
* The buttons on the card open the file in another app or show a full-screen preview.
* Tap a thumbnail in the strip to jump to that file. Swiping it again replaces your earlier decision.

You can leave at any time. Your decisions are saved with the session and you can continue later. When SD Maid restarts, files it can no longer find are dropped from the session. Until then, the session may still list files that were deleted or moved by something else.

## Reviewing and deleting

Tap `Review` to see all decisions. When no undecided files are left, the review opens by itself.

* Change decisions per file, or long-press to select several files at once.
* Tap a file to jump back to it.
* `Delete` shows how many files and how much space are affected, and in which folders, before anything happens.

Deleting is permanent. Files are not moved to a trash folder and SD Maid can't restore them.

After deleting, kept and deleted files leave the session. Undecided files stay for later. Once nothing is left, the session is removed. If some files could not be deleted, they stay in the session and you can try again.

Removing a session only discards your decisions. It doesn't touch any files.

### Things to know

* **Keep only applies to the current session.** If you start a new session on the same folder, kept files show up again. To hide a file permanently, exclude it (long-press Skip).
* **A session doesn't pick up new files.** Files added to the folder after the scan don't appear. Start a new session to include them.
* **Archives include APK files.**
* **Hidden files are included.**

## Free version

Without the upgrade, Swiper is limited to 2 sessions with 50 files each. With a sort order set, you get the first 50 files in that order.

## Settings

### Swap swipe directions

Swipe left to keep, right to delete. Off by default.

### Show file details

Shows the file name, size, date and folder on the swipe screen. On by default.

### Haptic feedback

Vibrates when you keep or delete a file. On by default.
