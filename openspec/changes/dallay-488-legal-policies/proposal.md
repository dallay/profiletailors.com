# Proposal: Publish Privacy Policy, Terms, Cookie Policy and Acceptable Use Policy

## Overview

### Intent

Publish four legally-required policy pages (Privacy Policy, Terms of Service, Cookie Policy, Acceptable Use Policy) on the marketing site in EN and ES, with footer links, to meet GDPR, CCPA, and platform API requirements before launch.

### Changes

#### In Scope
- Four new Astro pages with EN/ES variants: Privacy Policy (`/privacy/`), Terms of Service (`/terms/`), Cookie Policy (`/cookies/`), Acceptable Use Policy (`/acceptable-use/`)
- Each page follows the thin route → shared component pattern: `pages/privacy.astro` → `_PrivacyPolicy.astro`
- Policy content stored as structured i18n objects in `src/i18n/en.ts` and `src/i18n/es.ts` (Option A — keeps type safety, follows existing pattern)
- Footer component updated with legal link group
- `Layout.astro` updated with `sameAs` pointers in JSON-LD structured data
- Compliance inputs from `docs/compliance/` (data-inventory.yaml, controller-processor-matrix) used as source material for Privacy Policy sections (data categories, processor list, retention schedules)

#### Out of Scope
- Cookie consent banner / cookie management UI (handled as separate future change)
- Backend changes — all policy pages are static SSG
- Vue dashboard legal pages — link directly to marketing site URLs
- Legal review gate or sign-off workflow (content is drafted inline from compliance inputs)
- PDF or printable versions of policies
- `/ccpa` or `/gdpr` dedicated pages (rights are covered within Privacy Policy)

### Capabilities

#### New Capabilities
- `legal-pages`: Static policy pages on the marketing site in EN and ES with footer navigation and compliance-sourced content.

#### Modified Capabilities
None

### Approach

1. Add top-level `legal` key to both `en.ts` and `es.ts` with nested section objects (`privacy`, `terms`, `cookies`, `aup`). Each section contains subsection keys for the policy body (e.g., `privacy.intro`, `privacy.dataCollected`, `privacy.dataUsage`).
2. Create four shared page components (`_PrivacyPolicy.astro`, `_TermsPage.astro`, `_CookiePolicyPage.astro`, `_AcceptableUsePage.astro`) — each receives its translated content as props and renders a `Layout` with appropriate meta.
3. Create thin route wrappers: `pages/privacy.astro` → `_PrivacyPolicy.astro locale="en"`, `pages/es/privacy.astro` → `_PrivacyPolicy.astro locale="es"`, etc.
4. Update `Footer.astro` to accept a `legalLinks` prop and render a legal link group.
5. Update `_HomePage.astro` and all policy pages to pass footer links.
6. Draft policy content drawing from `docs/compliance/data-inventory.yaml` for data categories, processor list, retention schedules, and controller/processor role definitions.

### Open Questions

| Question | Recommendation |
|----------|---------------|
| Content approach: i18n objects (A) or Markdown files (B)? | **A** — i18n objects win for type safety and consistency. Markdown files would need a separate renderer and break the typed pattern. |
| AUP as standalone page or subsection of Terms? | **Standalone** — common SaaS pattern, easier to link from footer, Terms stay focused on service contract. |
| Effective date policy? | Use the deploy date or a fixed "Last updated" field in the i18n object. Recommend a single date constant per release cycle. |
| Cookie table detail level? | List current providers (Vercel, Cloudflare, Auth0) + placeholder row for future analytics. Source from data-inventory.yaml. |

### Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Legal text has errors or omissions without a legal review | Medium | Source from compliance docs that map to actual processing activities; add a review step in tasks |
| Policy content changes require i18n edits across two files | Low | Structured objects make this mechanical; the translator (human or AI) edits both `en.ts` and `es.ts` in parallel |
| Footer gets cluttered with 4 new links | Low | Group under a "Legal" heading or separator, keep visual hierarchy clean |

### Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `apps/web/marketing/src/pages/{privacy,terms,cookies,acceptable-use}.astro` | New | EN route wrappers |
| `apps/web/marketing/src/pages/es/{privacy,terms,cookies,acceptable-use}.astro` | New | ES route wrappers |
| `apps/web/marketing/src/pages/_{PrivacyPolicy,TermsPage,CookiePolicyPage,AcceptableUsePage}.astro` | New | Shared page components |
| `apps/web/marketing/src/i18n/en.ts` | Modified | Add `legal` key with four policy objects |
| `apps/web/marketing/src/i18n/es.ts` | Modified | Add `legal` key with Spanish translations |
| `apps/web/marketing/src/components/Footer.astro` | Modified | Accept and render legal link group |
| `apps/web/marketing/src/layouts/Layout.astro` | Modified | Add `sameAs` URLs to JSON-LD |

### Rollback Plan

- Static pages: revert route files, footer component, and i18n additions to previous commit.
- No database changes — zero rollback risk on the data layer.
- Pages are unlinked until Footer is updated; footer change is the atomic publish point.

## Related

- Linear Issue: DALLAY-488
- Compliance Docs: `docs/compliance/data-inventory.yaml`, `docs/compliance/controller-processor-matrix.md`
