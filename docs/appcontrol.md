---
title: "AppControl"
lang: en
translation_key: appcontrol
permalink: /appcontrol
---
# AppControl
{: #appcontrol }
Is your one stop shop for all action related to controlling installed applications. After scanning you will have a searchable list of your installed apps. System apps are not shown by default, this has to be enabled in the settings.
Some options may only be available (possible) if SD Maid has been granted root permissions.

Some actions can be executed on multiple apps, some only on single items. To execute actions on multiple apps long press an app then tap to select additional apps.

## Actions
{: #actions }
Some of these actions are only accessible in the apps details which can be reached by tapping an entry in the list.

### Run app
{: #run-app }
Launches the app. It's primary activity, usually the same action the launcher would execute too.

### Force stop
{: #force-stop }
Tries to force stop the app. Works best with Root or ADB/Shizuku, otherwise uses the accessibility service.

### Export apk
{: #export-apk }
Tries to copy the apps `.apk` file from internal private storage to your public storages download directory. If the app is encrypted, then this action requires root, unencrypted apps can also be exported without root.

### Freeze app
{: #freeze-app }
Requires root or ADB/Shizuku. Disables the app. The app will not be visible and can't start itself. It has to be enabled again to work. No dataloss occurs and this state also persists if you uninstall SD Maid. Any good app enabled/disabler app can enable apps that have been disabled with SD Maid and vice versa.

### Delete app
{: #delete-app }
Deletes the app and it's data.


## Settings
{: #settings }
### Determine size
{: #determine-size }
Can be disabled to speed up loading the list of apps. Determing how large an app is takes a lot of time.

### Determine activity
{: #determine-activity }
Can be disabled to speed up loading the list of apps. Determing whether an app has recently been active takes a lot of time.
