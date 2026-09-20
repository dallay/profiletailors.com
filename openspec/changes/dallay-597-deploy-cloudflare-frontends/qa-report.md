# QA Report — dallay-597-deploy-cloudflare-frontends

**Change**: dallay-597-deploy-cloudflare-frontends  
**Date**: 2026-09-19  
**Phase**: qa  
**Mode**: openspec  
**QA Agent**: sdd-qa

## Overview

### Executive Summary

Acceptance QA for the release-driven Cloudflare Pages deployment change is **PARTIALLY TESTED**:
AC1–AC10 remain **NOT TESTED** because live deployment verification requires a merged release on
`main`, while AC11–AC12 pass static documentation checks against repository artifacts. This is a
GitHub Actions workflow and deployment configuration change with no frontend application runtime in
this repository.

AC1–AC10 from `state.yaml` map to workflow contract scenarios that require live GitHub Actions
execution after merge and remain **NOT TESTED**. AC11–AC12 pass static documentation checks against
the runbook and ADR repository artifacts. Static code review was completed in the verify phase and
confirmed structural correctness. The change includes a TDD helper script with passing unit tests
(5/5), but the workflow contract itself has no test harness.

**Verdict**: PARTIALLY TESTED **Rationale**: AC1–AC10 require live GitHub Actions execution and
remain deferred to first-merge verification; AC11–AC12 pass static documentation checks based on
repository artifacts.

---

### Source Artifacts and Verification Handoff

| Artifact                                           | Status  | Notes                                  |
|----------------------------------------------------|---------|----------------------------------------|
| `proposal.md`                                      | ✅ Read | Intent, scope, excluded scope clear    |
| `specs/release-driven-frontend-deployment/spec.md` | ✅ Read | 6 requirements, 11 scenarios           |
| `design.md`                                        | ✅ Read | 8 architecture decisions documented    |
| `tasks.md`                                         | ✅ Read | 25 tasks across 5 phases               |
| `verify-report.md`                                 | ✅ Read | 25/25 tasks complete; Phase 5 deferred |
| `state.yaml`                                       | ✅ Read | 12 acceptance criteria defined         |

**Verification Phase Handoff**:

- All 25 implementation tasks marked complete
- Static analysis passing
- Unit tests for `extract-release-info.mjs` passing (5/5)
- Phase 5 (first-merge verification) explicitly deferred
- No blocking defects reported

---

### Target, Environment, and Limitations

| Dimension                 | Value                                                                                          |
|---------------------------|------------------------------------------------------------------------------------------------|
| **Target Application**    | None (GitHub Actions workflow configuration)                                                   |
| **Deployment Target**     | Cloudflare Pages (3 projects: `app-profile-tailors`, `profiletailors`, `profiletailors-admin`) |
| **Test Environment**      | Local repository checkout                                                                      |
| **Authentication**        | Not applicable (no GitHub Actions runtime available)                                           |
| **Permissions**           | Read-only filesystem access to repository                                                      |
| **Executor Capabilities** | Static code inspection, file reading, test runner invocation for Node.js scripts               |

**Critical Limitation**: This change modifies `.github/workflows/release-please.yml` to add
deployment logic triggered by Release Please release creation. The workflow contract can only be
validated by:

1. Merging the change to `main`
2. Creating a Release Please release for one of the three frontends
3. Observing GitHub Actions workflow execution
4. Verifying Cloudflare Pages deployment

None of these steps can be performed in the QA phase pre-merge. The repository explicitly accepts
this constraint per the verify report: "Live Cloudflare deployment verification requires a merged
release on `main` and is deferred to first-merge verification per the change's explicit
policy-allowed exception."

---

### Capability Inventory

#### Available Capabilities

| Capability               | Status      | Rationale                                                          |
|--------------------------|-------------|--------------------------------------------------------------------|
| **Static Code Review**   | ✅ Selected | Already completed in verify phase; QA re-inspection not applicable |
| **Unit Test Execution**  | ✅ Selected | `extract-release-info.test.mjs` unit tests available and passing   |
| **Dependency Analysis**  | ✅ Selected | GitHub Actions dependencies pinned; wrangler SHA-pinned            |
| **Documentation Review** | ✅ Selected | Docs, ADR, runbook present and reviewed in verify                  |

#### Unavailable Capabilities

