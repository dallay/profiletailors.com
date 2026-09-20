# Acceptance QA Report: dallay-567-accept-invitations-registration-flow

## 1. Identity
- Change: `dallay-567-accept-invitations-registration-flow`
- Mode: `openspec`
- Phase: `qa`
- Date: `2026-09-10`
- QA execution mode: `fallback`

## 2. Source Artifacts and Technical Verification Handoff
- Proposal: `openspec/changes/dallay-567-accept-invitations-registration-flow/proposal.md`
- Delta spec: `openspec/changes/dallay-567-accept-invitations-registration-flow/specs/invitations/spec.md`
- Related main specs: `openspec/specs/invitations/spec.md`, `openspec/specs/e2e/spec.md`
- Design: `openspec/changes/dallay-567-accept-invitations-registration-flow/design.md`
- Tasks: `openspec/changes/dallay-567-accept-invitations-registration-flow/tasks.md`
- Apply evidence: `openspec/changes/dallay-567-accept-invitations-registration-flow/apply-progress.md`
- Technical verification: `openspec/changes/dallay-567-accept-invitations-registration-flow/verify-report.md`
- State/configuration: `openspec/changes/dallay-567-accept-invitations-registration-flow/state.yaml`, `openspec/config.yaml`

The latest verification handoff reports:

- Invitation Playwright: 36/36 passed across Chromium, Firefox, and Mobile Chrome.
- Scheduler-filtering Playwright: 9/9 passed across Chromium, Firefox, and Mobile Chrome.
- App Vitest: 147 files and 1,729 tests passed.
- App lint, type-check, build, and `git diff --check`: passed.
- Backend check, PostgreSQL integration, fast BDD, and PostgreSQL BDD: passed in the retained prior evidence.
- The prior Mobile Chrome scheduler-sidebar regression is resolved by the current invitation and scheduler-filtering runs.
- No runner envelope or deterministic SDD quality runner/FSM is configured. Results are therefore preserved as supplied evidence under `fallback`, not reinterpreted as runner output.

### Exact retained evidence counts

- QA-06 through QA-10: **5/5 PASS** acceptance scenarios.
- Fast Cucumber: **24/24 local-auth** scenarios and **20/20 platform-admin** scenarios; no failures
  are reported in the retained XML inventory.
- PostgreSQL Cucumber: **PASS**; the verification handoff does not retain a separate per-feature
  test-count transcript for this lane.
- Invitation Playwright: **36/36 PASS** across Chromium, Firefox, and Mobile Chrome.
- Scheduler-filtering Playwright: **9/9 PASS** across Chromium, Firefox, and Mobile Chrome.
- App Vitest: **1,729/1,729 PASS** across 147 files.
- App lint: **830 files checked, 0 errors**; app type-check and build passed, with the existing
  non-blocking chunk-size advisory.
- Deterministic QA runner/FSM: **0 runner envelopes available**; classified as `NOT TESTED` under
  `fallback`, not as a pass.

## 3. Target, Environment, Permissions, and Limitations
- Target supplied: none. No deployed URL or external operator target was provided.
- Evaluated surface: the local `server/smp` invitation registration/acceptance implementation and `apps/web/app` invitation flow through the supplied API, PostgreSQL, Vitest, and Playwright evidence.
- Environment: local worktree branch `feat/dallay-567-invitation-evidence`; configured backend test infrastructure and app Playwright browser projects. The invitation E2E uses the configured local/mock capability and registration fixtures.
- Permissions: test fixture identities, mocked principals, and test-only PostgreSQL/infrastructure permissions. No production credentials were used or supplied.
- Limitations: no live deployed acceptance, no deterministic quality runner/FSM, and no dedicated invitation accessibility or locale execution. Static inspection is not used as acceptance PASS evidence.

This report is an audit of the supplied local evidence. It does not claim product acceptance for a deployed target or for unavailable external flows.

## 4. Capability Inventory
| Capability | Status | Rationale |
|---|---|---|
| Invitation Playwright E2E | available — selected | Directly observes invite acceptance, registration handoff, safe errors, redaction, and final navigation. |
| Chromium, Firefox, and Mobile Chrome browser projects | available — selected | Latest invitation run passed 36/36 across all three projects. |
| Scheduler-filtering Playwright regression | available — selected | Confirms the prior Mobile Chrome sidebar regression is fixed without treating scheduler behavior as invitation acceptance. |
| Backend WebTestClient/Cucumber BDD | available — selected | Observes HTTP status, application codes, sessions, workspace resolution, replay, and safe response content. |
| PostgreSQL/Testcontainers integration | available — selected | Observes persistence, rollback, membership/workspace effects, and concurrent one-winner behavior. |
| App Vitest | available — selected | Supports observable client classification, state transitions, safe copy, redirect, and missing-token behavior. |
| Backend check and architecture/static checks | available — selected as supporting evidence | Technical evidence only; rejected as a sole product-acceptance basis by `openspec/config.yaml`. |
| App lint, type-check, build, and diff check | available — rejected for acceptance | Technical conformance evidence, not observable product behavior. |
| Invitation accessibility-specific execution | available — rejected pending execution | No scoped result was supplied; static markup inspection cannot produce PASS. |
| Invitation locale/internationalization execution | available — rejected pending execution | No invitation-specific locale result was supplied. |
| Manual/deployed exploratory session | unavailable | No target, credentials, or permissioned operator session was supplied; this remains an acceptance `BLOCKED` condition. |
| Deterministic SDD QA runner/FSM | unavailable | No `openspec/quality-runner.json` or configured deterministic runner exists; runner-unavailable maps to QA `NOT TESTED` and the phase is visibly `fallback`. |

