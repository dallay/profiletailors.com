# Apply Progress: `release-please-title-pattern-fix`

## Overview

- **Change:** `release-please-title-pattern-fix`
- **Scope:** Restore Release Please processing by making the root title pattern parser compatible with Release Please 17.6.0 while preserving the intended stable grouped Release Please PR title and the existing workflow, manifest, package set, components, tags, and permissions.
- **Delivery:** Single PR with a configuration-only change (1 line) merged as #839.

## Changes

### Completed in this apply slice

- `release-please-config.json` root `pull-request-title-pattern` changed from `chore: release${component} ${version}` to `chore${scope}: release${component} ${version}` so the existing grouped title `chore(release): prepare releases` is recognized by Release Please 17.6.0.
- `group-pull-request-title-pattern`, package components (`admin`, `app`, `landing`, `smp`), tags, the manifest, and the workflow remain untouched.

## Usage

### Verification evidence

- RED — focused `jq` contract before the change:
  - `jq 'if (.["pull-request-title-pattern"] | contains("${scope}") and contains("${component}") and contains("${version}")) and (.["group-pull-request-title-pattern"] == "chore(release): prepare releases") and (.packages | length == 4) and (.packages | keys | sort == ["apps/web/admin","apps/web/app","apps/web/marketing","server/smp"]) then "PASS" else "FAIL" end' release-please-config.json` — `FAIL` against the previous pattern (missing `${scope}`).
- GREEN — same contract after the change: `"PASS"`.
- `jq empty release-please-config.json` — clean.
- `jq '(.packages | to_entries | map(.value.component) | sort == ["admin","app","landing","smp"])' release-please-config.json` — `true`; components preserved.
- `git diff --check` — clean.
- Final diff: `release-please-config.json | 2 +-` (1 insertion / 1 deletion).
- `git diff origin/main..HEAD --stat` — only the intended `release-please-config.json` line and the OpenSpec change directory; no package versions, manifest versions, workflow permissions, or tag definitions changed.
- Post-merge re-run [32711346079](https://github.com/dallay/profiletailors.com/actions/runs/32711346079) confirmed the title-pattern warnings disappeared and the per-package parser uses `chore${scope}: release${component} ${version}`.

## Troubleshooting

### Orphan release backfill (post-#839)

After the parser-compatibility fix landed, the next Release Please run (32711346079) found that the previously merged grouped release PR #787 (`chore(release): prepare releases`, merge commit `114cbbe4`) still carried the `autorelease: pending` label and that the `smp@v0.4.3` git tag and GitHub Release had never been created. The manifest already declared `server/smp: 0.4.3` and three other components had matching tags, but `server/smp` was orphaned. Release Please intentionally refuses to open a new grouped release PR while an untagged merged release PR is outstanding, so this blocked the next cycle.

Operator recovery executed out-of-band (no commit):

1. Created the missing git tag `smp@v0.4.3` pointing at the PR #787 merge commit `114cbbe45db15f7dcdb8fb0f0d57a7e15fe4fe8b` via `POST /repos/{owner}/{repo}/git/refs`.
2. Created the GitHub Release `smp: v0.4.3` against `target_commitish=114cbbe4` with the same Features/Bug Fixes notes that the merged PR #787 body advertised (Features: #798, #767; Bug Fixes: #792).
3. Removed the `autorelease: pending` label from PR #787 and added `autorelease: tagged` so Release Please treats the prior release as completed.

No additional commits to `main` were produced by the recovery itself. The next push to `main` will retrigger the `Release Please` workflow and open the new grouped release PR.

### Handoff and remaining work

- This is a CI/release configuration change; no product behavior or release artifacts changed.
- The actual fix shipped via PR #839 and the orphan-state recovery is documented above for the next operator session.
- `sdd-verify` and `sdd-qa` are scoped to product/operator acceptance, so no product verification or QA evidence is expected for this change beyond the focused configuration contract above and the post-merge run output.

## References

- `openspec/changes/release-please-title-pattern-fix/proposal.md`
- `openspec/changes/release-please-title-pattern-fix/design.md`
- `openspec/changes/release-please-title-pattern-fix/specs/release-automation/spec.md`
- `openspec/changes/release-please-title-pattern-fix/tasks.md`
- PR #839 — `fix(ci): restore Release Please parser compatibility`
- PR #787 — previous grouped release PR whose `smp@v0.4.3` was missing
- Release Please run [32711346079](https://github.com/dallay/profiletailors.com/actions/runs/32711346079)
- `release-please-config.json`
- `.github/workflows/release-please.yml`