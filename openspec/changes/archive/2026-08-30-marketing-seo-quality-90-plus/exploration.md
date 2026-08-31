# Exploration: marketing-seo-quality-90-plus

## Request and change framing

This is a new, product-facing quality improvement change for the public Astro marketing surface. The requested Ahrefs-reported score of 54 is not present in the repository or recoverable from the available Ahrefs API: calls for project `9293424` returned `Insufficient plan`. Treat the 54 and 90+ targets as user-provided goals, not as verified measurements. The repository contains a prior archived `seo-issues` change for the 2026-08-27 Ahrefs crawl; this exploration treats it as historical context and re-checks current source and production output instead of assuming it remains valid.

## Current State

- The marketing app is a static-first Astro 7.2.6 surface with English routes at `/`, `/privacy/`, `/terms/`, `/cookies/`, `/acceptable-use/`, `/accessibility/` and Spanish equivalents under `/es/`. `src/pages/*` wrappers delegate to shared page components. The public product truth says this surface converts early-access prospects into waitlist registrations and publishes legal documents; it does not describe a broad content/SEO platform.
- `src/layouts/Layout.astro` centralizes the document head. It currently emits `lang`, description, robots/googlebot directives, self-referencing canonical, `en`/`es`/`x-default` hreflang, Open Graph, Twitter metadata, favicon links, JSON-LD (`WebSite` for the home page and `WebPage` for legal pages), speculation rules, analytics, and consent/theme scripts. Legal pages can be `noindex` when legal publication is not approved; current `legal-publication.ts` says publication is approved, so all 12 production pages are indexable.
- `src/pages/sitemap.xml.ts` generates 12 URLs from six route entries and includes a current timestamp, weekly change frequency, and priorities. `src/pages/robots.txt.ts` emits allow-all plus explicit allow stanzas for seven AI/search bots and a sitemap URL. The repository's SEO runbook intentionally leaves IndexNow and redirect/HSTS execution outside the repository.
- English and Spanish translation files provide route titles and descriptions. Current production output, checked with `curl`, has title lengths from 32 to 65 and description lengths from 126 to 159; all 12 titles end in ` — Profile Tailors`, all descriptions are within the existing 120–160 invariant, and all 12 pages currently have one `h1`. The home page's visible `h1` is the product headline, not the document title, so the existing archived invariant “h1 equals title” does not describe the current home implementation even though legal pages match. The home page still has a coherent single H1 and a logical H2/H3 hierarchy.
- The current production pages expose no `http://` links in rendered `href`/`src` values, and same-origin navigational links fetched with `curl` returned 200. However, a current production crawl of the ten legal pages still exposes Cloudflare-generated `/cdn-cgi/l/email-protection#...` links for email addresses in legal copy. This contradicts the archived `seo-issues` claim that the mailto safeguard was fixed. The source translation strings do contain Markdown mailto links, so the deployed output has a source/deployment mismatch or Cloudflare transformation that must be resolved and re-verified rather than assumed fixed.
- `Layout.astro` and the marketing components contain several inline scripts. `ConsentScript.astro`, the theme bootstrap, `Hero.astro` animations, `ThemeToggle.astro`, `WaitlistForm.astro`, consent banner code, analytics loading, and Cloudflare-managed scripts all affect page weight and runtime behavior. The home page sends no product image; OG assets are SVGs. Global CSS imports Google Fonts through a CSS `@import`, which the current Chrome performance trace identified as render-blocking along with the generated component CSS. The latest trace showed excellent lab LCP/CLS on an unthrottled production navigation (LCP 155 ms, CLS 0.00) but did identify render-blocking CSS/font requests; this is not field data and does not establish the Ahrefs score.
- The consent banner is intentionally non-modal and fixed at the bottom, but on a first visit it becomes the LCP element in the current trace. This can distort performance measurement and deserves an explicit consent-visible versus consent-seeded measurement policy. Consent is product/privacy behavior and must not be weakened merely to improve a score.
- Accessibility conventions are already present: skip link, `tabindex="-1"` main-content targets, responsive controls, reduced-motion axe context, and a dedicated accessibility statement. `scroll-reveal.ts` exits under `prefers-reduced-motion`; hero animation tests and marketing accessibility E2E exist.
- Existing coverage includes 12 marketing unit test files and five main E2E specs. `tests/e2e/seo.spec.ts` already covers a 12-URL link-hygiene crawl, sitemap/inbound graph, robots bot policy, metadata, canonical/hreflang, JSON-LD, and source guards. `src/i18n/utils.test.ts` pins title/description and IndexNow invariants. The current `seo.spec.ts` must be read in full and extended only where the new requirements are clear; it is not evidence that the deployed Cloudflare output is healthy.
- Existing documentation at `docs/marketing/seo.md` records the previous Ahrefs issue disposition, platform-owned 301/HSTS matrix, AI policy, 12-URL sitemap, and measure-only Lighthouse baseline. `docs/marketing/lighthouse/baseline.json` is a synthetic/measure-only record with 12 URL entries and budgets of LCP 2500 ms, CLS 0.1, and INP 200 ms. It should not be presented as a verified Ahrefs or CrUX score.
- OpenSpec is configured as `openspec-only`, with strict TDD and active changes under `openspec/changes`. The active unrelated changes are `private-beta-launch-readiness` (QA, blocked acceptance), `dallay-561-registration-policy` (verify), `dallay-413-bulk-scheduling` (explore), and the consent UX change (QA). They must not be modified. The previous `seo-issues` change is archived under `openspec/changes/archive/2026-08-28-seo-issues/` and has already synchronized the `marketing-a11y-seo` main spec.

