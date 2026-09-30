# Plan — Skill Knowledge Bundle Hardening

## Overview

Explicit SDD — complete the active `skill-knowledge-bundle-hardening` change across its five slices, preserving archived history and treating the current repository as authoritative.

Status: In progress — SDD-005 remains unchecked. Removal of the deterministic scripts and CI workflow is complete under ADR-0026. PR 4 is cancelled; PR 5 is pending.

## Changes

- [x] SDD-001 Confirm and document the PR 1 baseline: flat topology, manifests, OpenSpec, and current gates.
- [x] SDD-002 PR 2: recursively harden all Spring bundles, rewrite incompatible guidance, and validate real bundles in CI. Evidence recorded for this slice: recursive Node scanner, scoped negative-guidance regression fixtures, active Spring asset cleanup, current Spring AI MCP patterns, CI invocation, focused tests, manual active-token scan, and real-root scan pass; ready for verification.
- [x] SDD-003 PR 3: align Vue, shadcn-vue, `@profiletailors/vue-ui`, design, web security, modern-web-guidance, and Kotlin. Evidence recorded for this slice: vue skill updated `@profiletailors/vue-ui` and `app` filter; best-practices scrubbed X-XSS-Protection, polyfill.io, npm/yarn, JSON.parse/stringify; modern-web-guidance demoted from MANDATORY to fallback; kotlin coroutines rule clarified; nothing-design activates from DESIGN.md not explicit invocation; frontend-design defers to DESIGN.md; playwright security test fixed. All 16 script tests passed, spring-scrub-rg was clean, skill-doctor reported 66/66 PASS, comment-scan was clean, registry was regenerated, and git diff --check was clean.
- [x] ~~SDD-004 PR 4: turn Skill Doctor into a recursive validator of bundles, paths, metadata, families, and versions.~~ Cancelled under ADR-0026; scripts removed instead of extended.
- [ ] SDD-005 PR 5: add semantic scenarios, run relevant gates, regenerate the registry, and perform an independent audit.
- [x] SDD-006 Remove the deterministic scripts and CI workflow; ADR-0026 records the cancellation.

## Usage

### Acceptance criteria

- Active sources describe the flat `.agents/skills/<skill-id>/SKILL.md` topology.
- Canonical specifications express current invariants rather than migration snapshots.
- Active Spring guidance uses Kotlin, coroutines, WebFlux, R2DBC, reactive security, and MockK.
- Frontend guidance uses `app`, `@profiletailors/vue-ui`, and documents shadcn-vue correctly.
- `DESIGN.md` takes precedence over generic skills and automatically activates Nothing-inspired visuals where appropriate.
- Skills are validated through code review; the CI gate scripts have been permanently removed.
- Semantic scenarios and relevant gates pass; any check not executed is explicitly reported.

### Evidence

Record evidence as each slice is completed. Do not declare success based only on the absence of tokens or isolated unit tests. Earlier script results above are historical evidence; the scripts and their CI workflow have since been removed.

## Troubleshooting

- If guidance still requires the removed deterministic scripts or CI workflow, align it with the code review process in ADR-0026.
- If the summary conflicts with the task checklist or initiative state, keep the initiative in progress while SDD-005 is unchecked; PR 4 is cancelled and PR 5 is pending.
- If a relevant check cannot run, report it explicitly and leave its verification outstanding.

## References

- [Initiative state](../../openspec/changes/skill-knowledge-bundle-hardening/state.yaml)
- [ADR-0026: Remove Automated Skill Doctor and Registry Generator](../../docs/architecture/adr/0026-remove-automated-skill-doctor.md)
- [Skill registry](../../.agents/skill-registry.md)
