# Apply Progress: `release-please-title-pattern-fix`

## Overview

- **Change:** `release-please-title-pattern-fix`
- **Scope:** Restore Release Please processing by making the root title pattern parser compatible with the pinned action v5 / Release Please 17.6.0 while preserving the intended stable grouped Release Please PR title and the existing workflow, manifest, package set, components, tags, and permissions.
- **Delivery:** Single PR with a configuration-only change (1 line).

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
- Release Please 17.6.0 matcher verification: configuration-only; the pattern `chore${scope}: release${component} ${version}` matches the Release Please default pattern structure and includes all required placeholders (`${scope}`, `${component}`, `${version}`), but no live matcher command was executed pre-merge. Post-merge workflow execution will provide runtime matcher evidence.

## Troubleshooting

### Handoff and remaining work

- This is a CI/release configuration change; no product behavior or release artifacts changed.
- After this PR merges to `main`, the next push to `main` (or a manual dispatch of the `Release Please` workflow) should open a new grouped Release Please PR whose title is recognized by the pinned action v5 / Release Please 17.6.0 title parser.
- `sdd-verify` and `sdd-qa` are scoped to product/operator acceptance, so no product verification or QA evidence is expected for this change beyond the focused configuration contract above.

## References

- `openspec/changes/release-please-title-pattern-fix/proposal.md`
- `openspec/changes/release-please-title-pattern-fix/design.md`
- `openspec/changes/release-please-title-pattern-fix/specs/release-automation/spec.md`
- `openspec/changes/release-please-title-pattern-fix/tasks.md`
- `release-please-config.json`
- `.github/workflows/release-please.yml`