## Affected Areas

- `apps/web/marketing/src/layouts/Layout.astro` — single metadata/structured-data/resource-hint composition point; likely location for verified head improvements, but changes affect every public route.
- `apps/web/marketing/src/pages/_HomePage.astro` and `apps/web/marketing/src/components/{Hero,Features,Nav,Footer,Logo,ThemeToggle}.astro` — landing page semantics, heading hierarchy, visible content, navigation graph, and first-paint/runtime behavior.
- `apps/web/marketing/src/pages/{_PrivacyPolicy,_TermsPage,_CookiePolicyPage,_AcceptableUsePage,_AccessibilityPage}.astro` plus locale wrappers — legal-page headings, rendered Markdown links, indexability, internal navigation, and Cloudflare email-link behavior.
- `apps/web/marketing/src/i18n/{en,es}.ts` and `src/i18n/utils.ts` — bilingual titles, descriptions, page copy, route/link targets, and existing SEO unit invariants. Any copy change must preserve product truth, legal approval boundaries, and locale parity.
- `apps/web/marketing/src/pages/{robots.txt.ts,sitemap.xml.ts}` — crawl directives and URL inventory; modify only with evidence because the previous change already established a deliberate allow-all policy and 12-route sitemap.
- `apps/web/marketing/src/styles/global.css`, `src/scripts/{hero-animations,scroll-reveal}.ts`, `src/components/{Analytics,consent/ConsentScript,consent/ConsentBanner,WaitlistForm}.astro` — performance, resource loading, animation, consent, and JavaScript behavior. Avoid treating third-party or Cloudflare-managed requests as in-repo fixes without proving ownership.
- `apps/web/marketing/tests/e2e/{seo,accessibility,landing-page,consent,waitlist-form}.spec.ts` — rendered contract, crawl graph, a11y, consent, and critical landing interactions.
- `apps/web/marketing/src/{__tests__/robots.txt.test.ts,i18n/utils.test.ts,components/**/*.test.ts,scripts/**/*.test.ts}` — fast unit/source guard coverage.
- `apps/web/marketing/{package.json,astro.config.mjs,playwright.config.ts,vitest.config.ts,tsconfig.json}` and `Justfile` — available checks and build/test orchestration. Required local runtime is Node `>=24.19.0` with pnpm 11.
- `docs/marketing/seo.md` and `docs/marketing/lighthouse/baseline.json` — evidence/runbook and performance-budget records; update only after the new scope and measurements are approved.
- `openspec/specs/marketing-a11y-seo/spec.md` — existing source-of-truth contract. A delta spec is likely needed if this change modifies product-visible crawl/indexability, metadata, accessibility, or performance behavior.
- `apps/web/marketing/PRODUCT.md`, `apps/web/PRODUCT.md`, and `.agents/DESIGN.md` — product truth, surface boundary, bilingual expectations, legal constraints, and Nothing-inspired visual constraints. `shared/web`, dashboard/admin, backend, and Cloudflare dashboard configuration are out of scope unless the proposal explicitly proves a contract boundary change.

