# Proposal: Release-Driven Production Deployment for Cloudflare Frontends

## Intent

The `app`, `admin`, and `landing` frontends are currently deployable from `main` through
Cloudflare's
Git integration, so a merge to `main` can ship a frontend to production even when that component
has not produced an explicit release. DALLAY-597 makes a Release Please release for a specific
component the sole production deployment boundary for the three Cloudflare Pages-hosted frontends,
while keeping `main` as the integration branch. The existing `smp` container pipeline is not
touched.

## Scope

### In Scope

- Expose per-component Release Please outputs (`apps/web/app`, `apps/web/admin`,
  `apps/web/marketing`)
  on `.github/workflows/release-please.yml`.
- Add component-scoped Cloudflare deployment jobs (one per frontend) gated by that component's
  `release_created` output and tagged by its `tag_name`.
- Build each deploy job from the exact release SHA, not implicit `main` HEAD.
- Use least-privilege Cloudflare credentials (`CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`) as
  GitHub Actions secrets.
- Preserve component isolation: an `app` release deploys only `app`; an `admin` release deploys only
  `admin`; a `landing` release deploys only `landing`. Multi-component releases in the same run
  deploy independently.
- Pass the release SHA as `GIT_SHA` to `scripts/compute-build-info.mjs` so the frontend version
  badge (PR #1089) reflects the exact production build.
- Add a post-deployment verification step (HTTP GET on the production endpoint asserting version
  badge content) that fails the workflow when production is unhealthy.
- Document the Cloudflare-side operator action that disables Git-integration production auto-deploy
  for `app-profile-tailors`, `profiletailors`, and `profiletailors-admin` in the deployment runbook.

### Out of Scope

- Changing Release Please versioning strategy or tag format.
- Coupling frontend releases so that releasing one forces releases of the others.
- Replacing Cloudflare as the frontend hosting provider.
- Changing the existing `smp` container release pipeline (`.github/workflows/release-image.yml`)
  beyond a small shared-workflow refactor if strictly required.
- Modifying product behavior, copy, locale, consent, or any product-facing surface.

## Capabilities

### New Capabilities

- `release-driven-frontend-deployment`: per-component Release Please outputs, SHA-pinned Cloudflare
  Pages deploys, post-deploy verification, least-privilege credentials, and component isolation.

### Modified Capabilities

None. No existing capability under `openspec/specs/` is altered by this change.

## Approach

Extend `.github/workflows/release-please.yml` to declare `app--release_created`, `app--tag_name`,
`admin--release_created`, and `admin--tag_name` outputs alongside the existing `landing--*` and
`smp--*` outputs. Replace the current `notify-landing` placeholder with three deploy jobs
(`deploy-app`, `deploy-admin`, `deploy-landing`), each `needs: release-please` and gated by its own
`release_created` output. Each deploy job:

1. Checks out the repository at the exact release SHA resolved from its tag (`actions/checkout` with
   `ref: <tag>` and full history so the tag is dereferenceable).
2. Installs Node.js and dependencies via `.github/actions/setup-frontend`.
3. Builds the component (`pnpm --filter app|admin|marketing build`) with `GIT_SHA` exported so
   `scripts/compute-build-info.mjs` emits the correct short SHA into the version badge.
4. Deploys to Cloudflare Pages through `cloudflare/wrangler-action` (or `wrangler pages deploy`)
   targeting the canonical project name from `state.yaml` (`app-profile-tailors`,
   `profiletailors`, `profiletailors-admin`).
5. Performs a post-deploy verification: HTTP GET on the production URL asserting the badge text
   contains the released version and the expected short SHA.

Component isolation is enforced by the per-job `if:` condition and the per-component `ref`. The
existing `build-and-push-smp` job remains untouched so `smp` releases and image behavior stay
unchanged. Cloudflare Git-integration production auto-deploy must be disabled by an operator for
each of the three Pages projects; the runbook entry records the exact dashboard action and the
account/zone identifiers required.

## Migration & Compatibility

The change is workflow-only and runtime-only at the Cloudflare boundary. No backend contracts,
database schema, application code, or product copy changes ship with it. The two existing Release
Please outputs (`landing--release_created`, `smp--release_created`) keep their semantics; the new
outputs are additive. The `version-badge-in-ui-frontends` change (PR #1089) already established the
`GIT_SHA` contract that this change relies on, so no badge code edits are required. A temporary
rollback path is preserved: re-enabling the Cloudflare Git integration auto-deploy for any single
project restores the prior deployment behavior without code changes.

## Affected Areas

| Area                                                             | Impact          | Description                                                                                                                                           |
|------------------------------------------------------------------|-----------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| `.github/workflows/release-please.yml`                           | Modified        | Add `app--*` and `admin--*` outputs; replace `notify-landing` with three component-scoped deploy jobs.                                                |
| `release-please-config.json`                                     | Verified        | `apps/web/app` and `apps/web/admin` already declared as Release Please components; no config edit expected.                                           |
| `apps/web/admin/wrangler.toml`                                   | New             | Currently absent; add a minimal file mirroring `apps/web/app/wrangler.toml` so wrangler has a local anchor for the Cloudflare project name.           |
| `apps/web/app/wrangler.toml`, `apps/web/marketing/wrangler.toml` | Verified        | Already declare `pages_build_output_dir`; no edit expected.                                                                                           |
| `scripts/compute-build-info.mjs`                                 | Reuse           | No edit; deploy job passes `GIT_SHA` and the existing version badge picks it up.                                                                      |
| GitHub Actions secrets                                           | New             | Provision `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` with Pages Deploy-only scopes.                                                           |
| Cloudflare Pages dashboard                                       | Operator action | Disable Git-integration production auto-deploy for `app-profile-tailors`, `profiletailors`, `profiletailors-admin`; documented in deployment runbook. |
| `docs/operations/` (runbook)                                     | Modified        | Record the operator action, the rollback toggle, and the post-deploy smoke probe.                                                                     |

## Testing Strategy

- Static: `actionlint` (or equivalent) validation of the modified workflow plus a YAML schema check
  against GitHub Actions' published schema.
- Unit: a small Node test that exercises the tag-to-SHA resolution helper (ref pin + full history)
  against fixture tags.
- Workflow dry-run: run the modified `release-please.yml` on a feature branch with
  `pull_request` (or `workflow_dispatch` against a pre-release tag) to confirm each deploy job is
  skipped when `release_created != true` and fires exactly once when it is.
- Post-deploy verification: the deploy job's `curl` probe runs against the canonical production URL
  and asserts the badge text contains both the released `version` and the short SHA from
  `scripts/compute-build-info.mjs`.
- Pre-merge checklist: confirm the operator action (Cloudflare Git integration disable) has been
  applied to all three projects before the new workflow is enabled in `main`.

## Risks

| Risk                                                                                        | Likelihood | Mitigation                                                                                                                                                                                          |
|---------------------------------------------------------------------------------------------|------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Operator accidentally disables preview deployments alongside production auto-deploy         | Medium     | Scope the operator action to the production branch only; preview deploys from PR branches stay intact and are verified manually before enabling the new workflow.                                   |
| SHA extraction from Release Please output mismatches the actual tag                         | Medium     | Resolve the SHA via a single helper step (`actions/checkout` with `ref: <tag>` + `fetch-depth: 0`) and unit-test it against fixture tags before merge.                                              |
| Cloudflare Pages CDN serves a cached artifact that diverges from the freshly deployed build | Low        | Post-deploy smoke probe asserts the version badge; on mismatch the deploy job fails and `wrangler rollback` (or a re-deploy of the previous SHA) is invoked.                                        |
| Operator delay in disabling Cloudflare Git integration for the production branch            | High       | Gate the merge of the new workflow on the operator action being completed in the same release PR; the runbook entry records the dashboard path, the three project names, and the verification curl. |

## Rollback Plan

Disable the three new deploy jobs in `.github/workflows/release-please.yml` (or revert the file to
the previous commit) and re-enable Cloudflare Git-integration production auto-deploy on each Pages
project from the Cloudflare dashboard. The `smp` job continues independently because it does not
share the deploy-job lifecycle. No Releases or tags are removed and no Cloudflare project state is
destroyed; the rollback is a workflow toggle plus a dashboard toggle.

## Dependencies

- Existing version badge infrastructure: `scripts/compute-build-info.mjs` and the frontend wiring
  delivered by `openspec/changes/version-badge-in-ui-frontends` (PR #1089). The deploy jobs pass the
  resolved release SHA as `GIT_SHA` so the badge content matches the exact production build.
- Operator action on the Cloudflare dashboard: disable Git-integration production auto-deploy for
  `app-profile-tailors`, `profiletailors`, and `profiletailors-admin` before the new workflow is
  enabled.
- GitHub Actions secrets: `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` provisioned with
  Pages Deploy-only scopes.
- Existing Release Please outputs (`landing--release_created`, `smp--release_created`) remain the
  contract for downstream consumers; new outputs are additive.

## Success Criteria

- [ ] A merge to `main` does NOT deploy `app`, `admin`, or `landing` to Cloudflare production.
- [ ] Releasing `app@vX.Y.Z` through Release Please triggers only the `app` production deployment.
- [ ] Releasing `admin@vX.Y.Z` through Release Please triggers only the `admin` production
  deployment.
- [ ] Releasing `landing@vX.Y.Z` through Release Please triggers only the `landing` production
  deployment.
- [ ] Each deploy build checks out the exact SHA associated with the Release Please release, not the
  implicit `main` HEAD.
- [ ] Multiple frontend releases created in the same Release Please run deploy independently.
- [ ] The existing `smp` release and image behavior remains unchanged.
- [ ] Cloudflare Git-integration production auto-deploy is disabled for the three frontend projects.
- [ ] Cloudflare authentication uses least-privilege GitHub Actions secrets.
- [ ] Production deployment metadata is correlatable with component version, release tag, and Git
  SHA.
- [ ] The frontend version badge receives metadata from the exact production build.
- [ ] A post-deployment verification step fails the workflow when the production deployment is not
  healthy.
