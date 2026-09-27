# Proposal: Threads Provider Integration

## Intent

Add Meta Threads as the second first-class publishing provider without duplicating publication,
scheduling, calendar, queue, or delivery-attempt workflows. Product truth will describe MVP behavior;
Meta app-review, deployment, and production-rollout evidence remains external.

## Scope

### In Scope
- Provider-aware `THREADS`/`PERSONAL_PROFILE` routing at
  `/api/publishing/{provider}/connections/{initiate|complete}`, retaining LinkedIn aliases.
- Signed Threads OAuth requesting only `threads_basic` and `threads_content_publish`; use
  Meta-supported parameters and do not invent PKCE. Encrypt short/long-lived credentials and refresh
  ahead from returned expiry metadata.
- Provider-neutral credential extraction; never expose tokens, codes, signed media URLs, or raw
  provider payloads in APIs, logs, delivery evidence, or audit metadata.
- Text, single image/video, and 2–20 item mixed-media carousel publishing through bounded container
  creation, polling, and finalization. Reuse worker, capability validation, retries, and idempotent
  operation keys; persist container/final remote IDs and statuses as evidence.
- Provider-fetchable HTTPS media URLs whose TTL covers the container workflow, without a public bucket;
  media-library remains authoritative for readiness.
- Catalog/connect UI, provider-aware callback, reconnect, and disconnect that revokes or invalidates
  credentials and safely removes the connection/account.

### Out of Scope
- Threads analytics, inbound sync, replies, ads, pages, business accounts, or a second composer.
- Invented PKCE, public media storage, entitlement/plan redesign, or production-rollout claims.
- Meta App Review or deployed smoke execution in this repository change; these are external gates.

## Capabilities

### New Capabilities
- None; this extends `publishing`.

### Modified Capabilities
- `publishing`: Threads catalog, lifecycle, capabilities, container evidence, media TTL, idempotency,
  and provider-neutral delivery.
- `oauth-initiation-api`: provider-aware initiation/completion with LinkedIn aliases and signed state.
- `oauth-callback-ui`: provider-aware callback routing with LinkedIn compatibility.

## Approach

Generalize the provider registry/router, OAuth contracts, credential resolver, and configuration
startup checks; add a Threads adapter and Meta transport behind existing ports. Keep controllers thin
and reuse persistence, worker, media readiness, channel events, and safe summaries. Enable Threads
only with complete valid configuration; otherwise omit it from the catalog and keep startup safe.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `server/smp/.../publishing` | Modified | Routing, OAuth, credentials, delivery, disconnect |
| `server/smp/src/main/resources/application.yaml` | Modified | Enablement, endpoints, TTL/poll settings |
| `apps/web/app/src/modules/publishing` | Modified | Catalog, connect, callback, reconnect UX |
| `openspec/specs` and product truth | Modified | Publishing and OAuth contracts |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Container/media expiry or ambiguous finalization | Med | TTL margin, bounded polling, persisted IDs/phases, idempotency |
| Secret leakage or invalid enablement | Med | Port boundary, redaction tests, startup validation, hidden catalog state |
| LinkedIn regression | Med | Alias compatibility and provider contract/BDD regression tests |

## Rollback Plan

Disable Threads configuration, remove its registry/routes/UI action, and revert adapter or migration
changes. Existing LinkedIn aliases and connections remain usable; no destructive data action is needed.

## Dependencies

- Existing publishing/media-library contracts and encrypted credential storage.
- Meta app credentials, redirect registration, test user/app role, and external App Review evidence.

## Success Criteria

- [ ] Threads connects, reconnects, disconnects, and publishes all MVP media shapes through the shared workflow.
- [ ] LinkedIn behavior and aliases remain green; no secret or expired media URL leaks.
- [ ] Local contract/BDD/integration tests pass; deployed Meta smoke and App Review status are recorded separately before production enablement.