## 5. Scenario Matrix
| ID | Capability | Acceptance scenario | Result | Evidence or reason |
|---|---|---|---|---|
| QA-01 | Invitation Playwright | An invitee accepts a valid invitation and reaches the accepted workspace. | PASS | Latest invitation Playwright suite passed 36/36 across Chromium, Firefox, and Mobile Chrome; the valid acceptance scenario observes successful acceptance and final navigation. |
| QA-02 | Invitation Playwright + backend BDD | A fresh invitee registers from an invitation and reaches the dashboard with invitation-linked workspace state. | PASS | Latest invitation suite passed the registration handoff scenario. Retained BDD evidence observes successful invite-only registration, PENDING verification policy, workspace ID, and refresh cookie. |
| QA-03 | PostgreSQL integration | Existing-workspace invitations use their stored workspace and new-workspace invitations provision exactly one workspace and membership. | PASS | Retained coordinator and PostgreSQL integration evidence covers both target paths and persisted membership/workspace assertions. |
| QA-04 | Invitation Playwright + backend BDD | An invalid invitation is rejected without registration mutation or sensitive response content. | PASS | Invitation invalid-path E2E and retained local-auth BDD evidence passed with deterministic invalid classification and no registration mutation. |
| QA-05 | Invitation Playwright + backend BDD | A consumed or replayed invitation is rejected deterministically without a second acceptance. | PASS | Invitation replay E2E and retained platform-admin BDD evidence passed with consumed/replay classification. |
| QA-06 | API/BDD/browser external journey | An expired invitation produces the specified external `410 INVITATION_EXPIRED` outcome. | PASS | Fast and PostgreSQL Cucumber suites pass the dedicated local-auth scenario with `410`, `INVITATION_EXPIRED`, no registration mutation, ACTIVE invitation state, and token/email redaction. Invitation Playwright passes the dedicated expired Problem Details scenario across Chromium, Firefox, and Mobile Chrome. |
| QA-07 | API/BDD/browser external journey | A revoked invitation produces the specified external `410 INVITATION_REVOKED` outcome. | PASS | Fast and PostgreSQL Cucumber suites pass the dedicated local-auth scenario with `410`, `INVITATION_REVOKED`, no registration mutation, REVOKED invitation state, and token/email redaction. Invitation Playwright passes the dedicated revoked Problem Details scenario across Chromium, Firefox, and Mobile Chrome. |
| QA-08 | API/BDD/browser external journey | An invitation email mismatch produces the specified external `403 INVITATION_EMAIL_MISMATCH` outcome. | PASS | Fast and PostgreSQL Cucumber suites pass the normalized mismatch scenario with `403`, `INVITATION_EMAIL_MISMATCH`, no registration mutation, ACTIVE invitation state, and token/email redaction. Invitation Playwright passes the dedicated mismatch Problem Details scenario across Chromium, Firefox, and Mobile Chrome. |
| QA-09 | Backend API/BDD | A client workspace override is rejected and workspace remains invitation-derived. | PASS | Retained controller/API/BDD evidence passed `INVITATION_WORKSPACE_OVERRIDE_NOT_ALLOWED` without dispatching acceptance. |
| QA-10 | API/BDD/persistence | A matching existing identity accepts without creating a duplicate identity or credential. | PASS | Fast and PostgreSQL Cucumber suites pass the dedicated platform-admin scenario using `principal-1` / `jwt-user@example.com`, `Bearer valid-token`, invitation-derived workspace, ACTIVE membership, redacted response, ACCEPTED invitation, and exact one-identity/credential/workspace/membership assertions. |
| QA-11 | Backend API/BDD | Unauthenticated, unsupported-principal, and blank-token attempts are rejected without acceptance dispatch. | PASS | Retained controller evidence observes 401, 403, and blank-token 400 outcomes with no handler invocation. |
| QA-12 | PostgreSQL integration | A failure after invitation completion rolls back identity, credential, consent, verification, membership/workspace, and invitation state together. | PASS | Retained PostgreSQL integration evidence observes the invitation remaining ACTIVE and no partial persisted registration state after injected failure. |
| QA-13 | PostgreSQL integration | Two valid contenders produce exactly one complete acceptance and no partial loser account. | PASS | Retained PostgreSQL race evidence observes one winner, one accepted invitation, one membership, and one identity. |
| QA-14 | Backend BDD + App Vitest + Invitation Playwright | Verification, consent, session, and final navigation follow existing registration policy. | PASS | Retained BDD, app, and invitation E2E evidence observes successful response/session behavior and the existing PENDING verification policy. |
| QA-15 | Backend/API/DOM/audit/metrics evidence | Server responses, DOM, audit, and metrics omit raw token, full email, password, and consent payloads. | PASS | Latest verification handoff retains passing redaction and bounded-observability tests plus invitation DOM checks. |
| QA-16 | Browser security observation | Registration handoff avoids raw-token exposure in browser URL/history/referrer surfaces. | FAIL | The observed handoff uses `/register?invitationToken=...`; server evidence is redacted, but URL/history/referrer exposure is not mitigated or covered. Tracked as P2, not CRITICAL/P0/P1. |
| QA-17 | Invitation Playwright | Invitation acceptance remains observable in desktop and mobile browser projects. | PASS | Latest invitation Playwright result is 36/36 across Chromium, Firefox, and Mobile Chrome. |
| QA-18 | Scheduler-filtering Playwright | The prior Mobile Chrome sidebar regression after invitation follow-on remains fixed. | PASS | Scheduler-filtering suite passed 9/9 across Chromium, Firefox, and Mobile Chrome; the prior regression is resolved. |
| QA-19 | Accessibility-specific browser execution | Invitation acceptance and registration have verified keyboard, focus, naming, and assistive-technology behavior. | NOT TESTED | No dedicated invitation accessibility execution result was supplied. |
| QA-20 | Locale/internationalization execution | Invitation acceptance and registration are verified for supported locale behavior. | NOT TESTED | No invitation-specific locale execution result was supplied. |
| QA-21 | Manual/deployed exploratory capability | An operator completes the invitation flow against a deployed target. | BLOCKED | No deployed target, credentials, or permissioned operator session was supplied. |
| QA-22 | Deterministic SDD QA runner/FSM | The configured runner executes the acceptance matrix and returns runner envelopes. | NOT TESTED | No deterministic runner/FSM or runner envelope is available; fallback evidence is retained without promoting prose or direct command output to runner status. |

