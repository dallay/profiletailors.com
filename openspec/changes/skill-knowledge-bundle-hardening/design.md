# Design: Focused Skill Content Review

## Authority

Resolve guidance conflicts in this order:

1. Current repository implementation and manifests for facts about the existing system.
2. `.agents/AGENTS.md`, applicable `PRODUCT.md` files, `.agents/DESIGN.md`, and accepted ADRs for
   required architecture, product, and design constraints.
3. Current OpenSpec requirements that remain compatible with those authorities.
4. Local technology skills.
5. Upstream-adapted skills and their references.

Generic examples may illustrate another platform only when their scope is explicit. They must not
be presented as the Profile Tailors implementation default.

## Spring and Kotlin

The Profile Tailors backend baseline is Kotlin, Spring Boot, WebFlux, coroutines, and reactive
persistence. Domain defines repository and gateway ports; Application consumes them;
Infrastructure implements them. Keep truly blocking integrations isolated at their adapter
boundary. Read the version catalog and module build files for dependency facts.

## Frontend and design

The dashboard package is `app`; the admin SPA is separate and flatter; marketing is static-first
Astro. The shared Vue package is `@profiletailors/vue-ui`, and shadcn-vue primitives follow the
current surface paths. Use Pinia for state that must be shared beyond a component.

`.agents/DESIGN.md` is the visual authority. `impeccable` owns process and quality,
`nothing-design` supplies Profile Tailors' visual language automatically, and `frontend-design` is
generic inspiration where project guidance is silent. `modern-web-guidance` is a fallback for web
platform details without a specialized local skill.

## Maintenance

Review changed skills and directly linked references together. Keep the registry hand-maintained.
The deterministic skill doctor, comment scanner, family scans, registry generator, and dedicated
workflow were removed by ADR-0026; this content pass does not recreate them.
