# Security Scanning Stack

## Purpose

This repository uses a layered DevSecOps scanning model to keep pull request feedback fast while
still running broader scheduled analysis for deeper security visibility.

The stack is intentionally split into two lanes:

- **PR lane**: fast, path-aware, merge-relevant feedback.
- **Scheduled deep lane**: reporting-oriented scans that preserve evidence and broaden coverage
  without slowing normal pull request flow.

The implementation is additive. It does **not** replace the existing CLA, Detekt, or release
automation workflows.

## Verification Notes

- Local frontend verification is executed with Biome.
- Current baseline result: **pass with no errors**.
- No dependency or configuration changes were required to establish the documented local lint
  contract.
- Existing workflows checked for non-disruption during this change:
    - `.github/workflows/cla.yml`
    - `.github/workflows/detekt.yml`
    - `.github/workflows/release-please.yml`
- Workflow contract verification was executed with a local Ruby YAML validation script.
- Verified contract points: deep workflow triggers, non-cancelling scheduled concurrency, required
  deep jobs, SARIF-capable permission wiring, Sonar skip contract, PR path-classifier buckets, and
  preservation of CLA/Detekt/Release Please workflow identities.

## Architecture Overview

### Workflows

| Workflow               | File                                  | Trigger                         | Purpose                                      | Concurrency                          |
|------------------------|---------------------------------------|---------------------------------|----------------------------------------------|--------------------------------------|
| PR security lane       | `.github/workflows/security-pr.yml`   | `pull_request`                  | Fast, path-aware, merge-facing checks        | Cancels superseded PR runs           |
| Deep security lane     | `.github/workflows/security-deep.yml` | `schedule`, `workflow_dispatch` | Full/deeper reporting with retained evidence | Does **not** cancel in-progress runs |
| Kotlin static analysis | `.github/workflows/detekt.yml`        | existing triggers               | Kotlin quality/static-analysis lane          | Separate responsibility              |

### Scanner responsibility map

| Scanner                | Primary role                                                         | Main scope                                                            | Findings channel                                                          | PR gate                                              |
|------------------------|----------------------------------------------------------------------|-----------------------------------------------------------------------|---------------------------------------------------------------------------|------------------------------------------------------|
| CodeQL                 | Deep backend code-graph analysis                                     | `server/smp`, `shared/**`                                             | GitHub code scanning                                                      | Yes when backend or repo-security scope is relevant  |
| Semgrep                | Fast cross-language SAST and security-sensitive config scanning      | Backend, frontend, workflow/config surfaces                           | GitHub code scanning via SARIF                                            | Yes when relevant in PR lane                         |
| Gitleaks               | Secret detection                                                     | Entire repository                                                     | GitHub code scanning in deep lane, native artifact/log channel in PR lane | Yes in PR lane for non-doc-only changes              |
| Trivy                  | Dependency, filesystem, and IaC/config scanning                      | Repository filesystem, dependency manifests, security-relevant config | GitHub code scanning via SARIF + retained artifacts                       | Yes in PR lane with narrowed severity                |
| Biome security rules   | Frontend JS/TS/Vue/Astro security linting and formatting             | `apps/web/**`                                                         | Workflow logs and annotations                                             | Yes when frontend or repo-security scope is relevant |
| Detekt                 | Kotlin static analysis and code quality                              | Existing backend/shared Gradle modules                                | Existing workflow artifact/log output                                     | Existing repo policy                                 |
| SonarQube / SonarCloud | Centralized reporting, historical trends, and optional PR decoration | Repository areas supported by configured binding                      | Sonar dashboard and PR decoration                                         | Reporting-only initially                             |

## Scanning Strategy

### PR lane: `.github/workflows/security-pr.yml`

The PR lane exists to answer one question quickly: **does this change introduce a high-signal
security problem that should block merge right now?**

#### Trigger

- `pull_request` on `main`
- Event types:
    - `opened`
    - `synchronize`
    - `reopened`
    - `ready_for_review`

#### Concurrency contract

```yaml
concurrency:
  group: security-pr-${{ github.event.pull_request.number || github.ref }}
  cancel-in-progress: true
```

That means new commits on the same PR cancel older in-progress security runs. Contributors get only
the latest feedback.

#### Path-aware classifier buckets

The `changes` job classifies modified files into these buckets:

- `backend`
- `frontend`
- `repo_security`
- `docs_only`

