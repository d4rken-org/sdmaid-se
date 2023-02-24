# Privacy policy

This is the privacy policy for the Android app "SD Maid SE".

## Preamble

I do not collect, share or sell personal information.

My underlying privacy principle is the [Golden Rule](https://en.wikipedia.org/wiki/Golden_Rule).

Send a [quick mail](mailto:support@darken.eu) if you have questions.

## Permissions

Details about senstive permissions can be found below.

### Query installed apps

SD Maid SE can show you which files belong to uninstalled applications.
To do this SD Maid SE needs to know which applications are currently installed.
Retrieving the information about all currently installed applications requires the `QUERY_ALL_PACKAGES` permission.
This information is processed locally on your device.

Two edge cases exist: Information about installed apps may be contained in manually generated [debug logs](#debug-log)
and [automatic error reports](#automatic-error-reports).

### Accessibility service

SD Maid SE contains optional features that utilize Android's AccessibilityService API to automate tedious actions.
Usage of the AccessibilityService API is optional, opt-in and can be disabled at any time.

SD Maid SE does not use the AccessibilityService API to collect, send or transmit information.

## Automatic error reports

Anonymous device information may be collected in the event of an app crash or an error.

To do this the app uses the service "Bugsnag":
https://www.bugsnag.com/

Bugsnag's privacy policy can be found here:
https://docs.bugsnag.com/legal/privacy-policy/

Error reports contain information related to the error that occured. This can include information about your device (type, versions, state), installed apps and file paths that were processed when the error occured.

You can disable automatic reports in the app's settings.

## Debug log

The app has a debug log feature that can be used to assist troubleshooting efforts.
This feature creates a log file that contains verbose output of what the app is doing. 

It is manually triggered by the user through an option in the app settings.
The recorded log file can be shared through compatible apps (e.g. your email app) using the system's share dialog.
As this log file may contain sensitive information (e.g. details about files or your installed applications) it should only be shared with trusted parties.
