# API Contract Drift Auditor Report

## Purpose
Audit HTTP contract drift between the frontend applications and backend REST API controllers.

## Scope Inspected
- Backend REST Controllers: `PrivacyController.kt`, `ConsentController.kt`, and related DTO classes under `com.profiletailors.smp.privacy.infrastructure.http`.
- Frontend API Stores and Clients: Pinia stores, specifically `privacy.store.ts` in `apps/web/app/src/modules/settings/infrastructure`.

## Execution Result
**CHANGES_APPLIED**

One verified medium-risk contract drift mismatch was identified and fully corrected.

## Changes Applied
- Aligned request payload structure in `privacy.store.ts` for submitting Data Subject Access Requests (DSAR) with CORRECTION. Flattened the `newEmail` and `newUsername` parameters directly into the root level of the payload body to match the backend `SubmitPrivacyRequestDto` schema instead of using a nested `correctionData` block.
- Implemented robust frontend data mapping within the store's actions (`submitRequest`, `fetchRequests`, `fetchRequest`) to correctly translate the backend's real DTO response formats (`SubmitPrivacyResponseDto`, `PrivacyRequestStatusResponseDto`) into the existing frontend state format (`DsarRequest`), preventing UI breakage and aligning schemas seamlessly.
- Updated mock expectations in frontend store unit tests (`privacy.store.test.ts`) to align exactly with real backend contract signatures and verified all tests pass perfectly.

## Evidence Table
| Drift ID | Severity | File/Location | Backend Expectation | Frontend Behavior (Prior to Fix) | Action Taken |
|---|---|---|---|---|---|
| `privacy-dsar-contract-drift` | MEDIUM | `privacy.store.ts` | Flat root properties `newEmail`/`newUsername` on POST requests. | Nested `correctionData` object under the payload root. | Flattened payload properties and mapped responses. |

## Validation Table
| Validation Step | Command / Recipe | Status | Details |
|---|---|---|---|
| App Store Unit Tests | `pnpm --filter app run test:run` | Passed | All 990 unit tests in the Vue 3 app passed perfectly. |
| Marketing Site Tests | `pnpm --filter marketing run test` | Passed | All 85 unit tests passed. |
| Formatting and Linting | `pnpm lint` | Passed | Biome lint checks passed. |

## Unresolved Findings
None.

## Blockers
None.

## Automation State
Pushed updates to state file `.agents/automation/state/api-contract-drift.yaml`.

## Risk Assessment
- **Risk Category**: MEDIUM.
- **Justification**: Aligning client payloads to backend expected API schemas ensures high-fidelity communication and resolves standard client-to-backend interface drift. The changes were fully isolated to the `privacy.store.ts` mapping layer and verified by the unit test suite.

## Human Review Notes
- The backend's flat schema for DSAR correction inputs is now fully integrated. No further actions or manual interventions are required.
