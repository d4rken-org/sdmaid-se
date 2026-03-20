#!/usr/bin/env bash
#
# Validates Fastlane metadata files stay within store character limits.
#
# Checked files per locale:
#   title.txt             - hard limit 30 chars
#   short_description.txt - hard limit 80 chars
#   full_description.txt  - hard limit 3800 chars, warn at 3700
#   changelogs/*.txt      - hard limit 500 chars each

set -euo pipefail
export LC_ALL=C.UTF-8

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
METADATA_DIR="$SCRIPT_DIR/metadata/android"

TITLE_MAX=30
SHORT_DESC_MAX=80
FULL_DESC_MAX=3800
FULL_DESC_WARN=3700
CHANGELOG_MAX=500

failed=0
warned=0
checked=0

# Count characters excluding only the trailing newline
count_chars() {
    sed -z 's/\n$//' < "$1" | wc -m | tr -d '[:space:]'
}

check_file() {
    local file="$1"
    local label="$2"
    local hard_limit="$3"
    local warn_limit="${4:-0}"

    if [ ! -f "$file" ]; then
        return
    fi

    local chars
    chars="$(count_chars "$file")"
    checked=$((checked + 1))

    if [ "$chars" -gt "$hard_limit" ]; then
        echo "FAIL  $label: $chars chars (limit: $hard_limit)"
        failed=$((failed + 1))
    elif [ "$warn_limit" -gt 0 ] && [ "$chars" -gt "$warn_limit" ]; then
        echo "WARN  $label: $chars chars (approaching limit)"
        warned=$((warned + 1))
    fi
}

if [ ! -d "$METADATA_DIR" ]; then
    echo "ERROR: Metadata directory not found: $METADATA_DIR"
    exit 1
fi

locale_count=0
for locale_dir in "$METADATA_DIR"/*/; do
    [ -d "$locale_dir" ] || continue
    locale="$(basename "$locale_dir")"
    locale_count=$((locale_count + 1))

    check_file "$locale_dir/title.txt" "$locale/title.txt" "$TITLE_MAX"
    check_file "$locale_dir/short_description.txt" "$locale/short_description.txt" "$SHORT_DESC_MAX"
    check_file "$locale_dir/full_description.txt" "$locale/full_description.txt" "$FULL_DESC_MAX" "$FULL_DESC_WARN"

    for changelog in "$locale_dir"/changelogs/*.txt; do
        [ -f "$changelog" ] || continue
        changelog_name="changelogs/$(basename "$changelog")"
        check_file "$changelog" "$locale/$changelog_name" "$CHANGELOG_MAX"
    done
done

if [ "$locale_count" -eq 0 ]; then
    echo "ERROR: No locale directories found in $METADATA_DIR"
    exit 1
fi

if [ "$checked" -eq 0 ]; then
    echo "ERROR: No metadata files found to check"
    exit 1
fi

echo ""
echo "Checked $checked files across $locale_count locales."

if [ "$failed" -gt 0 ]; then
    echo "$failed failure(s), $warned warning(s)."
    exit 1
fi

if [ "$warned" -gt 0 ]; then
    echo "All within limits. $warned warning(s)."
fi

exit 0
