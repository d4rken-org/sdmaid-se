---
title: "Bugs"
lang: en
translation_key: bugs
permalink: /bugs
---
# Bugs

🐌 🦋 🐛 🐜 🐝 🐞

Bugs are part of an apps life. They mean you no harm, but they are not here to stay either 😠.

What are bugs?

* If the app crashes it is obviously a bug
* If something is behaving unexpectedly, it could be a bug.

In any case check that you are on the latest update, the bug might already be fixed.

If the bug isn't fixed, then report it :grin:.

## Reporting a bug

Create an issue ticket on GitHub:
<https://github.com/d4rken-org/sdmaid-se/issues>

* Describe what you expected and what actually happened
* List the steps that someone else would have to take to reproduce the issue
* Describe your setup
    * Your device type (e.g. Pixel 5)
    * Android version (e.g. Android 13.1)
    * If you are rooted, the Magisk version

Include a [debug log](#debug-log) if you can reproduce the issue. 

Hint: You can [long press the "Changelog" entry](https://github.com/d4rken-org/sdmaid-se/pull/940) in SD Maid's settings to copy the version to your clipboard.

## Debug log

A debug log is a recording of SD Maid's action. You start the recording, reproduce the issue, then stop it and can share a zip file with a chronological account of what SD Maid did.

To start recording a debug log:
* Go to settings in SD Maid by pressing the settings icon in the bottom right of the dashboard
* Find the `Support` entry in the settings list
* Click `Debug log` on the support screen
* A new notification from SD Maid will pop up and notify you that a log is being recorded (make sure you have given SD Maid notification permission).
* **Now reproduce the issue you want to record**
* Once you have reproduced the issue, open your notifications and find the debug log notification from SD Maid
* Press the `Done` button in the notification. If the `Done` action is not visible, expand the notification.
* After a short moment you will get a popup that allows you to share the compressed log file.

The debug log will be recorded until you stop it, the recording will continue, even if you force-stop and restart SD Maid.

If you are troubleshooting issues related to core functionality, e.g. root detection or detected storage areas, it is useful to force-stop and restart SD Maid while recording as some data is only initialized once per app session.

The debug log files are stored in `/storage/emulated/0/Android/data/eu.darken.sdmse/cache/debug/logs`.

In pictures:

<a href="/assets/images/bugs/debug-log-1.webp"><img src="/assets/images/bugs/debug-log-1.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-2.webp"><img src="/assets/images/bugs/debug-log-2.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-3.webp"><img src="/assets/images/bugs/debug-log-3.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-4.webp"><img src="/assets/images/bugs/debug-log-4.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-5.webp"><img src="/assets/images/bugs/debug-log-5.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-6.webp"><img src="/assets/images/bugs/debug-log-6.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-7.webp"><img src="/assets/images/bugs/debug-log-7.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-8.webp"><img src="/assets/images/bugs/debug-log-8.webp" width="100" alt=""></a>
