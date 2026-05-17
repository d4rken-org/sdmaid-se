# Releasing SD Maid SE

## How to cut a release

1. Go to **Actions → Release prepare → Run workflow** (top right).
2. Pick:
   - **bump_kind**: `build` (patch-level follow-up), `patch` (bug fix), `minor` (new feature), `major` (breaking change).
   - **version_type**: `keep-current` (usual), `rc` or `beta` to switch the release track.
   - **dry_run**: `true` first to preview the plan; `false` to commit, tag, and push.
3. With `dry_run=false`: Job 1 computes and validates. Job 2 commits the bump, creates an annotated tag, and pushes both atomically to `main`. The App-token push fires `release-tag.yml` automatically.
4. **Cancel window**: Between Job 1 and Job 2 you have a few seconds to cancel from the Actions run page if the summary looks wrong.

## Version scheme

Defined in `tools/release/bump.sh`. All fields must be in `0..99` (the formula overflows at ≥100):

```
versionName = <major>.<minor>.<patch>-<type><build>   (e.g. 1.7.1-rc0)
versionCode = major*10_000_000 + minor*100_000 + patch*1_000 + build*10
```

Files updated by every release:
- `version.properties` — read by Gradle at build time.
- `VERSION` — plain text for third-party consumers (e.g. F-Droid).

## Tag → channel mapping

| Tag suffix | FOSS build | FOSS GitHub release | Gplay lane | Play Store track | Rollout |
|---|---|---|---|---|---|
| `-beta<n>` | `assembleFossBeta` | **pre-release** | `lane :beta` | `beta` | 10% |
| `-rc<n>` | `assembleFossRelease` | **release** | `lane :production` | **`beta`** | 10% |

> **Surprising convention:** `lane :production` in the Fastfile uploads to the **beta** track in Play (not `production`). Manual promotion to GA is done via Play Console. `lane :listing_only` is the only path that touches the `production` track and only for metadata refreshes.

## Validation guards

- **`check-release-tooling`** in `code-checks.yml`: runs `shellcheck`, `bats`, and a live `--mode=check` on every PR.
- **`validate-tag`** in `release-tag.yml`: on every tag push, runs `bump.sh --mode=check --expected-tag=<tag>`. Rejects malformed tags and tags that don't match `version.properties`.
- **Stale-state guard**: Job 2 of `release-prepare.yml` calls `--mode=check --expected-current=<name-from-job-1>` before writing. Fails if `main` advanced between jobs.

## Emergency release (Actions unavailable)

The branch ruleset bypass is configured for the **`d4rken-org-releaser` GitHub App only** — a human direct-push will be rejected. If CI is down:

1. An org admin must temporarily add the relevant human account as a bypass actor on the main-branch ruleset.
2. Run locally: `bash tools/release/bump.sh --mode=write --bump-kind=<kind>`, then commit, tag, and push manually.
3. Remove the human bypass actor immediately after.

## Pre-merge admin (first-time setup per repo)

Required before `release-prepare.yml` can push:

1. Install `d4rken-org-releaser` GitHub App on this repo.
2. Ensure org secrets `RELEASE_APP_CLIENT_ID` and `RELEASE_APP_PRIVATE_KEY` are scoped to this repo.
3. Add the App as **bypass actor** in:
   - The `main` branch ruleset (PR + status-check requirements).
   - Any tag ruleset restricting `v*` creation.
