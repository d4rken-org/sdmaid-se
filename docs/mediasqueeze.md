---
title: "Media Squeeze"
lang: en
translation_key: mediasqueeze
permalink: /mediasqueeze
---
# Media Squeeze
{: #media-squeeze }

Media Squeeze compresses photos and videos so they take up less space. You trade some quality for the space you get back.

**Compression permanently reduces quality and can't be undone.** The original file is replaced and no copy is kept. Check your settings and the preview before you start.

Scanning is free. Compressing requires the upgrade.

## How it works
{: #how-it-works }

1. Pick the folders to search. There is no default location, e.g. pick your camera folder.
2. Set the compression quality and the minimum age.
3. Scan. Each result shows its size and an estimated saving.
4. Tap a single item, select several with a long-press, or use `Compress all`. A confirmation shows the total size and estimated savings first.

If the selection contains only photos or only videos, `View comparison` in the confirmation shows the original and the compressed version side by side. For videos, the comparison is a single frame and only approximate.

### What happens to a file
{: #what-happens-to-a-file }

* It is re-encoded in its own format: a JPEG stays a JPEG, a WebP stays a WebP, an MP4 stays an MP4.
* Image resolution stays the same, except for [very large photos](#compress-very-large-photos).
* For videos, only the video track is re-encoded at a lower bitrate. The audio track is copied over unchanged.
* Date, location, camera and other EXIF metadata of photos are kept. If SD Maid can't read or preserve a photo's EXIF metadata, it leaves the photo unchanged.
* XMP metadata is not kept, e.g. photo sphere and panorama information or ratings from photo editors.
* The file's modified date is kept, so galleries keep sorting it in the same place.
* If the compressed version isn't smaller, the original is left untouched.

The estimated savings are a rough guess based on the file type and quality, not a measurement. Actual savings can differ.

### Locations
{: #locations }

Media Squeeze needs direct access to the files and write access to their folders. It doesn't use root or ADB. Some locations picked through Android's folder picker, e.g. USB drives, can't be used and are removed from the selection.

## Previously compressed files
{: #previously-compressed-files }

SD Maid remembers which files it already compressed, and which ones didn't get smaller, and skips them in later scans. It recognizes a file by its content, so this also works after the file was renamed or moved. Compressing a file again costs more quality and saves little.

The history is kept on your device and is part of SD Maid's settings backup. Clearing it makes previously compressed files eligible again, unless they carry an EXIF marker and [Write EXIF marker](#write-exif-marker) is enabled. Clearing it doesn't change any files.

## Photos that are skipped by default
{: #photos-that-are-skipped-by-default }

Some photos contain data that compression can't keep. SD Maid skips them if it detects that data, unless you enable the matching setting. Detection isn't complete: photos in formats it doesn't recognize are compressed and lose that data.

* **HDR and depth photos**: HDR photos become normal photos, and portrait photos lose the data behind their background blur.
* **Motion Photos**: the still image is kept, the video clip is removed.
* **Very large photos** (8192 pixels or more wide or tall): their width and height are halved, and halved again if needed, until both are below 8192 pixels. A 20,000 pixel wide panorama ends up 5,000 pixels wide.

HEIC files that contain multiple images are always skipped.

The scan summary counts photos skipped for HDR or depth data, Motion Photos and very large size, and files that weren't accessible. Files skipped because they were compressed before are not counted, and neither are multi-image HEIC files unless they also have HDR or depth data.

## Settings
{: #settings }

The compression quality, search locations and minimum age are set on the Media Squeeze screen before scanning. Everything else is under `Settings > Media Squeeze`.

### Compression quality
{: #compression-quality }

Default 80. Lower quality means smaller files but reduced visual quality. Below 40 images may become unusable. At 95 and above there is little to save.

### Minimum age
{: #minimum-age }

Only include files older than this. Default 90 days, based on the file's modified date.

### Minimum file size
{: #minimum-file-size }

Skip files smaller than this. Default 512 KB.

### JPEG
{: #jpeg }

Include JPEG images (`.jpg`, `.jpeg`). On by default.

### WebP
{: #webp }

Include WebP images (`.webp`). On by default. Animated WebP files are not detected and would lose their animation.

### HEIC/HEIF
{: #heicheif }

Include HEIC/HEIF images (`.heic`, `.heif`). Off by default. Requires Android 9 or newer. HDR gain maps, depth data and Live/Motion Photo parts can't be preserved for HEIC.

### MP4 Video
{: #mp4-video }

Include MP4 videos (`.mp4`). Off by default. Compressing a video needs free space of about 110% of its size on the same storage.

### Compress HDR & depth photos
{: #compress-hdr--depth-photos }

See [Photos that are skipped by default](#photos-that-are-skipped-by-default). Off by default.

### Compress Motion Photos
{: #compress-motion-photos }

Keeps the still image and removes the video clip. Off by default.

### Compress very large photos
{: #compress-very-large-photos }

Includes photos that are 8192 pixels or more wide or tall, and reduces their resolution, see [Photos that are skipped by default](#photos-that-are-skipped-by-default). Off by default.

### Skip previously compressed
{: #skip-previously-compressed }

Skip files that SD Maid already compressed. On by default.

### Write EXIF marker
{: #write-exif-marker }

Adds a marker to the photo's EXIF user comment, so SD Maid recognizes it as compressed even after the compression history was cleared. SD Maid only checks for the marker while this setting is enabled. Off by default. Only applies to JPEG and WebP.

### Compression history
{: #compression-history }

Shows how many files are in the history. Tap it to clear the history.

## Exclusions
{: #exclusions }

Media Squeeze skips paths that are [excluded](/exclusions) in general. You can also exclude results from the result list, which only affects Media Squeeze.

Media Squeeze can't be run by the [Scheduler](/scheduler).
