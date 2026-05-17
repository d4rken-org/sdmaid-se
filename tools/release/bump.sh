#!/usr/bin/env bash
# Source of truth for version bumping. Used by release-prepare.yml and release-tag.yml.
# Mirrors the versionCode formula in buildSrc/src/main/java/ProjectConfigPlugin.kt.

set -euo pipefail

usage() {
  cat <<'EOF'
Usage: bump.sh --mode=<check|plan|write> [options]

Modes:
  check   Validate version.properties + VERSION are consistent. No mutation.
  plan    check + compute the new version per inputs. Print plan to stdout.
  write   plan + rewrite version.properties and VERSION, verify post-condition.

Options (for plan/write):
  --bump-kind=build|patch|minor|major   (default: build)
  --version-type=keep-current|rc|beta   (default: keep-current)
  --version-override=<M.m.p-(rc|beta)n> (overrides bump-kind/version-type)
  --expected-current=<M.m.p-(rc|beta)n> (fail if current version differs)
  --repo-root=<path>                    (default: current working directory)

Options (for check):
  --expected-tag=<vM.m.p-(rc|beta)n>   (assert tag matches current version)

Output (plan/write modes):
  Human-readable report on stderr; KEY=value pairs on stdout for parsing:
    current_name=...
    current_code=...
    new_name=...
    new_code=...
EOF
}

die() {
  echo "ERROR: $*" >&2
  exit 1
}

log() {
  echo "$*" >&2
}

# ----- argument parsing ----------------------------------------------------

mode=""
bump_kind="build"
version_type="keep-current"
version_override=""
expected_current=""
expected_tag=""
repo_root=""

for arg in "$@"; do
  case "$arg" in
    --mode=*)              mode="${arg#*=}" ;;
    --bump-kind=*)         bump_kind="${arg#*=}" ;;
    --version-type=*)      version_type="${arg#*=}" ;;
    --version-override=*)  version_override="${arg#*=}" ;;
    --expected-current=*)  expected_current="${arg#*=}" ;;
    --expected-tag=*)      expected_tag="${arg#*=}" ;;
    --repo-root=*)         repo_root="${arg#*=}" ;;
    -h|--help)             usage; exit 0 ;;
    *)                     die "unknown argument: $arg" ;;
  esac
done

case "$mode" in
  check|plan|write) ;;
  "") usage >&2; exit 2 ;;
  *)  die "invalid --mode: $mode (expected check|plan|write)" ;;
esac

case "$bump_kind" in
  build|patch|minor|major) ;;
  *) die "invalid --bump-kind: $bump_kind" ;;
esac

case "$version_type" in
  keep-current|rc|beta) ;;
  *) die "invalid --version-type: $version_type" ;;
esac

if [[ -z "$repo_root" ]]; then
  repo_root="$(pwd)"
fi

if [[ ! -d "$repo_root" ]]; then
  die "repo root does not exist: $repo_root"
fi

props_file="$repo_root/version.properties"
version_file="$repo_root/VERSION"

# ----- helpers -------------------------------------------------------------

# Reject leading zeros on numeric components (allow plain "0").
no_leading_zero() {
  local n="$1" label="$2"
  [[ "$n" =~ ^0$ || "$n" =~ ^[1-9][0-9]*$ ]] || die "$label has leading zero or invalid digits: '$n'"
}

bound_0_99() {
  local n="$1" label="$2"
  [[ "$n" -ge 0 && "$n" -le 99 ]] || die "$label out of range 0..99: $n"
}

parse_name() {
  # Sets globals: pn_major, pn_minor, pn_patch, pn_type, pn_build
  local name="$1" label="$2"
  if [[ ! "$name" =~ ^([0-9]{1,2})\.([0-9]{1,2})\.([0-9]{1,2})-(rc|beta)([0-9]{1,2})$ ]]; then
    die "$label does not match <M.m.p-(rc|beta)n>: '$name'"
  fi
  pn_major="${BASH_REMATCH[1]}"
  pn_minor="${BASH_REMATCH[2]}"
  pn_patch="${BASH_REMATCH[3]}"
  pn_type="${BASH_REMATCH[4]}"
  pn_build="${BASH_REMATCH[5]}"
  no_leading_zero "$pn_major" "$label major"
  no_leading_zero "$pn_minor" "$label minor"
  no_leading_zero "$pn_patch" "$label patch"
  no_leading_zero "$pn_build" "$label build"
}

# Compute versionCode the same way ProjectConfigPlugin.kt does.
compute_code() {
  local major="$1" minor="$2" patch="$3" build="$4"
  echo $(( major * 10000000 + minor * 100000 + patch * 1000 + build * 10 ))
}

format_name() {
  local major="$1" minor="$2" patch="$3" type="$4" build="$5"
  echo "${major}.${minor}.${patch}-${type}${build}"
}

# Count exact matches of an anchored regex in a file.
count_matches() {
  local pattern="$1" file="$2"
  grep -cE "$pattern" "$file" || true
}

