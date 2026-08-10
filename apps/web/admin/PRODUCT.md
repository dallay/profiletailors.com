# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

Vue 3 SPA (Vue Router 5, Pinia), shadcn-vue + Tailwind CSS 4, vue-i18n, Zod, Vitest.

## Users

Platform operator (Yuniel Acosta, the sole admin at early-access stage). Used for internal waitlist management, user administration, and audit review. No public URL — internal-only surface.

## Product Purpose

Give the operator the tools to manage early-access operations: review waitlist signups, inspect user accounts, manage platform permissions, and audit system activity. This is an operations tool, not a user-facing product.

## Positioning

Internal tooling with the same visual language as the dashboard app. No differentiation needed — this surface is never seen by users. Security and correctness take priority over aesthetics.

## Operating Context

- Accessed via a separate portless app (`pt-admin.localhost`) — distinct from the user-facing dashboard app
- Requires platform-level permission (`platform.*` scope) in addition to regular auth
- Separate auth store (`useAdminAuthStore`) from the main app
- Permission gates on every route (e.g., `platform.dashboard.read`, `platform.users.read`, `platform.waitlist.read`, `platform.audit.read`)
- Audit log surface shows system activity — sensitive; access is restricted to operator role

## Capabilities and Constraints

- Login (separate from user auth)
- Dashboard: high-level platform overview (waitlist count, user count, recent activity)
- Waitlist: list and inspect waitlist entries
- Users: browse all registered users, inspect individual account details
- Audit: search and filter system audit logs
- Access denied view for users who authenticate but lack required permissions

**Constraints:**
- No user self-service, no onboarding flows, no email sending
- No public-facing surface — this app is not discoverable
- No multi-admin support at early-access stage

## Brand Commitments

Same root DESIGN.md visual language as the dashboard app. No additional branding constraints.

## Evidence on Hand

- No user-facing content; all evidence is internal operational data
- Waitlist data originates from the marketing site's waitlist form (client-side only at this stage; backend persistence per ADR-0011)
- Audit logs are generated server-side by the Spring Boot backend

## Product Principles

1. Correct over fast — every action has appropriate confirmation and audit trail
2. Least privilege — routes are gated by specific permissions, not a single admin role
3. Readable data — tables and views prioritise information density and scanability
4. No magic — no bulk actions without explicit confirmation

## Accessibility & Inclusion

Same accessibility standards as the dashboard app. This surface is internal-only but not exempt — internal tools should also be accessible.
