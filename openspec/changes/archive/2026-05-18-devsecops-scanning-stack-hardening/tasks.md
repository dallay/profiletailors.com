# Tasks: DevSecOps Scanning Stack Hardening

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Review budget | 400 changed lines unless project config says otherwise |
| Estimated workload | High |
| Chained PRs recommended | Yes |
| Proposed delivery strategy | stacked-prs |
| Work-unit balance | Slice 1 = scanner configs + frontend lint contract; Slice 2 = PR workflow + detekt alignment; Slice 3 = deep scan workflow + docs + verification evidence |

## Phase 1: Scanner Foundations

- [x] 1.1 Create `.github/codeql/codeql-config.yml` to scope CodeQL to `server/smp/**` and `shared/**`, and document any query-suite narrowing in English comments.
- [x] 1.2 Create `.semgrep/config.yml`, `.gitleaks.toml`, `.trivyignore`, and `sonar-project.properties` with minimal repo-local tuning, narrow excludes, and English justifications for every suppression.
- [x] 1.3 Create `apps/web/marketing/eslint.config.mjs` with security-focused JS/TS/Astro rules limited to the active frontend app.
- [x] 1.4 Modify `apps/web/marketing/package.json` to add a `lint` script plus the exact ESLint dependencies required by the new frontend security lint contract.

## Phase 2: Pull-Request Security Lane

- [x] 2.1 Create `.github/workflows/security-pr.yml` with `pull_request` triggers, workflow-level concurrency cancellation, and a `changes` job using `dorny/paths-filter` outputs for `backend`, `frontend`, `repo_security`, and `docs_only`.
- [x] 2.2 Add required PR jobs in `.github/workflows/security-pr.yml` for `gitleaks-pr`, `semgrep-backend`, `semgrep-frontend`, `codeql-backend`, `trivy-backend`, `frontend-eslint-security`, and `summary`, each gated by the classifier outputs and using explicit minimal `permissions`.
- [x] 2.3 Add SARIF upload or native reporting wiring in `.github/workflows/security-pr.yml` so CodeQL, Semgrep, Trivy, and Gitleaks publish findings through the documented channel, while skipped jobs remain clearly non-applicable.
- [x] 2.4 Add optional `sonar-pr` behavior in `.github/workflows/security-pr.yml` that runs only when required Sonar secrets/config exist and emits explicit skip messaging when they do not.
- [x] 2.5 Modify `.github/workflows/detekt.yml` only as needed to align naming, permissions, or concurrency conventions without changing Detekt’s separate role.

## Phase 3: Scheduled Deep-Scan Lane

- [x] 3.1 Create `.github/workflows/security-deep.yml` with `schedule` and `workflow_dispatch` triggers plus non-cancelling concurrency for retained scheduled evidence.
- [x] 3.2 Add deep-scan jobs in `.github/workflows/security-deep.yml` for `gitleaks-history`, `semgrep-full`, `codeql-full-backend`, `trivy-full`, optional `sonar-full`, and `retention-summary` with explicit reporting-only intent.
- [x] 3.3 Configure `.github/workflows/security-deep.yml` artifact retention, workflow summaries, and SARIF publication so maintainers can tell which findings land in GitHub security views versus Sonar or workflow artifacts.

## Phase 4: Documentation and Verification

- [x] 4.1 Create `docs/security/scanning-stack.md` explaining each scanner’s role, PR vs scheduled lanes, blocking vs reporting-only checks, covered paths, findings channels, local run commands, and suppression expectations in English.
- [x] 4.2 Verify frontend-local execution by running the documented lint command inside `apps/web/marketing` and record any dependency or config fixes needed for a clean baseline.
- [x] 4.3 Verify workflow contracts by checking YAML validity, permissions, concurrency, path filters, Sonar skip behavior, and representative frontend-only, backend-only, repo-security, and docs-only trigger scenarios.
- [x] 4.4 Verify non-disruption by confirming `.github/workflows/cla.yml`, `.github/workflows/detekt.yml`, and `.github/workflows/release-please.yml` still retain their prior purpose and are not replaced by the new security stack.
