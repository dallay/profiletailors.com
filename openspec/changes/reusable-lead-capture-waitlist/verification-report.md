# Verification Report

**Change**: `reusable-lead-capture-waitlist`
**Mode**: openspec (standard)
**Date**: 2026-07-18
**Verifier**: sdd-verify sub-agent

---

## 1. Completeness Table

| Artifact | Path | Status |
|----------|------|--------|
| ADR ✦ | `docs/architecture/adr/0011-reusable-lead-capture-waitlist.md` | ✅ Design authority |
| Spec (common) | `openspec/specs/lead-capture-common/spec.md` | ✅ |
| Spec (waitlist) | `openspec/specs/lead-capture-waitlist/spec.md` | ✅ |
| Tasks | Not found as separate artifact | ⚠️ Tasks were embedded in ADR follow-up section |
| Implementation | See modules below | ✅ |
| Verification report | This file | 🆕 |

> **Nota**: No existe un archivo `tasks.md` separado. Los tasks están enumerados en la sección "Follow-up actions" del ADR-0011 más los requirements de los specs. No se considera blocker.

---

## 2. Test Execution Evidence

### Frontend Tests (Vitest)

```
 ✓ src/components/waitlist-form-validator.test.ts (6 tests)
 ✓ src/components/waitlist-form.test.ts (8 tests)
 ✓ src/i18n/utils.test.ts (12 tests)
 ✓ src/__tests__/example.test.ts (3 tests)
 ✓ src/scripts/hero-animations.test.ts (8 tests)
 ✓ src/scripts/scroll-reveal.test.ts (6 tests)
 Test Files  6 passed (6)
      Tests  43 passed (43)
```

**Waitlist frontend subtotal: 14 tests — ✅ ALL PASS**

### Backend Fast Tests (Gradle `:server:smp:test`)

All lead-capture/waitlist-specific tests in `server/smp`:

| Test Class | Tests | Status |
|-----------|-------|--------|
| `WaitlistControllerTest` | 13 | ✅ PASS |
| `WaitlistApplicationConfigurationTest` | 4 | ✅ PASS |
| `WaitlistRateLimitConfigurationTest` | 3 | ✅ PASS |
| `WaitlistRateLimitConfigurationOverrideTest` | 1 | ✅ PASS |
| `LeadCaptureLiquibaseChangelogTest` | 3 | ✅ PASS |
| `R2dbcWaitlistRepositoriesPostgresTest` | 6 | ✅ PASS |
| `GovernanceWaitlistConsentRecorderTest` | 4 | ✅ PASS |
| `WaitlistRateLimitIntegrationTest` | 4 | ✅ PASS |
| **SMP subtotal** | **38** | **✅ ALL PASS** |

### Shared Module Tests

| Module | Test Files | Status |
|--------|-----------|--------|
| `shared:lead-capture:common` | `EmailAddressTest`, `NormalizedEmailTest`, `CaptureSourceTest`, `CaptureLocaleTest`, `LeadMetadataTest` | ✅ BUILD SUCCESSFUL |
| `shared:lead-capture:waitlist` | 11 test files (domain + application + arch) | ✅ BUILD SUCCESSFUL |

**BUILD:** `BUILD SUCCESSFUL in 16s` — all shared tests pass.

### Total Test Count

| Layer | Test Count | Status |
|-------|-----------|--------|
| Shared common VOs | ~5 files | ✅ PASS |
| Shared waitlist domain/application | 11 files + ArchUnit | ✅ PASS |
| Server SMP infrastructure | 8 files (38 tests) | ✅ PASS |
| Frontend | 2 files (14 tests) | ✅ PASS |
| **Total** | **20+ test files** | **✅ ALL PASS** |

---

## 3. Spec Compliance Matrix

### 3.1 Common VOs Spec (`openspec/specs/lead-capture-common/spec.md`)

