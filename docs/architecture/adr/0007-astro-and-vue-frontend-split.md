# ADR-0007: Astro & Vue Frontend Split

- Status: Accepted
- Date: 2026-06-21
- Decision owners: Principal Architect
- Scope: Frontend (`apps/web`)
- Supersedes: None
- Superseded by: None

## Context
The project requires a fast, SEO-friendly marketing presence and a high-interactivity dashboard application.

## Decision drivers
- SEO (marketing site needs fast static rendering).
- Performance (minimal JS on public pages).
- Interactivity (dashboard requires complex state management and rich UI components).

## Decision
The frontend MUST be split into two distinct applications:
1. **Marketing Site**: Built with **Astro 6**. Static-first, minimal client-side JS.
2. **Dashboard App**: Built with **Vue 3** (SPA). Uses **Pinia** for state management and **shadcn-vue** for UI components.

Both apps SHOULD share common assets (logos, colors) via the `shared/assets` directory.

## Scope and boundaries
- `apps/web/marketing`: Astro project.
- `apps/web/app`: Vue SPA project.

## Alternatives considered
### Single Unified App (Vue or Astro only)
- Reason rejected: Vue-only misses SEO benefits for marketing; Astro-only is less suited for the highly dynamic, stateful dashboard experience.
### React for Dashboard
- Reason rejected: Vue 3 was selected for its performance, ease of use with Tailwind, and established patterns in the current team. (Note: Documentation mentioning React is stale).

## Consequences
### Positive
- Marketing site achieves near-perfect Lighthouse scores.
- Dashboard remains responsive and modular.
- Decoupled deployments for marketing and product.
### Negative
- Duplication of some components or styles across frameworks.
- Multiple dev servers to manage.

## Compliance and enforcement
Enforced by monorepo structure and package configuration.

## Verification
- `apps/web/app/package.json` contains `vue`.
- `apps/web/marketing/package.json` contains `astro`.

## Migration or remediation
Update all stale documentation that still references React as the dashboard framework.

## Revisit conditions
- A cross-framework component sharing solution (e.g., Web Components) becomes necessary.
- Integration between marketing and dashboard becomes too complex to manage as separate apps.
