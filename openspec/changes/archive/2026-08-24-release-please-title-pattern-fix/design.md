# Design: Release Please Title Pattern Compatibility

## Technical Approach

Make one configuration-only change in `release-please-config.json`: replace the root per-package `pull-request-title-pattern` with Release Please's parser-compatible form, `chore${scope}: release${component} ${version}`. Keep `group-pull-request-title-pattern` unchanged at `chore(release): prepare releases`, so Release Please continues producing one grouped release PR with the stable user-facing title. The existing manifest, package-specific components, versions, tags, workflow trigger, and pinned action remain untouched.

Release Please 17.6.0's `generateMatchPattern` contract expects `${scope}`, `${component}`, and `${version}` in a custom pattern. It expands those placeholders into the optional branch scope, package component, and semantic version match groups. The proposed pattern mirrors the pinned implementation's default pattern and therefore supports parsing both historical and future release PR titles without changing the grouped title.

## Architecture Decisions

| Decision | Choice | Alternatives / rationale |
|---|---|---|
| Pattern shape | Use `chore${scope}: release${component} ${version}` | Keeping the current pattern omits all parser placeholders and prevents reliable merge recognition; inventing a custom syntax adds unnecessary compatibility risk. |
| Grouped PR title | Preserve `chore(release): prepare releases` | Changing it would alter the stable review/automation surface and is outside the defect. |
| Change boundary | Modify only `release-please-config.json` | Workflow, manifest, package versions, tags, permissions, and action pin are unrelated to the parser defect and should not be broadened. |

## Data Flow

```text
push to main
    │
    ▼
release-please.yml ──▶ release-please-action v5
                            │
                            ▼
                  release-please-config.json
                  per-package parser pattern
                            │
                            ├── parses component release PRs
                            └── groups output using stable title
                                  chore(release): prepare releases
```

The manifest continues to provide current versions. Package `component` values continue to drive component and tag behavior; the title pattern only supplies parser-compatible matching tokens.

## File Changes

| File | Action | Description |
|---|---|---|
| `release-please-config.json` | Modify | Add `${scope}`, `${component}`, and `${version}` to the root per-package title pattern; retain the grouped title and all package settings. |

## Interfaces / Contracts

Configuration contract:

```json
{
  "pull-request-title-pattern": "chore${scope}: release${component} ${version}",
  "group-pull-request-title-pattern": "chore(release): prepare releases"
}
```

Release Please expands `${scope}` to an optional parenthesized branch scope, `${component}` to the configured component representation, and `${version}` to the release version matcher. No application or API interface changes are introduced.

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Configuration | JSON syntax and exact pattern values | Run `python3 -m json.tool release-please-config.json` and inspect parsed keys. |
| Focused regression | Minimality and preserved grouped workflow | Review the focused Git diff; verify only the root pattern changes and the grouped title remains byte-for-byte stable. |
| Compatibility evidence | Placeholder contract | Compare the selected pattern with Release Please 17.6.0 `generateMatchPattern` behavior; no application unit, BDD, or E2E test is applicable. |

## Migration / Rollout

No migration required. Merge the configuration change normally; the next push to `main` causes the pinned Release Please action to use the corrected parser pattern. Existing release PR title and manifest/version behavior remain compatible.

## Open Questions

- None.
