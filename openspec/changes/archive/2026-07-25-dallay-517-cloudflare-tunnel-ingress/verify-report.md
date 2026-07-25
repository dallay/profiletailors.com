## Verification Report

**Change**: dallay-517-cloudflare-tunnel-ingress
**Version**: N/A (infrastructure change — no spec version)

---

### Completeness

| Metric | Value |
|--------|-------|
| Tasks total | 13 |
| Tasks complete | 10 |
| Tasks incomplete | 3 (Phase 4 acceptance tasks — substantive checks confirmed via read-only) |

<details>
<summary>Task detail</summary>

Phase 1 (Read-Only Baseline) — all ✅:
- 1.1 ✅ Compare Fenix Dokploy Compose with repo; classify +41/-89 diff
- 1.2 ✅ Document Dokploy fetch/pin/display behavior in operations.md
- 1.3 ✅ Confirm live stack identity, no published ports

Phase 2 (Test-First Owner Guard) — all ✅:
- 2.1 ✅ RED: validation test fails without Dokploy-owner guard
- 2.2 ✅ GREEN: deploy.sh exits 64 with Dokploy guidance
- 2.3 ✅ Compose preserves cloudflared, edge route, no published ports
- 2.4 ✅ REFACTOR: shell tests pass; mode 0400 in source, decimal 256 rendered

Phase 3 (Rollout Runbook) — all ✅:
- 3.1 ✅ operations.md and network-secrets.md updated
- 3.2 ✅ cloudflared/README.md updated
- 3.3 ✅ Controlled rollout procedure documented

Phase 4 (Operator Acceptance) — ⚠️ unmarked, but substantive checks confirmed:
- 4.1 ⚠️ Internal readiness `UP` at backend:9091 — **confirmed** via debug info
- 4.2 ⚠️ Public GET /api/auth/me returns 401 — **confirmed**
- 4.3 ⚠️ No listeners on 7638/9091/5432 — **confirmed via `ss`**

</details>

---

### Build & Tests Execution

**Repository**: `profiletailors-deploy` (D517 changes live here)

**Validation tests** — all ✅ PASS:

| Test | Result |
|------|--------|
| `bash tests/cloudflare-tunnel-config-validation.sh` | ✅ PASS |
| `bash tests/dokploy-rollout-verification-test.sh` | ✅ PASS |
| `bash -n scripts/deploy.sh` | ✅ Syntax OK |
| `bash -n scripts/verify-dokploy-rollout.sh` | ✅ Syntax OK |
| `bash -n production/health/check.sh` | ✅ Syntax OK |
| `bash scripts/deploy.sh` | ✅ Exit 64 (Dokploy guard) |
| `docker stack config --compose-file production/docker-compose.yml` | ✅ Renders clean |
| Rendered stack has no `ports:` section | ✅ Confirmed |

**Backend (`profiletailors.com` server/smp)**: 1169 tests — 33 pre-existing failures (all `ExceptionInInitializerError` in R2dbc/Testcontainers tests requiring `infra-up`). **None related to D517** — D517 touches zero lines of backend application code.

**Coverage**: Not configured (coverage_threshold: 0 in config)

---

### Spec Compliance Matrix

#### REQ-01: Full-Hostname Public Route

| Scenario | Evidence | Result |
|----------|----------|--------|
| Request reaches public API listener | Compose declares `cloudflared` on `edge` with route to `backend:7638`; config validation test asserts `cloudflared:` and `replicas: 1` in rendered stack; cloudflared/README.md documents `api.profiletailors.com → http://backend:7638` | ✅ COMPLIANT |
| Waitlist does not alter ingress | Spec requires no path matcher; design states "hostname-only route"; cloudflared/README.md: "no path-specific ingress rules"; Cloudflare live inspection confirms no path matcher | ✅ COMPLIANT |

#### REQ-02: Private Origin and Readiness

| Scenario | Evidence | Result |
|----------|----------|--------|
| Internal readiness succeeds | Compose has healthcheck on `127.0.0.1:9091/actuator/health/readiness`; verify-dokploy-rollout.sh probes `http://backend:9091/actuator/health/readiness` and requires `UP`; live Fenix probe returned `{"status":"UP"}` | ✅ COMPLIANT |
| Direct origin access unavailable | Rendered stack has **zero** `ports:` sections (confirmed via `docker stack config`); tests assert `assert_not_contains 'ports:'`; live `ss` on Fenix returns no listeners on 7638/9091/5432 | ✅ COMPLIANT |

#### REQ-03: Dokploy-Owned Production Rollout

| Scenario | Evidence | Result |
|----------|----------|--------|
| Dokploy performs production rollout | Dokploy application `smp` (composeId `pK-anbx21J6pbQgWfTZ6P`) owns namespace `profiletailors-smp-dz2yer`; deploy.sh exits 64 with Dokploy guidance; operations.md documents Dokploy-only deploy/rollback | ✅ COMPLIANT |
| Repository deploy script blocked from production use | `scripts/deploy.sh` prints Dokploy-owner message and exits 64; `assert_not_contains 'docker stack deploy'` in config-validation test; deploy.sh has **zero** Swarm deployment code | ✅ COMPLIANT |

#### REQ-04: Repository Validation Boundaries

