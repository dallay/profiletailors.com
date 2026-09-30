# Apply Progress: Focused Skill Content Review

## Changes Applied

- Curated Spring, Kotlin, Vue, web-security, modern web, and design skill guidance against current
  repository structure and manifests.
- Replaced stale references that presented blocking/JPA/Mockito patterns as repository defaults;
  retained broad examples only where their scope is explicitly legacy or general reference.
- Removed the stale Vue maintainability reference with invented app paths and exports.
- Updated the hand-maintained skill registry and current AI engineering documentation.
- Reconciled active OpenSpec specs with ADR-0026 so skill review does not imply automated validation
  or a dedicated CI gate.

## Verification Status

Reviewed the changed skill content and its direct references against the checked-in product,
architecture, package, and design sources. Targeted searches confirmed that active Vue examples no
longer use the old package/filter names, active web guidance does not recommend the obsolete
security header or polyfill CDN, and active Kotlin repository guidance does not use
`Dispatchers.IO` as a general rule. `git diff --check` produced no diagnostics.

No tests, skill-format scripts, or dedicated skill CI checks were run or added. The unrelated
WireMock worktree changes were left untouched.
