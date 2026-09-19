# Tasks: DALLAY-597 Release-Driven Cloudflare Frontend Deployment

## Overview

A Release Please release per component becomes the sole production deployment boundary for the three Cloudflare Pages-hosted frontends. Twenty-five tasks across five phases split into three stacked PRs add the workflow contract, the deploy jobs, and the docs/ADR/operator-gates surface. Phase 5 only runs after PR 3 lands on `main`.

## Changes

### Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | 380–520 |
| 400-line budget risk | Medium |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 → PR 2 → PR 3 |
| Delivery strategy | ask-on-risk |
| Chain strategy | github-stacked-prs |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: github-stacked-prs
400-line budget risk: Medium

### Work Units

| Unit | Goal | Stack metadata |
|---|---|---|
| 1 | Workflow contract + wrangler configs | trunk=main; parent_branch=main; base=main; branch=feat/dallay-597-workflow-contract; position=1; issue=DALLAY-597 |
| 2 | Deploy jobs + TDD helper | trunk=main; parent_branch=feat/dallay-597-workflow-contract; base=feat/dallay-597-workflow-contract; branch=feat/dallay-597-deploy-jobs; position=2; issue=DALLAY-597 |
| 3 | Docs, ADR, validation, first-merge verification | trunk=main; parent_branch=feat/dallay-597-deploy-jobs; base=feat/dallay-597-deploy-jobs; branch=feat/dallay-597-docs-verify; position=3; issue=DALLAY-597 |

### Phase 1: Workflow Contract and Wrangler Config (PR 1)

- [ ] 1.1 Add `app--release_created`, `app--tag_name`, `app--sha`, `admin--release_created`, `admin--tag_name`, `admin--sha`, `landing--sha`, `smp--sha` to `release-please.outputs:` in `.github/workflows/release-please.yml`, each mapping to `steps.release.outputs['<path>--<field>']`.
- [ ] 1.2 Edit `apps/web/app/wrangler.toml`: change `name` to `"app-profile-tailors"`.
- [ ] 1.3 Edit `apps/web/marketing/wrangler.toml`: change `name` to `"profiletailors"`.
- [ ] 1.4 Create `apps/web/admin/wrangler.toml` with `name = "profiletailors-admin"`, `pages_build_output_dir = "dist"`, `compatibility_date = "2026-07-24"`, empty `[vars]`.
- [ ] 1.5 Confirm `git diff .github/workflows/release-please.yml` shows `build-and-push-smp` and its `uses: ./.github/workflows/release-image.yml` byte-equivalent to the prior commit.

### Phase 2: Deploy Jobs and TDD Helper Script (PR 2)

- [ ] 2.1 RED: write failing `tests/workflow/extract-release-info.bats` covering `strip_scope_from_tag`, `resolve_release_sha` (with `<scope>--sha` populated and empty `git rev-parse <tag>^{commit}` fallback), and `short_sha` (first 7 chars).
- [ ] 2.2 GREEN: create `.github/scripts/extract-release-info.sh` exporting those three functions under `set -euo pipefail`; the bats file passes.
- [ ] 2.3 Replace `notify-landing` in `.github/workflows/release-please.yml` with `deploy-app`, `deploy-admin`, `deploy-landing`, each `needs: release-please`, `if: needs.release-please.outputs.<scope>--release_created == 'true'`, `runs-on: ubuntu-latest`, `timeout-minutes: 20`, `permissions: { contents: read }`.
- [ ] 2.4 Add the six-step body to each deploy job: SHA-pinned `actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1` (`ref: <tag>`, `fetch-depth: 0`, `persist-credentials: false`); `id: release-sha` bash sourcing `.github/scripts/extract-release-info.sh` and exporting `GIT_SHA` to `$GITHUB_ENV`; `.github/actions/setup-frontend`; `pnpm --filter <workspace> build` with `env.GIT_SHA`; SHA-pinned `cloudflare/wrangler-action@9acf94ace14e7dc412b076f2c5c20b8ce93c79cd # v3.15.0` invoking `pages deploy <dist> --project-name=<canonical> --branch=production --commit-dirty=true`; verification step.
- [ ] 2.5 Add bounded-retry verification (5 × 15 s `curl` + `grep -Eq "v${version} · ${sha}"`) to each deploy job; `marketing` probes `/legal/terms`.
- [ ] 2.6 Source each deploy job's `apiToken`/`accountId` only from `${{ secrets.CLOUDFLARE_API_TOKEN }}` and `${{ secrets.CLOUDFLARE_ACCOUNT_ID }}`; `grep -RE "CLOUDFLARE_(API_TOKEN|ACCOUNT_ID)" .github/` shows `${{ secrets.* }}` references only.

