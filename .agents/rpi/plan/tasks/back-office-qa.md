# Back Office QA Closure

## Overview

This document records the executable QA coverage and auditable evidence for GitHub #656 and Linear DALLAY-560. It covers the invitation-to-first-login journey across waitlist intake, invitation creation, delivery state, acceptance, activation, workspace membership, and first login. It distinguishes completed local evidence from CI, deployed, provider, and manual evidence.

The functional milestone remains blocked. DALLAY-556 / GitHub #652 is still outstanding, and deployed provider delivery, deployed invitee activation, deployed first login, and operator evidence have not been collected.

## Changes

- Reused existing authoritative Cucumber, Vitest, and Playwright coverage for invitation lifecycle, expiry, resend, revoke, idempotency, duplicate email, delivery failure, audit behavior, activation, workspace membership, and backend permission boundaries.
- Added `apps/web/admin/e2e/specs/protected-navigation.spec.ts` for the missing mocked-browser permission boundary coverage:
  - unauthenticated visitors are redirected from `/waitlist` to `/login?redirect=/waitlist`;
  - `SUPPORT_AGENT` operators are denied access to `/direct-invitations`.
- Persisted the evidence in `openspec/changes/private-beta-launch-readiness/qa-report.md`.
- No production behavior was changed.

### Coverage matrix

| Area | Authoritative coverage | Status | Remaining boundary |
| --- | --- | --- | --- |
| Waitlist, invitations, and delivery states | `platform-admin.feature`, `platformadmin/invitations-direct.feature`, `platformadmin/notifications-admin.feature`, `apps/web/admin/src/views/WaitlistView.spec.ts`, `WaitlistEntryView.spec.ts`, and `e2e/specs/waitlist-bulk-invite.spec.ts` | Local coverage complete | The mocked admin lane does not model real direct-invitation delivery. |
| Acceptance, activation, workspace membership, and first login | `local-auth.feature` and `DirectInvitationBddSteps.kt` | Local backend coverage complete | Deployed invitee acceptance and first login remain unverified. |
| Permission boundaries | Backend Cucumber scenarios and `protected-navigation.spec.ts` | Local coverage complete | Deployed operator evidence remains unverified. |
| Operational evidence | OpenSpec QA report and local test results | Partially complete | CI, provider delivery, deployed behavior, and manual operator evidence remain pending. |

### Completed local evidence

- Admin Vitest: **PASS**
- Admin type-check: **PASS**
- Admin lint and Biome: **PASS**
- Focused mocked Playwright: **2/2 passed**
- Full mocked admin Playwright lane: **15/15 passed**
- Backend fast Cucumber suite: **291 tests, 0 failures, 0 errors, 0 skipped**
- `git diff --check`: **PASS**
- Pre-push backend Detekt hook: **PASS**

## Usage

Run the focused browser test from the repository root with:

```sh
pnpm --filter admin exec playwright test --config e2e/playwright.mocked.config.ts e2e/specs/protected-navigation.spec.ts
```

Run the full mocked admin browser lane with:

```sh
pnpm --filter admin exec playwright test --config e2e/playwright.mocked.config.ts
```

Run the admin unit, type, and lint checks with:

```sh
pnpm --filter admin test:run
pnpm --filter admin type-check
pnpm --filter admin lint
```

Run the authoritative fast backend acceptance suite with:

```sh
just backend-bdd-fast
```

### Next steps

1. Inspect the applicable GitHub Actions results for this change; do not infer CI status from local runs.
2. Collect provider delivery evidence in the configured deployed environment.
3. Execute the deployed operator workflow and invitee acceptance through first login.
4. Resolve DALLAY-556 / GitHub #652 before recommending functional milestone closure.
5. Reconcile the OpenSpec QA report with the deployed and operator evidence.

The focused and full mocked Playwright results listed above are completed evidence, not pending work.

## Troubleshooting

- A mocked Playwright pass proves route and UI behavior against intercepted APIs only. It does not prove deployed API behavior, real email delivery, or provider configuration.
- A passing local Cucumber suite proves the repository's local backend scenarios, not production activation or first login.
- If the login assertion fails after a route change, verify the accessible `Email` textbox and the redirect query value before changing selectors.
- If CI is unavailable, record it as not inspected rather than treating local checks as remote evidence.
- Do not close the functional milestone while DALLAY-556 / GitHub #652 or the required deployed and manual evidence remains unresolved.

## References

- [GitHub #656](https://github.com/dallay/profiletailors.com/issues/656)
- Linear `DALLAY-560`
- OpenSpec acceptance QA report (`openspec/changes/private-beta-launch-readiness/qa-report.md`)
- [Platform admin Cucumber coverage](../../server/smp/src/test/resources/features/platform-admin.feature)
- [Direct invitation Cucumber coverage](../../server/smp/src/test/resources/features/platformadmin/invitations-direct.feature)
- [Notification delivery Cucumber coverage](../../server/smp/src/test/resources/features/platformadmin/notifications-admin.feature)
- [Local authentication Cucumber coverage](../../server/smp/src/test/resources/features/local-auth.feature)
- [Protected admin navigation Playwright coverage](../../apps/web/admin/e2e/specs/protected-navigation.spec.ts)
