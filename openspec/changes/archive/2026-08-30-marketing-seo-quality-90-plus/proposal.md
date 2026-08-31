# Proposal: Raise Marketing Site Quality and SEO

## Intent

Improve the Astro site from reported 54 toward 90+ through actionable SEO, performance, accessibility, and crawl-integrity work. Reports are leads; source and deployment output remain authoritative.

## Goals

- Make six English and six Spanish routes crawlable and consistent.
- Reduce first-load cost without weakening consent or waitlist behavior.
- Eliminate source failures; hand off platform anomalies with evidence.

## Non-Goals

- Backlinks, rankings, Search Console outcomes, or score guarantees.
- New claims, pricing, integrations, content strategy, or unapproved legal changes.
- Backend, dashboard/admin, shared-web, Cloudflare, or hostname ownership beyond handoff.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `marketing-a11y-seo`: extend the contract for performance, accessibility, bilingual SEO, crawl integrity.

## Acceptance Direction

All 12 routes must pass metadata, canonical/hreflang, heading, indexability, robots/sitemap, link, and crawl-graph checks. Lighthouse runs must show no accessibility/SEO regression and meet approved budgets, split by consent-visible and seeded states. Privacy/legal controls remain intact. Record before/after evidence; 90+ is a target.

## Approach

Use existing layout, pages, locale data, routes, and tests. Reproduce findings against source/build output, apply minimal changes, and verify every locale. Treat `/cdn-cgi/l/email-protection` and `www` redirects as platform work requiring ownership and live verification.

## Surfaces

| Area | Impact | Description |
|------|--------|-------------|
| `apps/web/marketing/src` | Modified | Head, semantics, links, locale, scripts, and runtime cost |
| `apps/web/marketing/{tests,src/__tests__}` | Modified | SEO, a11y, consent, crawl, regression coverage |
| `docs/marketing`, `openspec/specs/marketing-a11y-seo/spec.md` | Modified | Evidence, budgets, ownership, requirements |

## Evidence

The 54 baseline and 90+ goal are user-provided; Ahrefs `9293424` was unavailable. Reports fetched 2026-08-30 show home performance 0.92/best practices 0.81; accessibility performance 0.62/accessibility 0.98; legal performance 0.62–0.79/accessibility 0.98; sampled SEO scores were 1. The anomaly is Cloudflare Email Protection, not a marketing route. These are lab snapshots, not field data or rankings.

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Consent, animation, or localized/legal behavior regresses | Med | Preserve contracts; test first-visit, seeded, reduced-motion, and all routes |
| Cloudflare/cache drift masks a source fix | High | Separate operator handoff and live verification |
| Lab performance varies | High | Record configuration and separate lab/field evidence |

Rollback: revert source, tests, docs, and spec together; roll back deployment transformations separately and purge affected cache.

## Dependencies

- Ahrefs export/crawl identifier or repeatable comparison measurement.
- Operator access for email obfuscation, cache, HSTS, and `www` redirect verification.

## Success

- [ ] 12-route SEO, crawl, a11y, and performance checks meet the contract without privacy regressions.
- [ ] Source failures are eliminated; platform anomalies have an evidenced owner and disposition.
- [ ] Reproducible before/after evidence and limitations are recorded.
