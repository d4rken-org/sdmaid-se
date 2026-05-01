#!/usr/bin/env bats
# Unit tests for bump.sh. Run with: bats tools/release/bump.bats

setup() {
  BUMP_SH="${BATS_TEST_DIRNAME}/bump.sh"
  TMP_REPO="$(mktemp -d)"
  cat > "$TMP_REPO/version.properties" <<'EOF'
### Updated by tools/release/bump.sh ###
project.versioning.major=1
project.versioning.minor=7
project.versioning.patch=1
project.versioning.build=0
project.versioning.type=rc
########################################
EOF
  echo "1.7.1-rc0 10701000" > "$TMP_REPO/VERSION"
}

teardown() {
  rm -rf "$TMP_REPO"
}

bump() {
  "$BUMP_SH" --repo-root="$TMP_REPO" "$@"
}

# ----- check mode ----------------------------------------------------------

@test "check: passes on consistent state" {
  run bump --mode=check
  [ "$status" -eq 0 ]
  [[ "$output" == *"current_name=1.7.1-rc0"* ]]
  [[ "$output" == *"current_code=10701000"* ]]
}

@test "check: fails on VERSION/version.properties drift (name)" {
  echo "1.7.0-rc0 10700000" > "$TMP_REPO/VERSION"
  run bump --mode=check
  [ "$status" -ne 0 ]
  [[ "$output" == *"drift"* ]]
}

@test "check: fails on VERSION/version.properties drift (code)" {
  echo "1.7.1-rc0 99999999" > "$TMP_REPO/VERSION"
  run bump --mode=check
  [ "$status" -ne 0 ]
  [[ "$output" == *"drift"* ]]
}

@test "check: fails on duplicate key" {
  echo "project.versioning.major=2" >> "$TMP_REPO/version.properties"
  run bump --mode=check
  [ "$status" -ne 0 ]
  [[ "$output" == *"major"* ]]
  [[ "$output" == *"found 2"* ]]
}

@test "check: fails on missing key" {
  sed -i '/project\.versioning\.type=/d' "$TMP_REPO/version.properties"
  run bump --mode=check
  [ "$status" -ne 0 ]
  [[ "$output" == *"type"* ]]
  [[ "$output" == *"found 0"* ]]
}

@test "check: fails on bad type" {
  sed -i 's/^project\.versioning\.type=.*/project.versioning.type=alpha/' "$TMP_REPO/version.properties"
  run bump --mode=check
  [ "$status" -ne 0 ]
}

@test "check: fails on malformed VERSION" {
  echo "garbage" > "$TMP_REPO/VERSION"
  run bump --mode=check
  [ "$status" -ne 0 ]
  [[ "$output" == *"VERSION file does not match"* ]]
}

@test "check: fails on leading zero in version.properties" {
  sed -i 's/^project\.versioning\.build=.*/project.versioning.build=08/' "$TMP_REPO/version.properties"
  echo "1.7.1-rc8 10701080" > "$TMP_REPO/VERSION"
  run bump --mode=check
  [ "$status" -ne 0 ]
  [[ "$output" == *"leading zero"* ]]
}

# ----- check: --expected-tag -----------------------------------------------

@test "check --expected-tag: passes when tag matches" {
  run bump --mode=check --expected-tag=v1.7.1-rc0
  [ "$status" -eq 0 ]
  [[ "$output" == *"tag check OK"* ]]
}

@test "check --expected-tag: fails on build mismatch" {
  run bump --mode=check --expected-tag=v1.7.1-rc1
  [ "$status" -ne 0 ]
  [[ "$output" == *"does not match"* ]]
}

@test "check --expected-tag: fails on patch mismatch" {
  run bump --mode=check --expected-tag=v1.7.2-rc0
  [ "$status" -ne 0 ]
}

@test "check --expected-tag: fails on malformed tag (no -rc)" {
  run bump --mode=check --expected-tag=v1.7.1
  [ "$status" -ne 0 ]
  [[ "$output" == *"does not match"* ]]
}

@test "check --expected-tag: fails on malformed tag (bad type)" {
  run bump --mode=check --expected-tag=v1.7.1-foo0
  [ "$status" -ne 0 ]
}

@test "check --expected-tag: fails on malformed tag (no v prefix)" {
  run bump --mode=check --expected-tag=1.7.1-rc0
  [ "$status" -ne 0 ]
}

# ----- plan: bump kinds ----------------------------------------------------

@test "plan: build bump increments build" {
  run bump --mode=plan --bump-kind=build --version-type=keep-current
  [ "$status" -eq 0 ]
  [[ "$output" == *"new_name=1.7.1-rc1"* ]]
  [[ "$output" == *"new_code=10701010"* ]]
}

@test "plan: patch bump zeros build" {
  run bump --mode=plan --bump-kind=patch --version-type=keep-current
  [ "$status" -eq 0 ]
  [[ "$output" == *"new_name=1.7.2-rc0"* ]]
  [[ "$output" == *"new_code=10702000"* ]]
}

