# Design: Raise Marketing Site Quality and SEO

## Technical Approach

Keep the static-first Astro route wrappers and make `Layout.astro` the single document-head contract. Introduce only small pure helpers for the six route identities and locale pairing, shared by head generation, sitemap generation, and tests. Keep truthful bilingual copy in `src/i18n/{en,es}.ts`; preserve the legal-publication gate, consent ordering, waitlist behavior, and existing dependencies. The local Unlighthouse reports are lab evidence only: sampled production SEO was 1.00, legal-route performance was 0.62–0.79, and much CPU/deprecation cost came from Cloudflare-managed scripts.

## Architecture Decisions

| Option | Tradeoff | Decision |
|---|---|---|
| Existing layout plus typed route inventory | Shared blast radius requires strong output tests | Choose; prevents 12-route drift with minimal surface area |
| New SEO integration/package | More dependencies and abstraction for six fixed identities | Reject; Astro primitives are sufficient |
| Immediate font self-hosting or script rewrite | Could change visual loading, consent, and theme behavior | Defer; make resource changes only after controlled measurement |
| Source fix for Cloudflare transforms/redirects | Repository cannot control edge, cache, or hostname ownership | Reject; create an operator handoff and verify after deployment |

## Data Flow

```text
locale wrapper -> shared page + translations -> Layout route identity
  -> metadata/canonical/hreflang/robots/OG/JSON-LD -> static build output
  -> Vitest contracts + Playwright preview crawl -> deployment-only edge/live checks
```

## File Changes

| File | Action | Description |
|---|---|---|
| `apps/web/marketing/src/layouts/Layout.astro` | Modify | Centralize title/description, HTTPS trailing-slash canonical, reciprocal `en`/`es`/`x-default`, legal indexability, OG/Twitter, and truthful `WebSite`/`WebPage` JSON-LD. Preserve synchronous consent/theme ordering; add only justified resource hints. |
| `apps/web/marketing/src/i18n/{en,es}.ts` | Modify | Maintain unique 30+ character titles and 120–160 character descriptions; adjust only truthful localized labels/copy needed for parity and accessible names. |
| `apps/web/marketing/src/i18n/utils.ts` or narrowly scoped `src/seo/` helper | Modify/Create | Own typed route inventory and locale URL functions; avoid a generic utility layer. |
| `src/pages/{robots.txt.ts,sitemap.xml.ts}` | Modify | Consume the inventory, safely emit valid documents, preserve approved bot `Allow: /` policy, and list exactly 12 approved canonical URLs. |
| `src/pages/_*.astro`, locale wrappers, `src/components/{Nav,Footer,Logo,Hero,Features}.astro` | Modify | Ensure one `main`, one non-empty `h1`, named locale links to counterparts, semantic navigation, and followable links covering every sitemap route. |
| `src/components/{Analytics,consent/*,WaitlistForm}.astro`, `src/scripts/*`, `src/styles/global.css` | Modify only if measured | Reduce avoidable asset/font/script cost while retaining consent, DNT/GPC, reduced motion, waitlist, and theme contracts; do not treat Cloudflare resources as application fixes. |
| `apps/web/marketing/src/__tests__/*`, `tests/e2e/{seo,accessibility,consent,landing-page,waitlist-form}.spec.ts` | Modify/Create | Add source/build contracts and all-locale preview coverage. Update `docs/marketing/{seo.md,lighthouse/baseline.json}` only when budgets or evidence change. |

## Interfaces / Contracts

```ts
type Locale = 'en' | 'es'
type RouteId = '/' | '/privacy/' | '/terms/' | '/cookies/' | '/acceptable-use/' | '/accessibility/'
type RouteSeo = { route: RouteId; title: string; description: string; indexable: boolean; jsonLdType: 'WebSite' | 'WebPage' }
function counterpartPath(locale: Locale, route: RouteId): string
function canonicalUrl(locale: Locale, route: RouteId, site: URL): string
```

Canonical is self-referencing, HTTPS, and trailing-slash. Every route emits all three alternates; English and Spanish point to each other. Legal `index,follow` versus `noindex,nofollow` remains derived from `isLegalPublicationApproved()`. JSON-LD contains only supported identity fields and existing verified `sameAs`; no unsupported claims. Source links must not introduce `http://`, `nofollow`, or `/cdn-cgi/l/email-protection`.

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Unit | Inventory, pairing, metadata bounds, URL helpers, robots, sitemap XML, JSON-LD | Write failing Vitest tests first, implement the smallest helper/output change, then run the focused suite. |
| Build | 12 pages, headings/landmarks, indexability, emitted resources | Run marketing `astro check` and build; parse `dist` HTML/XML. |
| E2E | All locales, crawl graph/status, accessible names, skip-link focus, reduced-motion axe, consent/waitlist | Use existing Playwright suites in first-visit and seeded-consent states. |
| Deployment-only | Redirects, HSTS/cache, Cloudflare email obfuscation, live recrawl, field performance/Ahrefs | Operator runs `curl -I`, live Lighthouse/Unlighthouse, and an available Ahrefs crawl; report separately. Code cannot improve backlinks or guarantee external rankings. |

## Migration / Rollout

No data migration or feature flag. Apply in TDD slices: inventory/contracts, head and sitemap output, semantics/link hygiene, then measured resource changes. Deploy and verify all 12 routes and edge ownership; roll back source and edge changes independently.

## Open Questions

- [ ] Obtain an Ahrefs export/crawl identifier or repeatable comparison measurement; 54 and 90+ remain goals, not verified scores.
- [ ] Assign Cloudflare email-obfuscation, managed JSD, redirect/HSTS, and cache findings to an operator owner.
- [ ] Approve budgets for consent-visible and seeded states on mobile and desktop; current baseline is measure-only.