## Verified gaps and constraints

1. **Ahrefs score gap is unverified.** The Ahrefs project endpoint is unavailable under the current plan. The next phase must require the exact Ahrefs issue export/crawl identifier or clearly label score/issue data as user-supplied. Do not invent issue counts or promise that repository changes alone can reach 90+.
2. **Production/deployment drift exists.** `curl` against the live legal pages currently finds `/cdn-cgi/l/email-protection` links despite source mailto Markdown and repository guards. Investigate Cloudflare email obfuscation, deployment provenance, and cache state. A repository-only fix may be insufficient; the proposal must separate code, deployment, and platform actions.
3. **Redirect and hostname ownership is external.** `https://www.profiletailors.com/` currently ends at `https://profiletailors-com.l.ink/` after one redirect, while `http://profiletailors.com/` redirects to the canonical HTTPS origin. Redirect/HSTS behavior cannot be proven or fixed from the Astro repository; retain an operator-owned platform workstream and verify with `curl -I`/redirect-chain checks after changes.
4. **Performance score is not the same as technical SEO.** Current Lighthouse mobile and desktop navigation audits reported 100 for accessibility, best practices, and SEO, but a failed agentic-browsing audit remained and performance was not included in that Lighthouse tool result. The current Chrome trace found render-blocking generated CSS and Google Fonts. Use Lighthouse performance reports under controlled conditions and document lab/field limitations before changing CSS or font loading.
5. **Consent/privacy must remain first-class.** Analytics must remain consent-gated; DNT/GPC behavior and the non-modal banner are product/privacy contracts. Any performance optimization must prove no regression in consent, analytics gating, keyboard access, or localized copy.
6. **No new marketing claims without product approval.** The product is an early-access preview, publishing integrations are still being validated, and commercial terms are not announced. SEO copy must use existing truthful positioning, not fabricated features, pricing, integrations, or performance claims. Legal text requires qualified review and publication controls.
7. **Bilingual parity matters.** English and Spanish routes are both indexable and listed in the sitemap. Metadata, canonical/hreflang, internal links, headings, and any content/structured-data change must be tested across all 12 URLs. Spanish copy can be longer; do not introduce fixed-width assumptions.
8. **Zero-comment policy applies.** Do not add code comments/docblocks/TODOs or weaken existing checks. The repository already contains historical comments in source, but new work must follow the canonical zero-comment rule.
9. **Review-size gate applies downstream.** `openspec/config.yaml` has strict TDD; the common SDD protocol requires the task forecast to include exact `Decision needed before apply`, `Chained PRs recommended`, and `400-line budget risk` lines. Keep this change split into independent deliverables if measurement, metadata, platform evidence, and performance work would exceed the 400 changed-line review budget.

## Approaches

1. **Evidence-first SEO hardening with a narrow code delta** — Require a fresh Ahrefs export and production crawl, then fix only verified repository-owned issues: metadata/heading/schema/link invariants, rendered legal mailto output, sitemap/robots consistency, and targeted tests. Keep redirects/HSTS/Cloudflare behavior as explicit operator evidence rather than pretending it is code-fixed.
   - Pros: smallest reversible change; grounded in actual failures; preserves product/design truth; directly extends the existing SEO contract and tests; low risk of speculative SEO work.
   - Cons: cannot guarantee 90+ if Ahrefs weights platform/backlink/redirect factors; may require a separate Cloudflare/deployment action; production verification is outside local CI.
   - Effort: Medium

