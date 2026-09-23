# Cloudflare release deployment repair

## Overview

### Goal

Make the release-driven deployment jobs for marketing, app, and admin run successfully from their component release tags, then verify each build and deployment path locally without changing application behavior.

### Route

Delegated direct: one shared deployment tool, three consumers, and one CI command path. No product contract or durable SDD cycle is required.

### Decision

Install Wrangler once as a pinned root `devDependency`. The three frontends consume the same deployment CLI, so duplicating it in each package would create unnecessary version drift and repeated manifest and lockfile entries. Run `pnpm exec wrangler` from the workspace root with explicit artifact paths; Pages deployment accepts the artifact directory and project name through CLI flags, so no frontend-specific Wrangler dependency is required.

## Changes

### Evidence

- Release creation succeeds, but the landing and app deploy jobs fail inside `cloudflare/wrangler-action` while the action installs Wrangler through `pnpm add`.
- The workspace's `pnpm-workspace.yaml` now explicitly allows the `workerd` install script required by the pinned local Wrangler dependency; the prior dynamic installation path failed with `ERR_PNPM_IGNORED_BUILDS`.
- The release workflow already checks out each component tag and builds the corresponding app before invoking Wrangler.
- Cloudflare's current installation guidance recommends a project-local Wrangler installation so the team and CI use a controlled version.

### Plan

1. Inspect the exact action invocation and repository dependency policy.
2. Add the pinned Wrangler version already used by CI (`3.90.0`) to the workspace root `devDependencies`.
3. Replace each ephemeral `pnpm dlx ... wrangler` invocation with root-level `pnpm exec wrangler` and explicit `apps/web/*/dist` paths.
4. Keep the three deploy jobs structurally consistent and preserve the existing tag checkout, release metadata, build, and production verification steps.
5. Use the repository's existing Node and pnpm versions and run the relevant local builds for marketing, app, and admin.
6. Validate YAML/workflow changes, inspect the diff, and report any check that cannot run locally because it needs GitHub secrets or Cloudflare credentials.

### Files expected to change

- `package.json`: root `devDependencies` entry for Wrangler.
- `pnpm-lock.yaml`: resolved Wrangler dependency graph.
- `.github/workflows/release-please.yml`: shared local Wrangler invocation for the three deployments.
- `plan/tasks/cloudflare-release-deploy.md`: decision and evidence.

### Acceptance criteria

- Wrangler is declared exactly once at the workspace root.
- CI uses the lockfile-installed Wrangler instead of downloading it dynamically.
- All three deploy commands use explicit artifact paths and preserve their project names and production branch behavior.
- No frontend runtime or build behavior changes.

## Usage

### Checks

- YAML parse and structure check.
- `pnpm exec wrangler --version` resolves the pinned local CLI.
- Local marketing build.
- Local app build.
- Local admin build.
- Git diff and worktree review.
- No live deployment from this session unless explicitly requested after the fix is validated; the local environment does not prove GitHub secret-backed deployment.

## Troubleshooting

### Common issues

- **`ERR_PNPM_IGNORED_BUILDS`**: ensure `workerd: true` is set in `pnpm-workspace.yaml` under `allowBuilds`.
- **`pnpm exec wrangler` not found**: run `pnpm install --frozen-lockfile` to populate the lockfile-installed CLI.

## References

- Cloudflare Wrangler installation: <https://developers.cloudflare.com/workers/wrangler/install-and-update/>
- pnpm workspace root exec: <https://pnpm.io/cli/exec>

## Status

Ready.

### Evidence

- `pnpm install --frozen-lockfile --offline`: PASS; Wrangler and its `workerd` runtime installed from the lockfile.
- `pnpm exec wrangler --version`: PASS; resolved `3.90.0`.
- Wrangler Pages command shape for all three artifact paths: PASS.
- Workflow YAML parse: PASS.
- `pnpm --filter marketing build`: PASS.
- `pnpm --filter app build`: PASS.
- `pnpm --filter @profiletailors/admin build`: PASS.
- Live Cloudflare deployment: NOT RUN; credentials were not available and no production deploy was authorized.
- `git status`: only the planned workflow, manifest, lockfile, workspace policy, and task-plan files are modified.