### Phase 3: Documentation and ADR (PR 3)

- [ ] 3.1 Create `docs/infrastructure/cloudflare-deployment.md` documenting the dashboard steps to disable Git-integration production auto-deploy on `app-profile-tailors`, `profiletailors`, `profiletailors-admin`, the Pages-Edit API-token scope, the three `PT_PRODUCTION_*_URL` variables, and the verification curl.
- [ ] 3.2 Add `#### CLOUDFLARE_API_TOKEN` to `docs/production-secrets.md` with Pages-Edit scope on the three project names only and 12-month rotation cadence.
- [ ] 3.3 Add `#### CLOUDFLARE_ACCOUNT_ID` to `docs/production-secrets.md` with the Cloudflare account ID variable and its retrieval path.
- [ ] 3.4 Create `docs/architecture/adr/0022-release-driven-frontend-deployment.md` from `docs/architecture/adr/template.md` filling the design's ADR outline with Status `Accepted` and Date `2026-09-18`.
- [ ] 3.5 Append a row for ADR 0022 to `docs/architecture/adr/README.md` index with Status `Accepted` and Date `2026-09-18`.

### Phase 4: Validation and Operator Preconditions (PR 3)

- [ ] 4.1 Run `actionlint .github/workflows/release-please.yml`; record zero findings.
- [ ] 4.2 Validate `.github/workflows/release-please.yml` against GitHub Actions' published schema via `actionlint -shellcheck`; record pass/fail.
- [ ] 4.3 Run `bats tests/workflow/extract-release-info.bats`; record pass/fail of each helper function.
- [ ] 4.4 Run `pnpm --filter app type-check`, `pnpm --filter admin type-check`, `pnpm --filter marketing check`, `just frontend-lint`, `just frontend-check`, `just backend-lint`; record passed/failed status for each gate.
- [ ] 4.5 Add a PR-description pre-merge checklist confirming the operator has (a) created the least-privilege Cloudflare API token, (b) disabled Git-integration production auto-deploy on all three Pages projects, and (c) confirmed `PT_PRODUCTION_APP_URL`, `PT_PRODUCTION_MARKETING_URL`, `PT_PRODUCTION_ADMIN_URL`; reviewer blocks merge until each box is checked.

### Phase 5: First-Merge Verification (post-merge)

- [ ] 5.1 Trigger a deliberate `app@vX.Y.Z` release on `main`; confirm `deploy-app` runs, uploads to `app-profile-tailors`, exits zero.
- [ ] 5.2 Confirm `curl -fsSL "$PT_PRODUCTION_APP_URL"` body contains the released `version` and the first 7 chars of the release SHA within 75 s; record the run URL.
- [ ] 5.3 Repeat 5.1–5.2 for `admin@vX.Y.Z` against `profiletailors-admin` and `landing@vX.Y.Z` against `profiletailors`; record evidence for each.
- [ ] 5.4 Confirm a plain merge to `main` with no release skips every deploy job via the workflow run summary; record the run URL.

## Usage

Execute units in stack order. PR 1 wires the contract and wrangler names; PR 2 depends on PR 1's outputs; PR 3 ships docs, ADR, validation, and the post-merge first-release verification. The apply agent holds task 4.5 until every operator precondition is checked and runs Phase 5 only after PR 3 merges to `main`.

## Troubleshooting

Empty `<scope>--sha` from Release Please: the helper falls back to `git rev-parse <tag>^{commit}`; if that also fails the deploy job exits non-zero with a logged empty path. CDN serves an older build: the 5 × 15 s retry absorbs one cache miss; persistent mismatch fails the job and exposes `wrangler rollback`. Operator disables preview deploys alongside production: the runbook and PR checklist call out the production-only toggle; verify preview deploys stay enabled on a test PR before merging. `actionlint` flags a new finding: fix the workflow or step rather than weaken the rule or suppress the warning. `bats` not installed locally: install `bats-core` via Homebrew or document the blocker; do not skip the helper's TDD gate.

## References

- [Proposal](proposal.md)
- [Design](design.md)
- [Spec delta](specs/release-driven-frontend-deployment/spec.md)
- [Change state](state.yaml)
- [Reference tasks template](../../changes/dallay-565/tasks.md)
