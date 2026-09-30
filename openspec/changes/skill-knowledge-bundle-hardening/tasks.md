# Tasks: Skill Knowledge Bundle Hardening

## PR 1: Knowledge Graph Integrity

- [x] Inventory legacy pre-flatten skill paths in active sources.
- [x] Update active instructions, skills, ADR links, specifications, and bundle-consumer configuration.
- [x] Rewrite ADR-0002 so domain port ownership is stated once without contradictory wording.
- [x] Normalize Spring skills to `category: backend-platform` and `family: spring-boot`.
- [x] Regenerate the skill registry and run current deterministic skill tests.
- [x] Add this active change to trace the complete five-PR initiative.

## PR 2: Spring Semantic Hardening

- [x] Audit every active asset below `spring-boot` and `spring-boot-*` recursively.
- [x] Rewrite or remove active servlet, blocking persistence, Mockito, Lombok, and Java guidance.
- [x] ~~Replace `spring-scrub-rg.mjs` with a recursive validator that accepts a real skill root.~~ Cancelled by ADR-0026.
- [x] Add flattened-layout regression fixtures, including a nested `RestTemplate` failure.

## PR 3: Frontend and UI Truth

- [ ] Rewrite Vue guidance around `app`, `@profiletailors/vue-ui`, and feature-owned state.
- [ ] Rebuild `best-practices` as subordinate, evidence-based modern guidance.
- [ ] Make modern web guidance a fallback when specialized local skills own the request.
- [ ] Encode `DESIGN.md` → `impeccable` → `nothing-design` → `frontend-design` precedence.
- [ ] Apply the related Kotlin and TypeScript wording corrections.

## PR 5: Semantic Acceptance

- [ ] Add backend scenarios for domain ports, WebFlux security, R2DBC, and real service markers.
- [ ] Add frontend scenarios for shared UI and feature-owned state.
- [ ] Add design scenarios for automatic repository design-language precedence.
- [ ] Add dependency scenarios that require manifest lookup.
- [ ] Regenerate the final registry and record an independent audit report.
- [ ] Archive only after every deterministic and semantic acceptance gate passes.
