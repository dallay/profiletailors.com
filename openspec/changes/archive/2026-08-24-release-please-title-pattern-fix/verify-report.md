## Verification Report

**Change**: release-please-title-pattern-fix
**Version**: N/A (CI configuration only)
**PR**: #839 — `fix(ci): restore Release Please parser compatibility`

---

### Completeness

| Metric           | Value |
|------------------|-------|
| Tasks total      | 7     |
| Tasks complete   | 7     |
| Tasks incomplete | 0     |

All tasks (RED validation, GREEN config change, REFACTOR focused verification, cleanup) are complete with zero behavior change beyond the intended configuration line.

---

### Build & Tests Execution

| Gate | Command | Result |
|------|---------|--------|
| Configuration validity | `jq empty release-please-config.json` | Passed (clean) |
| Focused placeholder contract (RED before change) | `jq 'if (.["pull-request-title-pattern"] | contains("${scope}") and contains("${component}") and contains("${version}")) and (.["group-pull-request-title-pattern"] == "chore(release): prepare releases") and (.packages | length == 4) and (.packages | keys | sort == ["apps/web/admin","apps/web/app","apps/web/marketing","server/smp"]) then "PASS" else "FAIL" end' release-please-config.json` | `FAIL` against previous pattern |
| Focused placeholder contract (GREEN after change) | same `jq` command | `PASS` |
| Components preserved | `jq '(.packages | to_entries | map(.value.component) | sort == ["admin","app","landing","smp"])' release-please-config.json` | `true` |
| Diff hygiene | `git diff --check` | Passed (clean) |
| Change scope | `git diff origin/main..HEAD --stat` | `release-please-config.json | 2 +-` (1 insertion / 1 deletion) |
| Post-merge Release Please run | [32711346079](https://github.com/dallay/profiletailors.com/actions/runs/32711346079) | Title-pattern warnings disappeared; per-package parser confirmed `chore${scope}: release${component} ${version}` |

---

### Spec → Implementation Cross-Reference

| Spec requirement | Status |
|------------------|--------|
| Per-package parser-compatible `pull-request-title-pattern` includes `${scope}`, `${${component}`, `${${version}` | Implemented (`release-please-config.json` line 6) |
| Grouped title `chore(release): prepare releases` preserved | Preserved (`release-please-config.json` line 7) |
| No workflow, manifest, package, tag, or permission changes | Confirmed (`git diff` scope limited to the one line) |

---

### Verification Verdict

**PASS** — the configuration-only change ships a parser-compatible title pattern and preserves every other Release Please setting. The post-merge run confirms the warnings disappeared.

### Operator Follow-up

PR #787 was merged without its `smp@v0.4.3` git tag or GitHub Release being created; the manifest declared `server/smp: 0.4.3` and three other components had matching tags. Release Please's `findMergedReleasePullRequests` continues to refuse opening a new grouped release PR while an untagged merged release PR is outstanding, so the orphan had to be backfilled out-of-band:

1. Created the missing git tag `smp@v0.4.3` at PR #787's merge commit `114cbbe45db15f7dcdb8fb0f0d57a7e15fe4fe8b`.
2. Created the GitHub Release `smp: v0.4.3` against `target_commitish=114cbbe4` with the Features/Bug Fixes notes advertised in the merged PR #787 body.
3. Replaced `autorelease: pending` with `autorelease: tagged` on PR #787.

The next push to `main` will retrigger the `Release Please` workflow and open the new grouped release PR.