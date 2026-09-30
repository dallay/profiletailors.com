# ADR-0026: Remove Automated Skill Doctor and Registry Generator

- Status: Accepted
- Date: 2026-09-30
- Decision owners: Principal Architect
- Scope: `.agents/scripts/`, `.github/workflows/skill-doctor.yml`, `.agents/skill-registry.md`, `.agents/AGENTS.md`, `.agents/agents/comment-cleanup.md`, `.agents/agents/skill-doctor.md`
- Supersedes: None
- Superseded by: None
- Related:
  - OpenSpec: `openspec/changes/skill-knowledge-bundle-hardening/`
  - ADR-0025: superseded by this decision

## Context

ADR-0025 introduced deterministic CI checks (`skill-doctor.mjs`, `skill-comment-scan.mjs`, `spring-scrub-rg.mjs`, `regen-skill-registry.mjs`) and a sub-agent (`comment-cleanup.md`) that consumed the scanner output. The five-PR `skill-knowledge-bundle-hardening` initiative deployed them and proved they catch structural drift and contamination introduced during the initial skill cleanup.

After deployment the scripts became a maintenance tax. The contamination and broken-path checks solved a one-time problem from skills imported from other repos; the comment scanner enforced a rule the team already knows; the registry generator produced output that is no longer regenerated; and the family-specific blocking-api scanner (planned as PR 4) found 171 violations in reference files that were mostly legitimate "do-not-do-this" examples. Skills are living documentation that will keep evolving, and the scripts added friction without catching recurring drift.

## Decision drivers

- Living documentation needs freedom to change, not a permanent scanner that flags every reference to a deprecated pattern.
- Scripts written for one-time cleanup become unused overhead once the cleanup is done.
- The team already understands the comment policy; enforcement via CI does not add value beyond what code review catches.
- The five-PR initiative delivered a clean baseline. The deterministic doctor succeeded at its goal.

## Decision

Remove every deterministic skill-governance script and the workflow that runs them:

- Delete `.agents/scripts/skill-doctor.mjs`, `.agents/scripts/__tests__/skill-doctor.test.mjs`.
- Delete `.agents/scripts/skill-comment-scan.mjs`, `.agents/scripts/__tests__/skill-comment-scan.test.mjs`, `.agents/scripts/skill-comment-allowlist.json`.
- Delete `.agents/scripts/spring-scrub-rg.mjs`, `.agents/scripts/__tests__/spring-scrub-rg.test.mjs`.
- Delete `.agents/scripts/regen-skill-registry.mjs`, `.agents/scripts/__tests__/regen-skill-registry.test.mjs`.
- Delete `.github/workflows/skill-doctor.yml`.
- Delete `.agents/agents/comment-cleanup.md` (its only purpose was consuming the removed scanner's JSON output).
- Update `.agents/skill-registry.md` to mark it as hand-maintained instead of generated.
- Update `.agents/AGENTS.md` to remove the reference to `skill-comment-scan.mjs` and its allowlist.
- Update `.agents/agents/skill-doctor.md` to remove the reference to the removed scripts.
- Cancel PR 4 of the `skill-knowledge-bundle-hardening` initiative.
- Mark ADR-0025 as Superseded.

## Consequences

- `.agents/skill-registry.md` is now hand-maintained. Future renames or removals of skills must update it manually. A future automation, if any, must justify its maintenance cost.
- Skills no longer have automated CI drift detection. Reviewers and authors remain responsible for structural conformance (frontmatter, identity, metadata) and the comment policy codified in `.agents/AGENTS.md`.
- The skill-doctor sub-agent (`.agents/agents/skill-doctor.md`) keeps its LLM-based semantic review role but no longer references executable evidence sources.
- The `openspec/specs/capability-automated-skill-doctor/` capability is no longer implemented. Capabilities that list it as a hard dependency must be reconsidered.
- OpenSpec change `skill-knowledge-bundle-hardening` records the cancellation in its `state.yaml`.

## Verification

- `git grep skill-doctor.mjs` returns only references in historical `openspec/changes/archive/` and `plan/tasks/` directories.
- `ls .agents/scripts/` returns `No such file or directory`.
- `ls .github/workflows/skill-doctor.yml` returns `No such file or directory`.
- `just ci-local` runs without referencing any removed script.