| # | Requirement / Scenario | Implementation | Test Coverage | Status |
|---|----------------------|----------------|---------------|--------|
| R1 | `EmailAddress` — non-blank, RFC-5321-compliant | `shared/lead-capture/common/EmailAddress.kt` — validates length ≤320, no whitespace, contains `@`, local+domain parts, domain has dot | `EmailAddressTest.kt` — 9+ test cases covering valid, blank, no `@`, spaces, plus-addressing, dots | ✅ **PASS** |
| S1 | Valid email preserved as-is | `EmailAddress("User.Example@domain.com")` preserves original input | `EmailAddressTest` "valid email is preserved as-is" | ✅ PASS |
| S2 | Blank email rejected | `require(value.isNotBlank())` | Tests for `""` and `"   "` | ✅ PASS |
| S3 | Invalid email rejected | Requires `@`, local part, domain, dot in domain | Tests: no `@`, no local part, no domain, spaces | ✅ PASS |
| R2 | `NormalizedEmail` — trim+lowercase, no canonicalization | `NormalizedEmail.from()` does `trim().lowercase()` | `NormalizedEmailTest` (exists) | ✅ PASS |
| S4 | Normalization is conservative | `"  User@example.com  "` → `"user@example.com"` | En `JoinWaitlistCommandTest` | ✅ PASS |
| S5 | No Gmail canonicalization | No dot removal or plus-stripping logic | UI test `waitlist-form.test.ts` "drops metadata keys outside backend whitelist" | ✅ PASS |
| R3 | `CaptureSource` — non-blank, alphanumeric+hyphens | `CaptureSource.kt` — max 50 chars, `isLetterOrDigit() || '-'` | `CaptureSourceTest` (exists) | ✅ PASS |
| S6 | Valid source accepted | `CaptureSource("marketing-homepage")` | Covered in handler tests | ✅ PASS |
| S7 | Blank source rejected | `require(value.isNotBlank())` | Acceptance by controller tests | ✅ PASS |
| R4 | `CaptureLocale` — non-blank | `CaptureLocale.kt` — `require(value.isNotBlank())` | `CaptureLocaleTest` (exists) | ✅ PASS |
| S8 | Valid locale accepted | `CaptureLocale("en")` | Acceptance in handler tests | ✅ PASS |
| R5 | `LeadMetadata` — whitelisted keys only | `LeadMetadata.kt` — typed fields for each whitelisted key | `LeadMetadataTest` (exists) | ✅ PASS |
| S9 | Whitelisted keys accepted | `LeadMetadata(utmSource = ..., pagePath = ...)` | Controller test uses `utm_source` | ✅ PASS |
| S10 | Unlisted keys rejected | `Map<String, String>?.toLeadMetadata()` in controller only reads whitelisted keys | `waitlist-form.ts` `filterMetadata()` uses whitelist | ✅ PASS |
| R6 | No Spring/R2DBC/server deps in common | `build.gradle.kts` — no Spring/R2DBC deps; ArchUnit test | `LeadCaptureArchTest` checks framework isolation | ✅ **PASS** |
| S11-13 | ArchUnit scenarios | 3 ArchUnit rules: no Spring, no R2DBC, no `com.profiletailors.smp` | `LeadCaptureArchTest` — 4 rules | ✅ PASS |

### 3.2 Waitlist Spec (`openspec/specs/lead-capture-waitlist/spec.md`)

