# AGENTS.md

> AI agent instructions for the `profiletailors.com` monorepo.

## Project Identity

**Profile Tailors** — a social media management platform (schedule, publish, analyze, engage, collaborate). This repo hosts its public-facing marketing site. The product name is **Profile Tailors**; `profiletailors.com` is the domain/org name only.

## Monorepo Structure

```
apps/web/landing/   ← Astro 6 landing page (the only active app)
docs/plans/         ← Design specs and implementation plans (read before coding)
tmp/                ← Research notes and feature briefs (context only, not deployed)
.agents/            ← Agent tooling config (agentsync.toml, skills, commands)
```

## Active App: `apps/web/landing`

- **Framework:** Astro 6, static-first, no SSR
- **Package manager:** pnpm (uses its own `pnpm-workspace.yaml` inside `apps/web/landing/`)
- **Node requirement:** `>=22.12.0`

### Dev Commands (run from `apps/web/landing/`)

| Command        | Action                              |
| -------------- | ----------------------------------- |
| `pnpm install` | Install dependencies                |
| `pnpm dev`     | Dev server at `localhost:4321`      |
| `pnpm build`   | Build to `./dist/`                  |
| `pnpm preview` | Preview production build locally    |

## Design Spec (source of truth)

Read `docs/plans/2026-05-13-socialnexus-landing-design.md` before any UI work. Key constraints:

- **Style:** Nothing-inspired, monochrome, typographically driven — avoid generic SaaS aesthetics and heavy card grids.
- **Fonts:** Space Grotesk (body/headings), Space Mono (labels/metadata), Doto (**once** in hero only).
- **Color:** Dark-first (OLED black); light mode is a designed system, not a color inversion.
- **Tokens:** CSS custom properties — `--bg-primary-dark`, `--text-display`, `--text-secondary`, etc. Define tokens independently per mode.
- **Spacing rhythm:** 8px base; jumps at 32 / 64 / 96px for section breaks.
- **Motion:** Minimal and precise — no bounce, blur, or ornamental animation; respect `prefers-reduced-motion`.

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

- Keep `apps/web/landing` lightweight and static — no backend, no CMS, no heavy deps.
- Add dependencies only when Astro-native primitives are insufficient.
- Waitlist form is client-side only for now (no persistence backend defined yet).
- Prefer few files with clear content/style boundaries over many small fragments.

