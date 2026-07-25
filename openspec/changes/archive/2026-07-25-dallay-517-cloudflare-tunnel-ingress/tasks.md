# Tasks: Dokploy-Managed Cloudflare Tunnel Ingress

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | 260–380 |
| 400-line budget risk | Medium |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|---|---|---|---|
| 1 | Guard, validate, and document Dokploy rollout | Single PR | Deploy repo; rollout remains operator-approved |

## Phase 1: Read-Only Reconciliation Baseline

- [x] 1.1 Read-only: compare Fenix `/etc/dokploy/compose/profiletailors-smp-dz2yer/code` at `3d47697` with merged deploy-repo `6a2d397`; capture a redacted normalized `production/docker-compose.yml` diff and classify all +41/-89 lines as Dokploy metadata, approved source delta, or blocker.
  - Evidence: Dokploy's effective input is the host-local Compose artifact; its `mode: 400` values propagate unchanged to live Swarm secret mounts as `0620`. No database Compose override exists.
- [x] 1.2 Read-only: document in `/Users/acosta/Dev/dallay/profiletailors-deploy/docs/operations.md` how Dokploy fetches, pins/selects, displays, and records the merged SHA; block rollout unless the displayed/resolved revision is `20a9cab`.
- [x] 1.3 Read-only: confirm Dokploy app/path, live stack `profiletailors-smp-dz2yer`, service labels, images, networks, versioned secrets, and no published `7638`, `9091`, or `5432` listeners.

## Phase 2: Test-First Owner Guard and Configuration

- [x] 2.1 RED: extend `tests/cloudflare-tunnel-config-validation.sh` to fail if `scripts/deploy.sh` contains a production `docker stack deploy` path or omits the Dokploy-owner guard; retain rendered private-ingress assertions without secrets.
- [x] 2.2 GREEN: modify `scripts/deploy.sh` to fail fast for production, name `profiletailors-smp-dz2yer`, and point operators to the Dokploy procedure; it must never create `profile-tailors-production`.
- [x] 2.3 Modify `production/docker-compose.yml` only after Phase 1 approves source deltas; preserve one `cloudflared` replica, `edge` route to `backend:7638`, private management readiness, and no published origin ports.
- [x] 2.4 REFACTOR: run shell syntax and config-validation tests; inspect source Compose mode `0400` and rendered Docker mode `256` without printing secret values.

## Phase 3: Dokploy Rollout and Recovery Runbook

- [x] 3.1 Update `docs/operations.md` and `docs/network-secrets.md` with Git sync/revision evidence, drift classifications, no-parallel-stack guardrail, and Dokploy-only deploy/rollback commands.
- [x] 3.2 Update `production/cloudflared/README.md` with the remote hostname-only route `api.profiletailors.com` → `http://backend:7638`; exclude local ingress-file validation as route proof.
- [x] 3.3 Define controlled rollout: record previous Dokploy revision, select verified `20a9cab`, deploy only the active Dokploy app, wait for desired/current replicas, and roll back in Dokploy on failed acceptance.

## Phase 4: Operator Acceptance

- [ ] 4.1 After approval, inspect Cloudflare route/DNS and connector logs; prove internal `edge` readiness at `backend:9091/actuator/health/readiness` is `200`/`UP`.
  - Post-deploy gate evidence: Dokploy resolved `656dce0` (current `main`), not requested SHA `20a9cab`; the gate stopped before its temporary probe. Manual read-only checks confirm the known secret-mode drift remains at `0620` despite service convergence and internal readiness `UP`.
- [ ] 4.2 Verify public `GET https://api.profiletailors.com/api/auth/me` with `Accept: application/vnd.api.v1+json` and no credentials returns `401`; confirm public Actuator routing is absent.
- [ ] 4.3 Confirm no direct listeners on `7638`, `9091`, or `5432`; record evidence, stop on failure, and use only recorded Dokploy rollback.
