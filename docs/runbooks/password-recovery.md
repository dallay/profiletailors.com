# Password Recovery Runbook

## Overview

Use this runbook when password reset requests, resets, notification delivery, or token cleanup behave
abnormally. Start with aggregate telemetry and safe identifiers. Never search for or record a raw token,
email, raw IP, reset URL or query string, password, token hash, password hash, provider text,
or exception message. Do not paste those values into tickets, dashboards, logs, or chat. Use only
incident IDs, principal IDs when access is authorized, fixed categories, timestamps, and aggregate
counts.

## Changes

- Added operational procedures for password recovery incidents, retries, terminal failures, cleanup,
  observability, feature-flag rollback, validation, and escalation.

## Usage

### Symptoms and first response

| Symptom | First safe check | Likely area |
|---|---|---|
| Reset failures increase | Reset outcomes by fixed `failure.category` | Endpoint, validation, flag, or database |
| Delivery retries increase | Notification outcomes by category and attempt bucket | Email provider or network |
| Terminal failures increase | Aggregate failure rows by category and time bucket | Provider outage or permanent rejection |
| Token rows grow continuously | Aggregate expired-row counts and cleanup configuration | Cleanup scheduler or database |
| Recovery returns 503 | Confirm the deployed feature-flag value | Intentional rollback or configuration drift |

1. Record the incident ID, UTC start time, environment, deployment revision, and observed fixed
   categories.
2. Check health and the password recovery metrics before changing configuration.
3. Do not retry individual users manually. A new forgot-password request safely invalidates prior
   active tokens and uses the normal retry policy.
4. If sensitive data is exposed, stop copying it, restrict the incident record, preserve evidence,
   and escalate immediately.

### Dashboards, metrics, and spans

The implemented counter is `identity.password.recovery.outcomes`. Prometheus exposes it as
`identity_password_recovery_outcomes_total`. The implemented observation/span name is
`identity.password.recovery`.

Use only these five bounded labels and span attributes:

- `operation`: `reset` or `notification_delivery`
- `notification.type`: `password_reset`, `none`, or `unknown`
- `status`: `completed`, `failed`, `success`, `retry`, or `terminal_failure`
- `failure.category`: `none`, reset categories such as `invalid_request` or `internal`, or the fixed
  provider categories `provider_unavailable`, `provider_timeout`, `provider_rejected`, and
  `invalid_request`
- `attempt.bucket`: `none`, `first`, `retry`, or `exhausted`

Safe PromQL examples:

```promql
sum by (operation, status, failure_category) (
  rate(identity_password_recovery_outcomes_total[5m])
)

sum by (notification_type, status, attempt_bucket) (
  increase(identity_password_recovery_outcomes_total{operation="notification_delivery"}[15m])
)
```

If the monitoring backend preserves dotted label names, select them as `notification.type`,
`failure.category`, and `attempt.bucket`; Prometheus normalizes dots to underscores. Never add a
principal ID, email, raw IP, token, reset URL, or exception as a metric label or span attribute.

### Notification retries and terminal failures

Retry behavior is bounded exponential backoff. Retryability comes from the fixed provider category,
not provider-message parsing.

| Configuration key | Default | Meaning |
|---|---:|---|
| `app.identity.password-recovery.notification-retry.max-attempts` | `3` | Total attempts, including the first |
| `app.identity.password-recovery.notification-retry.initial-backoff` | `1s` | Delay after the first retryable failure |
| `app.identity.password-recovery.notification-retry.multiplier` | `2` | Backoff multiplier |
| `app.identity.password-recovery.notification-retry.max-backoff` | `30s` | Maximum delay between attempts |

`PROVIDER_UNAVAILABLE` and `PROVIDER_TIMEOUT` are retryable. `PROVIDER_REJECTED` and
`INVALID_REQUEST` fail without another attempt. Exhaustion writes one terminal failure to
`password_reset_notification_failures` and emits status `terminal_failure` with attempt bucket
`exhausted`.

Terminal failure records contain only these safe fields:

| Field | Use |
|---|---|
| `id` | Failure record correlation |
| `principal_id` | Authorized internal correlation; never a metric label |
| `notification_type` | Fixed value `PASSWORD_RESET` |
| `attempts` | Bounded attempt count |
| `failed_at` | UTC failure time |
| `failure_category` | Fixed provider failure category |

Do not replay a stored reset notification: the terminal record does not retain a token or reset URL.
Ask the user to initiate a new request after provider recovery.