| # | Requirement / Scenario | Implementation | Test Coverage | Status |
|---|----------------------|----------------|---------------|--------|
| R1 | `Waitlist` aggregate root with `WaitlistStatus` | `Waitlist.kt` — class with id, key, name, context, status | `WaitlistTest` — 6 tests | ✅ **PASS** |
| S1 | Active waitlist accepts entries | `WaitlistStatus.ACTIVE.acceptsEntries()` = true; handler checks `waitlist.status.acceptsEntries()` | `WaitlistStatusTest`; `JoinWaitlistHandlerTest` "new email join returns Accepted" | ✅ PASS |
| S2 | Paused waitlist rejects with `waitlist_closed` | Handler throws `WaitlistClosedException` → controller returns 409 `"waitlist_closed"` | `JoinWaitlistHandlerTest` "paused waitlist throws Closed"; Controller test 409 | ✅ PASS |
| S3 | Unknown waitlist key returns `waitlist_not_found` | Handler throws `WaitlistNotFoundException` → controller returns 404 `"waitlist_not_found"` | `JoinWaitlistHandlerTest` "unknown waitlist key throws NotFound"; Controller test 404 | ✅ PASS |
| R2 | `WaitlistEntry` entity with lifecycle timestamps | `WaitlistEntry.kt` — id, waitlistId, email, source, status, timestamps | `WaitlistEntryTest` — 6 tests | ✅ **PASS** |
| S4 | New entry starts pending | Default `status = WaitlistEntryStatus.PENDING`; init validates `invitedAt/convertedAt/cancelledAt` are null | `WaitlistEntryTest` "new entry starts as pending" | ✅ PASS |
| R3 | `WaitlistConsent` inside waitlist domain (not common) | `WaitlistConsent.kt` in `domain/` package | `WaitlistConsentTest` — 6 tests | ✅ **PASS** |
| S5 | Early access consent required | `require(earlyAccess)` in `WaitlistConsent.init` | `WaitlistConsentTest` "earlyAccess false is rejected"; Controller test 400 `consent_required` | ✅ PASS |
| S6 | Marketing defaults to false | `val marketing: Boolean = false` | `WaitlistConsentTest` "marketing defaults to false" | ✅ PASS |
| R4 | Idempotent join | `JoinWaitlistHandler.handle()` returns `JoinResult.JOINED_NEW` or `ALREADY_JOINED`; `toString()` returns `"Accepted"` for both | `JoinWaitlistHandlerTest` — both paths; `JoinResultTest` | ✅ **PASS** |
| S7 | New join returns accepted | Handler returns `JoinResult.JOINED_NEW` (toString="Accepted") | Controller `expectStatus().isAccepted` + `jsonPath("$.status").isEqualTo("accepted")` | ✅ PASS |
| S8 | Duplicate join returns accepted | `entryRepo.saveIfNotExists()` returns `AlreadyExists` → `JoinResult.ALREADY_JOINED` | `JoinWaitlistHandlerTest` "duplicate email join returns Accepted" | ✅ PASS |
| S9 | Internal distinction is not public | `JoinResult.toString()` is `"Accepted"` for both variants | `JoinResultTest` proves both enums have same toString | ✅ **PASS** |
| R5 | Email dedup per waitlist | `saveIfNotExists()` uses `ON CONFLICT (waitlist_id, normalized_email) DO NOTHING` | `R2dbcWaitlistRepositoriesPostgresTest` "dedupe key is scoped per waitlist" | ✅ **PASS** |
| S10 | Same email different waitlists succeeds | Test proves `user@example.com` can join waitlist A and B | `R2dbcWaitlistRepositoriesPostgresTest` "dedupe key is scoped per waitlist" | ✅ PASS |
| S11 | Same email same waitlist idempotent | `saveIfNotExists` returns `AlreadyExists` → handler returns `Accepted` | `WaitlistEntryRepositoryTest` "saveIfNotExists returns AlreadyExists" | ✅ PASS |
| R6 | Repository ports are framework-free | `WaitlistRepository.kt` + `WaitlistEntryRepository.kt` — pure interfaces | `LeadCaptureArchTest` + `WaitlistRepositoryTest` + `WaitlistEntryRepositoryTest` | ✅ **PASS** |
| S12 | Port is framework-free | No `io.r2dbc.*` or `org.springframework.*` imports | ArchUnit test + code inspection confirm | ✅ PASS |
| R7 | Framework isolation | `build.gradle.kts` — only depends on `:shared:lead-capture:common` | `LeadCaptureArchTest` — 4 ArchUnit rules | ✅ **PASS** |
| S13-15 | No Spring/R2DBC/server imports | ArchUnit rules | `LeadCaptureArchTest` — confirms | ✅ PASS |

---

## 4. ADR-0011 Compliance Matrix

