# Apply Evidence: Stable Cloudflare Tunnel Ingress for SMP

## Repository Validation

The deployment repository validation was implemented test-first in `tests/cloudflare-tunnel-config-validation.sh`. Its initial RED failure required rendered-stack validation and backend-only rollback behavior in `scripts/deploy.sh`; a second RED failure removed GNU-only `mktemp --suffix` usage so the validation runs on macOS.

The following focused validation passed without printing secret values:

```text
bash tests/cloudflare-tunnel-config-validation.sh
bash -n scripts/deploy.sh
bash -n production/health/check.sh
docker stack config --compose-file production/docker-compose.yml
```

The rendered stack confirms one `cloudflared` replica, an `edge` attachment, the `cloudflare_tunnel_token` secret mount at mode `0400` (rendered by Docker as decimal `256`), and no published ports. The local tunnel-token source is present, non-empty, and mode `0600`. The deployment preflight checks all secret sources before it queries the Docker Swarm or can create or update a service.

## Cloudflare Read-Only Inspection

No Cloudflare configuration was changed because the live configuration already conforms to the specified contract:

- Tunnel `profiletailors-api` is healthy, remotely configured, and has one active connector.
- Its route is `api.profiletailors.com` to `http://backend:7638`, with no path matcher.
- The active `api.profiletailors.com` CNAME targets that tunnel and is proxied.

## External Acceptance Probe

The unauthenticated protected endpoint passed:

```text
GET /api/auth/me
Accept: application/vnd.api.v1+json
→ 401
```

The readiness acceptance probe failed:

```text
GET /actuator/health/readiness
→ 404 application/problem+json
{"detail":"No static resource actuator/health/readiness for request 'http://api.profiletailors.com/actuator/health/readiness'.", ...}
```

The live route is correct, but the backend on port `7638` does not expose the specified readiness endpoint. Changing the remote route to the management port or adding a path-specific route would violate this change's full-hostname `http://backend:7638` design. No deployment or Cloudflare mutation was performed.

Direct external listener checks for `7638`, `9091`, and `5432` remain pending because this workstation has no Swarm-manager or VPS network access; the local Docker engine is not an active Swarm.

## Verified Health Contract Update

Systematic debugging established that the `404` is deterministic on public port `7638`, while the public authorization probe consistently returns `401`. The application binds Actuator to the dedicated management listener `backend:9091`; its integration test and release-image verifier both probe readiness through that management port.

The deployment validation now uses a temporary Swarm service on `edge` to request `http://backend:9091/actuator/health/readiness` and requires its successful HTTP response to contain `"status":"UP"`. Public `production/health/check.sh` now validates only `https://api.profiletailors.com/api/auth/me` returning `401`. No management port is published or routed by Cloudflare.

Focused repository validation passed:

```text
bash tests/cloudflare-tunnel-config-validation.sh
bash -n scripts/deploy.sh
bash -n production/health/check.sh
docker stack config --compose-file production/docker-compose.yml
```

Live Swarm execution remains pending because this workstation is not a Swarm manager.

## Approved Live Read-Only Preflight

The preflight ran over system SSH to `fenix-icloud` without deployment or Cloudflare/Dokploy/Swarm configuration changes. The temporary readiness service described below was the explicitly approved, self-removing validation probe.

### Swarm and secret evidence

- The active Dokploy-managed stack is `profiletailors-smp-dz2yer`, not the example `profile-tailors-production` name in the deployment repository.
- `profiletailors-smp-dz2yer_backend`, `profiletailors-smp-dz2yer_cloudflared`, and `profiletailors-smp-dz2yer_postgresql` each reported `1/1` running replicas with no service endpoint ports.
- All required secret names were present by metadata-only `docker secret inspect`, including `profiletailors_cloudflare_tunnel_token_v1`; no secret value was read or printed.
- The `edge` overlay is non-attachable. Its live service attachments are cloudflared and backend; backend also has the private `data` overlay.

### Tunnel and route evidence

- Cloudflare API inspection reported one connector and the remote hostname route `api.profiletailors.com` → `http://backend:7638` with no path matcher.
- DNS inspection reported proxied CNAME `api.profiletailors.com` → `c09232ef-ce3f-4377-b8de-4d45cc6cf0bd.cfargotunnel.com`.
- Connector logs show the token-file startup contract, successful tunnel registration, and the applied remote route. They retain one historical origin-DNS failure at `2026-07-24T09:48:14Z`; current public authorization and internal readiness probes passed.

### Acceptance evidence

