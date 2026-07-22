#!/usr/bin/env bash
# Generate Play Store screenshots for SD Maid SE via the com.android.compose.screenshot plugin
# (host-side layoutlib rendering, no device). Renders one PNG per locale per screen into each
# module's screenshotTest reference dir, then run copy_screenshots.sh to sort them into fastlane.
#
# Usage:
#   fastlane/generate_screenshots.sh            # render the committed smoke locale set
#
# The rendered locale set is whatever the per-module screenshotTest/.../PlayStoreLocales.kt annotation
# declares (currently the committed smoke set, also documented in fastlane/screenshots/locales.txt).
# There is no automated full-locale (~50 locales) mode yet: to add locales, extend both the manifest
# in locales.txt AND the @Preview lines in every module's PlayStoreLocales.kt, then re-run.
#
# Each module's reference dir is wiped before rendering so a run never leaves stale PNGs behind for
# copy_screenshots.sh to pick up. The update task treats the reference dir as untracked, so it is
# UP-TO-DATE after a wipe and would skip rendering — hence --rerun-tasks to force a fresh render.
# One Gradle invocation per module (--no-daemon) also bounds layoutlib's per-process render count and
# sidesteps its documented ImagePool leak. Keep this module list and the manifest in
# copy_screenshots.sh in sync with the screenshot screens.
set -euo pipefail

cd "$(dirname "$0")/.."

# module-gradle-path : update-task  (the app module is flavored -> Foss variant; libraries are not)
MODULES=(
  ":app:updateFossDebugScreenshotTest"
  ":app-tool-appcleaner:updateDebugScreenshotTest"
  ":app-tool-corpsefinder:updateDebugScreenshotTest"
  ":app-tool-systemcleaner:updateDebugScreenshotTest"
  ":app-tool-deduplicator:updateDebugScreenshotTest"
  ":app-tool-appcontrol:updateDebugScreenshotTest"
  ":app-tool-analyzer:updateDebugScreenshotTest"
  ":app-tool-scheduler:updateDebugScreenshotTest"
)

echo "Generating screenshots for ${#MODULES[@]} modules…"
for task in "${MODULES[@]}"; do
  module_dir="${task#:}"; module_dir="${module_dir%%:*}"   # ":app-tool-x:task" -> "app-tool-x"
  # Wipe any prior reference PNGs for this module so no stale renders survive into the copy step.
  rm -rf "$module_dir"/src/screenshotTest*/reference
  echo "==> ./gradlew $task"
  ./gradlew "$task" --rerun-tasks --no-daemon
done

echo
echo "Done. Reference PNGs are under <module>/src/screenshotTest*/reference/."
echo "Next: fastlane/copy_screenshots.sh --clean   (sort them into fastlane/metadata)"
