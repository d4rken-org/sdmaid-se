# Privacy policy

This is the privacy policy for the Android app "SD Maid 2/SE".

## Preamble

I do not collect, share or sell personal information.

My underlying privacy principle is the [Golden Rule](https://en.wikipedia.org/wiki/Golden_Rule).

Send a [quick mail](mailto:support@darken.eu) if you have questions.

## Permissions

Details about senstive permissions can be found below.

In general, SD Maid only processes data locally, on your device. Two edge cases exist:

* If you record a [debug log](#debug-log), the resulting file will contain a detailed log of SD Maids actions.
* If you enable [automatic error reports](#automatic-error-reports) and an error occurs, the resulting bug report may
  contain information about what SD Maid did shortly before the error occured.

### Query installed apps

SD Maid can show you which files belong to uninstalled applications. To do this SD Maid needs to know which applications
are currently installed. Retrieving the information about all currently installed applications requires
the `QUERY_ALL_PACKAGES` permission. Information is processed locally on your device.

### Accessibility service

SD Maid contains optional features that utilize Android's AccessibilityService API to automate tedious actions. Usage of
the AccessibilityService API is optional, opt-in and can be disabled at any time. SD Maid does not use the
AccessibilityService API to collect or send or information.

## Message of the day

SD Maid contains a "Message of the day" (MOTD) system that can show the user one-time dismissable messages.
Data for the messages is hosted on GitHub within SD Maid's respository.
SD Maid sends HTTP GET requests (similar to visiting a link with a web browser) to GitHub's servers to check for new
MOTDs. A GitHub account is not required.

The MOTD check is optional and can be disabled in settings.

GitHubs privacy policy can be found here:
https://docs.github.com/site-policy/privacy-policies/github-privacy-statement

## Debug log

The app has a debug log feature that can be used to assist troubleshooting efforts.
This feature creates a log file that contains verbose output of what the app is doing. 

It is manually triggered by the user through an option in the app settings.
The recorded log file can be shared through compatible apps (e.g. your email app) using the system's share dialog.
As this log file may contain sensitive information (e.g. details about files or your installed applications) it should only be shared with trusted parties.
