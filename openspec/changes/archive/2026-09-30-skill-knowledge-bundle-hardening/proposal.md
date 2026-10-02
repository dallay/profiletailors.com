# Proposal: Curate Agent Skills Against Current Repository Reality

## Intent

Finish the focused semantic cleanup of the Profile Tailors skills that still contradict current
repository architecture, manifests, product surfaces, design authority, or package boundaries.
Treat skills as living documentation maintained through ordinary authoring and review.

## Scope

- Spring and Kotlin guidance: reactive WebFlux/coroutines/R2DBC baseline, MockK conventions,
  correct blocking boundaries, and manifest-owned dependency versions.
- Vue and frontend guidance: actual app package, `@profiletailors/vue-ui`, shadcn-vue usage,
  feature-owned state, and separate app/admin/marketing boundaries.
- Design and web guidance: `.agents/DESIGN.md` as visual authority, local design skills' roles,
  modern web guidance as a fallback, and removal of obsolete security or dependency advice.
- Directly affected agent documentation and active OpenSpec requirements that still describe
  removed deterministic skill gates.

## Out of Scope

- Skill-format validators, contamination scanners, registry generators, or dedicated CI workflows.
- Rewriting every upstream framework reference that is clearly labeled as legacy, migration, or
  general reference material.
- Dependency upgrades, product behavior changes, or changes to production code.

## Success Criteria

- Changed guidance agrees with current manifests, source, architecture, product files, and
  `.agents/DESIGN.md`.
- Incompatible framework examples are removed from active recommendations or clearly labeled as
  legacy/reference material.
- Skill activation and design precedence are explicit without requiring an automated gate.
- `.agents/skill-registry.md` reflects the changed discovery metadata and remains hand-maintained.
- The diff contains no new skill validation scripts, test fixtures, CI workflows, or generated
  registry machinery.
