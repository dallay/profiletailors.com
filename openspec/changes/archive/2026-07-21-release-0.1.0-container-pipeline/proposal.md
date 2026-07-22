# Proposal: DALLAY-510 Release 0.1.0 Container Build & Deployment Pipeline

## Intent

Make the 0.1.0 Private Alpha release deployable by closing the gap between release-please tags and
runnable production images.

## Problem Statement

1. No backend Dockerfile exists; production Compose expects a pre-built image with
   `pull_policy: missing`.
2. No CI workflow builds container images.
3. No CI workflow pushes images to a registry.
4. `release-please.yml` deploy hooks are placeholder comments, so tagged releases do not create
   artifacts.

## Scope

### In Scope

- Add a reproducible backend image build path (Dockerfile or `bootBuildImage`).
- Create `.github/workflows/release-image.yml` triggered on release-please tags.
- Wire `release-please.yml` to invoke the image workflow after release creation.
- Update `infra/apps/smp/production/compose.yaml` to default to registry images.
- Update `docs/release-verification.md` with the automated flow.
- Optionally add a smoke-test step.

### Out of Scope

- LinkedIn real-provider smoke test (DALLAY-511 / #375).
- Kubernetes / Cloud Run / Vercel / Netlify / Fly targets.
- Multi-region or CDN-fronted split deployment.
- Image signing / SBOM / cosign.

## Capabilities

### New Capabilities

- `container-release-pipeline`: build, tag, push, and smoke-test backend and dashboard images on
  release tags.

### Modified Capabilities

None.

## Approach

Use GitHub Actions to log into GHCR, build and push both images on release tags, and run a smoke
test. Tag images with the git tag and `latest`, push to `ghcr.io/dallay/profiletailors-smp` and
`ghcr.io/dallay/profiletailors-dashboard`, and update Compose defaults so a clean server can start
without local builds.

## Affected Areas

| Area                                                     | Impact       | Description               |
|----------------------------------------------------------|--------------|---------------------------|
| `server/smp/Dockerfile` or `server/smp/build.gradle.kts` | New/Modified | Backend image build       |
| `.github/workflows/release-image.yml`                    | New          | Image build/push workflow |
| `.github/workflows/release-please.yml`                   | Modified     | Invoke image workflow     |
| `infra/apps/smp/production/compose.yaml`                 | Modified     | Registry image defaults   |
| `docs/release-verification.md`                           | Modified     | Automated flow docs       |

## Risks

| Risk                                   | Likelihood | Mitigation                                           |
|----------------------------------------|------------|------------------------------------------------------|
| Buildpack build is slow or fails in CI | Med        | Pin digests; add timeout; keep Dockerfile fallback   |
| GHCR push fails due to permissions     | Med        | Use `packages: write` and `GITHUB_TOKEN`             |
| Smoke test is flaky in CI              | Low        | Use healthcheck retries; mark non-blocking initially |

## Rollback Plan

Disable the workflow via GitHub UI or revert the PR. Re-tag manually if a bad image is pushed and
reset Compose defaults.

## Dependencies

- Unblocks DALLAY-510, DALLAY-511, and DALLAY-508.
- Requires GitHub Container Registry access for the `dallay` org.

## Success Criteria

- [ ] A release-please tag builds and pushes both images.
- [ ] Production Compose starts without a local image build.
- [ ] `docs/release-verification.md` reflects the automated pipeline.
- [ ] Smoke test passes against the published images.
