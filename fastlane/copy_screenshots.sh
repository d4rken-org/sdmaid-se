#!/usr/bin/env bash
# Sort rendered screenshot PNGs (from generate_screenshots.sh) into the fastlane metadata tree.
#
# Reads every <module>/src/screenshotTest*/reference/**/<Func>_<locale>_<hash>_<idx>.png, maps <Func>
# to its Play Store slot via the manifest below, and copies it to
#   fastlane/metadata/android/<locale>/images/phoneScreenshots/<N>_<name>.png
# where <locale> is the fastlane dir baked into the @Preview(name=…). The leading number sets the
# Play Store ordering (supply uploads in sorted filename order); the name says what the shot shows.
# The manifest is authoritative: unknown functions and duplicate destinations are hard errors, and
# every locale that produced any screenshot must produce exactly one per screen (a full set) or the
# script exits non-zero.
#
# Usage:
#   fastlane/copy_screenshots.sh            # copy into place (fails if a locale is incomplete)
#   fastlane/copy_screenshots.sh --clean    # first remove phoneScreenshots/*.png for the locales in
#                                           # this run only, then copy
set -euo pipefail

cd "$(dirname "$0")/.."

# <Func> -> "<order>_<name>" destination stem (order sets Play Store position, name is descriptive).
declare -A SLOT=(
  [DashboardScreenshot]="1_dashboard"
  [AppCleanerScreenshot]="2_appcleaner"
  [CorpseFinderScreenshot]="3_corpsefinder"
  [SystemCleanerScreenshot]="4_systemcleaner"
  [DeduplicatorScreenshot]="5_deduplicator"
  [AppControlScreenshot]="6_appcontrol"
  [AnalyzerScreenshot]="7_analyzer"
  [SchedulerScreenshot]="8_scheduler"
)
EXPECTED_PER_LOCALE=${#SLOT[@]}

CLEAN=0
[[ "${1:-}" == "--clean" ]] && CLEAN=1

mapfile -t PNGS < <(find . -type d -name reference -path '*screenshotTest*' -prune -exec find {} -name '*.png' \; 2>/dev/null | sort)
if [[ ${#PNGS[@]} -eq 0 ]]; then
  echo "ERROR: no rendered PNGs found. Run fastlane/generate_screenshots.sh first." >&2
  exit 1
fi

# First pass: parse + validate, collect (locale -> list of "slot:src"), detect unknowns/duplicates.
declare -A PLACED       # "locale/slot" -> src (duplicate detection)
declare -A LOCALE_COUNT # locale -> count
declare -a JOBS         # "locale<TAB>slot<TAB>src"
FAIL=0

for src in "${PNGS[@]}"; do
  base="$(basename "$src" .png)"
  # <Func>_<locale>_<hash>_<idx> — Func has no underscore; locale may contain hyphens.
  func="${base%%_*}"
  rest="${base#*_}"
  locale="${rest%%_*}"
  slot="${SLOT[$func]:-}"
  if [[ -z "$slot" ]]; then
    echo "ERROR: unknown screenshot function '$func' (not in manifest): $src" >&2
    FAIL=1
    continue
  fi
  key="$locale/$slot"
  if [[ -n "${PLACED[$key]:-}" ]]; then
    echo "ERROR: duplicate destination $key from both:" >&2
    echo "         ${PLACED[$key]}" >&2
    echo "         $src" >&2
    FAIL=1
    continue
  fi
  PLACED[$key]="$src"
  LOCALE_COUNT[$locale]=$(( ${LOCALE_COUNT[$locale]:-0} + 1 ))
  JOBS+=("$locale	$slot	$src")
done

# Every locale that produced anything must be complete.
for locale in "${!LOCALE_COUNT[@]}"; do
  if [[ ${LOCALE_COUNT[$locale]} -ne $EXPECTED_PER_LOCALE ]]; then
    echo "ERROR: locale '$locale' has ${LOCALE_COUNT[$locale]}/$EXPECTED_PER_LOCALE screenshots (incomplete)." >&2
    FAIL=1
  fi
done

[[ $FAIL -ne 0 ]] && { echo "Aborting: validation failed, nothing copied." >&2; exit 1; }

# Optional clean: only the locales in this run.
if [[ $CLEAN -eq 1 ]]; then
  for locale in "${!LOCALE_COUNT[@]}"; do
    dir="fastlane/metadata/android/$locale/images/phoneScreenshots"
    [[ -d "$dir" ]] && rm -f "$dir"/*.png
  done
fi

# Second pass: copy.
for job in "${JOBS[@]}"; do
  IFS=$'\t' read -r locale slot src <<<"$job"
  dest_dir="fastlane/metadata/android/$locale/images/phoneScreenshots"
  mkdir -p "$dest_dir"
  cp "$src" "$dest_dir/$slot.png"
done

echo "Copied ${#JOBS[@]} screenshots across ${#LOCALE_COUNT[@]} locale(s), $EXPECTED_PER_LOCALE each."
