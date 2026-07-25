# Design: Dokploy-Managed Cloudflare Tunnel Ingress for SMP

## Technical Approach

For 0.1.0, Dokploy is the sole production deployment owner. Sync the approved merged deployment-repository commit (currently `6a2d397` when selected) through the Dokploy Git source, reconcile its host-local Compose changes deliberately, then deploy only the existing Dokploy application/Swarm stack `profiletailors-smp-dz2yer`. Cloudflare remains a remote full-hostname route to private `backend:7638`; application behavior is unchanged.

## Architecture Decisions

| Decision | Choice | Alternative | Rationale |
|---|---|---|---|
| Deployment owner | Dokploy application owns deployments | Repository `docker stack deploy` | Live labels and `/etc/dokploy/compose/profiletailors-smp-dz2yer/code` identify Dokploy ownership; the script's `profile-tailors-production` name would create a parallel stack. |
| Source sync | Select the approved immutable Git commit in Dokploy, then record it | Copy local files to host | Preserves reviewable Git provenance and Dokploy reconciliation. |
| Host-local drift | Diff Dokploy's Compose against the approved rendered source; classify each difference before change | Blind overwrite or retain all drift | Prevents losing Dokploy-required settings or preserving unreviewed production drift. |
| Direct script | Replace `scripts/deploy.sh` production path with a fail-fast Dokploy-owner guard and runbook pointer | Retarget it to Dokploy stack | Prevents bypassing Dokploy and accidental parallel-stack creation. |

## Data Flow

```text
Approved Git commit → Dokploy Git sync → Dokploy Compose reconciliation
                                  → profiletailors-smp-dz2yer (Swarm)
Client → Cloudflare hostname route → cloudflared/edge → backend:7638
                                               └→ backend:9091 readiness (edge-only)
```

## File Changes

| File | Action | Description |
|---|---|---|
| `scripts/deploy.sh` | Modify | Refuse production direct-Swarm deployment when Dokploy owns the stack; emit the Dokploy rollout command/runbook only. |
| `tests/cloudflare-tunnel-config-validation.sh` | Modify | Assert no production `docker stack deploy` path and retain ingress/readiness contract assertions. |
| `docs/operations.md` | Modify | Add commit sync, drift classification, Dokploy stack verification, rollout, and rollback procedure. |
| `docs/network-secrets.md` | Modify | Name Dokploy as deployment owner; retain Swarm/secret and private-network boundaries. |
| `production/docker-compose.yml` | Modify only if reconciliation approves | Apply approved source contract while preserving Dokploy-required generated metadata outside Git. |

## Interfaces / Contracts

```text
Active stack: profiletailors-smp-dz2yer
Cloudflare: api.profiletailors.com → http://backend:7638 (hostname-only)
Internal: edge → http://backend:9091/actuator/health/readiness = 200, {"status":"UP"}
External: GET /api/auth/me + Accept: application/vnd.api.v1+json = 401
```

No published ports; management remains private. Spring Security remains the authorization boundary.

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Static | Owner guard and rendered private-ingress contract | Shell/config validation without secrets. |
| Reconciliation | Commit and compose drift | Capture a redacted, normalized diff; classify as expected Dokploy metadata, approved source change, or blocker. |
| Deployment | Active stack identity and convergence | Verify Dokploy application ID/path plus service labels/names, desired/current replicas, image digest, networks, and secret metadata. |
| Smoke | Existing readiness and authorization contract | Edge-network readiness probe; external `401` probe; verify no listeners on `7638`, `9091`, `5432`. |

## Migration / Rollout

1. In Dokploy, verify the app maps to `profiletailors-smp-dz2yer`, its repository/branch, and current deployed revision. Fetch/select the approved merged SHA; do not deploy until the displayed revision matches it.
2. Export or inspect Dokploy's generated Compose and compare it to the commit's normalized rendered Compose. Classify every host-local difference: Dokploy-generated metadata (retain), deliberate source delta (must be committed), or unexplained runtime/config drift (block and investigate). Never copy the host file over Git or overwrite it blindly.
3. Confirm the stack's three services, labels, image, `edge`/`data` networks, versioned secret references, and absence of published ports. Deploy from Dokploy only.
4. Validate convergence, private readiness, and public `401`. On failure, use Dokploy rollback to the recorded previous revision; restore the remote route only if it changed. Do not use the direct script or create another stack.

No data migration required. A one-replica backend update can briefly return `502`.

## Open Questions

- [ ] Confirm Dokploy exposes a commit pin/revision selector; otherwise record the resolved commit SHA from its Git sync before deployment.