Representative meaning:

| Bucket          | Includes                                                                                           |
|-----------------|----------------------------------------------------------------------------------------------------|
| `backend`       | `server/smp/**`, `shared/**`, backend Gradle files, CodeQL/Semgrep/Trivy/Sonar configs             |
| `frontend`      | `apps/web/marketing/**`, `apps/web/app/**`, and relevant frontend security/lint configs            |
| `repo_security` | workflow files, scanner configs, dependency manifests, broad-impact CI/security files              |
| `docs_only`     | `docs/**`, `openspec/**`, and markdown-only changes that do not also touch security-relevant files |

**Conservative rule**: if a change touches docs and a security-relevant surface, the
security-relevant surface bucket wins.

#### PR jobs

| Job                       | Runs when                               | Notes                                       |
|---------------------------|-----------------------------------------|---------------------------------------------|
| `gitleaks-pr`             | Any non-doc-only PR                     | Secrets can appear anywhere                 |
| `semgrep-backend`         | `backend` or `repo_security`            | Backend and broad-impact SAST               |
| `semgrep-frontend`        | `frontend` or `repo_security`           | Frontend and broad-impact SAST              |
| `codeql-backend`          | `backend` or `repo_security`            | Backend/shared code-graph analysis          |
| `trivy-backend`           | `backend` or `repo_security`            | High-signal vulnerability/misconfig scan    |
| `frontend-biome-security` | `frontend` or `repo_security`           | Frontend lint and security contract         |
| `sonar-pr`                | Scope changed and Sonar config is valid | Optional, explicit, reporting-oriented      |
| `summary`                 | Always                                  | Explains what ran and where findings appear |

### Scheduled deep lane: `.github/workflows/security-deep.yml`

The deep lane exists to answer a different question: **what broader security drift or latent issues
exist across the repository that we do not want to hide, but also do not want to force into every
PR?**

#### Triggers

- Weekday nightly schedule:

```yaml
schedule:
  - cron: '17 3 * * 1-5'
```

- Manual reruns:

```yaml
workflow_dispatch:
```

#### Concurrency contract

```yaml
concurrency:
  group: security-deep-${{ github.ref }}
  cancel-in-progress: false
```

This is intentional. Scheduled evidence should be preserved instead of cancelled by a later manual
rerun.

#### Deep jobs

| Job                   | Purpose                                                 | Reporting mode |
|-----------------------|---------------------------------------------------------|----------------|
| `gitleaks-history`    | Full-history secrets scan                               | Reporting-only |
| `semgrep-full`        | Full-repository SAST and security-sensitive config scan | Reporting-only |
| `codeql-full-backend` | Deep backend/shared CodeQL analysis                     | Reporting-only |
| `trivy-full`          | Full filesystem, dependency, and IaC/config scan        | Reporting-only |
| `sonar-full`          | Optional centralized full Sonar analysis                | Reporting-only |
| `retention-summary`   | Documents channels, retention, and results              | Reporting-only |

## Severity Handling and Merge Gating

### Pull request gates

These are the initial merge-relevant checks:

- **Gitleaks**: blocking for findings in non-doc-only PRs.
- **Semgrep**: blocking when the path classifier says the change is in scope.
- **CodeQL**: blocking for backend or repo-security relevant PRs.
- **Trivy**: blocking in the PR lane with a narrowed severity threshold of `HIGH,CRITICAL`.
- **Frontend Biome security**: blocking for frontend or repo-security relevant PRs.
- **Detekt**: continues under its existing workflow and repo policy.

### Reporting-only checks

These are intentionally **not** merge gates initially:

- All scheduled deep-scan jobs.
- Sonar PR and full analysis.
- Retained artifacts and workflow summaries.

### Why the split exists

This split is deliberate:

- PR gates must stay predictable and actionable.
- Deep scans are broader and noisier by nature.
- Sonar overlaps with other scanners and is more useful initially as a centralized reporting layer
  than as another blocker.

## Findings Channels

Understanding where to look matters as much as running the scanner.

