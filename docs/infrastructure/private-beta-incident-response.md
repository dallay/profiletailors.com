# Private Beta Incident Response (DALLAY-557)

**Audience:** On-call operator, release manager, and Yuniel as incident owner.
**Sister documents:** [`private-beta-operator-checklist.md`](./private-beta-operator-checklist.md), [`private-beta-launch-readiness-runbook.md`](./private-beta-launch-readiness-runbook.md), [`private-beta-correlation-matrix.md`](./private-beta-correlation-matrix.md).

This document records the incident owner, escalation path, communication templates, and
review cadence for the private beta. It is intentionally small — the heavy procedure lives
in the operator checklist and the publishing runbook. This file is the human-side glue.

## Incident owner and escalation path

| Role | Person | Responsibility | Escalation triggers |
|---|---|---|---|
| Primary incident owner | Yuniel Acosta Pérez | First responder for any operator-confirmed incident during the private beta. Owns the response, the rollback decision, and the post-mortem. | All `Severity 1` and `Severity 2` incidents (definitions below). |
| Release manager | Yuniel Acosta Pérez (until back-up is named) | Approves any image change, env change, or rollback procedure that goes beyond the safe-off toggle. | Any change that goes beyond `SMP_PUBLISHING_WORKER_ENABLED` and the `docker stack deploy` reverification path. |
| Provider escalation contact | LinkedIn Developer Support (when provider approval is in scope) | Escalate `4xx`/`429` patterns and OAuth state mismatches. | Sustained provider `4xx` for > 15 min, or any `401` after a token refresh. |
| Email provider escalation | Resend support (when `SMP_RESEND_API_KEY` is in scope) | Escalate delivery failure patterns. | Sustained email delivery failure for > 30 min. |

Until the cohort exceeds one operator, Yuniel is the owner, release manager, and provider
escalation point. The roles split when a second on-call operator joins.

## Severity definitions

| Severity | Definition | Examples | Response target |
|---|---|---|---|
| Severity 1 | User-visible outage or data integrity risk | Login fully down; posts stuck in `PUBLISHING` for > 10 min and visible to the cohort; admin invite endpoint returns `5xx`; password reset emails not delivered for > 30 min | Acknowledge within 15 minutes; mitigation in place within 1 hour |
| Severity 2 | Partial degradation with workaround | Stale jobs accumulating (> 5 in `CLAIMED` state older than 30 min) without manual release; LinkedIn OAuth callback intermittent; Mailpit capturing unexpected outgoing emails; rate-limit retries piling up | Acknowledge within 1 hour; mitigation in place within 4 hours |
| Severity 3 | Cosmetic / non-blocking | Misleading log message; documentation drift; non-rejected duplicate scenarios | Acknowledge in next business day |

## Communication templates

### Severity 1 — initial cohort notification

Subject: `[Profile Tailors Beta] Service incident — short summary`

Body:

```text
Hi all,

We are investigating a service incident affecting [scope: login / publishing / waitlist /
invitation / admin]. Symptoms observed so far:

- [concrete symptom with timestamp in UTC]
- [second concrete symptom with timestamp]

We will post the next update in 30 minutes or sooner if we have material news.

— Yuniel, incident owner
```

### Severity 1 — mitigation update

Subject: `[Profile Tailors Beta] Service incident — mitigation in progress`

Body:

```text
Update:

- Mitigation: [what was changed, e.g. worker safe-off via SMP_PUBLISHING_WORKER_ENABLED=false]
- Affected scope: [cohort members / specific workspace ids]
- Next update: in 30 minutes or sooner
- Data integrity: [confirmed safe / under review]

— Yuniel
```

### Severity 1 — resolution

Subject: `[Profile Tailors Beta] Service incident — resolved`

Body:

```text
Resolved.

- Root cause: [one-sentence]
- Fix: [link to PR or env change]
- Follow-ups: [list of post-mortem action items with owner and target date]
- Operator checklist: [link to private-beta-operator-checklist.md]
- Post-mortem doc: [link, when written]

— Yuniel
```

### Severity 2 — internal-only note

```text
[UTC] partial degradation: [symptom]
Affected: [scope]
Workaround: [safe-off / retry / etc.]
Severity 2 — no cohort notification unless > 4h open.
```

### Post-mortem template (Severity 1 and Severity 2 only)

The post-mortem is written as a markdown file under `docs/infrastructure/post-mortems/`
named `YYYY-MM-DD-short-slug.md`. Required sections:

1. **Summary** — one paragraph in plain language.
2. **Impact** — number of users/workspaces affected, duration.
3. **Root cause** — technical explanation.
4. **Detection** — what told us about it first; time-to-detect.
5. **Response** — actions taken, time-to-mitigate, time-to-resolve.
6. **What went well** — bullet list.
7. **What we will change** — actionable items with owner and target date.
8. **Lessons** — non-actionable observations.

## Manual review cadence and alert thresholds

| Signal | Threshold | Action |
|---|---|---|
| Stale jobs (CLAIMED older than `SMP_PUBLISHING_WORKER_STALE_GRACE`) | > 5 rows | Run the stale-job query; investigate the worker poll loop; if not auto-recovering, escalate to Severity 2 |
| Provider rate-limit retries (`provider.rate-limit.retry` log line) | > 10 per minute sustained for > 15 minutes | Pause the worker (`SMP_PUBLISHING_WORKER_ENABLED=false`) and inspect the provider's response body for `Retry-After`; do not log raw provider payloads |
| Email delivery failures (welcome email / invitation email / password reset) | > 3 per minute sustained for > 30 minutes | Verify `SMP_RESEND_API_KEY` is mounted and valid; rotate if confirmed leaked; escalate to Severity 2 |
| Login failures (HTTP 401 on `POST /api/auth/login`) | spike to > 5x baseline for > 5 minutes | Inspect JWT issuer reachability and refresh-cookie issuance; escalate to Severity 1 if all logins affected |
| Invitation acceptance failures | > 0 with `invitation_id` matching a freshly minted invitation | Walk the pivot in [`private-beta-correlation-matrix.md`](./private-beta-correlation-matrix.md); confirm `WAITLIST_ENTRY_INVITED` audit event is recorded; escalate to Severity 1 if acceptance endpoint returns `5xx` |
| Worker toggle mismatch (effective env vs Swarm env source) | Any drift | Treat as Severity 2; redeploy to bring the running task back in sync with the source-of-truth env |

The cadence above is the **operator's** view. The engineering review cadence is
weekly during the private beta (Monday 09:00 UTC): review the past seven days of log
volume per `category`, the stale-job counts, the rate-limit retry counts, and the email
delivery failure counts; file concrete action items as Linear issues.

## What this document is NOT

- It is **not** a runbook for any single failure mode. Use the operator checklist for the
  per-surface checks and the publishing runbook for safe-off / re-enable.
- It is **not** a substitute for the production incident response on the corporate side.
  The private beta runs on a single VPS, not on the production Kubernetes topology; this
  document is intentionally smaller than a corporate SRE plan.
- It is **not** a provider-verified or multi-user-verified acceptance record. The
  thresholds above are calibrated for a small cohort; revisit them when the cohort grows
  past a single digit of active workspaces.
