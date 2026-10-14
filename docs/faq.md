---
title: "Frequently Asked Questions"
lang: en
translation_key: faq
permalink: /faq
---
# Frequently Asked Questions
{: #frequently-asked-questions }

## Can I migrate from SD Maid to SD Maid SE?
{: #can-i-migrate-from-sd-maid-to-sd-maid-se }

It's two separate apps. You can import exclusions and custom filters from SD Maid 1.

## Transfer pro status from SD Maid 1
{: #transfer-pro-status-from-sd-maid-1 }

SD Maid 2/SE is separate app and pro upgrades from SD Maid 1 are not transferable.
While I would like to do that, app economics don't allow for it.
12 years of SD Maid 1 updates for a single one-time purchase was a pretty good deal though, wasn't it? 🙂 

## I'm using regular SD Maid, how do I start using SD Maid SE?
{: #im-using-regular-sd-maid-how-do-i-start-using-sd-maid-se }

Just download SD Maid SE and follow instructions. SD Maid SE is available for Android 8.0+ (API26+).

## What is the difference between SD Maid 1/Legacy and SD Maid 2/SE?
{: #what-is-the-difference-between-sd-maid-1legacy-and-sd-maid-2se }

* SD Maid 2/SE performs better, more thorough results, especially on newer Android versions.
* A new UI with themes.
* Easier to use for people that are not "power-users", better setup and error handling.
* SD Maid 2/SE will receive updates, get new features and keep up with app and Android changes.
* SD Maid 1/Legacy will go into maintenance mode and be limited to security updates.

## Why are you creating SD Maid SE?
{: #why-are-you-creating-sd-maid-se }

[Regular SD Maid](https://play.google.com/store/apps/details?id=eu.thedarken.sdm) has suffered under all the storage changes that Google has introduced with newer Android versions. I'm struggling to keep it working well on newer Android versions without breaking it on older versions. By creating a new app I can leverage over 10 years of experience without having to worry about backwards compatibility. A clean slate to regain the fun I had working on SD Maid. 

## Why is SD Maid SE not an update for regular SD Maid?
{: #why-is-sd-maid-se-not-an-update-for-regular-sd-maid }

Making any significant changes to [regular SD Maid](https://play.google.com/store/apps/details?id=eu.thedarken.sdm) is impossible without upsetting users. I want to rewrite the whole app and make drastic changes. Doing this to an existing app is both difficult and not something I would want another dev do to one of my apps.

## Why can't I transfer SD Maid 1 Pro to SD Maid 2/SE
{: #why-cant-i-transfer-sd-maid-1-pro-to-sd-maid-2se }

SD Maid 1 had 10+ years of updates, that was a pretty good deal.
I think it's fair to have a new pro upgrade for a new app.
I have to draw the line somewhere, otherwise it won't be feasible to continue development.
No one is forced to switch. If you bought SD Maid 1 Pro, you still have SD Maid 1 Pro and can use it. 

There are also some technical hurdles. It's not possible to see what other purchases the user has on their account.

## Why are some features missing in SD Maid SE?
{: #why-are-some-features-missing-in-sd-maid-se }

SD Maid SE focuses on regular SD Maid's most popular features to keep the workload maintainable and the fun in development. I think apps should do only a few things, and those things well. Regular SD Maid suffered a bit under feature creep where I tried to please too many users. Features like the Explorer were never great in comparison to other stand-alone explorer apps. There is no need for SD Maid to compete on that front.

## Is regular SD Maid dead?
{: #is-regular-sd-maid-dead }

No. It will still receive security patches and some bug fixes, but no new features and no updates that improve compatibility with new Android versions.
Regular SD Maid and SD Maid SE will have similar performance at the beginning, and as time passes and new Android versions are released, SD Maid SE will overtake regular SD Maid with better performance, compatibility and features.

## 2 or SE?
{: id="2-or-se" }

`SD Maid 2/SE`, `SD Maid 2` and `SD Maid SE` is fine. Internally and officially the app is called `SD Maid SE`.
I renamed the old app to `SD Maid 1` and the new app `SD Maid 2/SE` to reduce confusion and emphasize succession.

## What does the SE in SD Maid SE stand for?
{: #what-does-the-se-in-sd-maid-se-stand-for }

What does the `SD` in `SD Maid` stand for? 🤷

## How can I upgrade SD Maid 2/SE without Google Play?
{: #how-can-i-upgrade-sd-maid-2se-without-google-play }

In addition to the normal version of SD Maid 2/SE, there is a `FOSS` version available through GitHub and F-Droid, [see here](https://github.com/d4rken-org/sdmaid-se?tab=readme-ov-file#download).

The FOSS version has no Google dependencies and can be upgraded through a voluntary donation. You can donate anything, from as little as you want :frowning: to as much as you can :heart_eyes:. This is currently not an enforced check and trusts you as you trust SD Maid with you data.

There is currently no functional difference between the Google Play and the `FOSS` version, only some small visual differences.

## Should I use SD Maid 1 or 2?
{: #should-i-use-sd-maid-1-or-2 }

* If your device runs on Android 7 (API 25) or older: Keep using SD Maid 1. 
* If your device runs at least Android 8 (API 26): You could use SD Maid 2/SE.
* If your device runs on Android 12 (API 32) or newer: Consider switching to SD Maid 2/SE.
* If your device runs on Android 14 (API 34): You should use SD Maid 2/SE.

---

# Using SD Maid SE
{: #using-sd-maid-se }

## What can I do for free and what requires the upgrade?
{: #what-can-i-do-for-free-and-what-requires-the-upgrade }

All tools in SD Maid SE can **scan for free**, so you can see exactly what would be cleaned before deciding to upgrade.

**Free features:**
- CorpseFinder: Scan and delete remnants of uninstalled apps
- SystemCleaner: Scan and delete system junk using built-in filters
- AppControl: View and manage installed apps
- Analyzer: Analyze storage usage and delete files

**Upgrade required for:**
- AppCleaner: Deleting app caches and expendable files (scanning is free)
- Deduplicator: Deleting duplicate files (scanning is free)
- Media Squeeze: Compressing photos and videos (scanning is free)
- Analyzer: The Storage Trend chart, the low space warning, and creating SystemCleaner filters from the analysis
- Scheduler: Enabling scheduled automatic cleaning
- Swiper: Unlimited files and sessions

The upgrade is available as a **one-time purchase** or a **subscription** on Google Play. The FOSS version can be unlocked through a GitHub Sponsors donation.

## Why does SD Maid SE need the Accessibility Service and how do I fix issues with it?
{: #why-does-sd-maid-se-need-the-accessibility-service-and-how-do-i-fix-issues-with-it }

SD Maid SE uses the Accessibility Service to automate cache cleaning. On modern Android, there is no API for apps to clear another app's cache directly. Instead, SD Maid SE navigates to each app's settings page and taps the "Clear Cache" button for you, which requires the Accessibility Service.

**Common issues:**

*"The Accessibility Service keeps turning off"*
Some manufacturers (Xiaomi, Huawei, Samsung, OnePlus) aggressively restrict background services. You may need to:
- Disable battery optimization for SD Maid SE
- Lock the app in your recent apps list
- Check your manufacturer's "App Management" or "Auto-start" settings

*"I can't enable the Accessibility Service"*
On some devices, the system blocks accessibility access for apps installed from sources it doesn't trust, or restricts it for security reasons. If you installed from Google Play and still can't enable it, try:
- Restarting your device
- Uninstalling and reinstalling SD Maid SE
- Checking if your manufacturer's security app is blocking the permission

Using **Shizuku** or **ADB** as an alternative access method avoids the Accessibility Service entirely and can resolve these issues. See the Shizuku FAQ entry below for more details.

## What is Shizuku and why does SD Maid SE recommend it?
{: #what-is-shizuku-and-why-does-sd-maid-se-recommend-it }

[Shizuku](https://shizuku.rikka.app/) is a separate open-source app that lets other apps perform elevated operations without root access. It uses Android's built-in debugging interface (ADB) to grant these permissions. [Porter](https://porter.darken.eu), a maintained fork of Shizuku, works the same way. If both are installed, SD Maid uses Porter. See [Setup: ADB access](/setup#shizuku).

SD Maid SE can use Shizuku to clean app caches and access files more thoroughly than with the Accessibility Service alone. Using Shizuku is **optional** but recommended because it is:
- **Faster**: No need to navigate the Settings UI per-app
- **More reliable**: Avoids Accessibility Service issues on some devices
- **More thorough**: Can access data that the Accessibility Service method can't reach

**How to set it up:**
1. Install [Shizuku from Google Play](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) or [GitHub](https://github.com/RikkaApps/Shizuku)
2. Follow Shizuku's built-in instructions to start it (requires a one-time ADB command from a computer, or can be started wirelessly on Android 11+)
3. Open SD Maid SE → Setup → `ADB access` → Select "Try to use ADB access"

**Note:** Shizuku needs to be restarted after each device reboot unless you use the wireless debugging method on Android 11+.

## Does SD Maid SE work on Android 16?
{: #does-sd-maid-se-work-on-android-16 }

Android 16 introduced changes to how apps can use the Accessibility Service and access storage, which can affect SD Maid SE's functionality.

**Known issues on Android 16:**
- Some devices block the Accessibility Service from being enabled for SD Maid SE
- Cache cleaning via the Accessibility Service may not work or may be incomplete
- Some manufacturers' Android 16 builds have additional restrictions

**What you can do:**
- Use **Shizuku** instead of the Accessibility Service, it is not affected by these restrictions
- Make sure you are running the **latest version** of SD Maid SE, as compatibility fixes are released regularly
- If you are on a beta/preview version of Android 16, expect instability, SD Maid SE targets stable releases

SD Maid SE is actively maintained and Android 16 compatibility improvements are ongoing.

## Why is SD Maid SE slower than SD Maid 1?
{: #why-is-sd-maid-se-slower-than-sd-maid-1 }

There are several reasons SD Maid SE is slower:

**1. Cache clearing method changed**
SD Maid 1 could use a hidden system API to wipe all app caches in a single call. Google removed access to this API in newer Android versions. SD Maid SE now has to use alternative methods (Accessibility Service, Shizuku, or root) which are inherently slower.

**2. Broader compatibility**
SD Maid SE is designed to work across many Android versions and storage access models. This extra compatibility layer adds overhead. SD Maid 1 was hyper-optimized for specific scenarios, but that optimization broke down as Android introduced changes.

**3. More thorough scanning**
SD Maid SE scans more thoroughly than SD Maid 1, which means it finds more to clean but takes longer to do so.

**Speeding things up:**
- **Shizuku / ADB**: Significantly faster than the Accessibility Service, can clear caches directly without navigating the UI
- **Root**: Fastest method, direct file system access

## How do I cancel my subscription or get a refund?
{: #how-do-i-cancel-my-subscription-or-get-a-refund }

SD Maid SE subscriptions are managed through **Google Play**, not through the app itself. Uninstalling SD Maid SE does **not** cancel your subscription.

**To cancel your subscription:**
1. Open the Google Play Store app
2. Tap your profile icon → Payments & subscriptions → Subscriptions
3. Find SD Maid 2/SE and tap "Cancel subscription"

**To request a refund:**
- Visit [Google Play's refund page](https://play.google.com/store/account/orderhistory) within 48 hours of purchase for automatic refunds
- For older purchases, contact Google Play support through the Play Store

**Note:** SD Maid SE also offers a **one-time purchase** option if you prefer not to manage a recurring subscription.

## Can SD Maid SE delete important files?
{: #can-sd-maid-se-delete-important-files }

SD Maid SE is designed to only target expendable data: caches, temporary files, and remnants of uninstalled apps. It does **not** delete personal files like photos, music, or documents unless you explicitly tell it to (e.g., through the Analyzer's file browser or custom SystemCleaner filters).

Before any deletion, SD Maid SE shows you exactly what will be removed, so you can review and deselect items.

**If you want to protect specific files or folders:**
- Long-press an item in the scan results and choose "Exclude"
- Or go to Settings → Exclusions to manually add paths

Exclusions persist across scans and prevent SD Maid SE from ever touching those files.

**Tip:** If you're unsure, run a scan first without deleting anything. Review the results to understand what SD Maid SE considers expendable on your device.

## My antivirus says SD Maid SE is malicious or contains a virus
{: #my-antivirus-says-sd-maid-se-is-malicious-or-contains-a-virus }

This is a **false positive**. If you check SD Maid SE on [VirusTotal](https://www.virustotal.com/), you will see that the vast majority of antivirus engines (typically 65+ out of 67) report it as clean. Only one or two engines may flag it with a generic heuristic label like "Evo-gen [Trj]".

**Why does this happen?**

SD Maid SE requires permissions that are unusual for a normal app, most notably the Accessibility Service and broad storage access. Some antivirus engines use heuristic detection that flags any app requesting these permissions as suspicious, regardless of what the app actually does. These permissions are essential for SD Maid SE to function (e.g., automating cache cleaning, scanning storage).

**What should you do?**

Report it as a false positive within your antivirus app. Most antivirus apps have an option to flag a detection as incorrect. This helps improve their detection accuracy for all users.

**Can you trust SD Maid SE?**

SD Maid SE has a fully [open-source FOSS build](https://github.com/d4rken-org/sdmaid-se) with [reproducible builds](https://f-droid.org/en/docs/Reproducible_Builds/). Anyone can audit the source code and independently verify that the published APK matches the source. There is nothing hidden.

**A note on antivirus apps and Android**

Android's built-in security model already provides strong protection through app sandboxing, the permission system, and Google Play Protect. Third-party antivirus apps on Android have limited effectiveness because they run in the same sandbox as every other app, so they cannot deeply inspect other apps the way a desktop antivirus can. False positives from legitimate apps that use advanced permissions are a common side effect of this limitation.

If you are still unsure or have concerns, feel free to reach out via email at **support@darken.eu**.

## The Scheduler doesn't run automatically
{: #the-scheduler-doesnt-run-automatically }

The Scheduler requires specific conditions to work reliably:

**1. Upgrade required**
Enabling schedules is a Pro feature. The free version lets you create schedules but not activate them.

**2. Accessibility Service or Shizuku must be active**
If your schedule includes AppCleaner, the Accessibility Service (or Shizuku) needs to be running when the schedule triggers. If the system killed the service in the background, the scheduled cleaning will fail.

**3. Battery optimization**
Many manufacturers aggressively kill background apps. To prevent this:
- Disable battery optimization for SD Maid SE
- On Xiaomi/MIUI: Enable "Auto-start" and lock the app in recents
- On Huawei/EMUI: Add SD Maid SE to "Protected apps"
- On Samsung: Exclude from "Sleeping apps" and "Deep sleeping apps"
- On OnePlus: Disable "Smart battery optimization" for SD Maid SE

For manufacturer-specific guidance, see [dontkillmyapp.com](https://dontkillmyapp.com/).

**4. Automation consent**
The first time SD Maid SE runs an automated task, it needs your consent for automation. If you haven't granted this, scheduled runs will be skipped.
