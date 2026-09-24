# Repair Cloudflare frontend deployments

## Route

Delegated direct: fix a shared workflow and validate the configuration of three Cloudflare Pages projects without changing product code.

## Objective

Ensure `app`, `admin`, and `landing` deploy their releases to production and the workflow verifies each application's actual badge.

## Tasks

- [x] RPI-001 Confirm the current workflow, Pages projects, and their production status.
- [x] RPI-002 Fix the version checks and health-check paths for all three apps.
- [x] RPI-003 Fix the production configuration of `app-profile-tailors`, `profiletailors-admin`, and `profiletailors` without changing secrets or domains.
- [ ] RPI-004 Deploy the three existing releases or safely rerun the equivalent workflow.

  The workflow push succeeded, but the jobs were `skipped` because it did not create new releases; a controlled dispatch is needed for the existing tags (`app@v0.3.12`, `admin@v0.0.11`, `landing@v0.2.16`). The workflow now uses a pinned Wrangler version independent of each tag's dependencies and verifies the SPAs' JavaScript assets.
- [ ] RPI-005 Verify each production URL and review the final diff.

## Acceptance Criteria

- A release of each frontend reaches a production deployment in its Pages project.
- `app`, `admin`, and `landing` show their corresponding version and SHA.
- The workflow does not construct impossible badges such as `vv0.2.16`.
- `profiletailors.com`, `app.profiletailors.com`, and the admin domain serve the correct deployment.
- No deployments are deleted, no secrets are modified, and no product code is changed.

## Initial Evidence

- `landing@v0.2.16` was uploaded successfully as ad hoc deployment `847e8698`, but the workflow failed at verification only.
- `profiletailors.com` still serves `v0.2.13 (938b3dc)`.
- The Pages projects report `production_deployments_enabled: false`.
- Subsequent pushes did not create releases, and their deploy jobs were `skipped`.

## Status

Working — the user authorized pushing the corrected workflow to `main`; the remote run will be reviewed after the push. `production_deployments_enabled` remains `false` to preserve release-driven behavior and prevent auto-deployments from merges to `main`.

## Current Evidence

- `profiletailors`, `app-profile-tailors`, and `profiletailors-admin` have `production_deployments_enabled: false`.
- No secrets, domains, or existing deployments were changed.
- `.github/workflows/release-please.yml` verifies `${EXPECTED_VERSION} (${EXPECTED_SHORT_SHA})` in all three apps.
- Marketing checks `/terms/`, which contains the actual badge.
- `wrangler pages deploy --branch=main` matches `production_branch: main` and allows a production deployment.

## Next Step

Push the workflow change and check whether the push triggers a run with all three deploy jobs. Do not force a release-please push that could change versions or changelogs.
