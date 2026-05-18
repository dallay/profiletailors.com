# Proposal: DevSecOps Scanning Stack Hardening

## Intent

Establish a practical, low-noise DevSecOps scanning baseline for this monorepo so pull requests get fast, relevant security feedback without turning CI into a slow or noisy bottleneck.

Today the repository has only a minimal Detekt lane and lacks first-party or broadly maintained coverage for source-code security rules, secret detection, dependency and container/image vulnerability scanning, code graph analysis, and centralized quality reporting. This change defines a maintainable scanning stack that fits the repo’s mixed Kotlin/Spring Boot and Astro footprint, scopes work by changed paths where safe, and separates fast PR checks from deeper scheduled scans.

## Scope

### In Scope
- Add a fast pull-request security lane using official or well-maintained GitHub Actions with minimal token permissions and concurrency controls.
- Introduce path-aware scanning coverage for the active backend (`server/smp`, `shared/**`) and frontend (`apps/web/marketing`) surfaces.
- Define scheduled deeper scans for full-repository analysis where PR-time execution would be too slow or noisy.
- Add baseline repository configuration for selected tools where required, such as Semgrep rules, CodeQL config, Gitleaks config, Trivy config, and ESLint flat config for the marketing app.
- Harden existing workflow design patterns across the new scanning workflows: explicit permissions, cancel-in-progress PR concurrency, and English-language workflow/docs output.
- Document the scanning stack, ownership boundaries, and rollout expectations in repo-local English documentation.

### Out of Scope
- Re-architecting the full CI system or rewriting unrelated existing workflows such as release automation.
- Blocking merges on every deep or scheduled scan from day one.
- Introducing paid hosted policy platforms or organization-wide governance workflows beyond repo-local configuration.
- Building custom security scanners when maintained upstream tools already solve the problem.
- Full dependency-update automation, SBOM distribution pipelines, container build redesign, or deployment-environment runtime security.
- Large Detekt policy expansion beyond what is necessary to coexist cleanly with the new stack.

## Approach

Adopt a layered scanning model with clear purpose per lane:

- **PR fast lane** for high-signal checks on touched surfaces only, prioritizing quick feedback and low noise.
- **Scheduled deep lane** for slower or broader analysis across the full monorepo, including queries and scans that are expensive or less suitable for every PR.
- **Repo-local configs** kept minimal and understandable so developers can tune suppressions and policy in code review.

Recommended tool choices:
- **CodeQL** for GitHub-native code graph security analysis on the Kotlin/Java backend.
- **Semgrep** for fast cross-language SAST rules on Kotlin, YAML, shell, and frontend code with practical PR feedback.
- **Gitleaks** for secret detection on PRs and scheduled history-aware scans.
- **Trivy** for filesystem/dependency vulnerability scanning and config/IaC checks where applicable.
- **ESLint** for the Astro marketing app JavaScript/TypeScript surface, kept narrow to `apps/web/marketing`.
- **Detekt** remains the Kotlin style/static-analysis lane and is not replaced by this change.

Rollout boundary:
- PR workflows SHOULD focus on changed paths and changed-language execution where possible.
- Scheduled workflows SHOULD run full-depth scans on a cadence appropriate for security hygiene.
- New required checks SHOULD start with the highest-signal lanes only; deeper scheduled jobs can report findings without immediately becoming merge gates.
- The change SHOULD preserve a clean monorepo contract: backend-oriented scans target `server/smp` and `shared/**`; frontend linting targets `apps/web/marketing`; repo-wide scanners remain explicitly repo-wide.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `.github/workflows/` | Modified | Add new PR and scheduled security workflows using official or well-maintained actions, plus hardened permissions and concurrency groups. |
| `.github/workflows/detekt.yml` | Modified | Align existing Detekt workflow conventions with the new CI hardening baseline where useful. |
| `server/smp/` | Modified | Backend scan targeting for Kotlin/Spring Boot CodeQL, Semgrep, and vulnerability analysis. |
| `shared/` | Modified | Include shared Gradle modules in backend-relevant scan scope. |
| `apps/web/marketing/` | Modified | Add ESLint and include frontend source in Semgrep and dependency scanning scope. |
| `apps/web/marketing/package.json` | Modified | Add lint script and ESLint dependencies if needed for the selected frontend linting baseline. |
| `.semgrep/` or repo-root Semgrep config files | New | Define maintainable Semgrep rules/config for monorepo scanning. |
| `.github/codeql/` or repo-root CodeQL config | New | Define CodeQL paths and query scope for backend-focused analysis. |
| `.gitleaks.toml` | New | Define secret scanning baseline and pragmatic allowlist controls if needed. |
| `.trivyignore` and/or Trivy config | New | Define vulnerability/config scan tuning to reduce false positives. |
| `docs/` or repo-root engineering documentation | Modified | Add English documentation for the scanning stack, scope, and local/CI usage. |
| `openspec/specs/platform/spec.md` | Modified | Platform/engineering guardrails may need to recognize repository security scanning expectations if durable behavior is specified. |
| `openspec/specs/governance/spec.md` | Modified | Governance expectations may need to reflect repo-level security scanning and findings handling boundaries. |