2. **Performance-focused marketing optimization** — After controlled Lighthouse performance baselines, address render-blocking font/CSS delivery, unnecessary client JavaScript, animation scheduling, consent-banner measurement, and asset/resource hints while preserving behavior. Add budgets and browser evidence.
   - Pros: addresses the current trace's render-blocking finding and may improve user experience/CWV; can reduce technical-quality penalties that Ahrefs or Lighthouse observes.
   - Cons: higher regression risk; fonts/FOIT/FOUC and theme/consent ordering are delicate; unthrottled lab results are not field evidence; may not improve technical SEO score if the 54 is driven by links/redirects/platform settings.
   - Effort: Medium/High

3. **Content/IA expansion for search growth** — Add new landing sections or content pages targeting social-content-planning terms, with richer structured data and internal linking.
   - Pros: potentially improves relevance and crawl depth over time.
   - Cons: conflicts with the deliberately minimal early-access product truth; risks unsupported claims, larger copy/i18n/legal surface, design drift, and a change larger than the stated technical SEO goal; does not directly repair Cloudflare or redirect issues.
   - Effort: High

4. **Platform-first remediation** — Treat the repository as mostly healthy and focus on Cloudflare canonical host, managed email obfuscation, redirects/HSTS, cache/deployment provenance, and a fresh re-crawl; make only regression-test updates in code.
   - Pros: directly targets the verified live mismatch and externally owned redirect behavior; avoids speculative application changes.
   - Cons: requires operator access and a deployment window; cannot be completed or evidenced solely in this worktree; may leave genuine source-level issues untouched.
   - Effort: Medium (platform) plus blocked repository evidence


## Unlighthouse evidence (2026-08-30)

The local report directory contains **7** `lighthouse.json` files: six usable canonical production routes and one crawl anomaly. The six usable reports were captured by Lighthouse **13.4.1** at approximately `2026-08-30T14:17:37Z`; the anomaly was captured at `2026-08-30T14:18:17Z`. All six usable routes kept requested, main-document, displayed, and final URLs identical, returned no `runWarnings`, and produced these category scores:

| Route / report | Performance | Accessibility | Best practices | SEO |
|---|---:|---:|---:|---:|
| `/` (`reports/lighthouse.json`) | 0.92 | 1.00 | 0.81 | 1.00 |
| `/acceptable-use/` (`reports/acceptable-use/lighthouse.json`) | 0.79 | 0.98 | 0.77 | 1.00 |
| `/accessibility/` (`reports/accessibility/lighthouse.json`) | 0.62 | 0.98 | 0.77 | 1.00 |
| `/cookies/` (`reports/cookies/lighthouse.json`) | 0.79 | 0.98 | 0.81 | 1.00 |
| `/privacy/` (`reports/privacy/lighthouse.json`) | 0.62 | 0.98 | 0.81 | 1.00 |
| `/terms/` (`reports/terms/lighthouse.json`) | 0.70 | 0.98 | 0.81 | 1.00 |
| **Mean (six usable routes)** | **0.74** | **0.983** | **0.797** | **1.00** |

### Per-route findings