- A user-approved temporary Swarm service joined `profiletailors-smp-dz2yer_edge`, requested `http://backend:9091/actuator/health/readiness`, returned `{"status":"UP"}`, completed, and was removed.
- `GET https://api.profiletailors.com/api/auth/me` with `Accept: application/vnd.api.v1+json` returned `401`.
- Public `/actuator/health/readiness` remained `404`, as expected because Cloudflare targets the public API listener on `backend:7638`, not management port `9091`.
- `ss` on `fenix-icloud` returned no listeners for `7638`, `9091`, or `5432`; Swarm service endpoint definitions also reported no published ports.

No rollback was required because the internal readiness and public authorization acceptance checks passed.

## Dokploy Compose Drift Classification

Read-only inspection of Fenix `/etc/dokploy/compose/profiletailors-smp-dz2yer/code` found a checkout at `3d47697a4db29a7251c88088809c3237618667db` with a local `production/docker-compose.yml` diff of `+41/-89` against that commit. The current remote `main` is merge commit `20a9cab3f9265f3a0c33a6641b5fc8ecd381df09`; its parent includes `6a2d397`, and its file list does not modify `production/docker-compose.yml`.

The host drift classification is:

- **Dokploy/UI serialization metadata:** removed comments, anchor rename (`default-logging` to `a1`), flow-list to block-list command/healthcheck formatting, and quoting removal for ordinary scalar values. These are semantic no-ops in the rendered service specification.
- **Dokploy-specific augmentation:** an unused external `dokploy-network` declaration. The overlay exists, but the live backend, cloudflared, and PostgreSQL services attach only to the Dokploy stack `data` and `edge` networks, so this declaration has no current service effect.
- **Rollout blocker:** every secret mount changed from YAML octal `0400` to decimal `400`. Live `stat` confirms backend secrets are mode `620` (uid `1002`, gid `1001`), not `0400`. This widens group access and cannot be treated as harmless formatting.

### Dokploy Revision Ownership

Dokploy database metadata for compose application `smp` (`composeId` `pK-anbx21J6pbQgWfTZ6P`) records:

- GitHub source `dallay/profiletailors-deploy`, branch `main`, compose path `./production/docker-compose.yml`.
- `autoDeploy=true`, `isolatedDeployment=false`, and generated app namespace `profiletailors-smp-dz2yer`.
- Deployment rows record the resolved commit in their description; the latest completed deployment records `3d47697a4db29a7251c88088809c3237618667db`.

Dokploy therefore selects the configured branch head during a deployment and records the resolved SHA afterward; its compose configuration does not pin an immutable SHA. The host clone is still `3d47697` and has not fetched remote `main` at `20a9cab`.

### Advancement Decision

Fenix must **not** advance the active Dokploy application to `20a9cab` yet. The exact blockers are the unclassified effective secret-mode drift (`620` versus required `0400`) and the absence of an immutable SHA pin in Dokploy. Before an approved rollout, preserve/classify or eliminate the host-local Compose drift, confirm Dokploy resolves exactly `20a9cab` immediately before deploy, and record that resolved SHA in the deployment evidence.

## Effective Dokploy Compose and Secret-Mode Origin

Dokploy database metadata has an empty `composeFile` field (`0` bytes, empty MD5) and a `composePath` of `./production/docker-compose.yml`. Its deployment log records `docker stack deploy -c ./production/docker-compose.yml`. Therefore the effective Compose input is Fenix's host-local artifact:

```text
/etc/dokploy/compose/profiletailors-smp-dz2yer/code/production/docker-compose.yml
SHA-256: 2cd6845634305332b2f841a3c22f6e6526e8ff979cb143dc7811483629c1d5af
```

Sanitized structural comparison:

| Property | Merged repository Compose | Effective Dokploy host Compose / live service |
|---|---|---|
| Services | `backend`, `cloudflared`, `postgresql` | Same three service names under namespace `profiletailors-smp-dz2yer` |
| Images | SMP `v0.3.4`; pinned cloudflared and PostgreSQL digests | Same images; SMP resolved to its digest |
| Service ports | No published ports | `Endpoint.Spec.Ports=null` for all three services |
| Networks | backend: `data` + `edge`; cloudflared: `edge`; PostgreSQL: `data` | Same live service attachments; host artifact additionally declares unused external `dokploy-network` |
| Secret UID/GID | backend `1002:1001`, cloudflared `65532:65532`, PostgreSQL `70:70` | Same UID/GID values |
| Secret mode | YAML `0400`, rendered by Docker as decimal `256` | Host artifact `400`, Docker stack rendering `400`, live task files `0620` (`-rw--w----`) |
| Source revision | Remote `main` is `20a9cab` | Last Dokploy deployment recorded `3d47697` |

