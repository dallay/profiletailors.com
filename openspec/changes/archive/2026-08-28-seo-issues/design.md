# Design: seo-issues — Ahrefs 2026-08-27 (9293424)

## Technical Approach

Pin PR #869 fixes (mailto `cdn-cgi`, Nav orphan) and prove via preview crawl; add per-bot Allow for Ahrefs AI heuristic; keep redirects/IndexNow out of code. Astro static only in `apps/web/marketing` — no backend/shared. Covers 6 requirements/17 scenarios via tightened `robots.txt.ts` + verified `sitemap.xml.ts`/`Layout.astro` and single `seo.spec.ts` on 12 URLs.

## Architecture Decisions

### Decision: Robots per-bot Allow

| Option | Tradeoff | Decision |
|---|---|---|
| A. `User-agent: * Allow: /` only | Minimal diff; Ahrefs re-flags 12 AI notices | — |
| B. Add 7 per-bot `Allow: /` for OAI-SearchBot, GPTBot, PerplexityBot, ClaudeBot, Google-Extended, GoogleOther, Bingbot + `Sitemap:` line | +7 lines, silences notices, no behavior change; couples to vendor list | **Choose B** |

**Rationale**: `Layout.astro` already `index,follow` on 12 URLs — allow-all deliberate. Explicit stanzas satisfy heuristic; no `Disallow`/`llms.txt`.

### Decision: Link hygiene

| Option | Tradeoff | Decision |
|---|---|---|
| A. `utils.test.ts` regex | Pins source; misses rendered graph | Keep |
| B. `grep http://` lint | Code-level check | Add |
| C. `seo.spec.ts` crawl (fetch `a[href]` <400, no `cdn-cgi`/`http://`, sitemap parity, inbound >=2) | Proves output; covers snapshot staleness | **Choose A+B+C** |

**Rationale**: Broken/orphan fixed — only preview crawl proves deploy-independent correctness.

### Decision: IndexNow

| Option | Tradeoff | Decision |
|---|---|---|
| A. `<key>.txt` + `POST api.indexnow.org` hook | Closes 10 notices; adds key/secret + coupling for 12 static URLs | — |
| B. Intentionally absent; `sitemap.xml` + Search Console is discovery | Leaves 10 as ACCEPT; zero cost, honest for weekly `changefreq` | **Choose B** |

**Rationale**: 12 static legal URLs, weekly change — sitemap sufficient (~0.015% IndexNow adoption).

### Decision: Perf + Redirects (FIX vs ACCEPT vs PLATFORM)

| Option | Tradeoff | Decision |
|---|---|---|
| A. Ship speculative perf fixes | Closes 6 slow unmeasured; false confidence | — |
| B. Measure-only Lighthouse budget; block perf code without artifact | Defers slow; needs wiring | **Choose B** |
| C. In-repo `vercel.json`/`_headers` redirects | Closes 3XX/302/HTTP/chain; conflicts with operator Bulk Redirect | — |
| D. Runbook documents PLATFORM (bulk 301 `http→https`, `www→apex`, `302→301`, HSTS); repo enforces `no http:// href` | Leaves 4 as PLATFORM | **Choose D** |

**Rationale**: FIX=fix+proof (broken/orphan/AI), ACCEPT=no-op pinned (IndexNow + title/meta/H1), PLATFORM=edge repo cannot prove.

## Data Flow

```
i18n (en.ts/es.ts) → Layout.astro → 12 HTML (canonical/hreflang/og/robots)
       robots.txt.ts (per-bot Allow + Sitemap) ─┐
       sitemap.xml.ts (12 URLs) ─────────────────┤→ seo.spec.ts (preview)
                                                │  1) head invariants 2) crawl graph <400
                                                │  3) no http/cdn-cgi 4) sitemap parity 5) robots
       docs/marketing/seo.md (runbook) is out-of-band; Lighthouse JSON is artifact
```

## File Changes

| File | Action | Description |
|---|---|---|
| `src/pages/robots.txt.ts` | Modify | Add 7 per-bot `Allow: /` blocks + `Sitemap:` |
| `src/pages/sitemap.xml.ts` | Verify | No change; 12 URLs already; E2E asserts parity |
| `src/layouts/Layout.astro` | Verify | No change; canonical/hreflang/robots already correct |
| `src/i18n/utils.test.ts` | Modify | Extend: title >=30 + suffix, H1 pin if needed |
| `tests/e2e/seo.spec.ts` | Create | 12-URL crawl: head, link graph, sitemap, robots |
| `docs/marketing/seo.md` | Create | Runbook: bulk 301 matrix + HSTS + re-crawl |
| `astro.config.mjs`/`shared/web`/`server/smp` | None | Out of scope |

## Interfaces / Contracts

```ts
// GET /robots.txt → text/plain
User-agent: *
Allow: /
User-agent: OAI-SearchBot
Allow: /  // + GPTBot, PerplexityBot, ClaudeBot, Google-Extended, GoogleOther, Bingbot
Sitemap: https://profiletailors.com/sitemap.xml

// 12 URLs
const URLS = ["/","/privacy/","/terms/","/cookies/","/acceptable-use/","/accessibility/"]
  .flatMap(r => [r, r==="/" ? "/es/" : `/es${r}`]);

// Per-URL head invariants
title.length>=30 && title.endsWith(" — Profile Tailors") && unique
desc.length∈[120,160] && unique
h1Count===1 && h1===title
canonical===`https://profiletailors.com${path}` // https + trailing-slash
hreflang en/es/x-default present; robots === "index,follow,..."
```

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Unit | Title suffix, desc band, no raw email, mailto | `vitest` `utils.test.ts` |
| E2E (new) | Head invariants, robots per-bot, link graph <400, no http/cdn-cgi, sitemap parity + inbound >=2 | `seo.spec.ts` on `astro preview` (`playwright.config.ts` webServer) |
| E2E (existing) | a11y/consent unchanged | `just frontend-test-e2e` green |
| Budget | LCP/CLS/INP vs budget | Lighthouse JSON per URL; CI blocks perf code without artifact |
| Negative | No IndexNow | `grep api.indexnow.org` + no `<key>.txt` in `dist/` |

## Migration / Rollout

No migration. Static only — `git revert` is rollback. Single PR; operator runs Bulk Redirect + HSTS + Ahrefs re-crawl per runbook post-merge.

## Risks

Stale snapshot re-flagging mitigated by E2E proof; PLATFORM 301 unenforceable mitigated by `http://` lint; i18n re-drift mitigated by spec+CI gate; perf without baseline blocked by artifact requirement.

## Open Questions

- [ ] Confirm Cloudflare (not Vercel `_headers`) owns Bulk Redirect + HSTS.
- [ ] Lighthouse thresholds: use `core-web-vitals` defaults or set after first artifact?