| Scanner            | Where to review findings                                                          |
|--------------------|-----------------------------------------------------------------------------------|
| CodeQL             | GitHub code scanning                                                              |
| Semgrep            | GitHub code scanning via uploaded SARIF                                           |
| Trivy              | GitHub code scanning via uploaded SARIF; deep lane also retains artifact evidence |
| Gitleaks PR lane   | Workflow logs, annotations, and native uploaded artifact                          |
| Gitleaks deep lane | GitHub code scanning via SARIF plus retained artifact                             |
| Biome              | Workflow logs and annotations                                                     |
| Detekt             | Existing workflow artifact/log output                                             |
| Sonar              | Sonar dashboard and optional PR decoration when configured                        |
| Workflow summaries | `$GITHUB_STEP_SUMMARY` content in each workflow run                               |

## Covered Paths

### Backend-oriented coverage

- `server/smp/**`
- `shared/**`
- backend Gradle files
- backend-relevant scanner configs

### Frontend-oriented coverage

- `apps/web/marketing/**`
- `apps/web/app/**`
- active frontend biome/config files

### Broad-impact security coverage

- `.github/workflows/**`
- `.github/codeql/**`
- `.semgrep/**`
- `.gitleaks.toml`
- `.trivyignore`
- `sonar-project.properties`
- dependency manifests and lockfiles

### Docs-only changes

- `docs/**`
- `openspec/**`
- markdown files that do not also touch broad-impact security surfaces

## SARIF Publication and Artifact Retention

### SARIF-capable publication

The repository uses GitHub code scanning where the scanner and execution path support it.

#### PR lane

- CodeQL: native GitHub code scanning upload.
- Semgrep: SARIF uploaded to GitHub code scanning.
- Trivy: SARIF uploaded to GitHub code scanning.
- Gitleaks: native maintained action channel in the PR lane for this repository setup.

#### Deep lane

- Gitleaks history: SARIF uploaded to GitHub code scanning.
- Semgrep full: SARIF uploaded to GitHub code scanning.
- CodeQL full backend: native GitHub code scanning upload.
- Trivy full: SARIF uploaded to GitHub code scanning.
- Sonar full: no SARIF; results stay in Sonar.

### Artifact retention

The deep lane retains evidence artifacts for **21 days**:

- `security-deep-gitleaks-history`
- `security-deep-semgrep-full`
- `security-deep-trivy-full`

Why 21 days:

- long enough for asynchronous triage
- short enough to avoid indefinite storage growth

## Required Secrets and Operational Dependencies

### No extra secrets required

These scanners are designed to run from repository content plus normal GitHub context:

- CodeQL
- Semgrep
- Gitleaks
- Trivy
- Biome
- Detekt

### Optional external integration secrets

Sonar is intentionally explicit and optional-by-secret.

| Secret / config                                  | Required for               | Notes                                                        |
|--------------------------------------------------|----------------------------|--------------------------------------------------------------|
| `SONAR_TOKEN`                                    | Sonar PR and full analysis | Mandatory for Sonar execution                                |
| `SONAR_HOST_URL`                                 | Self-hosted SonarQube only | Optional for SonarCloud style setups if configured elsewhere |
| `sonar.projectKey` in `sonar-project.properties` | Sonar binding              | Placeholder value must be replaced                           |
| `sonar.organization`                             | SonarCloud setups          | Uncomment and set when using SonarCloud                      |

If Sonar is not fully configured:

- the workflow must say so explicitly
- the summary must explain why it skipped
- the repository should not pretend centralized reporting is active

## Local Usage

### Frontend Biome security contract

Run the documented frontend security lint from the active workspace using Biome:

```bash
cd apps/web
pnpm dlx @biomejs/biome ci .
```

Or run format/lint individually on any project folder:

```bash
# Lint marketing with Biome
cd apps/web/marketing && pnpm lint

# Lint app with Biome
cd apps/web/app && pnpm lint
```

### Semgrep

If you have Semgrep installed locally:

```bash
semgrep scan --config .semgrep/config.yml --error
```

You can scope it during investigation, but CI remains the source of truth for the workflow contract.

### Gitleaks

Tree-oriented local run:

```bash
gitleaks dir . --config .gitleaks.toml
```

History-oriented local run:

```bash
gitleaks git . --config .gitleaks.toml --log-opts="--all"
```

### Trivy

Filesystem and config scan:

```bash
trivy fs \
  --scanners vuln,misconfig \
  --severity HIGH,CRITICAL \
  .
```

For deep local inspection you may widen severity to match the scheduled lane.

### CodeQL

CodeQL is primarily validated through GitHub Actions in this repository because it depends on the
extraction/build setup and GitHub-native reporting path.

### Sonar

