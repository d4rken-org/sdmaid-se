#!/usr/bin/env bash
# Installs an older SD Maid build, creates a user's state through the UI, updates that install in place to the
# current build, and checks the state survived (UpgradeTest in :app-e2e).
# Both app APKs are re-signed with this machine's debug key first, because an in-place update needs matching keys.
# Wipes eu.darken.sdmse on the target device, which ANDROID_SERIAL must name.
#
# Usage: tools/e2e/upgrade-test.sh <old-app.apk> <new-app.apk> <app-e2e.apk> <results-dir>
# The app-e2e APK must be the same flavor as both app APKs.
set -euo pipefail

if [ $# -ne 4 ]; then
    echo "Usage: $0 <old-app.apk> <new-app.apk> <app-e2e.apk> <results-dir>" >&2
    exit 2
fi
OLD_APK=$1
NEW_APK=$2
TEST_APK=$3
RESULTS=$4
: "${ANDROID_SERIAL:?set ANDROID_SERIAL to an emulator started for this run}"

APP=eu.darken.sdmse
TEST_PKG=eu.darken.sdmse.e2e
RUNNER=$TEST_PKG/androidx.test.runner.AndroidJUnitRunner

BUILD_TOOLS=$(ls -d "${ANDROID_HOME:-${ANDROID_SDK_ROOT:?set ANDROID_HOME}}"/build-tools/*/ | sort -V | tail -1)
DEBUG_KEYSTORE=${DEBUG_KEYSTORE:-$HOME/.android/debug.keystore}
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

certificate() {
    "$BUILD_TOOLS/apksigner" verify --print-certs "$1" | grep -m1 'SHA-256 digest' | sed 's/.*: //'
}

# Prints the key each APK came with, so a build that signs unexpectedly shows up in the log.
resign() {
    echo "$2 APK signed with $(certificate "$1")"
    cp "$1" "$WORK/$2.apk"
    "$BUILD_TOOLS/apksigner" sign --ks "$DEBUG_KEYSTORE" --ks-pass pass:android \
        --ks-key-alias androiddebugkey --key-pass pass:android "$WORK/$2.apk"
}

run_phase() {
    echo "== UpgradeTest#$1"
    adb shell am instrument -w -e class "eu.darken.sdmse.e2e.UpgradeTest#$1" "$RUNNER" | tee "$RESULTS/$1.txt"
    grep -q '^OK (1 test)' "$RESULTS/$1.txt"
}

resign "$OLD_APK" old
resign "$NEW_APK" new
echo "Both re-signed with $(certificate "$WORK/new.apk")"

rm -rf "$RESULTS"
mkdir -p "$RESULTS"

adb uninstall "$APP" >/dev/null 2>&1 || true
adb uninstall "$TEST_PKG" >/dev/null 2>&1 || true
adb install "$WORK/old.apk"
adb install -t "$TEST_APK"

result=0
if run_phase beforeUpgrade; then
    adb install -r "$WORK/new.apk"
    run_phase afterUpgrade || result=1
else
    result=1
fi

adb uninstall "$APP" >/dev/null 2>&1 || true
adb uninstall "$TEST_PKG" >/dev/null 2>&1 || true
if [ "$result" -ne 0 ]; then
    echo "Upgrade test failed, see $RESULTS" >&2
fi
exit "$result"