@test "plan: minor bump zeros patch and build" {
  run bump --mode=plan --bump-kind=minor --version-type=keep-current
  [ "$status" -eq 0 ]
  [[ "$output" == *"new_name=1.8.0-rc0"* ]]
  [[ "$output" == *"new_code=10800000"* ]]
}

@test "plan: major bump zeros minor, patch, build" {
  run bump --mode=plan --bump-kind=major --version-type=keep-current
  [ "$status" -eq 0 ]
  [[ "$output" == *"new_name=2.0.0-rc0"* ]]
  [[ "$output" == *"new_code=20000000"* ]]
}

@test "plan: version-type beta switches type and resets build" {
  run bump --mode=plan --bump-kind=build --version-type=beta
  [ "$status" -eq 0 ]
  [[ "$output" == *"new_name=1.7.1-beta1"* ]]
}

@test "plan: keep-current preserves type when type is beta" {
  sed -i 's/^project\.versioning\.type=.*/project.versioning.type=beta/' "$TMP_REPO/version.properties"
  echo "1.7.1-beta0 10701000" > "$TMP_REPO/VERSION"
  run bump --mode=plan --bump-kind=build --version-type=keep-current
  [ "$status" -eq 0 ]
  [[ "$output" == *"new_name=1.7.1-beta1"* ]]
}

@test "plan: version-type rc switches from beta back to rc0" {
  sed -i 's/^project\.versioning\.type=.*/project.versioning.type=beta/' "$TMP_REPO/version.properties"
  sed -i 's/^project\.versioning\.build=.*/project.versioning.build=3/' "$TMP_REPO/version.properties"
  echo "1.7.1-beta3 10701030" > "$TMP_REPO/VERSION"
  run bump --mode=plan --bump-kind=build --version-type=rc
  [ "$status" -eq 0 ]
  [[ "$output" == *"new_name=1.7.1-rc4"* ]]
}

# ----- plan: versionCode formula -------------------------------------------

@test "plan: versionCode for 0.0.0-rc0 is 0" {
  sed -i 's/^project\.versioning\.major=.*/project.versioning.major=0/' "$TMP_REPO/version.properties"
  sed -i 's/^project\.versioning\.minor=.*/project.versioning.minor=0/' "$TMP_REPO/version.properties"
  sed -i 's/^project\.versioning\.patch=.*/project.versioning.patch=0/' "$TMP_REPO/version.properties"
  echo "0.0.0-rc0 0" > "$TMP_REPO/VERSION"
  run bump --mode=plan --bump-kind=build --version-type=keep-current
  [ "$status" -eq 0 ]
  [[ "$output" == *"new_code=10"* ]]
}

@test "plan: versionCode sample 1.7.1-rc0 -> 10701000" {
  run bump --mode=check
  [ "$status" -eq 0 ]
  [[ "$output" == *"current_code=10701000"* ]]
}

# ----- plan: override ------------------------------------------------------

@test "plan: version-override accepts valid version" {
  run bump --mode=plan --version-override=1.8.0-rc0
  [ "$status" -eq 0 ]
  [[ "$output" == *"new_name=1.8.0-rc0"* ]]
  [[ "$output" == *"new_code=10800000"* ]]
}

@test "plan: version-override rejects build=100 (out of 1-2 digit range)" {
  run bump --mode=plan --version-override=1.7.2-rc100
  [ "$status" -ne 0 ]
  [[ "$output" == *"does not match"* ]]
}

@test "plan: version-override rejects bad type" {
  run bump --mode=plan --version-override=1.7.2-alpha0
  [ "$status" -ne 0 ]
}

@test "plan: version-override rejects leading zero" {
  run bump --mode=plan --version-override=1.07.0-rc0
  [ "$status" -ne 0 ]
  [[ "$output" == *"leading zero"* ]]
}

@test "plan: version-override rejects no-op identity" {
  run bump --mode=plan --version-override=1.7.1-rc0
  [ "$status" -ne 0 ]
  [[ "$output" == *"no-op"* ]]
}

@test "plan: version-override rejects monotonicity break" {
  run bump --mode=plan --version-override=1.7.0-rc0
  [ "$status" -ne 0 ]
  [[ "$output" == *"monotonicity"* ]]
}

# ----- plan: bounds --------------------------------------------------------

@test "plan: rejects build overflow past 99" {
  sed -i 's/^project\.versioning\.build=.*/project.versioning.build=99/' "$TMP_REPO/version.properties"
  echo "1.7.1-rc99 10701990" > "$TMP_REPO/VERSION"
  run bump --mode=plan --bump-kind=build --version-type=keep-current
  [ "$status" -ne 0 ]
  [[ "$output" == *"out of range"* ]]
}

@test "plan: rejects patch overflow past 99" {
  sed -i 's/^project\.versioning\.patch=.*/project.versioning.patch=99/' "$TMP_REPO/version.properties"
  echo "1.7.99-rc0 10799000" > "$TMP_REPO/VERSION"
  run bump --mode=plan --bump-kind=patch --version-type=keep-current
  [ "$status" -ne 0 ]
  [[ "$output" == *"out of range"* ]]
}

