# Spec Delta: release-driven-frontend-deployment

A Release Please release per frontend SHALL be the sole production deployment boundary for the
three Cloudflare Pages-hosted frontends (`app`, `admin`, `landing`). A merge to `main` SHALL NOT
deploy any of them. Each deploys from its release tag, uses least-privilege Cloudflare
credentials, and verifies production after upload. The `smp` pipeline is untouched. Canonical
project names come from `state.yaml:cloudflare_projects`: `app-profile-tailors`,
`profiletailors`, `profiletailors-admin`.

## ADDED Requirements

### Requirement: Per-component Release Please outputs are exposed

`.github/workflows/release-please.yml` MUST declare outputs `releases_created`,
`app--release_created`, `app--tag_name`, `app--sha`, `admin--release_created`, `admin--tag_name`,
`admin--sha`, `landing--release_created`, `landing--tag_name`, `landing--sha`,
`smp--release_created`, `smp--tag_name`, `smp--sha`. Each `<scope>--release_created` MUST map to
`releases_created.<package>__release_created`; each `<scope>--sha` MUST map to
`releases_created.<package>__sha`.

#### Scenario: Twelve per-component outputs declared

- GIVEN `.github/workflows/release-please.yml`
- WHEN the `release-please` job's `outputs:` block is read
- THEN all 12 keys above are present and map to the corresponding
  `steps.release.outputs['<package>--<field>']` value.

### Requirement: Merges to main do not trigger frontend deploys

No deploy job's `if:` evaluates true on `push` to `main` without a release. Deploy jobs run only
when their component's `<scope>--release_created` equals `'true'`.

#### Scenario: Merge to main skips every deploy

- GIVEN a `push` to `main` and all `<scope>--release_created` outputs are `'false'` or empty
- WHEN the workflow runs
- THEN `deploy-app`, `deploy-admin`, `deploy-landing`, `build-and-push-smp`, `notify-landing` are
  skipped.

### Requirement: Component-scoped deploy isolation

Each deploy job gates on its own `<scope>--release_created`, checks out
`ref: ${{ needs.release-please.outputs.<scope>--tag_name }}` with `fetch-depth: 0`, installs via
`.github/actions/setup-frontend`, runs `pnpm --filter <workspace> build`, deploys via wrangler,
and verifies production. No job touches another component's build or deploy.

#### Scenario: app release deploys only app

- GIVEN `app--release_created == 'true'`, others not releasing
- WHEN the workflow runs
- THEN exactly one `deploy-app` job runs, checks out `app@<tag>`, runs `pnpm --filter app build`,
  targets `app-profile-tailors`
- AND `deploy-admin` and `deploy-landing` are skipped.

#### Scenario: admin release deploys only admin

- GIVEN `admin--release_created == 'true'`, others not releasing
- WHEN the workflow runs
- THEN exactly one `deploy-admin` job targets `profiletailors-admin`
- AND the other two component deploy jobs are skipped.

#### Scenario: landing release deploys only landing

- GIVEN `landing--release_created == 'true'`, others not releasing
- WHEN the workflow runs
- THEN exactly one `deploy-landing` job targets `profiletailors`
- AND the other two component deploy jobs are skipped.

#### Scenario: multi-component release deploys independently

- GIVEN all three `<scope>--release_created` outputs equal `'true'` in one run
- WHEN the workflow runs
- THEN all three deploy jobs execute in parallel, each pinning its own ref, building its own
  component, deploying to its own project without depending on the others.

### Requirement: SHA-pinned checkout ref

Each deploy job MUST run `actions/checkout` with `ref: <tag>` set to
`needs.release-please.outputs.<scope>--tag_name` and `fetch-depth: 0` so the build inputs resolve
to the tagged commit even when `main` has advanced past it.

#### Scenario: Checkout resolves to the tagged commit

- GIVEN `app@v1.2.3` points at `deadbeef1234567890...`
- WHEN `deploy-app` runs
- THEN `actions/checkout` resolves to commit `deadbeef...` via tag `app@v1.2.3`
- AND the build runs against that tree, not current `main` HEAD.

#### Scenario: Build does not use main HEAD

- GIVEN `main` has advanced past the tagged commit since Release Please tagged it
- WHEN `deploy-app` runs
- THEN the checkout resolves to the tagged commit, not current `main` HEAD
- AND the deployed bundle's version metadata matches the tag, not `main`.

### Requirement: Build metadata propagation via GIT_SHA

Each deploy job MUST export `GIT_SHA=<release SHA>` (from
`needs.release-please.outputs.<scope>--sha` or `git rev-parse <tag>^{commit}`) before invoking
`pnpm --filter <workspace> build`, so `scripts/compute-build-info.mjs` reads the released SHA via
its `GIT_SHA` env override and the version badge reflects the released `version` and short SHA.

#### Scenario: Version badge reflects released version and short SHA

- GIVEN `app@v1.2.3` points at `deadbeef...` and `package.json` version is `1.2.3`
- WHEN `deploy-app` runs with `GIT_SHA=deadbeef...` exported before build
- THEN `scripts/compute-build-info.mjs` emits `version: "1.2.3"` and `gitSha: "deadbee"`
- AND the deployed bundle's version badge contains `1.2.3` and `deadbee`.

### Requirement: Wrangler Pages deploy invocation