# ----- read & validate current state ---------------------------------------

[[ -f "$props_file" ]] || die "missing version.properties at $props_file"
[[ -f "$version_file" ]] || die "missing VERSION file at $version_file"

# Each of the five keys must appear exactly once on its own line.
expect_one() {
  local key_pattern="$1" label="$2" file="$3"
  local n
  n=$(count_matches "$key_pattern" "$file")
  [[ "$n" == "1" ]] || die "$label: expected exactly 1 line in $file matching '$key_pattern', found $n"
}

expect_one '^project\.versioning\.major=[0-9]+$' "major" "$props_file"
expect_one '^project\.versioning\.minor=[0-9]+$' "minor" "$props_file"
expect_one '^project\.versioning\.patch=[0-9]+$' "patch" "$props_file"
expect_one '^project\.versioning\.build=[0-9]+$' "build" "$props_file"
expect_one '^project\.versioning\.type=(rc|beta)$' "type" "$props_file"

cur_major=$(grep -E '^project\.versioning\.major=' "$props_file" | cut -d= -f2)
cur_minor=$(grep -E '^project\.versioning\.minor=' "$props_file" | cut -d= -f2)
cur_patch=$(grep -E '^project\.versioning\.patch=' "$props_file" | cut -d= -f2)
cur_build=$(grep -E '^project\.versioning\.build=' "$props_file" | cut -d= -f2)
cur_type=$(grep -E '^project\.versioning\.type=' "$props_file" | cut -d= -f2)

no_leading_zero "$cur_major" "current major"
no_leading_zero "$cur_minor" "current minor"
no_leading_zero "$cur_patch" "current patch"
no_leading_zero "$cur_build" "current build"
bound_0_99 "$cur_major" "current major"
bound_0_99 "$cur_minor" "current minor"
bound_0_99 "$cur_patch" "current patch"
bound_0_99 "$cur_build" "current build"

cur_name=$(format_name "$cur_major" "$cur_minor" "$cur_patch" "$cur_type" "$cur_build")
cur_code=$(compute_code "$cur_major" "$cur_minor" "$cur_patch" "$cur_build")

# VERSION file: exactly one line, "<name> <code>".
version_line_count=$(wc -l < "$version_file" | tr -d ' ')
[[ "$version_line_count" -ge 1 && "$version_line_count" -le 1 ]] \
  || die "VERSION file must have exactly one line, found $version_line_count"

version_content=$(head -n1 "$version_file")
if [[ ! "$version_content" =~ ^([^[:space:]]+)\ ([0-9]+)$ ]]; then
  die "VERSION file does not match '<name> <code>': '$version_content'"
fi
file_name="${BASH_REMATCH[1]}"
file_code="${BASH_REMATCH[2]}"

# Drift check: VERSION must agree with version.properties.
[[ "$file_name" == "$cur_name" ]] \
  || die "drift: VERSION name '$file_name' != version.properties name '$cur_name'"
[[ "$file_code" == "$cur_code" ]] \
  || die "drift: VERSION code '$file_code' != computed code '$cur_code'"

if [[ -n "$expected_current" ]]; then
  [[ "$expected_current" == "$cur_name" ]] \
    || die "expected-current '$expected_current' != actual current '$cur_name'"
fi

log "current: $cur_name (code $cur_code)"

if [[ "$mode" == "check" ]]; then
  # Optional tag validation: assert the given tag matches the version files.
  if [[ -n "$expected_tag" ]]; then
    if [[ ! "$expected_tag" =~ ^v[0-9]{1,2}\.[0-9]{1,2}\.[0-9]{1,2}-(rc|beta)[0-9]{1,2}$ ]]; then
      die "--expected-tag '$expected_tag' does not match <vM.m.p-(rc|beta)n>"
    fi
    tag_name="${expected_tag#v}"
    [[ "$tag_name" == "$cur_name" ]] \
      || die "tag '$expected_tag' does not match version.properties name 'v${cur_name}'"
    log "tag check OK: $expected_tag matches current version $cur_name"
  fi
  echo "current_name=$cur_name"
  echo "current_code=$cur_code"
  exit 0
fi

# ----- compute new version -------------------------------------------------

if [[ -n "$version_override" ]]; then
  parse_name "$version_override" "version-override"
  new_major="$pn_major"
  new_minor="$pn_minor"
  new_patch="$pn_patch"
  new_type="$pn_type"
  new_build="$pn_build"
else
  new_major="$cur_major"
  new_minor="$cur_minor"
  new_patch="$cur_patch"
  new_build="$cur_build"

  case "$bump_kind" in
    build) new_build=$((cur_build + 1)) ;;
    patch) new_patch=$((cur_patch + 1)); new_build=0 ;;
    minor) new_minor=$((cur_minor + 1)); new_patch=0; new_build=0 ;;
    major) new_major=$((cur_major + 1)); new_minor=0; new_patch=0; new_build=0 ;;
  esac

  case "$version_type" in
    keep-current) new_type="$cur_type" ;;
    rc|beta)      new_type="$version_type" ;;
  esac
