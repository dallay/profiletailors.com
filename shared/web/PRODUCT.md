# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

TypeScript, Zod 4, localStorage API, `window`-global contract for analytics gate.

## Users

No direct users — this is a shared library consumed by both the marketing Astro site and the Vue 3 dashboard app. It defines the consent contract once so both surfaces stay in sync.

## Product Purpose

Provide a canonical, versioned definition of the GDPR-inspired consent model used across the Profile Tailors product family. Encodes: consent receipt schema, validation rules, localStorage persistence, privacy signal detection (DNT/GPC), and the `window.__PT_CONSENT_ANALYTICS` global that analytics scripts must check before loading.

## Positioning

Infrastructure, not a product. Positioning is defined by the surfaces that consume it. Any change to this library is a cross-cutting concern that must be validated against both the marketing site and the dashboard app simultaneously.

## Operating Context

- Consumed at build time by both `apps/web/marketing/` and `apps/web/app/`
- Exported as `@profiletailors/shared-web` via the pnpm workspace
- Consent version (`CURRENT_CONSENT_VERSION`) and policy version (`CURRENT_POLICY_VERSION`) are the source of truth — both surfaces must agree on these values
- Invalid or missing receipts degrade gracefully: no consent is assumed, banner re-shown
- DNT/GPC signals pre-disable the analytics toggle when detected by `detectPrivacySignals()`

## Capabilities and Constraints

- `ConsentReceipt` interface: version, policy, timestamp, region (EU), categories (necessary + analytics), DNT flag, source (banner | settings-panel)
- Zod validation schema (`validateConsentReceipt()`) with graceful degradation
- localStorage persistence via `loadConsent()` / `saveConsent()` / `clearConsent()`
- Privacy signal detection: DNT (Do Not Track) and GPC (Global Privacy Control)
- Window global: `window.__PT_CONSENT_ANALYTICS` set by inline script after consent is validated
- Version upgrade path: increment `EXPECTED_CONSENT_VERSION` in `validation/consent.ts` — old receipts fail validation, banner re-shows

**Constraints:**
- No server-side consent storage at early-access stage
- Region is hardcoded to `EU` — future markets require schema extension
- Only two consent categories: `necessary` (always true) and `analytics` (opt-in)
- Source values are limited to `banner` and `settings-panel`

## Brand Commitments

None — this is a library with no surface. Brand commitments live in the consuming products.

## Evidence on Hand

- Schema and validation logic in `types/consent.ts` and `validation/consent.ts`
- Storage and signal detection in `utils/consent-storage.ts` and `utils/privacy-signals.ts`
- No test fixtures fabricating consent scenarios — tests use real validation against the schema

## Product Principles

1. Fail closed — any invalid receipt is treated as no consent; analytics do not load
2. Versioned contracts — the schema is immutable once deployed; upgrades require a new version
3. No side effects — validation is pure; storage operations are explicit and auditable
4. Synchronised across surfaces — both marketing and app share the same schema and validation

## Accessibility & Inclusion

No user-facing surface. No additional accessibility requirements beyond what consuming products define.
