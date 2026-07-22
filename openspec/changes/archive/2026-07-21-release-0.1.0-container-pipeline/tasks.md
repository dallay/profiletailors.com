# Tasks: Release 0.1.0 Container Build & Deployment Pipeline

## Review Workload Forecast

| Field                   | Value                                                                                                                |
|-------------------------|----------------------------------------------------------------------------------------------------------------------|
| Estimated changed lines | ~250–350                                                                                                             |
| 400-line budget risk    | Medium                                                                                                               |
| Chained PRs recommended | Yes                                                                                                                  |
| Suggested split         | PR 1: `release-image.yml` + `compose.yaml` → PR 2: `release-please.yml` wiring + docs → PR 3: verification/exception |
| Delivery strategy       | auto-chain                                                                                                           |
| Chain strategy          | feature-branch-chain                                                                                                 |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: feature-branch-chain
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal                                                         | Likely PR | Notes                                                                                  |
|------|--------------------------------------------------------------|-----------|----------------------------------------------------------------------------------------|
| 1    | Add image build workflow and update Compose defaults         | PR 1      | Targets `feature/release-0.1.0-container-pipeline`; includes lint                      |
| 2    | Wire release-please to invoke image workflow and update docs | PR 2      | Targets feature branch from PR 1                                                       |
| 3    | Verify first release tag end-to-end                          | PR 3      | Targets feature branch from PR 2; may merge as size exception if verification is large |

## Phase 1: Workflow & Compose Foundation

- [x] 1.1 Create `.github/workflows/release-image.yml` with `on.push.tags: 'smp@*'`,
  `packages: write`, and `build-backend` job using `bootBuildImage`.
- [x] 1.2 Add `build-dashboard` job to `.github/workflows/release-image.yml` using
  `infra/apps/smp/production/dashboard.Dockerfile` and `vars.VITE_API_BASE_URL`.
- [x] 1.3 Add non-blocking `smoke-test` job to `.github/workflows/release-image.yml` that generates
  temp env files and runs `smoke-test.sh`.
- [x] 1.4 Update `infra/apps/smp/production/compose.yaml` to default `backend` and `dashboard` to
  GHCR images with `pull_policy: always`, keeping `build:` as optional override.

## Phase 2: Release-Please Integration

- [ ] 2.1 Update `.github/workflows/release-please.yml` to call
  `.github/workflows/release-image.yml` using the `smp--tag_name` output after an `smp` release.
- [ ] 2.2 Ensure `release-please.yml` passes the correct tag input to `release-image.yml` and does
  not duplicate echo placeholders.

## Phase 3: Documentation

- [ ] 3.1 Update `docs/release-verification.md` to describe the tag-triggered image build/push flow,
  registry names, and smoke-test behavior.

## Phase 4: Verification

- [ ] 4.1 Trigger a test release tag (or use existing `smp@0.1.0`) and confirm both images are
  pushed to GHCR with tag and `latest`.
- [ ] 4.2 Run `docker compose -f infra/apps/smp/production/compose.yaml up -d` on a clean checkout
  and verify services start from published images.
- [ ] 4.3 Confirm `smoke-test.sh` passes against the published images, or capture the non-blocking
  warning output.

## Per-Task Details

### 1.1 Create `.github/workflows/release-image.yml` — backend job

- **Files**: `.github/workflows/release-image.yml`
- **Verification**: Workflow YAML passes `actionlint`; `build-backend` job appears in PR checks.
- **Effort**: medium
- **Risks**: Buildpack timeout; Gradle cache miss. Mitigation: 30 min timeout, use cache.

### 1.2 Add dashboard job

- **Files**: `.github/workflows/release-image.yml`
- **Verification**: `build-dashboard` job builds locally with `docker build`.
- **Effort**: small
- **Risks**: `vars.VITE_API_BASE_URL` not configured. Mitigation: document required repo variable.

### 1.3 Add smoke-test job

- **Files**: `.github/workflows/release-image.yml`
- **Verification**: Job runs `smoke-test.sh` and reports warning on failure.
- **Effort**: medium
- **Risks**: Flaky test blocks release. Mitigation: `continue-on-error: true`.

### 1.4 Update `compose.yaml`

- **Files**: `infra/apps/smp/production/compose.yaml`
- **Verification**: `docker compose config` resolves default GHCR images.
- **Effort**: small
- **Risks**: Local dev depends on published images. Mitigation: `build:` override still available.

### 2.1 Update `release-please.yml`

- **Files**: `.github/workflows/release-please.yml`
- **Verification**: Dry-run or inspect workflow syntax; `release-image.yml` receives tag.
- **Effort**: small
- **Risks**: Incorrect output name. Mitigation: match `smp--tag_name` exactly.

### 2.2 Validate tag wiring

- **Files**: `.github/workflows/release-please.yml`
- **Verification**: Confirm input mapping in workflow call.
- **Effort**: small
- **Risks**: None.

### 3.1 Update `docs/release-verification.md`

- **Files**: `docs/release-verification.md`
- **Verification**: Doc accurately reflects workflow triggers and image names.
- **Effort**: small
- **Risks**: Docs drift from implementation. Mitigation: review after apply.

### 4.1–4.3 End-to-end verification

- **Files**: none (runtime verification)
- **Verification**: GHCR images exist; Compose starts; smoke test passes or warns.
- **Effort**: large
- **Risks**: GHCR permissions, LinkedIn provider not available. Mitigation: non-blocking smoke test.

## Definition of Done

- A release-please tag builds and pushes `ghcr.io/dallay/profiletailors-smp` and
  `ghcr.io/dallay/profiletailors-dashboard` with tag and `latest`.
- `infra/apps/smp/production/compose.yaml` starts from published images on a clean server.
- `docs/release-verification.md` documents the automated pipeline.
- Smoke test runs against published images and passes or reports a visible warning.
- No secrets are hardcoded in workflow files.
- Images run as non-root where applicable and base images are pinned by digest where applicable.

## Commit/PR Boundaries

1. **PR 1** (`feature/release-0.1.0-container-pipeline`): `release-image.yml` + `compose.yaml`.
2. **PR 2** (stacked on PR 1): `release-please.yml` wiring + `docs/release-verification.md`.
3. **PR 3** (stacked on PR 2): verification results and any fixes from first tag.

If verification requires large workflow fixes, merge PR 3 as a size exception with maintainer
approval.