## Scope Boundaries

- This change is about **repository scanning baseline hardening**, not application runtime security redesign.
- The stack MUST stay maintainable for a mixed monorepo and MUST avoid duplicative tools doing the same job without a clear reason.
- PR-time checks MUST optimize for signal, runtime, and developer trust.
- Scheduled scans MAY be broader and deeper, but they MUST remain understandable and support actionable ownership.
- Tool configuration MUST be written in English and SHOULD be easy to review and evolve.

## Non-Goals

- Turning the repo into a compliance platform.
- Solving org-wide policy management, exception portals, or enterprise triage workflows.
- Replacing Gradle, Astro, or existing project build conventions.
- Guaranteeing zero false positives across all scanners.
- Adding heavyweight scanning for inactive/unowned parts of the monorepo beyond defined boundaries.

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Too many scanners create alert fatigue and slow developer adoption | Medium | Separate fast PR checks from deeper scheduled scans and keep required gates limited to high-signal jobs first. |
| Path filters miss relevant security impact in shared code or config changes | Medium | Define conservative shared-path triggers and use scheduled full scans as the safety net. |
| Overlapping tools report the same issue class with conflicting noise levels | Medium | Assign each tool a clear role: CodeQL for code graph analysis, Semgrep for fast SAST, Gitleaks for secrets, Trivy for vulns/config, ESLint for frontend linting, Detekt for Kotlin static analysis. |
| GitHub workflow permissions are broader than necessary | Low | Use explicit job-level `permissions`, avoid unnecessary write scopes, and document token needs per workflow. |
| New configs become stale or too custom to maintain | Medium | Prefer upstream defaults with minimal repo-local tuning and document why any suppression exists. |
| Full scans are too expensive for every PR | High | Keep deep analysis scheduled and path-scope PR jobs wherever safe. |

## Rollback Plan

If the scanning stack causes unacceptable CI noise, runtime, or maintenance burden, revert the newly added workflows and tool configuration files, restore any modified existing workflow behavior, and remove new frontend lint hooks that were introduced solely for this change.

Rollback is low risk because this change is additive around repository tooling and CI policy rather than application runtime behavior. Existing build and release flows can continue once the new scanning workflows and configs are removed.

## Dependencies

- Existing GitHub Actions repository with `.github/workflows/cla.yml`, `.github/workflows/detekt.yml`, and `.github/workflows/release-please.yml`.
- GitHub Advanced Security availability for CodeQL if repository/org settings permit it.
- Node tooling in `apps/web/marketing` for ESLint execution.
- Gradle/Kotlin project structure in `server/smp` and `shared/**` for backend-targeted scans.
- Maintained upstream GitHub Actions for the selected scanners.

## Success Criteria

- [ ] A fast PR security lane is defined with path-aware execution, minimal permissions, and concurrency cancellation for in-progress PR updates.
- [ ] Scheduled full or deep scans are defined separately from PR checks for broader repository coverage.
- [ ] The selected stack covers source-code security analysis, secret scanning, vulnerability/config scanning, and frontend linting without unnecessary tool duplication.
- [ ] Backend-oriented scans explicitly cover `server/smp` and `shared/**`, while frontend linting explicitly covers `apps/web/marketing`.
- [ ] Repo-local scanner configs are minimal, maintainable, and documented in English.
- [ ] Existing workflows remain understandable and are not unnecessarily slowed by the new security baseline.
- [ ] The proposal clearly defers broader compliance, runtime security redesign, dependency automation, and nonessential CI rework.