Each deploy job invokes
`wrangler pages deploy <dist> --project-name=<canonical> --branch=production --commit-dirty=true`
authenticated by `${{ secrets.CLOUDFLARE_API_TOKEN }}` and
`${{ secrets.CLOUDFLARE_ACCOUNT_ID }}`. A non-zero wrangler exit MUST fail the deploy job.

#### Scenario: Successful upload exits zero

- GIVEN a built `dist/`
- WHEN wrangler uploads to the canonical project
- THEN wrangler prints a deployment confirmation containing the deployment ID and commit SHA
- AND the deploy step exits `0`.

#### Scenario: Failed upload fails the job

- GIVEN wrangler exits non-zero
- WHEN the step completes
- THEN the deploy job exits non-zero and no subsequent verification step runs.

### Requirement: Post-deployment production verification

After a successful wrangler upload, the deploy job `curl`s the canonical production URL, parses
the response, and asserts the version-badge marker contains the released `version` and the first 7
chars of the release SHA. CDN propagation MAY be tolerated via a bounded retry/poll; a final
mismatch MUST fail the job.

#### Scenario: Verification passes on a healthy deploy

- GIVEN a wrangler upload for `app@v1.2.3` (SHA `deadbeef...`) succeeds
- WHEN the verification step `curl`s the canonical `app` URL
- THEN the response body contains `1.2.3` and `deadbee`
- AND the step exits `0`.

#### Scenario: Verification fails on a stale or wrong build

- GIVEN a wrangler upload for `app@v1.2.3` succeeds
- WHEN the verification step polls and the cached response carries an older version or different
  SHA after the bounded retry window
- THEN the step exits non-zero and the deploy job fails.

### Requirement: wrangler.toml presence and shape for admin

`apps/web/admin/wrangler.toml` MUST exist with `pages_build_output_dir = "dist"` and either
`name = "profiletailors-admin"` or a deploy job that passes `--project-name
profiletailors-admin` to wrangler.

#### Scenario: Admin wrangler.toml exists with required fields

- GIVEN the change is applied
- WHEN `apps/web/admin/wrangler.toml` is read
- THEN the file exists, declares `pages_build_output_dir = "dist"`, and is reachable as
  `profiletailors-admin` via local `name` or deploy-job `--project-name`.

### Requirement: wrangler.toml alignment for app and marketing

`apps/web/app/wrangler.toml` and `apps/web/marketing/wrangler.toml` MUST target the canonical
projects (`app-profile-tailors`, `profiletailors`) at deploy time, either by editing `name` or
by passing `--project-name=<canonical>` to wrangler.

#### Scenario: App deploy targets the canonical Pages project

- GIVEN `apps/web/app/wrangler.toml` declares `name = "profiletailors-app"`
- WHEN `deploy-app` invokes wrangler
- THEN the upload is sent to `app-profile-tailors`, not `profiletailors-app`.

#### Scenario: Marketing deploy targets the canonical Pages project

- GIVEN `apps/web/marketing/wrangler.toml` declares `name = "profiletailors-marketing"`
- WHEN `deploy-landing` invokes wrangler
- THEN the upload is sent to `profiletailors`, not `profiletailors-marketing`.

### Requirement: smp release pipeline is untouched

`build-and-push-smp` in `.github/workflows/release-please.yml` and its call to
`.github/workflows/release-image.yml` MUST remain semantically equivalent. An `smp@vX.Y.Z`
release SHALL continue to build images, publish, and run smoke without interference from the new
component deploys.

#### Scenario: smp release still triggers image build and smoke

- GIVEN `smp--release_created == 'true'` with tag `smp@v0.5.0`
- WHEN the workflow runs
- THEN `build-and-push-smp` invokes `.github/workflows/release-image.yml` with
  `release-tag: smp@v0.5.0`
- AND `build-backend`, `build-dashboard`, `publish-dockerhub`, `smoke-test` run unchanged
- AND no `deploy-app`, `deploy-admin`, or `deploy-landing` fires from this release.

### Requirement: Least-privilege Cloudflare secrets

Deploy jobs reference Cloudflare credentials only as `${{ secrets.CLOUDFLARE_API_TOKEN }}` and
`${{ secrets.CLOUDFLARE_ACCOUNT_ID }}`. `docs/infrastructure/cloudflare-deployment.md` MUST record
the API token is scoped to "Cloudflare Pages: Edit" on the three project names only, with no
account-wide, DNS, Workers, or KV scope. No credential value SHALL be printed in logs.

#### Scenario: Secrets referenced by name only

- GIVEN the deploy job sources
- WHEN `grep -E "CLOUDFLARE_(API_TOKEN|ACCOUNT_ID)"` runs against workflow files
- THEN every match is a `${{ secrets.* }}` reference or a step input
- AND no literal token value or `echo "$CLOUDFLARE_*"` debug print appears.

### Requirement: Operator action documented for Git-integration disable

`docs/infrastructure/cloudflare-deployment.md` MUST record the exact dashboard steps to disable
Git-integration production auto-deploy for `app-profile-tailors`, `profiletailors`, and
`profiletailors-admin`, plus the rollback toggle. The doc MUST name the three project identifiers,
the dashboard path, and a verification curl matching the post-deploy probe.

#### Scenario: Runbook covers disable and rollback

- GIVEN the applied change
- WHEN `docs/infrastructure/cloudflare-deployment.md` is read
- THEN it lists the three project names, the dashboard path to disable Git-integration production
  auto-deploy, the rollback toggle, and a verification curl asserting version and short SHA.

## MODIFIED Requirements

None.

## REMOVED Requirements

None.