The `0620` observation does **not** originate from a Docker Swarm permission conversion: Docker's effective service metadata preserves the integer `400`, and the task filesystem reports its direct Unix representation, `0620`. It also does **not** originate from a Dokploy database Compose override or template because `composeFile` is empty. Its direct origin is the host-local modified Compose artifact's bare `mode: 400` values.

The available read-only evidence cannot attribute the writer of that local artifact conclusively to a Dokploy normalization step versus a prior operator edit. The accompanying comment removal, scalar normalization, and unused `dokploy-network` declaration are consistent with Dokploy preprocessing, but are not provenance proof. The security consequence and the rollout block are independent of that attribution.

## Dokploy-Owned Remediation

Two vertical TDD slices were completed without contacting Fenix, Dokploy, or Cloudflare:

1. The configuration validation test was extended to require a Dokploy-owner guard and prohibit `docker stack deploy` in `scripts/deploy.sh`. RED failed because the former script still contained a direct stack deployment. GREEN replaced it with an exit-64 guard that points operators to Dokploy and `scripts/verify-dokploy-rollout.sh <expected-commit-sha>`.
2. A new `tests/dokploy-rollout-verification-test.sh` was added. RED failed because the verification gate did not exist. GREEN added `scripts/verify-dokploy-rollout.sh`, which, when explicitly run from Fenix, checks the expected Dokploy-resolved commit, active namespace, three service replicas, image/service metadata, no published ports, declared secret mode `256`, actual backend mount mode `400`, private edge readiness, and public API `401`. It never invokes `docker stack deploy`.

The repository source Compose remained unchanged because it already declares secret mode `0400` and renders as Docker mode `256`, one cloudflared replica, private `edge`/`data` attachments, and no published origin ports. Documentation now makes Dokploy Environment/runtime ownership, generated-host Compose boundaries, the no-parallel-stack guardrail, and Dokploy-only rollback explicit.

Focused GREEN validation passed without credentials or environment values in output:

```text
bash tests/cloudflare-tunnel-config-validation.sh
bash tests/dokploy-rollout-verification-test.sh
bash -n scripts/deploy.sh
bash -n scripts/verify-dokploy-rollout.sh
docker stack config --compose-file production/docker-compose.yml
bash scripts/deploy.sh -> exit 64 with Dokploy-owner guidance
```

The runtime gate was not executed against Fenix because this phase does not deploy or mutate live infrastructure. It is the approved pre/post-Dokploy-rollout verification command.

## Dokploy Gate Pre-Rollout Execution

Focused local validation passed:

```text
bash tests/cloudflare-tunnel-config-validation.sh
bash tests/dokploy-rollout-verification-test.sh
bash -n scripts/deploy.sh
bash -n scripts/verify-dokploy-rollout.sh
docker stack config --compose-file production/docker-compose.yml
```

The verification script was streamed to Fenix without writing it to the host and invoked twice with expected SHA `20a9cab`. Both attempts exited `1` with the identical sanitized message:

```text
FAIL: profiletailors-smp-dz2yer_backend does not declare secret mode 0400
```

No credential, environment, or secret value was emitted. Dokploy metadata now records resolved SHA `20a9cab3f9265f3a0c33a6641b5fc8ecd381df09`, so the gate passed its revision check and deterministically stopped at the known secret-mode drift before creating its temporary edge-network readiness probe. A follow-up service listing found no `dallay517-dokploy-verify-*` service.

## Dokploy Post-Deploy Gate Attempt

After the reported Dokploy redeploy, focused local tests and syntax/config rendering still passed. The gate was streamed to Fenix with requested SHA `20a9cab` and exited before creating its temporary probe:

```text
FAIL: Dokploy did not record expected commit SHA 20a9cab
```

Read-only Dokploy metadata shows the new resolved revision is `656dce00201e175499726cf57e9f9dba166389b1`, which is the current `main` merge containing the Dokploy-owner guard and verifier. This is a revision-selection mismatch with the requested `20a9cab`, not a gate implementation failure.

Supporting read-only runtime checks found:

- All active Dokploy services are converged at `1/1`.
- No host listeners exist for `7638`, `9091`, or `5432`; service endpoint ports are `null`.
- Public `GET /api/auth/me` with the API `Accept` header returns `401`; public Actuator remains `404` as intended.
- Internal `http://backend:9091/actuator/health/readiness` returns `{"status":"UP"}`.
- Backend secret mount remains `0620` (`uid=1002`, `gid=1001`), and all service secret metadata remains `-rw--w----`; the known secret-mode blocker is not remediated.

No deployment, Dokploy mutation, Cloudflare change, or temporary gate probe occurred during this attempt.