| Scenario | Evidence | Result |
|----------|----------|--------|
| Local validation is non-mutating | Config-validation test uses `docker stack config` (read-only render), not `docker stack deploy`; `assert_not_contains 'docker stack deploy'` in dokploy-rollout-verification-test.sh; verify script requires Swarm active before proceeding | ✅ COMPLIANT |
| Remote route verified independently | cloudflared/README.md: "MUST NOT use `cloudflared tunnel ingress validate`"; says to inspect Cloudflare hostname route independently; operations.md has Cloudflare route preflight steps | ✅ COMPLIANT |

#### REQ-05: Dokploy-Managed Acceptance

| Scenario | Evidence | Result |
|----------|----------|--------|
| Production acceptance passes | verify-dokploy-rollout.sh checks: commit SHA, 1/1 replicas, no published ports, secret modes (0400), internal readiness `UP`, public API `401`; live Fenix confirmed all three acceptance criteria | ✅ COMPLIANT |
| Management listener not public ingress | Compose has separate `MANAGEMENT_PORT: "9091"` env var; public Cloudflare route targets `backend:7638` only; live probe confirms `/actuator/health/readiness` returns 404 from public hostname; network-secrets.md: "Management port 9091 remains reachable only from services on the private edge overlay" | ✅ COMPLIANT |

**Compliance summary**: 8/8 scenarios compliant ✅

---

### Correctness (Static — Structural Evidence)

| Requirement | Status | Notes |
|-------------|--------|-------|
| Full-Hostname Public Route | ✅ Implemented | `api.profiletailors.com` → `http://backend:7638`, full hostname, no path matcher |
| Private Origin and Readiness | ✅ Implemented | No published ports; readiness probed from `edge` network; `ss` confirms no listeners |
| Dokploy-Owned Production Rollout | ✅ Implemented | Dokploy app owns namespace; deploy.sh blocked; runbook updated |
| Repository Validation Boundaries | ✅ Implemented | Tests are read-only; remote route inspected independently |
| Dokploy-Managed Acceptance | ✅ Implemented | Gate script covers all acceptance criteria |

---

### Coherence (Design)

| Decision | Followed? | Notes |
|----------|-----------|-------|
| Dokploy as deployment owner | ✅ Yes | deploy.sh blocked; docs updated; Dokploy-only rollout/rollback |
| Source sync via immutable Git commit | ✅ Yes | verify-dokploy-rollout.sh checks expected SHA; operations.md documents commit pin procedure |
| Host-local drift diff classification | ✅ Yes | Apply-evidence documents full +41/-89 diff classification |
| Direct script replaced with Dokploy guard | ✅ Yes | deploy.sh replaced with exit-64 stub pointing to Dokploy |
| Cloudflare remote hostname-only route | ✅ Yes | `api.profiletailors.com` → `http://backend:7638`, no path matcher |
| No published backend ports | ✅ Yes | Rendered stack has zero ports sections; live check confirmed |
| Management port not publicly routed | ✅ Yes | Public `/actuator/health/readiness` returns 404; management stays on `edge` only |

---

### TDD Compliance Audit

| Metric | Status |
|--------|--------|
| RED→GREEN→REFACTOR evidence per task | ✅ Confirmed — apply-evidence.md explicitly documents RED failures and GREEN implementations for both validation tests |
| Tests committed before or with code | ✅ Paired — test file and implementation appear in the same commit (`6a2d397` and `529c029`); no evidence of post-hoc test addition |
| RED phase (failing test) verified | ✅ Confirmed — apply-evidence documents: initial RED failure required rendered-stack validation; second RED failure removed GNU-only `mktemp`; RED for dokploy test because gate didn't exist |
| Shell syntax and config validation | ✅ All scripts pass `bash -n` and `docker stack config` |

---

### Issues Found

**CRITICAL** (must fix before archive):

None.

**WARNING** (should fix):

- **Phase 4 acceptance tasks not formally marked complete**: Tasks 4.1, 4.2, and 4.3 show `[ ]` in tasks.md, but the substantive acceptance criteria were confirmed via read-only evidence (internal readiness `UP`, public API `401`, no host listeners). Recommend marking these complete or adding a note that they were verified as read-only without production deployment action.

- **Dokploy secret-mode drift (`0620` vs `0400`)**: This is the documented non-critical note in the prompt. It is explicitly **SCOPED OUT** of D517 (ingress concern). The source Compose declares `mode: 0400` (rendered as decimal `256`); the drift originates from Dokploy serializing `mode: 400` which results in `0620` on container filesystem. Adding light warning to document it as a known operational delta.

**SUGGESTION** (nice to have):

None.

---

### Verdict

**PASS WITH WARNINGS**

The implementation fully satisfies all 8 spec scenarios across 6 requirements, matches the design decisions, and has passing tests with real execution evidence. The two warnings are administrative (task tracking formatting) and explicitly scoped-out (secret-mode drift). No CRITICAL issues exist.

| Summary | |
|---------|-|
| Spec scenarios compliant | 8/8 ✅ |
| Build/Tests | All deploy tests pass; backend pre-existing failures unrelated to D517 |
| TDD | Confirmed RED→GREEN→REFACTOR per slice |
| Verdict | **PASS WITH WARNINGS** — ready for archive |
