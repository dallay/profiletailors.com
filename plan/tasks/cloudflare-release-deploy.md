# Cloudflare release deployment repair

## Goal

Make the release-driven deployment jobs for marketing, app, and admin run successfully from their component release tags, then verify each build and deployment path locally without changing application behavior.

## Evidence

- Release creation succeeds, but the landing and app deploy jobs fail inside `cloudflare/wrangler-action` while the action installs Wrangler through `pnpm add`.
- The workspace's `pnpm-workspace.yaml` allows selected dependency build scripts but does not allow `workerd`; pnpm exits with `ERR_PNPM_IGNORED_BUILDS` during dynamic Wrangler installation.
- The release workflow already checks out each component tag and builds the corresponding app before invoking Wrangler.

## Plan

1. Inspect the exact action invocation and repository dependency policy.
2. Prefer a workflow-local, explicit Wrangler invocation that does not mutate the workspace lockfile or run a dynamic `pnpm add` inside the monorepo.
3. Keep the three deploy jobs structurally consistent and preserve the existing tag checkout, release metadata, build, and production verification steps.
4. Use the repository's existing Node/pnpm versions and run the relevant local builds for marketing, app, and admin.
5. Validate YAML/workflow changes, inspect the diff, and report any check that cannot run locally because it needs GitHub secrets or Cloudflare credentials.

## Files expected to change

- `.github/workflows/release-please.yml`: deployment command setup for landing, app, and admin.

## Checks

- YAML parse/structure check.
- Local marketing build.
- Local app build.
- Local admin build.
- Git diff and worktree review.
- No live deployment from this session unless explicitly requested after the fix is validated; the local environment does not prove GitHub secret-backed deployment.