# ----- plan: expected-current ----------------------------------------------

@test "plan: expected-current matches passes" {
  run bump --mode=plan --bump-kind=build --version-type=keep-current --expected-current=1.7.1-rc0
  [ "$status" -eq 0 ]
}

@test "plan: expected-current mismatch fails" {
  run bump --mode=plan --bump-kind=build --version-type=keep-current --expected-current=1.0.0-rc0
  [ "$status" -ne 0 ]
  [[ "$output" == *"expected-current"* ]]
}

# ----- write mode ----------------------------------------------------------

@test "write: rewrites both files correctly (build bump)" {
  run bump --mode=write --bump-kind=build --version-type=keep-current
  [ "$status" -eq 0 ]
  grep -q '^project\.versioning\.build=1$' "$TMP_REPO/version.properties"
  [ "$(cat "$TMP_REPO/VERSION")" = "1.7.1-rc1 10701010" ]
}

@test "write: patch bump zeros build in file" {
  run bump --mode=write --bump-kind=patch --version-type=keep-current
  [ "$status" -eq 0 ]
  grep -q '^project\.versioning\.patch=2$' "$TMP_REPO/version.properties"
  grep -q '^project\.versioning\.build=0$' "$TMP_REPO/version.properties"
  [ "$(cat "$TMP_REPO/VERSION")" = "1.7.2-rc0 10702000" ]
}

@test "write: preserves comment block" {
  run bump --mode=write --bump-kind=build --version-type=keep-current
  [ "$status" -eq 0 ]
  grep -q '^### Updated by tools/release/bump.sh ###$' "$TMP_REPO/version.properties"
  grep -q '^########################################$' "$TMP_REPO/version.properties"
}

@test "write: preserves key order" {
  run bump --mode=write --bump-kind=patch --version-type=keep-current
  [ "$status" -eq 0 ]
  grep -nE '^project\.versioning\.' "$TMP_REPO/version.properties" > "$TMP_REPO/order.txt"
  run cat "$TMP_REPO/order.txt"
  [[ "${lines[0]}" == *"major="* ]]
  [[ "${lines[1]}" == *"minor="* ]]
  [[ "${lines[2]}" == *"patch="* ]]
  [[ "${lines[3]}" == *"build="* ]]
  [[ "${lines[4]}" == *"type="* ]]
}

@test "write: type switch persists to both files" {
  run bump --mode=write --bump-kind=build --version-type=beta
  [ "$status" -eq 0 ]
  grep -q '^project\.versioning\.type=beta$' "$TMP_REPO/version.properties"
  [ "$(cat "$TMP_REPO/VERSION")" = "1.7.1-beta1 10701010" ]
}

@test "write: post-write check passes" {
  run bump --mode=write --bump-kind=build --version-type=keep-current
  [ "$status" -eq 0 ]
  run bump --mode=check
  [ "$status" -eq 0 ]
  [[ "$output" == *"current_name=1.7.1-rc1"* ]]
}

@test "write: header refresh idempotent when already updated" {
  # Header already says tools/release/bump.sh (default fixture) — write should keep it
  run bump --mode=write --bump-kind=build --version-type=keep-current
  [ "$status" -eq 0 ]
  [ "$(grep -c '^### Updated by tools/release/bump.sh ###$' "$TMP_REPO/version.properties")" -eq 1 ]
}

@test "write: header refresh migrates old release.sh header" {
  sed -i 's|^### Updated by tools/release/bump.sh ###$|### Updated by release.sh ###|' "$TMP_REPO/version.properties"
  run bump --mode=write --bump-kind=build --version-type=keep-current
  [ "$status" -eq 0 ]
  grep -q '^### Updated by tools/release/bump.sh ###$' "$TMP_REPO/version.properties"
  [ "$(grep -c '### Updated by' "$TMP_REPO/version.properties")" -eq 1 ]
}

@test "write: requires --expected-current to be set" {
  # write without --expected-current should proceed (it is optional, but stale-state
  # guard fires when a mismatch occurs — no mismatch here, so it should succeed)
  run bump --mode=write --bump-kind=build --version-type=keep-current
  [ "$status" -eq 0 ]
}

@test "write: rejects stale --expected-current" {
  run bump --mode=write --bump-kind=build --version-type=keep-current --expected-current=5.0.0-rc0
  [ "$status" -ne 0 ]
  [[ "$output" == *"expected-current"* ]]
}

# ----- mode parsing --------------------------------------------------------

@test "rejects missing --mode" {
  run "$BUMP_SH" --repo-root="$TMP_REPO"
  [ "$status" -ne 0 ]
}

@test "rejects invalid --mode" {
  run "$BUMP_SH" --repo-root="$TMP_REPO" --mode=foo
  [ "$status" -ne 0 ]
}

@test "rejects unknown flag" {
  run "$BUMP_SH" --repo-root="$TMP_REPO" --mode=check --frobnicate=yes
  [ "$status" -ne 0 ]
}