## 6. Untested Scope, Reason, and Rerun Prerequisite
| Scope | Reason | Rerun prerequisite |
|---|---|---|
| Invitation accessibility and locale behavior | No dedicated execution evidence was supplied. | Run scoped accessibility and EN/ES invitation journeys; retain runner output. |
| Deployed/manual acceptance | No target, credentials, or permissions were supplied. | Provide a permissioned deployed target and operator session. |
| Deterministic QA/FSM execution | The repository has no configured deterministic runner; policy maps unavailable runner execution to `NOT TESTED`. | Configure the approved runner or continue to label evidence `fallback`; do not infer runner status. |

## 7. Findings
| ID | Severity | Scenario / location | Evidence | Status |
|---|---|---|---|---|
| QA-F-001 | CRITICAL | Prior Mobile Chrome scheduler-sidebar regression | Latest invitation suite passed 36/36 and scheduler-filtering passed 9/9 across Chromium, Firefox, and Mobile Chrome. | Resolved; no longer an archive blocker. |
| QA-F-002 | P2 | Raw-token registration URL handoff in `AcceptInvitationView.vue` | `/register?invitationToken=...` is observed. Server response, DOM, audit, logs, and metrics redaction pass, but browser history/referrer exposure is not addressed. | Open warning; no source change made in QA. |
| QA-F-003 | P2 | Complete external invalid-invitation matrix | Dedicated Cucumber and Playwright expired, revoked, and mismatch journeys now pass with exact status/code and redaction assertions. | Resolved by acceptance-evidence follow-up. |
| QA-F-004 | P2 | Successful matching-existing-identity acceptance | Dedicated authenticated Cucumber journey now passes with exact no-duplicate identity, credential, workspace, and membership assertions. | Resolved by acceptance-evidence follow-up. |
| QA-F-005 | P3 | Failure-injection breadth | Rollback after invitation completion passes, but each individual mutation boundary is not independently injected. | Open test-depth warning. |
| QA-F-006 | P3 | Deterministic quality runner and complete task-level RED output | Verification and QA are `fallback`; no deterministic runner/FSM or complete task-level RED transcript is available. | Open process limitation; does not override supplied runtime evidence. |
| QA-F-007 | P3 | Invitation failure advice ownership | Mapping remains platformadmin-owned rather than identity-advice-owned to preserve module boundaries; backend architecture checks pass. | Accepted intentional deviation; no remediation requested by QA. |
| QA-F-008 | P1 | Manual/deployed acceptance environment | No deployed target, credentials, permissions, or controlled invitation fixtures were supplied. | Open acceptance blocker; `BLOCKED` and archive-gating. |
| QA-F-009 | P2 | Invitation accessibility and locale coverage | No dedicated accessibility or EN/ES invitation execution evidence was supplied; static inspection is not acceptance evidence. | Open coverage warning; scenarios remain `NOT TESTED`. |
| QA-F-010 | P3 | Deterministic QA runner/FSM availability | `openspec/quality-runner.json` and `scripts/sdd-quality-runner.mjs` are unavailable. | Open process warning; runner scenario is `NOT TESTED` under visible `fallback`. |

