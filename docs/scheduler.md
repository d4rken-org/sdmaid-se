---
title: "Scheduler"
lang: en
translation_key: scheduler
permalink: /scheduler
---
# Scheduler
{: #scheduler }

The **Scheduler** feature in SD Maid 2 allows you to automate maintenance tasks at specific intervals, ensuring that your device stays optimized without manual effort. It supports multiple tools such as **AppCleaner**, **CorpseFinder**, and **SystemCleaner**, which can be configured to run at regular intervals or specific times.

<img src="/assets/images/scheduler/overview-1.webp" width="150">
<img src="/assets/images/scheduler/overview-2.webp" width="150">
<img src="/assets/images/scheduler/overview-3.webp" width="150">
<img src="/assets/images/scheduler/overview-4.webp" width="150">
<img src="/assets/images/scheduler/overview-5.webp" width="150">
<img src="/assets/images/scheduler/overview-6.webp" width="150">


## How to Set Up a Schedule
{: #how-to-set-up-a-schedule }

1. Navigate to **Settings** and select **Scheduler**.
2. Tap the **+** button to create a new schedule or manage existing ones.
3. Choose which tools (e.g., **AppCleaner**, **CorpseFinder**, **SystemCleaner**) should run on a schedule.
4. Set the **repeat interval** (e.g., every 3 days) and the **approximate time** of execution.
5. Enable the schedule by toggling the switch.

## Editing or Deleting Schedules
{: #editing-or-deleting-schedules }

1. Go to the **Scheduler** settings to manage your schedules.
2. You can modify existing schedules, adjust intervals, or delete them if no longer needed.

Once a schedule is active, SD Maid will run the chosen tasks at the specified times.

## Additional Options
{: #additional-options }

Configured via the main settings menu: `Settings > Scheduler` there a few extra options.

- **Skip on low battery**: If the device is in power-saving mode, scheduled tasks will be skipped.
- **Skip when not charging**: Scheduled tasks will only run when the device is charging, preventing battery drain.
- **Post-schedule commands**: You can set shell commands to be executed after the scheduled tasks complete.
- **Accessibility service**: By default, the scheduler will skip accessibility service based features as these don't work when the device is looked. If you enable this, without making any other changes to your device, then the scheduler will fail. So unless you know what you are doing, keep this disabled. This is only useful in edge-cases like an always on Android TV device.

## Troubleshooting
{: #troubleshooting }

### Scheduler is not executing 
{: #scheduler-is-not-executing }

Differentiate between the scheduler not running at all and the executed operations not yielding the desired results. You can check the execution history in SD Maid's [stats section](/stats). If the scheduler executed but failed, there should be an error.

If the scheduler did not run at all, it’s possible that the system prevented SD Maid from launching to save battery. After activating your first schedule, you should have seen a prompt titled "Advice" with a "Fix" action, asking you to disable battery optimizations. If you accidentally dismissed that prompt, you’ll need to manually go into your system settings for SD Maid and check if SD Maid is being "optimized" by the system.

### Accessibility service
{: #accessibility-service }
Accessibility based deletions, e.g. AppCleaner deleting "inaccessible items", do not work via scheduler. When the scheduler executed, the device is usually locked, just like you can't click things on a turned off screen or locked display, SD Maid can't either.

By default, the scheduler does not perform accessibility-based operations. However, you can enable the "Accessibility service" option in the scheduler settings if you know what you are doing.

Workarounds: Root the device (user + system apps) or install Shizuku (user apps only). Either mod allows SD Maid to perform similar operations without using the accessibility service feature.