Sonar is only meaningful locally if you have a valid project binding and token. Without those, use
the other local scanners and rely on CI summaries.

## CI Optimization Rules

### Why PR scans are path-aware

Path classification keeps the fast lane reviewable and avoids making frontend-only work wait on
backend graph analysis or vice versa.

### Why repo-security changes widen scope

Changes to workflows, scanner configs, lockfiles, or build manifests can affect repository-wide
outcomes. Those changes intentionally trigger broader checks.

### Why scheduled scans do not cancel

The deep lane is evidence-oriented. If one nightly run starts and another run is launched manually
later, both should remain reviewable.

### Why Sonar is not a required check yet

Sonar overlaps with multiple scanners and introduces an external-service dependency. The repository
uses it first for centralized visibility, not surprise gating.

## Suppression and False-Positive Governance

Suppressions must stay:

- repo-local
- narrow in scope
- reviewable in code review
- justified in plain English

### Config locations

- `.semgrep/config.yml`
- `.github/codeql/codeql-config.yml`
- `.gitleaks.toml`
- `.trivyignore`
- `apps/web/marketing/biome.json`
- `apps/web/app/biome.json`
- `sonar-project.properties`

### Expectations

- Prefer upstream defaults first.
- Do **not** suppress an entire scanner because of one noisy rule.
- Explain **why this repository is different** for each suppression.
- Scope by rule, path, or finding identifier whenever the tool supports it.

## Representative Trigger Scenarios

| Scenario                                              | Expected PR behavior                                                                                                   |
|-------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|
| Frontend-only change in `apps/web/marketing/**`       | `gitleaks-pr`, `semgrep-frontend`, `frontend-biome-security`, `summary`; backend CodeQL/Trivy jobs stay non-applicable |
| Backend-only change in `server/smp/**` or `shared/**` | `gitleaks-pr`, `semgrep-backend`, `codeql-backend`, `trivy-backend`, `summary`; frontend biome stays non-applicable    |
| Workflow or scanner-config change                     | Broad checks run because `repo_security` is true                                                                       |
| Docs-only change under `docs/**` or `openspec/**`     | Heavy path-specific security jobs skip cleanly; summaries should make that obvious                                     |
| Nightly or manual deep run                            | Full reporting-oriented lane executes with retained artifacts and non-cancelling concurrency                           |

## Troubleshooting

### Biome checks fail locally

- Confirm dependencies are installed with the lockfile:

```bash
pnpm install --frozen-lockfile
```

- Re-run the Biome check:

```bash
cd apps/web && pnpm dlx @biomejs/biome ci .
```

### Sonar always skips

Check all of these:

- `SONAR_TOKEN` exists in repository secrets.
- `sonar.projectKey` is not the placeholder `profiletailors-change-me`.
- `SONAR_HOST_URL` is set when using self-hosted SonarQube.
- `sonar.organization` is set when using SonarCloud.

### GitHub code scanning does not show findings

- Confirm the job had `security-events: write` where required.
- Confirm the SARIF upload step still runs with `if: always()`.
- Confirm the scanner actually produced a SARIF file before upload.

### Trivy is noisy

That is expected in deeper scans. The PR lane stays narrower by severity, while the deep lane keeps
broad visibility for triage.

### Gitleaks findings in docs or old commits

That is also expected. Secret detection remains repo-wide by design. If a result is a legitimate
false positive, suppress it narrowly in `.gitleaks.toml` with an English reason.

## Maintainer Checklist

When changing this stack, verify all of the following:

- workflow permissions remain minimal
- action references remain pinned to immutable SHAs with version comments
- PR concurrency still cancels superseded runs
- deep workflow concurrency still preserves evidence
- Sonar skip messaging remains explicit
- path filters still cover backend, frontend, repo-security, and docs-only scenarios
- retained artifacts still have intentional retention periods
- documentation stays aligned with actual workflow behavior

## Quick Reference

### PR blockers now

- Gitleaks
- Semgrep in relevant scope
- CodeQL in backend/repo-security scope
- Trivy in backend/repo-security scope with `HIGH,CRITICAL`
- Frontend Biome security in frontend/repo-security scope

### Reporting-only now

- Scheduled deep lane
- Sonar PR/full analysis
- Artifact-retention summaries

### Local command you should use first

```bash
cd apps/web && pnpm dlx @biomejs/biome ci .
```