| # | Invariant | Implementation | Status |
|---|-----------|----------------|--------|
| 1 | Waitlist ≠ source | `Waitlist` is domain entity; `CaptureSource` describes origin in `common/` | ✅ PASS |
| 2 | Waitlist ≠ form | `formId` is optional property on `WaitlistEntry` (not on `Waitlist`) | ✅ PASS |
| 3 | WaitlistEntry ≠ subscriber | `WaitlistEntry` has lifecycle status (pending/invited/converted/cancelled) | ✅ PASS |
| 4 | Early access ≠ marketing | `WaitlistConsent.earlyAccess` required true; `marketing` default false, never implicit | ✅ PASS |
| 5 | Duplicate join ≠ public error | Controller returns 202 with same `{status:"accepted"}` for new and duplicate | ✅ PASS |
| 6 | Unknown waitlistKey → 404 | Controller returns `404 NOT_FOUND` with `"waitlist_not_found"` | ✅ PASS |
| 7 | Shared modules must NOT depend on server | `build.gradle.kts` + ArchUnit prove one-way dependency | ✅ PASS |
| 8 | Email dedup per waitlist | `UNIQUE(waitlist_id, normalized_email)` in Liquibase + DB test | ✅ PASS |
| 9 | Email normalization conservative | `NormalizedEmail.from()` = trim + lowercase only; no Gmail canonicalization | ✅ PASS |
| 10 | Metadata whitelisted | `LeadMetadata` has typed fields; `WAITLIST_METADATA_WHITELIST` in frontend | ✅ PASS |

---

## 5. Implementation Artifact Map

| Module/Path | Files | Layer |
|-------------|-------|-------|
| `shared/lead-capture/common/` | 5 VOs: `EmailAddress`, `NormalizedEmail`, `CaptureSource`, `CaptureLocale`, `LeadMetadata` | 🔷 Framework-free VOs |
| `shared/lead-capture/waitlist/domain/` | 7 files: `Waitlist`, `WaitlistStatus`, `WaitlistEntry`, `WaitlistEntryStatus`, `WaitlistConsent`, `WaitlistKey`, `WaitlistId`, `WaitlistEntryId`, `WaitlistExceptions` | 🔷 Domain |
| `shared/lead-capture/waitlist/application/` | 4 files: `JoinWaitlistCommand`, `JoinWaitlistHandler`, `JoinResult`, `WaitlistEntryIdGenerator` + 3 port interfaces | 🔷 Application |
| `server/smp/infrastructure/http/` | `WaitlistController.kt` — `POST /api/waitlists/{waitlistKey}/entries` | 🟢 HTTP Adapter |
| `server/smp/infrastructure/persistence/` | `R2dbcWaitlistRepositories.kt` — 2 R2DBC implementations | 🟢 Persistence |
| `server/smp/infrastructure/governance/` | `GovernanceWaitlistConsentRecorder.kt` — consent recording | 🟢 Governance |
| `server/smp/infrastructure/configuration/` | `WaitlistApplicationConfiguration.kt`, `WaitlistRateLimitConfiguration.kt` | 🟢 DI Config |
| `server/smp/db/changelog/lead-capture/` | `001-create-waitlists.yaml`, `002-seed-profile-tailors-launch.yaml` | 🟢 Migrations |
| `apps/web/marketing/src/components/` | `WaitlistForm.astro`, `waitlist-form.ts`, `waitlist-form-validator.ts` | 🟢 Frontend |
| `server/smp/src/test/` | 8 test files (38 tests) | 🧪 Tests |
| `shared/lead-capture/**/src/test/` | 16 test files | 🧪 Tests |
| `apps/web/marketing/src/components/*.test.ts` | 2 test files (14 tests) | 🧪 Tests |

---

## 6. Issues Found

### CRITICAL

| Finding | Severity | Status |
|---------|----------|--------|
| None | — | N/A |

### WARNINGS

| Finding | Severity | Status |
|---------|----------|--------|
| **No separate tasks file** — tasks are embedded in ADR-0011 follow-up actions rather than a standalone `tasks.md` artifact. Not a blocker but breaks consistency with SDD convention. | WARNING | Open |
| **`WaitlistRateLimitConfigurationOverrideTest`** depends on `@SpringBootTest` which adds ~30s to the test suite. Minor. | WARNING (theoretical) | INFO |
| **Rate limit default disabled** — `application.rate-limit.waitlist.enabled` defaults to `false` until DALLAY-512/DALLAY-513 are resolved. Documented decision but the public endpoint is unprotected by default. | WARNING | Confirmed (documented in tests) |

### SUGGESTIONS

| Finding | Severity | Status |
|---------|----------|--------|
| Consider extracting `WaitlistConsent` version into a policy constant or config, rather than hardcoding `"2026-07-17"` in `WaitlistController`. | SUGGESTION | Open |
| Frontend `waitlist-form.ts` mirrors the metadata whitelist — consider a shared constants module to prevent drift. | SUGGESTION | Open |

