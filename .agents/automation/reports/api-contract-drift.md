# API Contract Drift Auditor Report

## Execution Result
**Status**: CHANGES_APPLIED

All identified contract drifts between the frontend Pinia stores (`consent.store.ts` and `privacy.store.ts`) and the backend Spring Boot REST services have been fully reconciled, tested, and validated.

## Scope Inspected
- `apps/web/app/src/modules/settings/infrastructure/consent.store.ts`
- `apps/web/app/src/modules/settings/infrastructure/privacy.store.ts`
- `apps/web/app/src/modules/settings/infrastructure/consent.store.test.ts`
- `apps/web/app/src/modules/settings/infrastructure/privacy.store.test.ts`
- `server/smp/src/main/kotlin/com/profiletailors/smp/governance/infrastructure/http/ConsentController.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/privacy/infrastructure/http/PrivacyController.kt`

## Changes Applied
1. **Consent Store Backend Sync Alignment**:
   - Restructured `syncToBackend` in `consent.store.ts` to call `POST /api/governance/consent` with flat `RecordConsentRequest` schema when analytics is `true`, and `POST /api/governance/consent/withdraw` with flat `WithdrawConsentRequest` schema when analytics is `false`.
   - Imported `i18n` from `@shared/i18n` to resolve and supply the active locale string to the backend.
   - Refactored `consent.store.test.ts` to mock and verify correct REST endpoints and exact flat payloads.

2. **Privacy Store DSAR Schema Reconciliation**:
   - Updated `submitRequest` in `privacy.store.ts` to flatten `correctionData` values `newEmail` and `newUsername` to root-level keys of the submitted request body to align with `SubmitPrivacyRequestDto`.
   - Implemented `mapStatusDtoToRequest` in `privacy.store.ts` to map backend `SubmitPrivacyResponseDto` on POST and `PrivacyRequestStatusResponseDto` on list/get requests to the local frontend model `DsarRequest`.
   - Refactored `privacy.store.test.ts` to mock backend payloads and assert correct normalization and submission.

## Evidence Table
| Drift ID | Title | Component | Type | Resolution |
|---|---|---|---|---|
| **F-1** | Consent Store payload / endpoint mismatch | `consent.store.ts` | HTTP API | Realigned payload properties, split POST endpoints for active/withdrawn |
| **F-2** | Privacy Store nested correction / DTO mismatch | `privacy.store.ts` | HTTP API | Flattened correction properties, mapped response DTO properties |

## Validation Table
| Test Suite | File | Status | Notes |
|---|---|---|---|
| Frontend Consent Store Unit Tests | `consent.store.test.ts` | **PASSED** | 16/16 tests successfully executed |
| Frontend Privacy Store Unit Tests | `privacy.store.test.ts` | **PASSED** | 7/7 tests successfully executed |
| Backend fast test suite | `just backend-test-fast` | **PASSED** | Spring Boot test suite passed with no regressions |

## Risk Assessment
- **Risk**: MEDIUM
- **Mitigation**: Code adjustments were scoped strictly to client HTTP client data mappings and tested via complete mock verification suites with zero impact to backend services or business logic.

## Unresolved Findings
- None
