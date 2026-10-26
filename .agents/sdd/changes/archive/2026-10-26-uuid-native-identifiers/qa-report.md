# QA Report: Native UUID Identifiers

## status

**PASS WITH WARNINGS — scoped local acceptance**, reviewed 2026-10-26 on
`feat/uuid-native-identifiers`. **Production readiness: NOT APPROVED.**

This independent review assesses existing observable evidence, not a new execution. No tests,
Gradle tasks or browser sessions were run by this reviewer. Only this report was written;
source, tasks, state and the technical report were not changed.

Policy: `.agents/sdd/config.yaml` requires observable acceptance evidence beyond unit,
integration, BDD and static checks. The browser, actual HTTP and persisted database evidence
below supplies that evidence for the scoped local candidate.

## executive_summary

- Actual Astro marketing and the packaged backend ran on loopback against disposable PostgreSQL
  with native UUID migrations. The backend used the `test` profile and `MockEmailSender`;
  the fixture email used `example.invalid`. No production or external email delivery is claimed.
- Browser summaries record two waitlist submissions returning HTTP **202**. Both screenshots
  show the success state. The retained database result reports **one entry, one public consent,
  one public event, one workspace and zero invalid projection scopes** after repeat submission.
- Actual registration returned **201** and created the workspace. The fixture deliberately gave
  a separate existing waitlist the same UUID before capture. Public consent retained the real
  waitlist reference and no workspace reference; capture did not fabricate another workspace.
- The inspected HTTP assertion helper and its completed report establish anonymous **401**,
  authenticated tenant list **200** with no public record, and authorized public-only withdrawal
  **404** without public projection/event changes. A separate workspace consent returned **201**,
  replay **200**, withdrawal **200**, and isolated history; its event count was two. Public
  evidence remained unchanged.
- Initial registration **503** with persisted mode `CLOSED`, **422** for an invalid terms version,
  and withdrawal **403** without consent-write permission were setup diagnostics, not suppressed
  failed acceptance cases. Setup used persisted local mode `OPEN` and `terms-v1.0.0`. An explicit
  disposable direct grant authorized only the synthetic principal in its own workspace. No app
  authorization policy changed. This does not certify default permission provisioning.
- The separate [technical report](verify-report.md) records **25/30 compliant, five partial,
  zero failing scenarios and no missing core implementation**. Four partial rows concern
  production rehearsal; one concerns dedicated malformed-shortlinks HTTP negative coverage.
  This QA report does not upgrade those partial results.

## artifacts

`TEMP` denotes the actual external evidence directory:

`/private/var/folders/zz/d4kl1hfj1j15nxm43d24px300000gn/T/opencode`

### Live acceptance evidence inspected

- `TEMP/uuid_live_qa.py`: packaged-jar, Astro and disposable PostgreSQL orchestration;
  loopback bindings, synthetic email, isolated environment and service cleanup logic.
- `TEMP/uuid-live-qa/browser-success.json`, `browser-repeat.json`,
  `waitlist-success.png`, `waitlist-repeat-success.png`: response summaries and visible success.
- `TEMP/uuid-live-qa/assert_http_scope.py`, `http-scope-acceptance.json`: actual HTTP status/body
  assertions and database checks for same-UUID scope isolation, replay, withdrawal and history.
- `TEMP/uuid-live-qa/repeat-browser-idempotency.json`, `repeat-browser-producer.sql`: result and
  exact producer SQL. The orchestrator retained this SQL **after execution from the same executed
  query**; it is not a pre-existing execution transcript. Inspection confirms its count fields
  match the result, including the projection XOR-scope predicate. It does not check every event
  invariant or establish concurrent browser submission behavior.
- `TEMP/uuid-live-qa/seed-and-registration.log`, `scoped-qa-grant.log`, `marketing.log`:
  registration, fixture authorization and actual Astro startup evidence.
- `TEMP/uuid-live-qa/backend.sanitized.log`: derived backend evidence. This reviewer compared it
  with the previously inspected private raw log: exactly five URL token occurrences were replaced
  with `[REDACTED]`, with no other differences. The raw private log was not copied into this report
  or the repository. Backend logs corroborate startup, UUID migrations, registration, scoped
  operations, mock email and graceful shutdown.

### Commands and provenance limits

The inspected helpers provide these executable entry points, **not rerun by this reviewer**:

```sh
TEMP=/private/var/folders/zz/d4kl1hfj1j15nxm43d24px300000gn/T/opencode
python3 "$TEMP/uuid_live_qa.py" start
python3 "$TEMP/uuid-live-qa/assert_http_scope.py"
python3 "$TEMP/uuid_live_qa.py" stop
```

These are not a complete replay recipe: registration, persisted local configuration, scoped grant,
browser actions and valid temporary `state.json`/`auth.json` are prerequisites for the HTTP helper.
The orchestration helper launches `java -jar server/smp/build/libs/smp-0.0.1-SNAPSHOT.jar` with
loopback address/port arguments and `pnpm exec astro dev --host 127.0.0.1 --port <allocated-port>`
from `apps/web/marketing`. Fresh packaging provenance belongs to the inspected technical report.

Browser artifacts are summaries, not a retained network trace or complete browser-driver source.
The orchestrator reports no response mocks; summaries alone cannot independently prove that
absence. Actual backend operations, PostgreSQL checks and screenshots corroborate the local flow.
No claim is inferred from unit/BDD results alone.

The orchestrator independently confirmed cleanup with `ps -p 92216,92229` and
`docker ps -a --filter name=pt-uuid-live-qa-b068bb85`: no owned processes/container remained.
It also confirmed the named browser closed. This reviewer confirmed temporary auth/state files
are absent and inspected backend graceful shutdown; process/container commands were not repeated.

### Existing browser and marketing regressions inspected

- `TEMP/web-scalpel-footer-report.md`, `web-scalpel-footer-green.log`: **43 critical Chromium
  passes**, no skips; corrected idea-dialog bounds are recorded for EN/ES at 360px and 1280px.
- `TEMP/web-scalpel-footer-scheduler-recheck.log`: **93 passed, one existing skip**, the
  backend-dependent worker-driven SSE calendar-refresh case. The corrected HAR origin is 5173;
  the earlier invalid-origin interrupted run is not passing evidence.
- `TEMP/web-scalpel-media-e2e.log`: **37 passed, four existing skips**: unnamed card actions,
  missing stable composer field hooks, PROCESSING/CAS terminology mismatch, and no composer
  media-library selector. These remain omissions/known limitations, not passes.
- `TEMP/web-scalpel-admin-e2e.log`: **15 passed**, no skips.
- `TEMP/uuid-marketing-final-gates.log`: **540 browser passes**, no skips; lint, Astro check,
  build and **163 unit tests** pass. These broad regression lanes do not establish live provider
  behavior or replace the distinct local backend acceptance evidence.

## next_recommended

**Accept the scoped local candidate with warnings.** No unresolved acceptance-relevant blocker,
CRITICAL/P0/P1 finding, or blocked required local acceptance scenario was identified in this review.
The malformed-shortlinks negative-path gap remains an explicit coverage warning, not a demonstrated
runtime failure or a claim of full HTTP negative-path coverage.

The orchestrator can reconcile state/checklists and proceed to archive under the local-candidate
policy, preserving both reports and their limitations. This report does not itself archive the
change or authorize release. No product, API, architecture or operational contract was changed by
this documentation-only assessment; their owning artifacts remain unchanged.

## risks

- Dedicated malformed/prefixed-shortlinks-header HTTP rejection/no-repository-access coverage
  remains absent, as recorded in the technical report. Add that focused regression in follow-up;
  parser rejection and HTTP mapping evidence must not be described as that integrated test.
- Strict-TDD chronology is not fully certified. Existing scheduler/media omissions and toolchain
  warnings remain visible; broad green counts do not remove them.
- Evidence is in temporary external paths, not a durable raw trace archive. Cleanup prevents replay
  against the original database. Preserve sanitized evidence before temporary retention expires.
- **Out of this local acceptance scope and NOT TESTED/NOT APPROVED for release:** live Unsplash,
  remote GitHub CI/security/Sonar gates, legal approval, representative production backup/restore,
  measured cutover timing, deployed-consumer coordination, stopped-writer rehearsal, production
  SQL, smoke checks and deployment. Their absence is not a local acceptance failure, but applicable
  operational/legal/remote gates remain release conditions. No downtime or rolling compatibility
  approval follows from archiving the local candidate.

Skill Resolution: paths-injected — Mirror executor instructions supplied directly; repository QA
evidence/archive policy read from `.agents/sdd/config.yaml`; no additional skill loaded.
