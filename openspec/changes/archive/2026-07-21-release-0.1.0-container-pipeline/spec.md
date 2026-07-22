# Release 0.1.0 Container Build & Deployment Pipeline Specification

## Purpose

Define the automated container pipeline that turns a release-please tag into published, runnable
production images for the SMP backend and dashboard.

## Requirements

### R1 — Trigger on release-please tags

The pipeline MUST trigger when release-please creates a release tag for the `server/smp` path.

### R2 — Build backend image

The pipeline MUST build a reproducible backend image from the tagged commit using the existing
`bootBuildImage` Gradle task or a new `server/smp/Dockerfile`.

### R3 — Build dashboard image

The pipeline MUST build the dashboard image using the existing
`infra/apps/smp/production/dashboard.Dockerfile`.

### R4 — Push both images to GHCR

The pipeline MUST push the backend image to `ghcr.io/dallay/profiletailors-smp` and the dashboard
image to `ghcr.io/dallay/profiletailors-dashboard`, tagged with the release tag and `latest`.

### R5 — Production Compose runnable from published images

`infra/apps/smp/production/compose.yaml` MUST default to the published registry images so a clean
server can start without a local image build.

### R6 — Smoke test

The pipeline SHOULD run `infra/apps/smp/production/smoke-test.sh` against the published images. It
MAY be non-blocking initially and reported as a warning.

### R7 — Reproducible from a clean checkout

The pipeline MUST be reproducible from a clean checkout without relying on local files, uncommitted
changes, or external credentials beyond repository secrets.

### R8 — No secrets in workflow files

No secrets MUST be hardcoded in workflow files. GitHub secrets MUST be referenced only through
`secrets.*` or `vars.*` and the workflow MUST use the principle of least privilege.

### R9 — Non-root images

Where applicable, built images MUST run as a non-root user.

### R10 — Digest-pinned base images

Where applicable, base images in Dockerfiles and buildpack references SHOULD be pinned by digest.

## Scenarios

### S1 — Happy path

- GIVEN release-please creates a release tag for `server/smp`
- WHEN the release image workflow runs
- THEN the backend image is built and pushed to GHCR with the tag and `latest`
- AND the dashboard image is built and pushed to GHCR with the tag and `latest`
- AND production Compose starts from the published images
- AND the smoke test passes

### S2 — Backend build failure

- GIVEN the backend build step fails
- WHEN the workflow runs
- THEN the workflow fails
- AND no images are pushed to GHCR

### S3 — Dashboard build failure

- GIVEN the dashboard build step fails
- WHEN the workflow runs
- THEN the workflow fails
- AND no images are pushed to GHCR

### S4 — Smoke test fails after push

- GIVEN both images are successfully pushed
- AND the smoke test fails
- WHEN the workflow reaches the smoke test step
- THEN the workflow fails or reports a non-blocking warning
- AND the failure is visible in the workflow run

### S5 — Rollback after bad push

- GIVEN a bad image has been pushed to GHCR
- WHEN the operator disables the workflow or re-tags the image manually
- THEN the bad image is no longer referenced as `latest`
- AND a previous good image can be restored

## Acceptance Criteria

- A release-please tag builds and pushes both images.
- Production Compose starts without a local image build.
- `docs/release-verification.md` reflects the automated pipeline.
- Smoke test passes against the published images.
- No secrets are hardcoded in workflow files.
- Images use non-root users where applicable.
- Base images are pinned by digest where applicable.

## Out of Scope

- LinkedIn real-provider smoke test (DALLAY-511 / #375).
- Kubernetes / Cloud Run / Vercel / Netlify / Fly targets.
- Multi-region or CDN-fronted split deployment.
- Image signing / SBOM / cosign.

## Open Questions / Assumptions

- GitHub Container Registry access is enabled for the `dallay` org.
- `GITHUB_TOKEN` has the `packages: write` permission.
- The release tag format produced by release-please follows the configured path-based manifest.
- The dashboard image build can receive the `VITE_API_BASE_URL` build argument.