### Cleanup and retention

Cleanup is scheduled in-process, runs after the initial delay, and then uses a fixed delay. It is
idempotent and deletes rows only when `expires_at` is strictly older than the retention cutoff and
`used_at` is either null or also strictly older than the cutoff. Active unexpired tokens and records
used inside the audit-retention window remain.

| Configuration key | Default |
|---|---:|
| `app.identity.password-recovery.cleanup.retention` | `30d` |
| `app.identity.password-recovery.cleanup.interval` | `24h` |
| `app.identity.password-recovery.cleanup.initial-delay` | `5m` |

Use aggregate SQL only. The following query does not select token hashes, network hashes, email,
passwords, or reset URLs:

```sql
SELECT 'terminal_failure' AS record_type,
       failure_category AS safe_category,
       date_trunc('hour', failed_at) AS time_bucket,
       count(*) AS record_count
FROM password_reset_notification_failures
WHERE failed_at >= now() - interval '24 hours'
GROUP BY failure_category, date_trunc('hour', failed_at)
UNION ALL
SELECT 'expired_token' AS record_type,
       'expired' AS safe_category,
       date_trunc('hour', expires_at) AS time_bucket,
       count(*) AS record_count
FROM password_reset_tokens
WHERE expires_at < now() - interval '30 days'
GROUP BY date_trunc('hour', expires_at)
ORDER BY time_bucket DESC;
```

A growing expired count after `initial-delay + interval` indicates scheduler, instance, or database
failure. Do not delete rows manually during an incident or legal hold. If cleanup must be corrected,
change the reviewed retention configuration and validate the scheduler in a controlled environment.

### Feature-flag rollback

Set `app.identity.password-recovery.enabled=false` in the deployment configuration and redeploy using
the normal platform process. This makes both recovery endpoints return 503 and prevents token
creation or consumption. It does not erase existing records or disable cleanup.

Rollback procedure:

1. Declare the rollback in the incident timeline and capture the deployment revision.
2. Apply `app.identity.password-recovery.enabled=false` through the approved configuration channel;
   never place secrets in the command line or incident record.
3. Verify aggregate 503 responses and confirm reset-completed outcomes stop.
4. Keep cleanup enabled and investigate notification/database health.
5. Re-enable only after focused validation passes and the incident owner approves recovery.

### Validation commands

Run from the repository root using `just` recipes:

```bash
just backend-test-fast
just backend-lint
```

Do not run broad BDD or CI suites during incident containment unless the incident owner explicitly
requires them and the environment prerequisites are available.

### Escalation

Escalate immediately to the identity/backend owner and Incident Commander when:

- reset `internal` failures or terminal notification failures persist for 15 minutes;
- the feature flag cannot contain the incident;
- cleanup does not run for more than two configured intervals;
- database integrity, session revocation, or notification failure persistence is uncertain; or
- any token, email, raw IP, reset URL, password, hash, provider text, or exception detail reaches
  logs, telemetry, diagnostics, or an incident record.

Include only the incident ID, UTC window, environment, revision, aggregate counts, fixed categories,
and actions taken. Follow the compliance incident-response runbook for suspected data exposure.

## Troubleshooting

- **Metrics are absent:** Confirm the internal Prometheus endpoint is scraped and generate a safe
  synthetic request in a non-production environment. Do not add sensitive labels to diagnose it.
- **Retries remain at `first`:** Check the fixed failure category. Permanent categories do not retry.
- **No terminal row exists after exhaustion:** Compare aggregate `terminal_failure` telemetry with
  aggregate database counts, then escalate a persistence failure; do not reconstruct the reset URL.
- **Cleanup count does not fall:** Confirm the deployed retention, interval, and initial-delay values,
  application scheduling health, and database connectivity. Respect legal holds.
- **Users report invalid links after rollback:** This is expected while the flag is disabled. Do not
  inspect their link; communicate the outage and ask for a new request after recovery.

## References

- [`openspec/changes/archive/2026-07-29-password-recovery/spec.md`](../../openspec/changes/archive/2026-07-29-password-recovery/spec.md)
- [`openspec/changes/archive/2026-07-29-password-recovery/design.md`](../../openspec/changes/archive/2026-07-29-password-recovery/design.md)
- [`docs/compliance/incident-response-runbook.md`](../compliance/incident-response-runbook.md)
- [`docs/monitoring/prometheus-grafana-setup.md`](../monitoring/prometheus-grafana-setup.md)
- [`Justfile`](../../Justfile)