## 8. Final Verdict
`BLOCKED`

### Verdict Rationale
The latest scoped implementation evidence is positive: invitation Playwright passed 36/36 across Chromium, Firefox, and Mobile Chrome; scheduler-filtering passed 9/9 across the same projects; app Vitest passed 147 files and 1,729 tests; app lint, type-check, build, and diff checks passed; and the fast and PostgreSQL Cucumber suites now pass the dedicated QA-06 through QA-10 scenarios. The prior Mobile Chrome scheduler-sidebar regression is resolved.

The QA/archive gate remains `BLOCKED` because no deployed target, credentials, permissions, or
controlled invitation fixtures were supplied for manual exploratory acceptance. QA-06 through QA-10
are now evidenced locally at 5/5 PASS, but local evidence is not deployed acceptance. Invitation
accessibility and locale execution remain `NOT TESTED`, QA-16 remains `FAIL` with an open P2 warning,
and the unavailable deterministic runner remains `NOT TESTED` under visible `fallback`. No
unresolved product defect at CRITICAL/P0/P1 severity was found; the P1 acceptance-environment
finding is still an archive blocker by policy.

## 9. Implementation Handoff
- The apply phase added and executed the missing expired, revoked, mismatch, and matching-identity acceptance journeys; the QA phase itself made no source-code or test changes.
- Do not claim invitation accessibility, locale, or deployed exploratory acceptance as tested.
- QA-06 through QA-10 evidence is present: **5/5 PASS**; retain the exact counts above when handing off.
- Rerun this QA gate before archive with the refreshed local evidence and remaining environment limitations.
- Decide whether to replace or otherwise constrain the raw-token URL handoff; retain the P2 warning until addressed or explicitly accepted by the owning product/security decision.
- Preserve the platformadmin-owned advice mapping unless a new architecture decision changes the module-boundary rationale.
- Configure the approved deterministic QA runner/FSM before treating runner execution as tested; until then retain `fallback` and `NOT TESTED`.
- This report is acceptance QA evidence for the supplied local implementation and is not a claim of deployed product acceptance.
- QA-F-011 was resolved locally after the original fallback exploration; the retained deployed-acceptance and independent QA limitations still block archive.

## 10. Local Exploratory Addendum
- Target: `https://dallay-567-invitation-evidence.pt-app.localhost:1355` with the worktree-local backend and PostgreSQL infrastructure.
- The Portless origin required an ephemeral `SMP_CORS_ALLOWED_ORIGINS` override; the repository `.env` does not include this branch-specific hostname. No repository configuration or secret was changed.
- Local authentication succeeded with the seeded development identity: `/api/auth/login` returned `200` and `/api/auth/me` returned `200`.
- Synthetic unauthenticated invitation submission followed the registration handoff to `/register?invitationToken=...`.
- Missing-token navigation rendered the expected accessible alert and did not render the submit form.
- After the QA-F-011 fix, authenticated submission of a synthetic invalid token called `POST /api/invitations/accept` with a Bearer `Authorization` header and received the expected domain `400` invalid-invitation response rather than `401` authentication failure.
- The seeded fixture documentation and stored bcrypt hash disagree on the password spelling; the hash was validated against the unprefixed local development password. No seed file was changed.

## 11. Finding Resolution
| ID | Severity | Scenario / location | Evidence | Status |
|---|---|---|---|---|
| QA-F-011 | P1 | Authenticated existing-identity acceptance in `apps/web/app/src/modules/invitation/infrastructure/invitation-api.ts` | Regression test now requires the Bearer header; local browser verification observed the header and a domain-level `400` for the synthetic invalid token. | Resolved locally; focused tests and local browser recheck passed. |

### QA-F-011 Resolution
The invitation store now passes the in-memory auth access token to the invitation API, and the API adds it as a Bearer header. This restores authentication for the existing-identity acceptance path without persisting the token or exposing it in the invitation URL. Archive remains blocked by the independent deployed-acceptance, deterministic-runner, accessibility/locale, and raw-token URL limitations.