fi

bound_0_99 "$new_major" "new major"
bound_0_99 "$new_minor" "new minor"
bound_0_99 "$new_patch" "new patch"
bound_0_99 "$new_build" "new build"

case "$new_type" in
  rc|beta) ;;
  *) die "computed type must be rc or beta, got '$new_type'" ;;
esac

new_name=$(format_name "$new_major" "$new_minor" "$new_patch" "$new_type" "$new_build")
new_code=$(compute_code "$new_major" "$new_minor" "$new_patch" "$new_build")

# Sanity: Android versionCode is Int (max 2_147_483_647).
[[ "$new_code" -le 2147483647 ]] || die "new versionCode exceeds Int.MAX_VALUE: $new_code"

[[ "$new_name" != "$cur_name" ]] || die "no-op: new name equals current ($new_name)"
[[ "$new_code" -gt "$cur_code" ]] \
  || die "monotonicity: new code ($new_code) is not greater than current ($cur_code)"

log "new:     $new_name (code $new_code)"

# ----- output --------------------------------------------------------------

echo "current_name=$cur_name"
echo "current_code=$cur_code"
echo "new_name=$new_name"
echo "new_code=$new_code"

if [[ "$mode" == "plan" ]]; then
  log ""
  log "--- diff (plan) ---"
  log "  version.properties:"
  log "    major: $cur_major  ->  $new_major"
  log "    minor: $cur_minor  ->  $new_minor"
  log "    patch: $cur_patch  ->  $new_patch"
  log "    build: $cur_build  ->  $new_build"
  log "    type:  $cur_type  ->  $new_type"
  log "  VERSION:"
  log "    -$cur_name $cur_code"
  log "    +$new_name $new_code"
  exit 0
fi

# ----- write mode ----------------------------------------------------------

sed_inplace() {
  if [[ "$(uname)" == "Darwin" ]]; then
    sed -i '' -E "$@"
  else
    sed -i -E "$@"
  fi
}

sed_inplace "s#^(project\.versioning\.major=)[0-9]+\$#\1${new_major}#" "$props_file"
sed_inplace "s#^(project\.versioning\.minor=)[0-9]+\$#\1${new_minor}#" "$props_file"
sed_inplace "s#^(project\.versioning\.patch=)[0-9]+\$#\1${new_patch}#" "$props_file"
sed_inplace "s#^(project\.versioning\.build=)[0-9]+\$#\1${new_build}#" "$props_file"
sed_inplace "s#^(project\.versioning\.type=)(rc|beta)\$#\1${new_type}#" "$props_file"

# Refresh header comment (handles both old and already-migrated header).
sed_inplace "s@^### Updated by release\.sh ###\$@### Updated by tools/release/bump.sh ###@" "$props_file"

# Rewrite VERSION (single-line file).
printf '%s %s\n' "$new_name" "$new_code" > "$version_file"

# ----- post-condition ------------------------------------------------------

post_major=$(grep -E '^project\.versioning\.major=' "$props_file" | cut -d= -f2)
post_minor=$(grep -E '^project\.versioning\.minor=' "$props_file" | cut -d= -f2)
post_patch=$(grep -E '^project\.versioning\.patch=' "$props_file" | cut -d= -f2)
post_build=$(grep -E '^project\.versioning\.build=' "$props_file" | cut -d= -f2)
post_type=$(grep -E '^project\.versioning\.type=' "$props_file" | cut -d= -f2)

[[ "$post_major" == "$new_major" ]] || die "post-condition: major did not write ($post_major != $new_major)"
[[ "$post_minor" == "$new_minor" ]] || die "post-condition: minor did not write ($post_minor != $new_minor)"
[[ "$post_patch" == "$new_patch" ]] || die "post-condition: patch did not write ($post_patch != $new_patch)"
[[ "$post_build" == "$new_build" ]] || die "post-condition: build did not write ($post_build != $new_build)"
[[ "$post_type"  == "$new_type"  ]] || die "post-condition: type did not write ($post_type != $new_type)"

expect_one '^project\.versioning\.major=[0-9]+$' "post major" "$props_file"
expect_one '^project\.versioning\.minor=[0-9]+$' "post minor" "$props_file"
expect_one '^project\.versioning\.patch=[0-9]+$' "post patch" "$props_file"
expect_one '^project\.versioning\.build=[0-9]+$' "post build" "$props_file"
expect_one '^project\.versioning\.type=(rc|beta)$' "post type" "$props_file"

post_version_content=$(head -n1 "$version_file")
[[ "$post_version_content" == "$new_name $new_code" ]] \
  || die "post-condition: VERSION did not write ('$post_version_content' != '$new_name $new_code')"

log "wrote: $new_name (code $new_code)"