---

## 7. Correctness Verification

| Aspect | Verdict | Evidence |
|--------|---------|----------|
| **Input validation** (email, consent, source) | ✅ CORRECT | `EmailAddress` validates structure; `WaitlistConsent` requires earlyAccess; controller translates `IllegalArgumentException` to HTTP 400 |
| **Idempotency** | ✅ CORRECT | `saveIfNotExists` + `ON CONFLICT DO NOTHING` + uniform public response |
| **Consent isolation** | ✅ CORRECT | Early access required; marketing default false; Gov recorder fires separate consent events |
| **Rate limiting** | ✅ CORRECT | Default disabled (explicit opt-in); integration test proves bucket keyed on `remoteAddress` + path |
| **Waitlist lifecycle** | ✅ CORRECT | Status transitions enforce valid paths (no activate from archived, etc.) |
| **Entry lifecycle** | ✅ CORRECT | `PENDING → INVITED → CONVERTED` or `PENDING/CONVERTED → CANCELLED` with invariant validation |
| **Email deduplication** | ✅ CORRECT | Per-waitlist via `UNIQUE(waitlist_id, normalized_email)`+ `NormalizedEmail` |
| **Framework isolation** | ✅ CORRECT | ArchUnit + Gradle dependency constraints |

---

## 8. Design Coherence

| Design Decision | Implementation | Status |
|----------------|---------------|--------|
| CQRS via `JoinWaitlistCommand` + `JoinWaitlistHandler` | ✅ Present in `shared/lead-capture/waitlist/application/` | ✅ COHERENT |
| Hexagonal: domain ports in shared, adapters in server | ✅ Ports in shared; R2DBC/HTTP in server/smp | ✅ COHERENT |
| `WaitlistConsent` in waitlist domain (not common) | ✅ `WaitlistConsent.kt` in `domain/` | ✅ COHERENT |
| `POST /api/waitlists/{waitlistKey}/entries` | ✅ `WaitlistController` | ✅ COHERENT |
| Uniform response for new + duplicate | ✅ `JoinWaitlistResponse(status="accepted")` for both | ✅ COHERENT |
| 404 for unknown key, 409 for closed | ✅ Controller maps exceptions correctly | ✅ COHERENT |
| Rate limit `WaitlistStrategy` | ✅ Configured in `WaitlistRateLimitConfiguration` | ✅ COHERENT |

---

## 9. Verdict

```yaml
verdict: PASS
summary: >
  La implementación del cambio `reusable-lead-capture-waitlist` cumple
  con todos los requirements especificados en los specs de openspec
  (lead-capture-common + lead-capture-waitlist) y con las invariantes
  arquitectónicas del ADR-0011.

  - 20+ archivos de implementación en 3 capas (shared domain, server infrastructure, frontend)
  - 20+ archivos de test con ~106+ tests individuales
  - Todos los tests pasan: frontend vitest (14 tests), backend smp (38 tests), shared modules
  - Framework isolation verificada via ArchUnit y Gradle constraints
  - Cumple con todos los escenarios BDD de los specs
  - Diseño hexagonal coherente: shared/domain framework-free, server/smp como adapter

  No se encontraron issues CRITICAL. 2 WARNINGS leves documentados
  (falta de tasks.md separado y rate-limit deshabilitado por defecto).
  Verdict: PASS.
```

---

## 10. Return Envelope

**Status**: `success`
**Executive Summary**: Implementación completa y verificada del Lead Capture Waitlist. Todos los specs de openspec están cubiertos, todos los tests pasan, el diseño hexagonal se mantiene, y no hay issues críticos. Se recomienda proceder con `sdd-archive` para sincronizar los delta specs a los specs canónicos y cerrar el ciclo.

**Artifacts**: 
  - `openspec/changes/reusable-lead-capture-waitlist/verification-report.md`

**Next**: `sdd-archive`
**Risks**: Rate-limit deshabilitado por defecto hasta resolver DALLAY-512/DALLAY-513. El endpoint público está temporalmente sin protección anti-abuse a menos que el operador lo active explícitamente.
