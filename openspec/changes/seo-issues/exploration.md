# Exploration: seo-issues (Ahrefs 2026-08-27, Project 9293424)

## Executive Summary

Ahrefs 2026-08-27 reports 15 issues vs 2026-08-26. Grounding against the current worktree shows ~6 of 12 prior SEO errors were **already fixed on 2026-08-27 in eb9fa53f/7eaf7639 (PR #869)** but Ahrefs re-flagged the trailing crawl snapshot. The remaining 9+ issues split into: (a) real redirect/protocol hygiene gaps with no in-repo redirect config, (b) intentional IndexNow gap (no impl), (c) AI-bot policy that is currently allow-all but under-specified per the seo skill checklist, and (d) intentional title/meta/H1 drift that now needs spec-pinned invariants. Primary surface is `apps/web/marketing` (Astro 7, EN+ES, 12 crawlable URLs = 6 routes x 2 locales); `shared/web` and `server/smp` are out of scope.

## Current State

### Site shape

- 12 crawlable URLs: `/`, `/privacy/`, `/terms/`, `/cookies/`, `/acceptable-use/`, `/accessibility/` and `/es/` variants. Confirmed in `sitemap.xml.ts:3` `ROUTES` and `buildUrl()` (`/es` prefix only for non-root).
- Each URL renders via a thin `pages/<route>.astro` wrapper (EN) and `pages/es/<route>.astro` wrapper (ES) that delegate to a shared `pages/_<Policy>.astro` component. All policy pages now embed `<Nav>` with a same-path language switch (`langHref = locale === 'en' ? '/es/<route>' : '/<route>'`), fixing the prior orphan-link gap.
- `Layout.astro` is the single SEO head: canonical is computed as `new URL(canonicalPath, base).href` (`:32-36`), hreflang emits `en`, `es`, `x-default` (`:98-100`), robots meta is `index,follow,...` or `noindex,nofollow,noarchive` when `noindex` prop is true (`:38-40`), JSON-LD `WebSite|WebPage` emitted when `includeStructuredData`. Legal pages pass `canonicalPath="/<route>/"`, `jsonLdType="WebPage"`, and `noindex={!publicationApproved}` (`_PrivacyPolicy.astro:40-43` etc.).
- `robots.txt.ts` is minimal allow-all: `User-agent: *` / `Allow: /` + `Sitemap: https://profiletailors.com/sitemap.xml` (`robots.txt.ts:7-11`). No per-bot `Disallow`/`Allow` lines, no `llms.txt` reference.
- `sitemap.xml.ts` emits 12 `<url>` entries with `lastmod = now`, `changefreq weekly`, priority 1.0 for `/`, 0.9 for `/es/`, 0.7/0.6 for others. No `lastmod` per-route, no `hreflang` alternates inside sitemap.
- i18n sources `src/i18n/en.ts` and `src/i18n/es.ts` are the single source for every `<title>`, `<meta description>`, and `<h1>` (policy.title is rendered directly as `<h1>`). No markdown content files or CMS.

### What was already fixed (the 2026-08-27 batch)

Diff `eb9fa53f^..7eaf7639` (PR #869) is the 10-page batch:

- **Broken email links (10 pages + 1x404 + 1x4XX):** Root cause was Cloudflare Email Obfuscation wrapping every raw `contact@profiletailors.com` / `accessibility@profiletailors.com` in rendered HTML with `<a href="/cdn-cgi/l/email-protection#...">` whose endpoint 404s. Fix converted every occurrence in `en.ts`/`es.ts` to markdown mailto form `[contact@profiletailors.com](mailto:contact@profiletailors.com)` so Cloudflare skips the obfuscator. `renderLegalText` / `renderText` in each policy page converts `[...](mailto:...)` to `<a href="mailto:...">` (`_PrivacyPolicy.astro:20-31`). Regression in `utils.test.ts:143-169` asserts: no raw `@profiletailors.com` outside `mailto:` link syntax, and presence of mailto links in both locales. Verified: current `grep` shows zero raw emails outside the mailto pattern.
- **Meta descriptions (10 pages too short <120 chars):** Expanded all 5 policy descriptions into 120-160 char band in both locales. Pinned by `utils.test.ts:108-131` (`En %s.description is between 120 and 160` per locale). Verified current `en.ts` descriptions are 125-148 chars.
- **Title too short (1 page):** `/privacy/` title changed from `Privacy Policy` (14 chars) to `Privacy Policy — Profile Tailors` (>=20) in both locales; similarly `Terms`, `Cookie`, `Acceptable Use`, `Accessibility` all gained the suffix. Pinned by `utils.test.ts:133-141` for privacy title length.
- **Single incoming dofollow (2 pages weak internal linking):** Added `<Nav>` import to each of the 5 policy components so every legal page now links to its sibling locale and the home via the language switcher and logo, plus `Footer` legalLinks (5 links on every page). This also resolves the orphan improvement noted by Ahrefs (+1 orphan). Footer still omits legal links only if `legalLinks` is empty — not the case.

No other marketing files changed between `2026-08-26` and `2026-08-27` crawls — the batch is fully explained.

### Affected Areas Inventory

| Path | Role & why relevant |
|---|---|
| `apps/web/marketing/src/pages/robots.txt.ts` | AI bot policy (#7, #8) — currently `Allow: /` only, no per-bot section |
| `apps/web/marketing/src/pages/sitemap.xml.ts` | Sitemap coverage for orphan (#15), IndexNow source-of-truth (#9), redirect targets |
| `apps/web/marketing/src/layouts/Layout.astro` | Canonical, hreflang, robots meta, title, description, JSON-LD, og/twitter (`:32-40`, `:95-100`) |
| `apps/web/marketing/src/i18n/en.ts`, `es.ts` | Title/description/H1 sources for drift issues (#10-12); raw-email invariant |
| `apps/web/marketing/src/pages/_*.astro` (5 policies) + `src/pages/*.astro` / `src/pages/es/*.astro` | H1 rendering (`{policy.title}`), `renderLegalText` mailto pipeline, canonicalPath wiring |
| `apps/web/marketing/src/components/Nav.astro`, `Footer.astro`, `Logo.astro` | Internal link graph that determines broken/orphan/redirect signals |
| `apps/web/marketing/src/i18n/utils.test.ts` | Existing SEO pin tests (description length, title length, no-raw-email) |
| `apps/web/marketing/astro.config.mjs` | `site: https://profiletailors.com`, no `redirects` or `trailingSlash` config — redirect handling gap |
| `apps/web/marketing/tests/e2e/*` | No `seo.spec.ts` exists; only `landing-page`, `accessibility`, `consent`, `waitlist-form` specs |
| `openspec/specs/marketing-a11y-seo/spec.md` | Current spec only covers robots/sitemap routes, tabindex, reduced-motion — no SEO invariants |
| `shared/web` | Not implicated; consent/asset alias unaffected |

No middleware, no `vercel.json` / `wrangler.toml` / `_redirects` / `_headers`, no `public/robots.txt`, no `llms.txt`, no IndexNow key file, no image optimization pipeline beyond static SVGs.

## Issue-by-Issue Grounding (15)

### Errors (must fix)

**#1  10 pages have links to broken page | #2 1 page returns 404 | #3 1 page returns 4XX — VERDICT: previously fixed, needs re-crawl proof**

- Evidence: `grep cdn-cgi` returns zero hits in current tree; raw email regex audit passes. There is no other known broken destination in the internal href graph (graph enumerated: `legalLinks` 5 routes, `Nav` lang switch 1 per page, `Footer` cookieSettings, `LegalPolicyUnavailable` href `/`, markdown mailto links which are external). The commit message identifies the single 404/4XX as `/cdn-cgi/l/email-protection` itself — the same URL that powered the "10 pages" signal. After the fix the destination no longer appears in rendered HTML.
- Yet Ahrefs 2026-08-27 still counts 10 pages — strongly suggests the crawl snapshot was taken **during or just before** the deploy/PR merge (commit at 09:35/10:03 +0200 on 2026-08-27) and the report shipped the delta vs 2026-08-26. No second batch edit exists to explain a new broken destination arriving on 2026-08-27.
- Hypothesis validated: the 10-page cluster was Cloudflare obfuscation.
- Remaining work: enumerate internal hrefs in an E2E (crawl each page, collect `a[href]` not starting with `mailto:`/`http`/`#`, fetch with `page.request`, assert <400), run against a preview build, and attach as `fix + re-crawl` evidence. Add no new link source without adding its href to the graph test.
- Disposition for proposal: **Fixed** (7eaf7639) — proposal should record as resolved pending a fresh Ahrefs crawl; keep regression tests in scope.

### Warnings

**#4  6 pages load slowly (+2)**

- Evidence: No performance work landed in the marketing surface in the 2026-08-26..27 window. `global.css` loads Google Fonts via `@import` (blocking, render-delayed), which is a known LCP antagonist. No image pipeline (`og-en.svg`/`og-es.svg` are static SVGs, no WebP/AVIF, no `loading="lazy"` on hero), no `Speculation Rules` beyond moderate prerender (`Layout.astro:141-157`), Tailwind 4 un-purged size not measured. No Lighthouse/PageSpeed data attached to the audit context.
- The "+2" suggests two previously-acceptable pages tipped over the threshold after the title/description expansion (larger HTML + possible re-validation) or from a synthetic lab variance. Not a code regression per se.
- Verdict: **Requires measurement before fix**. Recommend a `core-web-vitals` measured step: run local Lighthouse on the preview build for all 12 URLs, record LCP/CLS/INP, then consider low-risk fixes (self-host Space Grotesk/Mono via `@fontsource` or `font-display: swap` preconnect, compress/convert OG images, verify `Speculation Rules` scope). Do not speculate INP without a lab run.
- Proposal: **Requires investigation, not a code fix in this cycle unless measured** — or a docs decision to add a performance budget spec without claiming improvement.

**#5  3 pages return 3XX | #6 1x 302 redirect — VERDICT: unresolved, low-severity hygiene gap**

- Evidence: No in-repo redirect config exists. Candidates for 3XX in this site:
  1) Trailing-slash normalization: all internal hrefs use trailing-slash (`/privacy/`, `/es/privacy/`) and sitemap uses trailing-slash — unlikely to 301.
  2) Locale redirect: `astro.config.mjs` has `prefixDefaultLocale: false` and no middleware — no `/en/` redirect exists.
  3) `www` vs apex and `http` vs `https`: `site` is `https://profiletailors.com` but no `http://` or `https://www.` normalization is expressed in repo (no HSTS, no Bulk Redirect).
  4) Legal publication gate `noindex` flip: when `legalPublicationStatus === APPROVED` the page serves 200 with `index,follow`; when `BLOCKED` it serves same URL with `noindex` meta — no 302. So not this.
- Without `vercel.json` or Cloudflare Bulk Redirect, the platform (Cloudflare Pages/Workers) likely issues a 301 for `http://profiletailors.com` and for `https://www.profiletailors.com` at the edge. The single `302` vs `301` distinction matters — a temporary redirect should be permanent (301) for apex/www canonicalization, per the commit's pending operator note "configure www.profiletailors.com DNS + Bulk Redirect 301, enable HSTS preload".
- Remaining work: enumerate internal hrefs, hit each with `http://` variant and check Location header, then define the bulk 301 + HSTS decision (docs + platform config, not code alone). Internal links must all be `https://` and the apex canonical in `Layout` already is.
- Proposal: **Deferred to operator platform fix** with a docs-tracked decision; add an E2E that asserts no `http://` internal href exists.

**#13 2 HTTP->HTTPS redirects & #14 1 redirect chain — same cluster as #5/#6**

- Evidence: `grep "http://"` over `apps/web/marketing/src` returns zero hits — internal links are protocol-relative or absolute `https://` via `new URL(path, base)` where `base` is `https://profiletailors.com`. The `http://` links Ahrefs found are likely external referrer noise or the canonical platform redirect for the `http` probe itself, plus a chain if `http://www.` → `https://www.` → `https://profiletailors.com` (two hops). No mixed-content images (`http://` image src) found.
- Proposal: **Correct by construction** (no `http://` internal hrefs to fix) — the chain is resolved by the same bulk www/https 301 + HSTS action as #5. The product fix is to ensure we never introduce `http://` links back in, via an E2E `http://` href lint test.

### Notices (disposition per notice)

**#7 12 pages have inconsistent AI training bot policy | #8 12 pages blocked from AI search bots — VERDICT: current allow-all is deliberate but under-specified**

- Evidence: `robots.txt.ts` has a single `User-agent: *` / `Allow: /` block. Per `seo/SKILL.md` AI search visibility section, blocking AI crawlers (`OAI-SearchBot`, `PerplexityBot`, `Google-Extended`, `ClaudeBot`, `GoogleOther`, etc.) removes the site from AI citations; the skill recommends **not** blocking wholesale and deciding per-bot. Ahrefs classifies the site into two AI issues: (a) inconsistency (robots.txt vs meta robots differ per page), and (b) outright blocking.
- Current state (`robots.txt` allow-all + Layout `robotsContent` `index,follow` on all 12 URLs) means **(b) is false as of this worktree** — nothing is blocked from AI search bots at the robots layer. The "blocked" signal may describe the pre-fix crawl (before PR #869) or a transient misconfig when legal pages were `noindex` during review. After `legalPublicationStatus = APPROVED`, 12 pages are indexable so not blocked.
- (a) inconsistency: Ahrefs marks 12 pages inconsistent because `robots.txt` has no per-bot stanza while the meta robots is page-level — Ahrefs' heuristic treats absence of per-bot Allow as "inconsistent" with an allow-all meta. The seo skill's guidance is to make the decision explicit: either keep the minimal `User-agent: *` allow-all (and document that no `llms.txt` or per-bot Disallow is desired), or add explicit `User-agent: <bot>` / `Allow: /` stanzas for the known AI user-agents to satisfy the crawler's expectation of per-bot clarity.
- Recommendation for proposal: **Allow AI crawlers by default (no blocking)**, document the decision in `PRODUCT.md`/`spec.md` as the operator's current intent, and optionally add explicit per-bot `Allow` lines to silence Ahrefs without changing behavior. Do not add `llms.txt` beyond a speculative 5-minute stub — per the skill, adoption is ~0.015% and no vendor confirms reading it; mark as `Not implemented` unless operator opts in.
- Risk if ignored: 12 notices persist as noise; no functional SEO harm.

**#9 10 pages unavailable in IndexNow — VERDICT: intentional gap, not a bug**

- Evidence: Zero `IndexNow` strings in repo; no key file (`<key>.txt`), no `POST https://api.indexnow.org/indexnow` ping, no sitemap-ping integration. 10 pages (all legal pages) were not submitted — only `/` and `/es/` are prioritized (1.0/0.9) but still also missing IndexNow.
- Ahrefs reports IndexNow under Notices, not Errors. The site is <50 pages, weekly changefreq — search Console sitemap + natural crawl covers it. IndexNow adds latency benefit only if the operator rotates site content frequently (not the case for a static legal surface).
- Proposal options:
  - **Option A (recommended):** Mark IndexNow as `Planned/Not implemented` with rationale, skip code, keep verification that `sitemap.xml` is submitted to Search Console.
  - **Option B:** Implement minimal IndexNow: generate a key, serve `/<key>.txt`, POST the 12 URLs on deploy (Vercel/Cloudflare build hook). Effort Medium, marginal SEO gain.
- Default disposition: **Not implemented** (no code).

**#10 10 pages H1 changed | #11 10 pages meta description changed | #12 10 pages title changed — VERDICT: intentional batch drift, needs invariant pinning**

- Evidence: `git diff` shows every legal policy's `title` gained ` — Profile Tailors` and `description` expanded. Because the 5 policies exist in two locales, exactly 10 documents changed — which matches the count for all three notices. Each policy component renders `<h1>{policy.title}</h1>`, so H1 tracked title identically. The drift is therefore a single correlated i18n change, not 30 independent drifts.
- Current protections: `utils.test.ts:108-141` pins description length band and privacy title length and structural parity between EN/ES. What is still unpinned: H1 uniqueness (one `<h1>` per page), title/meta-to-H1 alignment, and that EN and ES title sets remain branded. The existing spec (`openspec/specs/marketing-a11y-seo/spec.md`) does not pin any title/meta/H1 invariants at all — only robots/sitemap routes and a11y focus.
- Proposal: **Accept the drift as the new invariants** — do not revert. In spec, pin: 120-160 char descriptions for all 5 policies both locales; privacy title >=30 or branded suffix rule; single `<h1>` per page; canonical + hreflang invariants. Add an E2E `seo.spec.ts` that traverses the 12 URLs via preview server and asserts title/description/h1/og/canonical/hreflang without re-implementing Vitest's string checks. Effort Low; high insurance against re-drift on next i18n batch.

**#15 1 orphan page improved (-1) — VERDICT: fixed**

- Evidence: Prior state had at least one policy page with only one incoming internal link (or zero — orphan). Adding `<Nav>` per policy added a second incoming path (language switch + logo/home), and `Footer` `legalLinks` gave every page 5 inbound links from every other page. Sitemap covers all 12 URLs. Orphan count dropping by 1 is the expected positive signal of the same PR #869.
- Proposal: **Resolved** — verify with sitemap-vs-crawl parity test (every sitemap URL has >=1 inbound internal link, discovered via a graph crawl of the built site).

### Cross-cutting signals

- The 10-page counts (email-protection, meta short, IndexNow, H1/title/meta changed, even the pre-fix weak-link count) all collapse to the same 5 policies x 2 locales set. The Ahrefs delta should not be read as 10 unrelated issues — it is one correlated batch surfaced through multiple detectors.
- No git diff between 2026-08-26 and 2026-08-27 other than PR #869 exists on `main` to explain Ahrefs' snapshot; treat intervening 2026-08-28 release commits as not crawl-relevant.

## Approaches Considered

### 1. Minimal — Pin invariants + verify, no platform changes

Scope: Keep `robots.txt` as `Allow: /`, do not implement IndexNow, add `seo.spec.ts` + spec invariants, enumerate internal link graph with a single 301/www/HSTS docs decision owned by the operator outside the repo.

- Pros: Fastest to a green re-crawl on the fixed issues (broken links, orphan, titles). Aligns with existing tests; no new infrastructure deps. Low review budget (<400-line guard safe).
- Cons: 3XX/www/HTTP notices persist as platform warnings until operator acts; IndexNow notice intentionally remains.
- Effort: Low (spec + 1-2 test files + docs).

### 2. Platform-complete — Minimal + explicit AI bot Allow + bulk 301/HSTS + optional IndexNow stub

Scope: As (1) plus: explicit per-bot `Allow` stanzas in `robots.txt.ts` for `OAI-SearchBot`, `GPTBot`, `PerplexityBot`, `ClaudeBot`, `Google-Extended`, `GoogleOther`, `Bingbot` (all `Allow: /` to silence Ahrefs), a placeholder `vercel.json` or Cloudflare Bulk Redirect config declaring `http://` and `www` 301s + `Strict-Transport-Security` / HSTS, and (optionally) an IndexNow key + deploy-hook ping.

- Pros: Maximum Ahrefs issue closure in one cycle (12 AI notices + 4 redirect notices). Makes crawl/hygiene explicit rather than implicit.
- Cons: Couples marketing repo to hosting-platform config (Cloudflare dashboard vs `vercel.json`) that may live outside this repo; IndexNow is marginal for 12 static URLs; more surface to verify (redirect matrix E2E).
- Effort: Medium.

### 3. Heavyweight content restructure — Rewrite legal i18n pipeline (content collections / markdown) to decouple copy from code

Not recommended. The current i18n-in-TypeScript pattern is consistent with the static-first Astro + `shared/web` separation-of-concerns posture and is already tested. Introducing content collections would add indirection without addressing any of the 15 issues (all are link/performance/protocol/policy, not content-model). Risk of reintroducing the email-obfuscation bug if markdown email rendering regresses.

## Recommendation

**Adopt Approach 1 (Minimal) as the posture for this change, with a named docs decision for the platform redirects that Approach 2 would own.**

Rationale:

- The 6 error-grade issues (the only user- or SEO-impacting ones) are already fixed; what remains is to **prove** they are fixed with a graph crawl test and spec invariants so the next i18n batch does not regress them.
- Redirect/HTTPS and AI-bot items are low-harm notices where the correct fix is partly outside the repo (Cloudflare Bulk Redirect / HSTS). Shipping in-repo `http://` lint + hreflang/canonical invariants gives evidence without prematurely coupling to a hosting config that the operator has already flagged as "outside this repo".
- IndexNow should stay `Not implemented` per `seo/SKILL.md` adoption note; adding it now would be busywork for negligible crawl latency gain on 12 static URLs.

If the operator prefers a zero-notice crawl, **Approach 2** is an additive second PR (per-bot Allow + platform redirect spec) that can chain off this one without reopening the invariant work.

## Proposed Dispositions (for sdd-propose to adopt)

| # | Ahrefs | Disposition | In-scope fix |
|---|---|---|---|
| 1 | 10 broken links | **Fixed** (PR #869) — needs re-crawl proof | Internal-link hygiene E2E + raw-email invariant (already present) |
| 2 | 1x404 | **Fixed** (same dest. as #1) | Same |
| 3 | 1x4XX | **Fixed** (same dest. as #1) | Same |
| 4 | 6 slow | **Investigate** — measure before fix | Add Lighthouse budget spec; optional low-risk font/image fix only after measurement |
| 5 | 3x3XX | **Deferred to operator** — platform 301 | Docs decision + `http://` href lint test |
| 6 | 1x302 | **Deferred** — should be 301 | Same |
| 7 | 12 inconsistent AI bot policy | **Allow-all by deliberate decision** — optionally explicit per-bot Allow | Spec line + optional robots.txt per-bot Allow |
| 8 | 12 blocked from AI bots | **Not blocked** (allow-all) — same decision | Same |
| 9 | 10 IndexNow | **Not implemented** | Record rationale; no code |
| 10 | 10 H1 changed | **Accepted drift** — pin invariants | New spec requirements + `seo.spec.ts` h1/title check |
| 11 | 10 meta changed | **Accepted drift** — pin invariants | Same; description 120-160 band |
| 12 | 10 title changed | **Accepted drift** — pin invariants | Same; branded suffix/title length |
| 13 | 2 HTTP->HTTPS | **Correct by construction** + platform 301 | `http://` href lint |
| 14 | 1 redirect chain | **Deferred to platform** | Same as #5 |
| 15 | 1 orphan improved | **Fixed** | Sitemap-vs-graph coverage test |

Every one of the 15 issues has a disposition — none silently omitted.

## Scope & Work Breakdown (for sdd-tasks)

**In scope (code + spec + docs + tests):**

- `openspec/specs/marketing-a11y-seo/spec.md` — add SEO invariants: title >=50-60 guideline / branded suffix, description 120-160, single `<h1>`, canonical + hreflang triple, sitemap coverage, robots allow-all decision.
- `apps/web/marketing/tests/e2e/seo.spec.ts` (new) — preview-crawl assertions: (a) every internal href resolves <400, (b) no `http://` href, (c) sitemap lists 12 URLs with `lastmod`/`changefreq`, (d) each page's title/description/h1/canonical/hreflang/og pass.
- `apps/web/marketing/src/pages/robots.txt.ts` — optional explicit per-bot `Allow` (if proposal opts for Approach 2).
- `docs/` — operator runbook entry for www bulk 301 + HSTS + re-crawl steps (or a `docs/marketing/SEO.md` if absent).

**Explicitly out of scope:**

- `shared/web` (consent contract untouched).
- `server/smp` / backend — not crawled by Ahrefs.
- `apps/web/app` / `apps/web/admin` — separate SPAs.
- IndexNow implementation — deferred.
- Image/font performance micro-optimizations — deferred pending Lighthouse numbers.

## Risks

- **Stale Ahrefs snapshot risk (High likelihood, Low impact):** Ahrefs cached snapshot may re-report already-fixed broken links despite the deploy. Mitigate by ordering a manual re-crawl immediately after the invariant PR ships, and by shipping the graph-crawl E2E as evidence that the in-repo state is green independently of Ahrefs' cadence.
- **Redirect assertions outside the repo (Medium, Low):** The remaining 4 redirect notices depend on Cloudflare dashboard state that the repo cannot enforce. Mitigate by making the docs decision explicit and testing only the in-repo invariant (no `http://` hrefs), and by not claiming redirect fixes we cannot prove in CI.
- **Title/meta/H1 re-drift on next i18n batch (Medium, Medium):** Without spec pinning, a future legal-copy update silently reintroduces Ahrefs warnings. Mitigate by gating `just frontend-check` + `seo.spec.ts` in CI.
- **Performance claims without measurement (Medium, Medium):** Adding speculative perf fixes without Lighthouse baselines risks wasted effort and false confidence. Mitigate by requiring a Lighthouse run artifact before any perf code claim.
- **AI crawler policy ambiguity (Low, Low):** Adding per-bot `Allow` is harmless but couples robots.txt to an unstable AI vendor list. Mitigate by documenting the deliberate allow-all decision and treating per-bot lines as optional syntactic completeness.

## Ready for Proposal

**Yes.** Every Ahrefs issue has been traced to an in-repo source (or deliberate non-source for platform/IndexNow items), the 2026-08-27 10-page batch diff has been recovered as PR #869, the affected areas have been enumerated, and the dispositions are concrete enough for `sdd-propose` to overwrite `proposal.md` without further investigation. The only prerequisite for implementation is the operator's choice between Approach 1 vs 2 for AI bot explicitness and IndexNow deferral — default recommendation is Approach 1.

## References to Real Code (grounding)

- Site origin & locale routing: `apps/web/marketing/astro.config.mjs:44,54-60`
- Sitemap contract (12 URLs): `apps/web/marketing/src/pages/sitemap.xml.ts:1-36`
- Robots contract (allow-all): `apps/web/marketing/src/pages/robots.txt.ts:1-19`
- Canonical/hreflang/robots meta: `apps/web/marketing/src/layouts/Layout.astro:32-40,88-101`
- Legal i18n sources: `apps/web/marketing/src/i18n/en.ts:58-192`, `apps/web/marketing/src/i18n/es.ts:58-189`
- SEO pins: `apps/web/marketing/src/i18n/utils.test.ts:108-169`
- Batch diff: `git show eb9fa53f --stat`, `git show 7eaf7639 --stat` and `en.ts`/`es.ts` diffs
- Skill source of truth for AI/Search: `.agents/skills/frontend-platform/seo/SKILL.md` (AI search visibility, sitemap/robots checklists)
- Product surface: `apps/web/PRODUCT.md`, `apps/web/marketing/PRODUCT.md`, `.agents/DESIGN.md`

## Proposal Input (verbatim for sdd-propose reuse)

> Ahrefs 2026-08-27 (9293424) 15 issues collapse to a single 10-page i18n batch (5 policies x 2 locales) plus four platform-redirect notices and a pre-existing performance signal. Six errors were already fixed in PR #869 (Cloudflare email-protection 404 via mailto conversion, orphan via Nav, titles/meta). The correct change is to pin the new SEO invariants in spec + add a crawl-parity E2E (`seo.spec.ts`) and docs for the bulk www/https 301 & HSTS operator step; keep IndexNow and per-bot robots as deliberate allow-all (or explicit per-bot Allow as a chained follow-up). No `shared/web` or `server/smp` work.

## Relevant Files

- `apps/web/marketing/src/pages/robots.txt.ts` — review per-bot AI policy
- `apps/web/marketing/src/pages/sitemap.xml.ts` — review sitemap coverage for orphan/IndexNow
- `apps/web/marketing/src/layouts/Layout.astro` — review canonical/hreflang/robots/title/meta
- `apps/web/marketing/src/i18n/en.ts`, `apps/web/marketing/src/i18n/es.ts` — trace H1/title/meta batch
- `apps/web/marketing/src/pages/_PrivacyPolicy.astro`, `_TermsPage.astro`, `_CookiePolicyPage.astro`, `_AcceptableUsePage.astro`, `_AccessibilityPage.astro` — H1 & mailto pipeline
- `apps/web/marketing/src/components/Nav.astro`, `apps/web/marketing/src/components/Footer.astro` — internal link graph
- `apps/web/marketing/src/i18n/utils.test.ts` — existing SEO invariant tests
- `apps/web/marketing/astro.config.mjs` — site origin & i18n routing (redirect handling gap)
- `openspec/specs/marketing-a11y-seo/spec.md` — delta target for pinned SEO invariants
