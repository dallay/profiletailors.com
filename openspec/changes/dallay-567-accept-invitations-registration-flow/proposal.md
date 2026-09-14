# Proposal: DALLAY-567 — Acceptance Evidence Follow-up

## Intent

Unblock the QA/archive gate with evidence for the four backend journeys and three frontend
classifications marked `NOT TESTED` in `qa-report.md`. `exploration.md` confirms the required seams.
This follow-up adds tests and fixtures only; production behavior and contracts remain unchanged.

## Scope

### In Scope
- Cucumber scenarios for expired, revoked, and email-mismatched invite-only registration.
- Cucumber scenario for authenticated matching-existing-identity acceptance with no duplicate
  identity, credential, membership, or workspace.
- Playwright scenarios for `410 INVITATION_EXPIRED`, `410 INVITATION_REVOKED` with its canonical
  payload code, and `403 INVITATION_EMAIL_MISMATCH`.
- Test-only lifecycle/authentication fixtures and database assertions for QA-06 through QA-10.

### Out of Scope
- Production implementation, API classifier, schema, UI copy, or runtime behavior changes.
- Accessibility, locale, raw-token URL exposure, unrelated regression coverage, or new requirements.
- Deployed/manual exploratory acceptance until a target, credentials, and controlled invitation
  fixtures are supplied.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- None. Existing `invitations` and `e2e` requirements are unchanged; this adds evidence only.

## Approach

Extend the local-auth and platform-admin features using existing hooks, invitation steps, and count
helpers. Reuse seeded `principal-1` / `jwt-user@example.com` for matching acceptance. Extend
`invitee-private-beta.spec.ts` with explicit status/code payloads and assert current classification;
revoked fixtures must include a code because status-only `410` maps to expired.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `server/smp/src/test/resources/features` | Modified | Four backend acceptance scenarios. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/bdd` | Modified | Lifecycle and mutation assertions. |
| `apps/web/app/e2e/specs/invitee-private-beta.spec.ts` | Modified | Three response-classification scenarios. |
| `openspec/changes/.../qa-report.md` | Later | Rerun missing scenario results. |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Shared fixtures leak state | Medium | Reset per scenario and assert persisted state. |
| Revoked `410` is classified as expired | Medium | Include `INVITATION_REVOKED` in the browser payload. |
| Manual acceptance remains unverifiable | High | Keep it `BLOCKED` pending target, credentials, and controlled fixtures. |

## Rollback Plan

Revert only the added feature scenarios and test fixtures. No production rollback, migration, or
configuration change is required.

## Dependencies

- Existing Cucumber PostgreSQL/Testcontainers, platform-admin principal fixture, and app Playwright
  route mocks.
- Deployed target, credentials, and controlled invitation lifecycle fixtures for manual acceptance;
  none are available in this proposal.

## Success Criteria

- [ ] Backend scenarios pass with specified statuses/codes and no unintended mutations.
- [ ] Matching acceptance proves no duplicate identity or credential.
- [ ] Playwright scenarios pass for all three classifications.
- [ ] QA-06 through QA-10 can be rerun; deployed/manual QA remains blocked.
