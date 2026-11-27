# ADR-0038: Public Static Changelog Feeds and Cross-App In-App Consumption

- Status: Accepted
- Date: 2026-11-27
- Decision owners: @yacosta738
- Scope: `apps/web/marketing`, `apps/web/app`, `shared/web`
- Supersedes: None
- Superseded by: None
- Related:
  - Issues/PRs: PR #1468
  - Docs: `docs/infrastructure/cloudflare-deployment.md`, `apps/web/marketing/PRODUCT.md`, `apps/web/app/PRODUCT.md`
  - ADRs: [ADR-0007](./0007-astro-and-vue-frontend-split.md), [ADR-0010](./0010-shared-kernel-governance.md), [ADR-0022](./0022-release-driven-frontend-deployment.md)

## Context

Profile Tailors publishes product updates and release announcements on its public marketing site
built with Astro (`apps/web/marketing`). Simultaneously, users of the authenticated dashboard SPA
(`apps/web/app`) require in-app awareness of recent features, improvements, and fixes without
relying on manual notification emails or external status pages.

Per [ADR-0007](./0007-astro-and-vue-frontend-split.md), the marketing site and the application SPA
are deployed as separate frontends. Per [ADR-0022](./0022-release-driven-frontend-deployment.md), both
are deployed to Cloudflare Pages. Maintaining a database-backed or server-rendered changelog service
would introduce backend complexity, unnecessary database queries, and deployment coupling for content
that is static and public by nature.

## Decision drivers

- Single source of truth for changelog entries across marketing and in-app surfaces.
- Zero server compute and zero database load for public changelog reads.
- Framework-neutral contracts and types shared between Astro and Vue without importing app internals.
- Safe cross-origin access from the app SPA to marketing static feeds.

## Decision

1. **Astro Content Collections as Canonical Source of Truth:**
   Maintain changelog entries as Markdown files with strict frontmatter schema under
   `apps/web/marketing/src/content/changelog/{locale}/`. Astro generates the static HTML pages, RSS
   feeds (`/changelog/rss.xml`, `/es/changelog/rss.xml`), and JSON feeds (`/changelog.json`,
   `/es/changelog.json`) during build.

2. **Cross-Origin Feed Consumption:**
   The dashboard SPA (`apps/web/app`) consumes `/changelog.json` or `/es/changelog.json` directly from
   the marketing origin (`https://profiletailors.com`, overridable via `VITE_MARKETING_URL` in local/staging
   environments).
   Explicit `Access-Control-Allow-Origin: *` headers are configured for `/changelog.json` and
   `/es/changelog.json` in `apps/web/marketing/public/_headers`.

3. **Shared Kernel Governance (`shared/web`):**
   Changelog domain types (`ChangelogFeed`, `ChangelogEntry`, `ChangelogCategory`, etc.), Zod validation
   schemas (`validateChangelogFeed`), and unread storage utilities (`localStorage` persistence) reside in
   `@profiletailors/shared-web`. Both frontends consume this package without direct cross-app imports.

4. **Client-Side Read State Tracking:**
   The dashboard tracks seen changelog entries in browser `localStorage` keyed by `pt-changelog-seen`.
   Marking entries as read does not require a backend API call.

## Consequences

- **Positive:**
  - Fast, edge-cached delivery via Cloudflare Pages CDN with minimal latency and high availability.
  - No new backend endpoints, database tables, or migrations required.
  - Changelog content authoring uses standard Git and Markdown workflows with full i18n support.
- **Negative and Risks:**
  - Wildcard CORS (`*`) on JSON feeds exposes changelog content publicly (accepted because product
    changelogs are public marketing material).
  - Cross-origin HTTP fetch from the dashboard depends on the availability of the marketing origin. If
    marketing is unreachable, the dashboard modal degrades gracefully into an error state.

## Alternatives Considered

### Dedicated Backend SMP Micro-Endpoint

- Rejected: Incurs database storage, API maintenance, and reactive backend overhead for static markdown
  content that changes infrequently.

### Build-Time Asset Bundling into SPA

- Rejected: Would require rebuilding and redeploying `apps/web/app` every time a new changelog post is
  written on marketing, violating deployment independence.

## References

- [ADR-0007: Astro and Vue Frontend Split](./0007-astro-and-vue-frontend-split.md)
- [ADR-0010: Shared Kernel Governance](./0010-shared-kernel-governance.md)
- [ADR-0022: Release-Driven Frontend Deployment to Cloudflare Pages](./0022-release-driven-frontend-deployment.md)