- `/` is the strongest report: performance `0.92`, LCP `2.3 s`, FCP `2.3 s`, Speed Index `4.6 s`, TBT `0 ms`, CLS `0.061`; the LCP insight identifies the first-visit consent description (`body > aside#consent-banner > div.consent-container > p#consent-description`).
- `/acceptable-use/`: performance `0.79`, LCP `4.2 s`, FCP `2.5 s`, Speed Index `5.2 s`, TBT `0 ms`; LCP is a legal-copy paragraph with `3,903.7 ms` element-render delay. Accessibility also reports the language-switch link mismatch and missing main landmark.
- `/accessibility/`: lowest performance at `0.62`, LCP `4.1 s`, FCP `1.9 s`, Speed Index `5.1 s`, TBT `800 ms`, max potential FID `1,610 ms`; main-thread work `3.3 s` and script boot-up `3.2 s`. LCP is a legal-copy paragraph with `4,107.3 ms` element-render delay. It also reports the language-switch mismatch and missing main landmark.
- `/cookies/`: performance `0.79`, LCP `4.2 s`, FCP `2.5 s`, Speed Index `4.9 s`, TBT `0 ms`; LCP is the page H1 with `3,729.8 ms` element-render delay. It reports the language-switch mismatch and missing main landmark.
- `/privacy/`: lowest performance tied at `0.62`, LCP `4.1 s`, FCP `1.9 s`, Speed Index `5.3 s`, TBT `820 ms`, max potential FID `1,620 ms`; main-thread work `3.4 s` and script boot-up `3.3 s`. LCP is the first policy paragraph with `4,103.8 ms` element-render delay. It reports the language-switch mismatch and missing main landmark.
- `/terms/`: performance `0.70`, LCP `4.2 s`, FCP `1.9 s`, Speed Index `2.9 s`, TBT `580 ms`, max potential FID `1,430 ms`; main-thread work `2.9 s` and script boot-up `2.8 s`. LCP is the first legal-copy paragraph with approximately `3,719 ms` element-render delay. It reports the language-switch mismatch and missing main landmark.

### Aggregate failed audits and diagnostics

Across the six usable reports, the repeated findings are:

- All six fail `first-contentful-paint` (`1.9–2.5 s`), `largest-contentful-paint` (`2.3–4.2 s`), `speed-index` (`2.9–5.3 s`), `interactive` (`2.3–4.2 s`), `mainthread-work-breakdown` (`2.9–3.4 s`), `bootup-time` (`2.8–3.3 s`), `network-dependency-tree-insight`, and `render-blocking-insight` (except the home report, which scores `0.5` but still lists render-blocking resources). The repeated performance signal is therefore route-wide, but legal routes are materially worse than the home page.
- All six report three deprecated-API warnings, sourced to Cloudflare-managed `https://profiletailors.com/cdn-cgi/challenge-platform/scripts/jsd/main.js`: Shared Storage API, `StorageType.persistent`, and Protected Audience API. These are not repository-owned application code based on the report URLs.
- Five of six reports fail `label-content-name-mismatch` for `div#main-content > nav.flex > div.flex > a.font-mono`, whose rendered element has visible text `ES` and `aria-label="Switch to Spanish"`; the home page does not fail it. Five of six fail `landmark-one-main` for `html`; the home page does not. This is a notable difference from the current source: `Nav.astro` has `aria-label={langLabel}` on the language link, and `Layout.astro`/the home page already expose a main landmark. The mismatch is therefore report evidence to re-check against the exact deployed route/markup, not a reason to edit code during exploration.
- The route-wide render-blocking chain is generated `Nav.MCZBMYAY.css` (about `5.8 KiB`) → Google Fonts CSS (`1,131 B`) → three Google font files (about `42 KiB` total). Reported estimated render-blocking savings are `750–810 ms` on legal routes and `150 ms` on cookies; the home report still shows the Google Fonts CSS and generated nav CSS. The network insight reports no preconnected origins and estimates font preconnect savings of about `300–895 ms` depending on route.
- The cache insight reports roughly `6.8–7.6 KiB` wasted bytes per route. Most is a Cloudflare-managed JSD `main.js` request with a four-hour TTL; legal routes also include Cloudflare `email-decode.min.js` with a long TTL. The report’s `bootup-time` attributes approximately `2.76–3.24 s` to the Cloudflare JSD script, and `long-tasks` records one `1,381–1,622 ms` task from the same managed script on every usable route.
- Other performance diagnostics are comparatively clean: `unused-javascript`, `unused-css-rules`, `unminified-css`, `unminified-javascript`, `font-display-insight`, `image-delivery-insight`, `modern-http-insight`, `server-response-time`, `errors-in-console`, and `third-party-cookies` pass or have no actionable items. Each report transfers roughly `86–89 KiB`, with no image requests; Google Fonts account for about `43 KiB`.

