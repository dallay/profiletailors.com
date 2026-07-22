# Apply Progress: Release 0.1.0 Container Build & Deployment Pipeline

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

- [x] 2.1 Update `.github/workflows/release-please.yml` to call
  `.github/workflows/release-image.yml` using the `smp--tag_name` output after an `smp` release.
- [x] 2.2 Ensure `release-please.yml` passes the correct tag input to `release-image.yml` and does
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

## Verification Results (Phase 1)

| Check                | Command                                                                              | Result                                                        |
|----------------------|--------------------------------------------------------------------------------------|---------------------------------------------------------------|
| Workflow syntax      | `actionlint .github/workflows/release-image.yml`                                     | PASS (no output)                                              |
| Compose resolution   | `docker compose -f infra/apps/smp/production/compose.yaml --env-file <dummy> config` | PASS                                                          |
| No hardcoded secrets | Manual review of `release-image.yml`                                                 | PASS (only `secrets.GITHUB_TOKEN` / `vars.VITE_API_BASE_URL`) |

### Notes

- The image tags use the release version stripped of the `smp@` prefix (e.g., `0.1.0`) because
  Docker references do not allow `@` in tags.
- **Corrected**: Moved `build:` block to `compose.override.yaml` so `compose.yaml` defaults to GHCR
  registry pull, preserving local build via override.
- **Corrected**: Added `SMP_REGISTRATION_ENABLED` env var to backend (missing from DALLAY-509).
- The `smoke-test` job is `continue-on-error: true` per the design decision for 0.1.0.

## Files Changed in Phase 1

- `.github/workflows/release-image.yml` — created
- `infra/apps/smp/production/compose.yaml` — modified (removed build block, added
  SMP_REGISTRATION_ENABLED)
- `infra/apps/smp/production/compose.override.yaml` — created (local build override)

## Commits

1. `b9af52b7` — feat(infra): add release-image workflow and default compose to GHCR
2. `63610f99` — fix(infra): separate build override and add registration env var
3. `784b1d0c` — feat(infra): wire release-please to invoke image build workflow

## Next: Phase 3 (Documentation)