| Capability                   | Status         | Reason                                       |
|------------------------------|----------------|----------------------------------------------|
| **Browser E2E Testing**      | ❌ Unavailable | No application runtime; workflow-only change |
| **API Testing**              | ❌ Unavailable | No HTTP service in scope                     |
| **Exploratory Testing**      | ❌ Unavailable | No interactive surface                       |
| **Accessibility Testing**    | ❌ Unavailable | No UI in scope                               |
| **Responsive Testing**       | ❌ Unavailable | No frontend in scope                         |
| **GitHub Actions Execution** | ❌ Unavailable | Cannot trigger workflows pre-merge           |
| **Cloudflare Deployment**    | ❌ Unavailable | Requires merge, release, and credentials     |

---

## Changes

### Scenario Matrix

AC1–AC10 from `state.yaml` map to workflow contract scenarios from the spec and cannot be validated
pre-merge. AC11–AC12 pass static documentation checks against repository artifacts.

#### AC1: Merge to main does not deploy frontends

**Scenario**: Merge to main skips every deploy  
**Category**: Happy Path (workflow contract)  
**Result**: **NOT TESTED**  
**Reason**: Requires live GitHub Actions execution after merge. Workflow `if:` conditions visible in
`.github/workflows/release-please.yml` lines 130, 173, 215 correctly gate on
`needs.release-please.outputs.<scope>--release_created == 'true'`.  
**Evidence**: Static inspection only; cannot observe runtime behavior.

---

#### AC2: app release triggers only app deploy

**Scenario**: app release deploys only app  
**Category**: Happy Path (component isolation)  
**Result**: **NOT TESTED**  
**Reason**: Requires Release Please release creation for `apps/web/app` and GitHub Actions
execution.  
**Evidence**: Workflow structure shows `deploy-app` job gated on `app--release_created`; cannot
execute.

---

#### AC3: admin release triggers only admin deploy

**Scenario**: admin release deploys only admin  
**Category**: Happy Path (component isolation)  
**Result**: **NOT TESTED**  
**Reason**: Requires Release Please release creation for `apps/web/admin` and GitHub Actions
execution.  
**Evidence**: Workflow structure shows `deploy-admin` job gated on `admin--release_created`; cannot
execute.

---

#### AC4: landing release triggers only landing deploy

**Scenario**: landing release deploys only landing  
**Category**: Happy Path (component isolation)  
**Result**: **NOT TESTED**  
**Reason**: Requires Release Please release creation for `apps/web/marketing` and GitHub Actions
execution.  
**Evidence**: Workflow structure shows `deploy-landing` job gated on `landing--release_created`;
cannot execute.

---

#### AC5: Deployment builds from exact release SHA

**Scenario**: Checkout uses release tag not main HEAD  
**Category**: Security/Correctness  
**Result**: **NOT TESTED**  
**Reason**: Requires GitHub Actions execution to observe SHA-pinned checkout behavior. **Evidence**:
Workflow uses the SHA-pinned `actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1` action with
`ref: ${{ needs.release-please.outputs.<scope>--tag_name }}` and `fetch-depth: 0`. The build sources
`GIT_SHA` through `${{ steps.resolve-meta.outputs.git_sha }}`.

---

#### AC6: Multiple releases in same run deploy independently

**Scenario**: Multi-component release deploys all triggered components  
**Category**: Happy Path (batch releases)  
**Result**: **NOT TESTED**  
**Reason**: Requires Release Please to create multiple releases in one run and GitHub Actions
execution.  
**Evidence**: Workflow structure supports independent job execution; cannot validate runtime.

---

#### AC7: smp release behavior unchanged

**Scenario**: smp release still triggers container build  
**Category**: Regression Prevention  
**Result**: **NOT TESTED**  
**Reason**: Requires Release Please release creation for `server/smp` and GitHub Actions
execution.  
**Evidence**: `build-and-push-smp` job unchanged per verify report; cannot execute.

---

#### AC8: Cloudflare credentials are least-privilege

**Scenario**: Secrets configured with minimal scope  
**Category**: Security  
**Result**: **NOT TESTED**  
**Reason**: Secrets are repository-level configuration external to this codebase; cannot inspect or
validate scope.  
**Evidence**: Workflow references `secrets.CLOUDFLARE_API_TOKEN` and
`secrets.CLOUDFLARE_ACCOUNT_ID`; assumes operator follows documented setup.

---

#### AC9: GIT_SHA passed to build for version badge

**Scenario**: Build info receives release SHA  
**Category**: Correctness  
**Result**: **NOT TESTED**  
**Reason**: Requires GitHub Actions execution to observe environment variable propagation through
build step.  
**Evidence**: Workflow shows `GIT_SHA: ${{ steps.resolve-meta.outputs.git_sha }}` in `env:`; cannot
verify build behavior.