### Crawl anomaly and payload evidence

`reports/cdn-cgi/l/email-protection/lighthouse.json` is not a marketing route. Its requested URL contains an email-protection hash, its main/final URL is `/cdn-cgi/l/email-protection`, Lighthouse reports `ERRORED_DOCUMENT_REQUEST` with HTTP `404`, and all category scores are `null`. Its `payload.html` is a Cloudflare **Email Protection** error document with `noindex, nofollow`; it is not included in the six-route score aggregates. The five legal payloads still contain one or two `/cdn-cgi/l/email-protection` links each, while the home payload contains none. This confirms the previously observed production/deployment mismatch, but does not establish whether the owner is source, a stale deployment, or Cloudflare Email Address Obfuscation.

Payload inspection was evidence-only. The payloads show canonical, four hreflang links, one JSON-LD block, and one H1 on each usable route; the home payload has one `<main>`, while legal payloads use `id="main-content"` without a literal `<main>` in the serialized HTML. Inline scripts and HTML comments were not treated as instructions.

### Exact local files and commands used

Files inspected: `openspec/changes/marketing-seo-quality-90-plus/exploration.md`, `openspec/config.yaml`, `apps/web/PRODUCT.md`, `apps/web/marketing/PRODUCT.md`, `.agents/DESIGN.md`, `.agents/skill-registry.md`, `.agents/skills/frontend-platform/seo/SKILL.md`, `.agents/skills/frontend-platform/frontend-architecture/SKILL.md`, `apps/web/marketing/src/layouts/Layout.astro`, `src/pages/{index,privacy,sitemap.xml,robots.txt}.ts`, `src/pages/es/index.astro`, `apps/web/marketing/src/components/{Nav,Analytics,consent/ConsentBanner}.astro`, `apps/web/marketing/src/styles/global.css`, `src/legal/legal-publication.ts`, and all seven report `lighthouse.json` plus `payload.html` files under `/Users/acosta/Downloads/.unlighthouse/profiletailors.com/9d0a/reports/`.

Commands used: `python3` scripts to recursively enumerate and parse every `reports/**/lighthouse.json`, aggregate category scores, list all numeric-score failures, extract audit details/URLs/diagnostics/opportunities/anomalies, inspect network dependency and LCP trees, and summarize payload markers; `git status --short`; and read-only file inspection. No application code, report payload, or existing artifact was edited by this exploration.

### Explicit limitations

- This is a single local Unlighthouse/Lighthouse navigation capture, not field Core Web Vitals, Search Console data, an Ahrefs Site Audit export, or proof of a 90+ score. Lighthouse category SEO is `1.00` for all six usable reports, but that does not validate Ahrefs’ unavailable score of `54` or guarantee the requested `90+` outcome.
- The report set contains only six usable production routes, not the 12 English/Spanish routes in the repository’s sitemap. No `/es/` report is present, so bilingual parity remains unmeasured here.
- The reports use Lighthouse `13.4.1`, a mobile network/user-agent profile with a desktop host user-agent, and Cloudflare-managed scripts. Results are lab conditions and may vary with cache, consent state, Cloudflare rules, geolocation, timing, and third-party availability.
- The report does not identify the Ahrefs issue categories, URLs, crawl configuration, or scoring formula. It also cannot assign Cloudflare findings to repository ownership, prove redirect/HSTS configuration, or establish deployment provenance.
- Payload HTML is serialized evidence from the crawl, not a trusted instruction source. It cannot prove source behavior where Cloudflare rewrites, cached output, or deployment drift may intervene.

## Recommendation

