# Design: DALLAY-567 Acceptance Evidence Follow-up

## Technical Approach

Add only local acceptance evidence for the four QA gaps: expired, revoked, and email-mismatched
invite-only registration, plus authenticated acceptance by the already-seeded matching identity.
Use the existing Cucumber WebTestClient, PostgreSQL reset, invitation seeding, `valid-token`
principal, HAR replay, and per-test Playwright route overrides. Do not change production code,
contracts, classifiers, UI copy, or deployment configuration.

## Architecture Decisions

| Option | Tradeoff | Decision |
|---|---|---|
| Add dedicated production test endpoints | Couples QA to non-user behavior | Rejected; seed and mutate lifecycle rows through existing test glue only. |
| Reuse shared Cucumber state across scenarios | Fast but leaks tokens and rows | Rejected; reset database and glue state before every scenario. |
| Use status-only browser errors | Cannot distinguish revoked from expired (`410`) | Rejected; revoked fixtures must include `INVITATION_REVOKED`. |
| Replace raw-token registration handoff | Correctness/security change outside evidence scope | Rejected; preserve the existing warning and assertions unchanged. |

## Data Flow

```text
Cucumber hook reset -> seed lifecycle + unique raw token -> WebTestClient request
  -> assert status/code -> query invitation and identity tables

Playwright resetSession -> HAR baseline + route override -> invitation UI
  -> assert response status/code and canonical copy
```

The raw token exists only in Cucumber scenario state or the Playwright URL/request fixture.
Helpers must not log it or include it in assertion messages.

Keep `AuthorizationBddSteps.resetScenarioState` as the global database reset,
`LocalAuthCapabilitiesBddSteps.resetAuthCapabilityState` for local response/token state, and
`PlatformAdminBddSteps.resetPlatformAdminState` for `@platform-admin` scenarios. Playwright keeps
`beforeEach(resetSession)`, a fresh context, HAR replay, and page-scoped route overrides; no test
shares cookies, storage, route state, or invitation tokens.

## File Changes

| File | Action | Description |
|---|---|---|
| `server/smp/src/test/resources/features/local-auth.feature` | Modify | Add expired, revoked, and normalized-email-mismatch registration scenarios. |
| `server/smp/src/test/resources/features/platform-admin.feature` | Modify | Add successful matching-identity acceptance with no duplicate assertions. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/glue/DirectInvitationBddSteps.kt` | Modify | Add expired/revoked test fixtures and lifecycle/status assertions; keep token in glue state only. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/glue/LocalAuthCapabilitiesBddSteps.kt` | Modify | Add explicit failure-code and no-mutation assertions for registration. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/BddDatabaseSupport.kt` | Modify | Add focused count queries for identity, local credential, membership, and workspace uniqueness. |
| `apps/web/app/e2e/specs/invitee-private-beta.spec.ts` | Modify | Add mocked `410` expired, `410` revoked, and `403` mismatch journeys with response/code checks. |
| `openspec/changes/dallay-567-accept-invitations-registration-flow/qa-report.md` | Later | QA reruns QA-06 through QA-10 and retains QA-16/QA-21 warnings. |

## Interfaces / Contracts

Backend assertions use existing `application/vnd.api.v1+json` requests and stable codes:
`INVITATION_EXPIRED`, `INVITATION_REVOKED`, and `INVITATION_EMAIL_MISMATCH`. The matching
identity scenario uses `principal-1` / `jwt-user@example.com` with `Bearer valid-token`; identity
and credential counts remain unchanged, the invitation workspace remains one row, and membership
is exactly one expected row after acceptance.

Playwright route fixtures return `application/problem+json` with the explicit status and canonical
code. The revoked route must not rely on the frontend's status-only `410` fallback to expired.

## Testing Strategy

| Layer | What to Test | Exact command |
|---|---|---|
| Cucumber fast | All new local scenarios and per-scenario reset | `just backend-bdd-fast` |
| Cucumber PostgreSQL | Same scenarios against repository infrastructure | `just infra-up`; `just backend-bdd-postgres`; `just infra-down` |
| Playwright | Focused invitation file across Chromium, Firefox, Mobile Chrome | `cd apps/web/app && node ../../../scripts/run-playwright.mjs -c e2e/playwright.config.ts e2e/specs/invitee-private-beta.spec.ts` |
| Frontend checks | Test-only route/type/lint correctness | `pnpm --filter app lint`; `pnpm --filter app type-check` |

## Migration / Rollout

No migration, feature flag, deployment change, or production rollback. If a backend run fails,
run `just infra-down` before retrying.

## Open Questions / Preserved Warnings

- [ ] QA-16 remains an explicit P2 raw-token URL/history/referrer warning; this design does not
  claim to fix it or promote it to safe browser handoff.
- [ ] QA-21 remains `BLOCKED` until a deployed target, credentials, and controlled invitation
  fixtures are supplied. Local BDD, HAR replay, and route mocks are not deployed acceptance.
