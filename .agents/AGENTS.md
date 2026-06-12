# AGENTS.md

> AI agent instructions for the `profiletailors.com` monorepo.

## Project Identity

**Profile Tailors** — a social media management platform (schedule, publish, analyze, engage,
collaborate). This repo hosts the marketing site and the backend service. The product name is
**Profile Tailors**; `profiletailors.com` is the domain/org name only.

## Monorepo Structure

```
apps/web/marketing/ ← Astro 6 landing page
server/smp/         ← Spring Boot 4 backend (Kotlin, WebFlux)
shared/             ← Reusable Kotlin libraries (bus, common, storage, security, etc.)
docs/plans/         ← Design specs and implementation plans (read before coding)
docs/architecture/  ← C4 architecture docs
tmp/                ← Research notes and feature briefs (context only, not deployed)
.agents/            ← Agent tooling config (agentsync.toml, skills, commands)
```

## Active App: `apps/web/marketing`

- **Framework:** Astro 6, static-first, no SSR
- **Package manager:** pnpm
- **Node requirement:** `>=22.12.0`

### Dev Commands (run from `apps/web/marketing/`)

| Command        | Action                           |
|----------------|----------------------------------|
| `pnpm install` | Install dependencies             |
| `pnpm dev`     | Dev server at `localhost:4321`   |
| `pnpm build`   | Build to `./dist/`               |
| `pnpm preview` | Preview production build locally |

## Backend: `server/smp/`

- **Framework:** Spring Boot 4, Kotlin, WebFlux, R2DBC
- **Build tool:** Gradle (run from repo root)

### Dev Commands (run from repo root)

| Command | Action |
|---------|--------|
| `./gradlew :server:smp:build` | Compile and package |
| `./gradlew :server:smp:test` | Run unit and integration tests |
| `./gradlew :server:smp:check` | Tests + detekt |
| `./gradlew :server:smp:bootRun --args='--spring.profiles.active=dev'` | Start dev server |

### Hexagonal Architecture (mandatory)

**Every feature (bounded context) follows this structure:**

```
{feature}/
├── domain/          # Pure Kotlin — NO Spring annotations
├── application/     # Use cases, CQRS handlers, ports consumed by handlers
└── infrastructure/  # Spring Boot, R2DBC, HTTP, external adapters
```

**Dependency rule:** `domain ← application ← infrastructure`

| Layer | Can depend on | Must NOT depend on |
|-------|---------------|--------------------|
| **Domain** | Nothing (pure Kotlin) | Application, Infrastructure, Spring |
| **Application** | Domain | Infrastructure, Spring stereotypes |
| **Infrastructure** | Domain + Application | — |

- **Package convention:** `com.profiletailors.smp.{context}.{layer}`
- **CQRS naming:** `GetXQuery`, `{Verb}XCommand`, `XHandler`, `R2dbcXRepository`
- **Application marker:** use `com.profiletailors.common.domain.Service` (not Spring `@Service`)
- **Source of truth:** `.agents/skills/backend-platform/hexagonal-architecture/SKILL.md`
- **Shared libs** (`shared/common`, `shared/bus`, `shared/spring-boot-common`) are cross-cutting
  libraries, not feature slices — they do not follow the per-feature folder rule.

## Design Spec (source of truth)

Read `docs/plans/2026-05-13-socialnexus-landing-design.md` before any UI work. Key constraints:

- **Style:** Nothing-inspired, monochrome, typographically driven — avoid generic SaaS aesthetics
  and heavy card grids.
- **Fonts:** Space Grotesk (body/headings), Space Mono (labels/metadata), Doto (**once** in hero
  only).
- **Color:** Dark-first (OLED black); light mode is a designed system, not a color inversion.
- **Tokens:** CSS custom properties — `--bg-primary-dark`, `--text-display`, `--text-secondary`,
  etc. Define tokens independently per mode.
- **Spacing rhythm:** 8px base; jumps at 32 / 64 / 96px for section breaks.
- **Motion:** Minimal and precise — no bounce, blur, or ornamental animation; respect
  `prefers-reduced-motion`.

## Bilingual Content Model

All user-facing strings must live in a single locale object — never hardcoded inline:

```ts
const content = {
  en: { nav, hero, platforms, features, audiences, finalCta, footer },
  es: { nav, hero, platforms, features, audiences, finalCta, footer },
}
```

- Default locale: English. Language switch updates copy and `lang` attribute.
- Spanish copy is longer — never use fixed-width containers that assume English length.
- EN/ES switcher lives in the header.

## Implementation Constraints

- Keep `apps/web/marketing` lightweight and static — no backend, no CMS, no heavy deps.
- Add dependencies only when Astro-native primitives are insufficient.
- Always check dependency scores with the depscore tool when you add a new dependency. If the score is low, consider using an alternative library or writing the code yourself.
- Waitlist form is client-side only for now (no persistence backend defined yet).
- Prefer few files with clear content/style boundaries over many small fragments.
- **Documentation:** All internal and external documentation (READMEs, design specs, architecture docs, and agent instructions) MUST be in English. Files must follow the lowercase `kebab-case.md` naming convention (except `README.md`) and adhere to the standard structure: Overview → Changes → Usage → Troubleshooting → References. No exceptions.

## Testing & Delivery Discipline

- Apply **BDD** when defining behavior: describe features from the user perspective with clear scenarios and expected outcomes before implementation.
- Apply **TDD** when implementing changes: start with a failing test when practical, implement the smallest change that makes it pass, then refactor safely.
- Keep acceptance criteria, scenarios, and tests aligned so product behavior, implementation, and verification stay consistent.
- Prefer focused, maintainable tests over broad brittle coverage, and update tests alongside behavior changes.

