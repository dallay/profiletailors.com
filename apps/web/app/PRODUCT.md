# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

Vue 3 SPA (Vue Router 5, Pinia), shadcn-vue + Tailwind CSS 4, vue-i18n, Zod, Vitest, Playwright E2E.

## Users

Authenticated early-access users managing social media presence for personal brand, freelance work, or small business. Primary use case: plan and schedule LinkedIn and Threads content from a focused workspace. Most users are non-technical. One operator account exists for platform management.

## Product Purpose

Let users create, schedule, and review social content from a clean workspace. The scheduler is the core product — a calendar-driven view of the publishing pipeline. Analytics, media library, ideas capture, and governance tools are supporting surfaces. The user measures success by having fewer tools to juggle and more confidence that content reaches the right platforms.

## Positioning

Not a generic social media dashboard. Profile Tailors is a workspace for deliberate content — not an AI content generator, not a discovery tool, not a full agency suite. LinkedIn was the first integrated platform and Threads is the second; further integrations follow after validation. The interface earns trust through scanability and precision, not novelty.

## Operating Context

- Authenticated users access via `https://pt-app.localhost` (production: `https://app.profiletailors.com`)
- Users connect one or more LinkedIn or Threads accounts via OAuth
- Content creation and scheduling happen in-browser; publishing executes server-side via stored OAuth tokens
- Dashboard state (workspace, theme, sidebar) persists in localStorage
- After human-authentication cutover, the dashboard starts human login through its separate `pt-app`
  BFF initiation. Provider tokens never enter browser JavaScript; the BFF session uses an HttpOnly
  cookie, and unsafe session-authenticated requests require CSRF protection
- Local password login, registration, password recovery, email verification, and refresh-token
  behavior are pre-cutover behavior only. They are not available as post-cutover human-authentication
  flows; the cutover must not retain a local-human-credential transition window
- EU data residency: Oracle Cloud Frankfurt; CDN: Cloudflare global edge
- Analytics loading is gated by user consent (banner → localStorage → `window.__PT_CONSENT_ANALYTICS`)

## Capabilities and Constraints

**Authenticated surfaces:**

- Dashboard home: welcome, quick-create post, pipeline overview
- Scheduler: calendar (week/day/month) + list view; create, edit, delete, reorder posts
- Analytics: publication performance metrics (planned — surface exists, data connection TBD)
- Media Library: upload, browse, organise uploaded assets
- Ideas: capture and organise content ideas (planned — surface exists)
- Shortlinks: when creating and submitting a new publication, automatically shorten each distinct URL in the post content. Shortening is best-effort: failed URLs remain unchanged and the composer shows a non-blocking warning. Existing publications are not rewritten when edited. Users can review recorded redirect counts in authenticated, paginated workspace analytics.
- Governance: content takedown request workflow
- Settings: profile, workspace, integrations (LinkedIn and Threads OAuth), API keys
- Auth flows: Keycloak-backed human login and logout through the dashboard BFF; LinkedIn and Threads
  callbacks remain separate integration flows. Local password registration, recovery, verification,
  and refresh behavior apply only before human-authentication cutover
- Invitation acceptance: an operator must provision the local principal and approved OIDC identity
  link before Keycloak access. The invitee then signs in through the BFF and accepts the workspace
  invitation. Invitation data and OIDC claims do not create or link accounts, and email equality
  does not link principals

**Constraints:**

- LinkedIn and Threads are the connected platforms at early-access stage
- Publishing is gated until integrations are validated
- No multi-workspace UI yet for non-owner members
- Admin surface is a separate app (`@profiletailors/admin`) — not reachable from this app

## Brand Commitments

- Name: "Profile Tailors" (app shell shows product name, not app-level branding)
- Aesthetic: inherits root DESIGN.md — Nothing-inspired monochrome, dark-first, Space Grotesk / Space Mono / Doto
- Tone: functional, precise. No gamification, no celebration animations
- No third-party branding in UI chrome — integrations show their own logos

## Evidence on Hand

- Real i18n strings maintained in `src/i18n/` (not fabricated)
- No real analytics data yet (surface built, data pipeline TBD)
- No real user-generated content at early-access stage

## Product Principles

1. Scanability first — the dashboard answers "what is planned and what needs attention" in one glance
2. Consistent conventions — every surface follows the same component language, spacing, and interaction model
3. Consent is structural — analytics cannot load without a valid consent receipt; the app degrades gracefully
4. Errors are specific — copy explains what went wrong and what to do; never generic "something went wrong"
5. Auth is secure by default — HttpOnly BFF session cookies, no provider token in browser JavaScript,
   CSRF protection for unsafe requests, and session hydration on every load

## Accessibility & Inclusion

WCAG 2.2 Level AA target. Automated axe-core checks gate PRs; manual screen-reader testing ongoing. Known gaps: calendar keyboard navigation, media picker drag-and-drop keyboard alternative, form validation announcements. EAA applies as a monitoring exercise at early-access stage; not yet a binding compliance obligation.

## Internal identifier format

Internal entity and aggregate IDs use canonical lowercase UUID strings without resource prefixes.
The client sends IDs unchanged, including `X-Workspace-Id`, under the existing
`application/vnd.api.v1+json` media type. Provider-owned IDs, credentials, tokens, idempotency keys,
and opaque keys retain their own formats. There is no permanent prefixed-ID compatibility path.

This contract follows [ADR-0029](../../../docs/architecture/adr/0029-use-raw-uuid-identifiers.md).
Local implementation does not certify production data conversion. Production deployment requires
operator-reviewed migration and production validation.