Proceed with **Approach 1 as the baseline**, with a deliberately separated **Approach 4 platform workstream** and only a measured, opt-in slice of Approach 2 if the fresh report or controlled Lighthouse run proves performance is a material contributor. Name the change `marketing-seo-quality-90-plus` (or a more precise variant only if the orchestrator receives a ticket/report name). The proposal should define a score-improvement program, not a guaranteed numeric outcome: establish a verified baseline, map every issue to code/deployment/platform ownership, fix repository-owned defects, and require a post-deploy Ahrefs crawl plus Lighthouse/Playwright evidence before claiming improvement.

The first implementation slice should likely be:

- reproduce the 12-route crawl against a fresh local production build and the live canonical host;
- resolve the live legal-page `cdn-cgi` email-link mismatch and confirm Cloudflare cache/deployment provenance;
- retain and strengthen metadata, canonical/hreflang, robots/sitemap, JSON-LD, link-graph, and heading checks without forcing the home H1 to equal the document title unless product/design approval explicitly accepts that change;
- run controlled mobile/desktop Lighthouse performance audits and record real reports/metrics separately from the existing measure-only baseline;
- keep Cloudflare redirect/HSTS actions and Ahrefs re-crawl as operator acceptance evidence, not application code;
- update the `marketing-a11y-seo` delta spec only for durable product behavior, and do not touch unrelated active changes.

## Recommended Evidence and Tests

- **Fresh external baseline:** obtain the Ahrefs Site Audit export/crawl timestamp for the reported 54, issue IDs/categories, affected URLs, and whether the score is health score or another metric. If API access remains unavailable, attach the user-provided export as untrusted input and verify each claim against source/live output.
- **Static contract checks:** `just frontend-lint`, `just frontend-check`, `just frontend-test` (or `pnpm --filter marketing test -- --run`); add focused unit tests for route inventory, metadata lengths/uniqueness, robots and sitemap output, JSON-LD, and mailto rendering/escaping where appropriate.
- **Build evidence:** `just frontend-build` / `pnpm --filter marketing build`, then inspect the generated `dist` output for 12 routes, no broken internal references, no unintended `http://`, no `cdn-cgi/l/email-protection` links, canonical/hreflang parity, valid JSON-LD, and expected robots/sitemap responses.
- **Browser crawl:** `just frontend-test-e2e` or the focused marketing Playwright command through `scripts/run-playwright.mjs`; run `seo.spec.ts` across all configured browsers as appropriate, plus accessibility, consent, landing, and waitlist suites. Include first-visit and pre-seeded-consent states so the banner does not hide regressions.
- **Performance:** run Lighthouse mobile and desktop for `/` and `/es/` at minimum, preferably all 12 URLs; record FCP/LCP/CLS/INP/TTFB, transfer sizes, render-blocking requests, and whether consent was seeded. Use the existing budgets only as thresholds, not as proof of an Ahrefs score. Use Chrome performance traces when interpreting render-blocking or LCP findings.
- **Accessibility and privacy regression:** run axe under reduced motion across EN/ES and legal pages; verify skip link/main-content focus, keyboard operation, no motion-only false positives, consent banner non-blocking behavior, DNT/GPC defaults, and analytics not loading before consent.
- **Platform checks:** `curl -I` for canonical/`www`/HTTP routes, `curl -fsSL` for robots/sitemap and all 12 pages, redirect count/final URL, cache/deployment headers, and a post-deploy Ahrefs re-crawl. Cloudflare dashboard evidence must identify the actual rule/cache/deployment state; local tests cannot substitute for it.
- **Security/quality:** run `git diff --check`, inspect the final diff for secrets and unrelated changes, and preserve the repository's zero-comment and strict-TDD policies. No dependency addition is currently justified.

## Ready for Proposal

Yes, conditionally. The orchestrator can draft a proposal for `marketing-seo-quality-90-plus`, but it should first request or explicitly record the missing Ahrefs report details and decide whether the change includes only repository-owned SEO fixes or also an operator-owned Cloudflare/deployment acceptance track. Do not promise 90+ as a guaranteed result, and do not edit application code in this exploration phase.
