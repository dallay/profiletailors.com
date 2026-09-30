# Modern Skill Guidance

## Requirements

- Generic web guidance is subordinate to repository policy and specialized local skills.
- `modern-web-guidance` is a fallback for browser-platform details that no specialized local skill
  covers; it is not mandatory at the start of every frontend task.
- `best-practices` gives current, evidence-based review guidance and does not recommend obsolete
  security controls, unmaintained polyfill CDNs, or automatic dependency upgrades.
- Vue examples use the actual `app` package, `@profiletailors/vue-ui`, shadcn-vue, and the
  repository's feature boundaries. Pinia is for state shared beyond a component, not a default for
  local state.
- Design guidance follows `.agents/DESIGN.md`, with `impeccable` owning process and quality,
  `nothing-design` supplying the Profile Tailors visual language, and `frontend-design` providing
  generic inspiration only where project guidance is silent.
- Skills omit concrete project dependency versions and point to the relevant manifest.

## Review practice

Treat skills as living documentation and review semantic changes with the repository's normal
review process. Do not require a skill doctor, format validator, fixed scenario corpus, or dedicated
CI gate before updating or closing this guidance. The former hard dependency on an automated skill
doctor is withdrawn by [ADR-0026](../../../docs/architecture/adr/0026-remove-automated-skill-doctor.md).
