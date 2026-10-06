# Skill Doctor Capability — Withdrawn

## Status

This capability is withdrawn by [ADR-0026](../../../../docs/architecture/adr/0026-remove-automated-skill-doctor.md).
The repository does not require a deterministic skill validator, format scanner, registry
generator, dedicated workflow, or CI gate for skill content.

## Current practice

- Authors maintain `.agents/skill-registry.md` during ordinary review when skills are added,
  renamed, or removed.
- Reviewers compare changed skill guidance with `.agents/AGENTS.md`, `.agents/DESIGN.md`, the
  applicable `PRODUCT.md`, current repository configuration, and relevant ADRs.
- `.agents/agents/skill-doctor.md` is an optional semantic reviewer. It does not consume validator
  output and does not claim that a workflow or remote branch-protection rule exists.

## Historical material

Requirements for `skill-doctor.mjs`, `skill-comment-scan.mjs`, generated registry checks, and
`.github/workflows/skill-doctor.yml` are superseded by ADR-0026. Archived OpenSpec changes retain
their historical record and are not implementation instructions.
