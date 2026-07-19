# Archive Entry: Reusable Lead Capture Waitlist

**Change**: `reusable-lead-capture-waitlist`
**Archived**: 2026-07-18
**Archive Path**: `openspec/changes/archive/2026-07-18-reusable-lead-capture-waitlist/`
**Verdict**: PASS ✅

---

## SDD Cycle Summary

| Phase | Status | Artifact |
|-------|--------|----------|
| Proposal | ✅ Complete | `proposal.md` |
| Spec | ✅ Complete | `spec.md`, `specs/lead-capture-common/spec.md`, `specs/lead-capture-waitlist/spec.md` |
| Design | ✅ Complete | `design.md` |
| Tasks | ✅ Complete | `tasks.md` (48 tasks, 48 completed) |
| Apply | ✅ Complete | `apply-progress.md` — All phases 1–9 implemented |
| Verify | ✅ Complete | `verification-report.md` — 36/36 scenarios compliant, ~106+ tests pass |
| Archive | ✅ Complete | This entry |

## Specs Synced to Canonical Locations

| Domain | Action | Details |
|--------|--------|---------|
| `lead-capture-common` | Already in place | `openspec/specs/lead-capture-common/spec.md` — 13 scenarios (6 requirements) |
| `lead-capture-waitlist` | Already in place | `openspec/specs/lead-capture-waitlist/spec.md` — 23 scenarios (9 requirements) |

> **Note**: Los delta specs fueron escritos directamente en la ubicación canónica durante la fase `spec`. No se requirió merge porque no existían specs previos en esas ubicaciones. Ambos specs ya están en su forma final y han sido verificados contra la implementación.

## Design Synced

| Source | Status |
|--------|--------|
| `design.md` | ✅ Archived — diseño hexagonal validado: `common → waitlist → server/smp` |
| ADR-0011 | ✅ Accepted — `docs/architecture/adr/0011-reusable-lead-capture-waitlist.md` |

## Archive Contents

| Artifact | Status |
|----------|--------|
| `proposal.md` | ✅ |
| `spec.md` | ✅ (combined spec, 438 lines) |
| `specs/lead-capture-common/spec.md` | ✅ |
| `specs/lead-capture-waitlist/spec.md` | ✅ |
| `design.md` | ✅ |
| `tasks.md` | ✅ (48/48 tasks complete) |
| `apply-progress.md` | ✅ |
| `verification-report.md` | ✅ (fresh from sdd-verify) |
| `state.yaml` | ✅ (`current_phase: archive`) |

## Implementation Summary

### Modules Delivered

| Module | Package | Description |
|--------|---------|-------------|
| `shared/lead-capture/common` | `com.profiletailors.leadcapture.common` | 5 VOs: `EmailAddress`, `NormalizedEmail`, `CaptureSource`, `CaptureLocale`, `LeadMetadata` |
| `shared/lead-capture/waitlist` | `com.profiletailors.leadcapture.waitlist` | Domain: `Waitlist`, `WaitlistEntry`, `WaitlistConsent`; Application: `JoinWaitlistCommand`, `JoinWaitlistHandler`, ports |
| `server/smp` infrastructure | HTTP, R2DBC, config, rate limit | `WaitlistController`, `R2dbcWaitlistRepositories`, Liquibase migrations, seed data |
| `apps/web/marketing` | Astro component | `WaitlistForm.astro` with validation, consent, and backend integration |

### Key Architecture Decisions

1. **Two-tier shared modules**: `common` (pure VOs) → `waitlist` (domain + application + ports), both framework-free
2. **Consent in waitlist domain**, not common — bounded-context-specific
3. **Idempotent public join API** — same `202 accepted` for new and duplicate joins, preventing email enumeration
4. **Conservative email normalization** — trim + lowercase only, no Gmail/provider canonicalization
5. **Per-waitlist deduplication** via `UNIQUE(waitlist_id, normalized_email)`
6. **Metadata whitelist** — 9 approved keys, max 5 entries, max 200 bytes per value

### Test Coverage

| Layer | Test Count | Status |
|-------|-----------|--------|
| Shared common VOs | ~5 test files | ✅ PASS |
| Shared waitlist domain/application | 11 test files + ArchUnit | ✅ PASS |
| Server SMP infrastructure | 8 files (38 tests) | ✅ PASS |
| Frontend | 2 files (14 tests) + Playwright E2E | ✅ PASS |
| **Total** | **20+ test files / ~106+ tests** | **✅ ALL PASS** |

### Source of Truth Updated

The following canonical specs now reflect the new behavior:
- `openspec/specs/lead-capture-common/spec.md`
- `openspec/specs/lead-capture-waitlist/spec.md`

The following architecture document reflects the design:
- `docs/architecture/adr/0011-reusable-lead-capture-waitlist.md` (Accepted)

## Known Warnings (Post-Archive)

| Issue | Severity | Status |
|-------|----------|--------|
| DALLAY-512 — Distributed bucket backend needed before enabling WAITLIST rate limit in multi-replica | WARNING | Deferred. Rate-limit defaults to `false`. |
| DALLAY-513 — Trusted-proxy allowlist needed before enabling WAITLIST rate limit behind shared ingress | WARNING | Deferred. Rate-limit defaults to `false`. |
| `WaitlistConsent` version string `"2026-07-17"` hardcoded in controller | SUGGESTION | Consider extracting to config constant |
| Frontend metadata whitelist mirrors backend — no shared constants module | SUGGESTION | Consider shared module to prevent drift |

## SDD Cycle Complete

The change has been fully planned, implemented, verified, and archived.
Ready for the next change.
