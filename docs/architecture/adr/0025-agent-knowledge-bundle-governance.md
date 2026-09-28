# ADR-0025: Agent Knowledge Bundle Governance

- Status: Accepted
- Date: 2026-09-28
- Decision owners: Principal Architect
- Scope: `.agents/skills/`, `.agents/AGENTS.md`, `.agents/scripts/`, `.agents/skill-registry.md`, and related CI checks
- Supersedes: None
- Superseded by: None
- Related:
  - OpenSpec: `openspec/changes/skill-and-knowledge-bundle-remediation/`
  - Registry: `.agents/skill-registry.md`

## Context

The repository's agent knowledge bundle is executable project guidance. Drift in skill identity,
frontmatter, internal references, comments, or generated indexes can cause agents to select the
wrong guidance or follow instructions that no longer describe this repository. The bundle also needs
one stable discovery model so synchronized agent targets do not reintroduce a removed hierarchy.

## Decision

The canonical taxonomy is flat: every active skill is a direct child of `.agents/skills/` and owns a
`SKILL.md`. Supporting references and assets may remain below that skill, but disciplinary taxonomy
folders are not used. The `SKILL.md` frontmatter uses the canonical fields `name`, `description`,
and `metadata.category`, `metadata.family`, `metadata.source`, and `metadata.version`. The
frontmatter `name` matches its direct skill folder.

`.agents/skill-registry.md` is generated only by
`.agents/scripts/regen-skill-registry.mjs` from the current top-level
`.agents/skills/*/SKILL.md` files. The generator sorts entries, emits the metadata used for
selection, and supports a drift-only check for CI. Manual edits to the registry are not part of the
source of truth.

The deterministic doctor validates skill frontmatter, folder identity, metadata values, internal
paths, and known contamination. The comment scanner enforces the repository's post-processing
comment policy with its explicit allowlist. Pull requests affecting the knowledge bundle run the
doctor, scanner, focused regression tests, and registry drift check. These repository gates are
blocking when they fail; semantic cross-skill review remains a separate agent review.

The workflow and local checks provide repository evidence only. GitHub branch-protection settings,
required-check configuration, and whether a remote run was accepted are not observable from this
repository and must not be represented as proven by local files or local command results.

## Consequences

- Skill authors change source skills and regenerate the registry rather than editing generated output.
- New skills must use the flat taxonomy and canonical frontmatter before they can pass the doctor.
- CI catches deterministic drift before merge, while semantic contradictions still require review.
- The registry and checks remain dependency-free Node scripts that can run in local development and
  GitHub Actions.
- Remote branch protection remains an operational control outside this ADR's repository evidence.

## Verification

Run the focused Node test suite, the doctor, the comment scanner, and the registry generator's
`--check` mode. Report local results separately from GitHub Actions and branch-protection evidence.
