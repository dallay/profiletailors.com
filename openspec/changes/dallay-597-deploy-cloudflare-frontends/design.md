# Design: Release-Driven Production Deployment for Cloudflare Frontends

## Goal and Context

DALLAY-597 makes a Release Please release for `apps/web/app`, `apps/web/admin`, or
`apps/web/marketing` the sole production deployment boundary for each Cloudflare Pages-hosted
frontend. A merge to `main` MUST NOT deploy any of them. Each deploy is SHA-pinned, signed by a
least-privilege Cloudflare API token, and verified post-upload against the version badge from
`version-badge-in-ui-frontends` (PR #1089). The `smp` pipeline is untouched. Canonical Pages
project names: `app-profile-tailors`, `profiletailors`, `profiletailors-admin` (`state.yaml`).
Proposal: `openspec/changes/dallay-597-deploy-cloudflare-frontends/proposal.md`. Spec:
`…/specs/release-driven-frontend-deployment/spec.md`.

## Approach

Extend `.github/workflows/release-please.yml` with the four per-component Release Please outputs
the spec mandates and replace `notify-landing` with three component-scoped deploy jobs. Each
checks out the release tag, builds with `GIT_SHA` exported, deploys via SHA-pinned
`cloudflare/wrangler-action@v3.15.0`, and `curl`s the production URL to assert the version
badge. `build-and-push-smp` is unchanged.

## Architecture Decisions

| Decision | Choice | Tradeoff | Rationale |
|---|---|---|---|
| Workflow shape | Extend `release-please.yml`; three deploy jobs alongside `build-and-push-smp` | Split files scatter context | Matches existing pattern; keeps `needs: release-please` output wiring local. |
| Release Please SHA source | `<scope>--sha` outputs; fall back to `git rev-parse <tag>^{commit}` when empty | Two paths | `release-please-action` documents `<path>--sha`; fallback is defensive. |
| `wrangler.toml` `name` | Edit `app` and `marketing` to canonical names; create `admin/wrangler.toml`. Deploy job also passes `--project-name=<canonical>` | Local `wrangler pages dev` matches deploy target | Local dev/prod parity removes ambiguity. |
| Wrangler invocation | `cloudflare/wrangler-action@v3.15.0` SHA-pinned, not `pnpm dlx wrangler` | Action parses wrangler exit codes natively | Aligns with Cloudflare's Pages deployment docs. |
| Action pinning | Every external `uses:` SHA-pinned with `# vX.Y.Z` comment | More verbose | Required by `AGENTS.md` static-analysis posture. |
| Operator gate for Git-integration disable | Pre-merge PR checkbox (option b) | `workflow_run` needs side-channel pushes; webhook check only confirms configuration | Cheapest, lowest-risk. |
| CDN propagation retry | 5 × 15 s = 75 s | Cloudflare's edge propagation is typically <30 s | Bounded retry surfaces stale-cache failures. |
| `CLOUDFLARE_API_TOKEN` scope | "Cloudflare Pages: Edit" on three project names only; account-wide, DNS, Workers, KV forbidden | Token cannot deploy anywhere else | Least-privilege; spec requires it. |

## Workflow Changes

`release-please` job `outputs:` gains `app--release_created`, `app--tag_name`, `app--sha`,
`admin--release_created`, `admin--tag_name`, `admin--sha`, `landing--sha`, `smp--sha` alongside
the existing five. Each maps to `steps.release.outputs['<path>--<field>']` per the action's
documented `<path>--sha` contract.

Three deploy jobs (replace `notify-landing`). Each: `needs: release-please`;
`if: needs.release-please.outputs.<scope>--release_created == 'true'`; `runs-on: ubuntu-latest`;
`timeout-minutes: 20`; `permissions: { contents: read }` (App token's wider scope is used only
by the release-please step). Steps:

1. `actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1` with
   `ref: ${{ needs.release-please.outputs.<scope>--tag_name }}`, `fetch-depth: 0`,
   `persist-credentials: false`.
2. `id: resolve-meta` — `node scripts/extract-release-info.mjs --tag "${{ needs.release-please.outputs.<scope>--tag_name }}" --scope "<scope>" --provided "${{ needs.release-please.outputs.<scope>--sha }}"`. The script exports `version`, `git_sha`, and `short_sha` as GitHub Actions outputs.
3. `.github/actions/setup-frontend`.
4. `pnpm --filter <workspace> build` with `env: { GIT_SHA: ${{ steps.resolve-meta.outputs.git_sha }} }`.
5. `cloudflare/wrangler-action@9acf94ace14e7dc412b076f2c5c20b8ce93c79cd # v3.15.0` with
   `apiToken: ${{ secrets.CLOUDFLARE_API_TOKEN }}`,
   `accountId: ${{ secrets.CLOUDFLARE_ACCOUNT_ID }}`,
   `command: pages deploy <dist> --project-name=<canonical> --branch=production --commit-dirty=true`,
   `workingDirectory: ./apps/web/<scope>`.
6. Verification step (below).

Action pins: `actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1`,
`pnpm/action-setup@0977fd99725f1db4007ccb2928dbb4e90d06cc86 # v6.0.10`,
`actions/setup-node@820762786026740c76f36085b0efc47a31fe5020 # v7.0.0`,
`cloudflare/wrangler-action@9acf94ace14e7dc412b076f2c5c20b8ce93c79cd # v3.15.0`. New secrets:
`CLOUDFLARE_API_TOKEN` (Pages: Edit on three project names only),
`CLOUDFLARE_ACCOUNT_ID`. Existing `APP_ID`/`APP_PRIVATE_KEY` and
`DOCKERHUB_USERNAME`/`DOCKERHUB_TOKEN` unchanged.

## Cloudflare Configuration

| File | Action | Content |
|---|---|---|
| `apps/web/app/wrangler.toml` | Modify `name` | `name = "app-profile-tailors"` |
| `apps/web/marketing/wrangler.toml` | Modify `name` | `name = "profiletailors"` |
| `apps/web/admin/wrangler.toml` | Create | `name = "profiletailors-admin"`, `pages_build_output_dir = "dist"`, `compatibility_date = "2026-07-24"`, empty `[vars]` |

Deploy job reads URLs from `vars.PT_PRODUCTION_APP_URL`, `vars.PT_PRODUCTION_MARKETING_URL`,
`vars.PT_PRODUCTION_ADMIN_URL`. Defaults: `https://profiletailors-com-bx5.pages.dev`,
`https://profiletailors.pages.dev`, `https://profiletailors-admin.pages.dev`. Operator MUST
confirm or override in `cloudflare-deployment.md`; design invents no URLs.

## Build Metadata Propagation

`scripts/compute-build-info.mjs` reads `process.env.GIT_SHA` first (slices to 7), then falls
back to `git rev-parse --short HEAD`. `apps/web/app/vite.config.ts`,
`apps/web/admin/vite.config.ts`, `apps/web/marketing/astro.config.mjs` evaluate
`computeBuildInfo(...)` at config time and expose `__APP_VERSION__`, `__GIT_SHA__`,
`__BUILD_TIME__` via `define`. Deploy job exports `GIT_SHA` to `$GITHUB_ENV` BEFORE the build;
badge displays released version and first seven characters of release SHA. No edits to
`compute-build-info.mjs`, the Vite/Astro configs, or the badge components are required.

## Verification Strategy

After `wrangler-action` exits zero, the deploy job polls the production URL with a bounded
retry:

```bash
verify() {
  local version="$1" sha="$2" url="$3"
  for attempt in 1 2 3 4 5; do
    body="$(curl --silent --show-error --fail --max-time 15 "$url" || true)"
    if printf '%s' "$body" | grep -Eq "v${version} · ${sha}"; then
      echo "verified on attempt $attempt"
      return 0
    fi
    sleep 15
  done
  return 1
}
```

`<scope>--tag_name` strips its `<scope>@` prefix via bash parameter expansion; SHA slices to 7
chars. Marketing probes a non-landing route (e.g., `/legal/terms`). 5 × 15 s = 75 s covers
first-time cache warms without masking real failures. Non-zero exit fails the deploy job.

## Operator Actions

Out-of-repo, in order, before the workflow can deploy from `main`:

1. Create Cloudflare API token with `Cloudflare Pages: Edit` on three project names only; store
   as `CLOUDFLARE_API_TOKEN`; store account ID as `CLOUDFLARE_ACCOUNT_ID`. Confirm
   `git grep -E "CLOUDFLARE_(API_TOKEN|ACCOUNT_ID)"` shows only `${{ secrets.* }}`.
2. Disable Git-integration production auto-deploy on each Pages project
   (`Workers & Pages > <project> > Settings > Builds > Disconnect Git repository`). Preview
   deploys from PR branches remain enabled.
3. Confirm or override `PT_PRODUCTION_APP_URL`, `PT_PRODUCTION_MARKETING_URL`,
   `PT_PRODUCTION_ADMIN_URL`. Update runbook.
4. PR description includes a checkbox covering items 1–3; reviewer blocks merge until confirmed.

These require a Cloudflare account owning the three Pages projects, an API token scoped to
Pages: Edit on those names only, and three GitHub Actions variables for canonical URLs.

## Out-of-Repo Dependencies

- Cloudflare account owning `app-profile-tailors`, `profiletailors`, `profiletailors-admin`.
- Cloudflare API token scoped to `Cloudflare Pages: Edit` on those names only.
- Three GitHub Actions variables for canonical production URLs (defaults above).
- Operator-confirmed disablement of Cloudflare Git-integration production auto-deploy on each
  Pages project.
- Operator-confirmed values for the three production URL variables (custom domain vs. `*.pages.dev`).

## Documentation Changes

| File | Action |
|---|---|
| `docs/infrastructure/cloudflare-deployment.md` | Create — runbook |
| `docs/production-secrets.md` | Modify — add Cloudflare rows |
| `docs/architecture/adr/0022-release-driven-frontend-deployment.md` | Create — durable decision |
| `docs/architecture/adr/README.md` | Modify — append ADR row |
| `apps/web/{app,admin,marketing}/PRODUCT.md` | Verify — no product change |

## ADR Outline

`docs/architecture/adr/0022-release-driven-frontend-deployment.md` fills
`docs/architecture/adr/template.md` with:

- **Status** Accepted. **Date** 2026-09-18. **Decision owners** Principal Architect.
- **Scope**: repository CI; `apps/web/{app,admin,marketing}`; deployment bounded context.
- **Context**: Cloudflare Git-integration auto-deploy ships a frontend to production on every
  merge to `main`, decoupling production release from explicit release intent.
- **Decision drivers**: explicit release boundary, auditability, decoupled component release
  cadence, recoverability.
- **Decision**: Production deploys of `apps/web/app`, `apps/web/admin`, `apps/web/marketing`
  MUST be triggered only by a Release Please release of the corresponding component; a merge
  to `main` MUST NOT trigger a production deploy of any of them.
- **Alternatives**: keep Git-integration auto-deploy (rejected: does not decouple); separate
  `workflow_run` (rejected: splits release context); manual deploy via `wrangler-action`
  invoked by a release engineer (rejected: removes auditability).
- **Consequences**: positive — explicit release boundary, SHA-pinned bundles, recoverability;
  negative — workflow + dashboard coupling, operator precondition overhead.
- **Compliance and enforcement**: `release-driven-frontend-deployment` spec scenarios,
  `actionlint` validation, PR body checkbox.
- **Verification**: post-deploy `curl`; first-merge round-trip.
- **Migration or remediation**: disable Git-integration production auto-deploy on each Pages
  project; provision least-privilege Cloudflare credentials.
- **Revisit conditions**: Release Please replaced, or frontends move off Cloudflare Pages.

## Risks and Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| Operator enables preview deploys on the same toggle that disables production | Medium | Runbook names the production-only toggle; PR checkbox confirms preview deploys remain enabled. |
| `<scope>--sha` empty for a re-run where Release Please did not create a release | Low | `git rev-parse <tag>^{commit}` fallback; empty path is logged. |
| CDN cache serves an older build after wrangler upload completes | Low | Bounded 5 × 15 s retry; persistent mismatch fails the job. |
| API token over-scoped | Medium | Runbook and `docs/production-secrets.md` document exact scope; spec asserts `grep` for `secrets.*` references only. |
| `main` advances past the tagged commit between tagging and deploy | Low (spec scenario) | `ref: <tag>` with `fetch-depth: 0` dereferences to the released commit. |

## Open Questions

1. **Custom domain mapping**: when the operator wires a custom domain (e.g.,
   `https://app.profiletailors.com`), does verification switch to the custom domain or stay on
   `*.pages.dev`? Design treats the variable as the single source of truth; the runbook
   records the operator's chosen convention. **Resolved by operator during pre-merge
   checklist.**
2. **First-merge verification reach**: this design cannot exercise the live round-trip from
   the worktree. `verification_constraints.first_merge_required_for` in `state.yaml` is
   satisfied only after the workflow is live on `main`. Tasks phase MUST include a post-merge
   verification task: trigger a deliberate `app@vX.Y.Z` release and confirm the verification
   `curl` passes against `PT_PRODUCTION_APP_URL`.
3. **API token rotation cadence**: default — rotate every 12 months or immediately on suspected
   exposure; operator confirms.
4. **`<scope>--tag_name` parsing**: per-component tag is `<scope>@vX.Y.Z`; bash
   `${tag#<scope>@}` is straightforward. Tasks phase MUST unit-test the helper if a shell
   extraction script is introduced.

## Verification Reach

The live Cloudflare Pages deploy round-trip, the operator's Git-integration disable, and the
production endpoint smoke test are listed in
`state.yaml:verification_constraints.first_merge_required_for`. This design resolves every code
contract; the first release that exercises the workflow on `main` is the only context in which
the full stack is verified end-to-end.

## References

`openspec/changes/dallay-597-deploy-cloudflare-frontends/{proposal.md,specs/release-driven-frontend-deployment/spec.md}`,
`openspec/changes/version-badge-in-ui-frontends/proposal.md`,
`.github/workflows/release-please.yml` (modified), `.github/workflows/release-image.yml`
(unchanged), `scripts/compute-build-info.mjs` (unchanged),
`apps/web/{app,admin,marketing}/{vite,astro}.config.*` (unchanged),
`docs/production-secrets.md` (extended), `docs/architecture/adr/template.md` and `README.md`.