---

#### AC10: Post-deployment verification asserts version badge

**Scenario**: Production GET asserts version badge presence  
**Category**: Deployment Verification  
**Result**: **NOT TESTED**  
**Reason**: Requires successful Cloudflare deployment and live production URL availability.  
**Evidence**: Workflow shows `curl` step with `grep` assertion; cannot execute without live
deployment.

---

#### AC11: Documentation includes operator gate instructions

**Scenario**: Runbook documents Cloudflare Git integration disable  
**Category**: Documentation  
**Result**: **PASS**  
**Evidence**: `docs/infrastructure/cloudflare-deployment.md` exists and includes the Cloudflare
dashboard operator actions for disabling Git integration production deployments.

---

#### AC12: ADR records deployment boundary decision

**Scenario**: ADR documents release-driven policy  
**Category**: Documentation  
**Result**: **PASS**  
**Evidence**: `docs/architecture/adr/0022-release-driven-frontend-deployment.md` exists and is
indexed per verify report.

---

## Usage

### Untested Scope and Prerequisites

**Untested Due to Repository Constraints**:

- All 10 GitHub Actions workflow scenarios (AC1–AC10)
- Cloudflare Pages deployment outcomes
- Production URL health checks
- Version badge assertions
- Release Please output wiring
- Component isolation runtime behavior
- Least-privilege credential validation
- Multi-component release orchestration

**Prerequisites for Full Validation**:

1. Merge all three stacked PRs to `main`
2. Configure `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` as **Environment secrets** scoped to
   the `PROD` environment; deploy jobs declare `environment: PROD`
3. Disable Cloudflare Git integration for the three Pages projects (operator action)
4. Create a Release Please release for one or more frontends
5. Observe GitHub Actions workflow execution
6. Verify Cloudflare Pages deployments succeed
7. Verify production URLs return expected version badges
8. Test multi-component release scenario

**Rerun Condition**: After merge to `main` and first Release Please release creation, execute Phase
5 first-merge verification per `tasks.md`.

---

## Troubleshooting

### Findings

No findings. QA cannot produce findings without an executable test surface.

---

### Final Verdict

**PARTIALLY TESTED**

**Rationale**:
This change modifies GitHub Actions workflow configuration and deployment orchestration with no
application runtime or test harness available in this repository. AC1–AC10 require live GitHub
Actions execution after the change is merged to `main` and a Release Please release is created, so
those criteria remain **NOT TESTED**.

The verify phase confirmed:

- Structural correctness of workflow YAML
- Presence of all required outputs, jobs, and steps
- Unit test coverage for the TDD helper script (passing 5/5)
- Documentation and ADR completeness
- Static analysis passing

QA adds no additional validation beyond what verify already completed. AC11–AC12 pass static
documentation checks based on the runbook and ADR repository artifacts; the **NOT TESTED**
designation applies only to the unexecuted live checks for AC1–AC10.

The repository explicitly accepts deferred deployment verification as documented in the verify
report and change design. This is not a product acceptance gap—it is the expected behavior for
infrastructure changes that cannot be validated until they are live.

**Implementation Handoff**:

- All code changes structurally complete and correct per verify
- Unit tests passing for testable components
- Documentation in place
- First-merge verification required after merge (Phase 5 of `tasks.md`)
- No blocking defects for merge decision

---

### Archive Gate Decision

**Recommendation**: PROCEED TO ARCHIVE with explicit acknowledgment of deferred verification.

**Justification**:

- Zero application runtime in scope; QA cannot produce acceptance evidence
- Verify phase completed all static validation available pre-merge
- Documentation acceptance criteria (AC11, AC12) pass
- Repository policy explicitly allows first-merge verification for deployment changes
- No alternative validation path exists without live workflow execution

**Warning**: The change will not be fully validated until Phase 5 (first-merge verification)
completes after merge. Archive does not imply product acceptance—it records that the change is
structurally complete, documented, and ready for live validation post-merge.

**Next Steps After Archive**:

1. Merge stacked PRs to `main`
2. Configure Cloudflare secrets
3. Disable Git integration (operator action)
4. Create test release
5. Execute Phase 5 verification
6. Document outcomes in post-merge verification artifact

## References

- Runbook: `docs/infrastructure/cloudflare-deployment.md`
- ADR 0022: `docs/architecture/adr/0022-release-driven-frontend-deployment.md`
- Workflow: `.github/workflows/release-please.yml`
- Verification report: `openspec/changes/dallay-597-deploy-cloudflare-frontends/verify-report.md`
