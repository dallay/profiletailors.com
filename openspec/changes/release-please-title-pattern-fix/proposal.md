# Proposal: Release Please Title Pattern Compatibility

## Intent

Release Please 17.6.0 is not creating the next grouped release PR because it re-parses the existing title `chore(release): prepare releases` with the per-package `pull-request-title-pattern`, which lacks the placeholders required to recognize a merged release PR. Make the smallest configuration change that restores release processing without changing the one-PR grouped workflow or its stable title.

## Scope

### In Scope
- Update the root `pull-request-title-pattern` in `release-please-config.json` to be parser-compatible with Release Please 17.6.0.
- Preserve `group-pull-request-title-pattern: chore(release): prepare releases` and manifest package/version behavior.
- Validate JSON and the resulting configuration diff.

### Out of Scope
- Workflow triggers, GitHub App permissions, action pin changes, or release automation redesign.
- Changing the stable grouped PR title, splitting release PRs, or changing package versions/tags.
- Runtime product capabilities or OpenSpec product specifications.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- None. This is a CI/release configuration compatibility fix, not a product capability change.

## Approach

Change only the root per-package title pattern to include Release Please's `${scope}`, `${component}`, and `${version}` placeholders, while retaining the grouped title as the user-facing title. Release Please can then parse historical and future release PR titles during merge processing; package versions remain represented by the manifest and release PR body. Verify the JSON, inspect the focused diff, and confirm the placeholder contract against the pinned Release Please 17.6.0 implementation.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `release-please-config.json` | Modified | Make the per-package title parser compatible with Release Please 17.6.0. |
| `.github/workflows/release-please.yml` | Verified | No workflow or credential changes required. |
| `.release-please-manifest.json` | Verified | Package baselines remain unchanged. |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Generated per-package title format changes while grouped title remains stable. | Low | Preserve the grouped pattern and verify both patterns against Release Please parsing behavior. |
| Existing historical release PRs still do not match. | Low | Use the supported placeholders and validate the known rejected title path before relying on the next `main` push. |

## Rollback Plan

Revert the single `pull-request-title-pattern` change in `release-please-config.json`. The grouped title and manifest versions remain independently preserved, so rollback does not alter package releases or tags.

## Dependencies

- Release Please action pinned to v5.0.0, executing Release Please 17.6.0.

## Success Criteria

- [ ] Release configuration contains a parser-compatible root title pattern with `${scope}`, `${component}`, and `${version}`.
- [ ] `chore(release): prepare releases` remains the grouped PR title.
- [ ] JSON validation and `git diff --check` pass.
- [ ] The next post-merge Release Please run recognizes the existing release baseline and can create or update the grouped PR.
