# Codecov Configuration Guide

## Overview

This project uses [Codecov](https://about.codecov.io/) for code coverage tracking and reporting.

## Setup

### 1. Enable Codecov for the Repository

1. Go to [codecov.io](https://codecov.io/)
2. Sign in with your GitHub account
3. Add the `profiletailors.com` repository
4. Copy the **CODECOV_TOKEN** from the repository settings

### 2. Add GitHub Secret

1. Go to GitHub repository → Settings → Secrets and variables → Actions
2. Click **New repository secret**
3. Name: `CODECOV_TOKEN`
4. Value: Paste the token from Codecov
5. Click **Add secret**

## Configuration Files

### `codecov.yml`

Main Codecov configuration at the repository root:

- **Coverage targets**: 80% project, 80% patch
- **Flags**: `backend`, `frontend` for per-component status checks
- **Ignore patterns**: Tests, docs, build artifacts
- **Aligns with**: SonarQube — both tools ingest the same report files

### `.github/workflows/quality-gate.yml`

CI workflow that:

- Runs backend tests and generates Kover XML reports
- Runs frontend tests and generates LCOV reports
- Uploads the same reports to both SonarQube and Codecov
- Stores coverage reports as artifacts for debugging

### Backend coverage reports

Gradle build logic provides:

- Kover plugin enabled across backend modules
- XML report generation (required by SonarQube and Codecov)
- Consistent JVM test execution across `server/` and `shared/` modules
- Coverage verification target of 80%

## Local Usage

### Run tests with coverage

```bash
./gradlew :server:smp:test :server:smp:koverXmlReport --no-daemon
```

### Verify coverage thresholds

```bash
./gradlew :server:smp:koverCheck --no-daemon
```

## CI/CD Integration

The workflow runs automatically on:

- Push to `main` or `develop`
- Pull requests to `main` or `develop`

### Coverage Reports

After each run:

- Coverage data is uploaded to Codecov
- Test results are stored as artifacts (7 days)
- Coverage reports are stored as artifacts (7 days)
- Codecov comments on PRs with coverage diff

## Badges

Add to your README.md:

```markdown
[![codecov](https://codecov.io/gh/dallay/profiletailors.com/branch/main/graph/badge.svg)](https://codecov.io/gh/dallay/profiletailors.com)
```

## Coverage Targets

- **Project coverage**: 80% (±2% threshold)
- **Patch coverage**: 80% (±2% threshold)
- **Precision**: 2 decimal places
- **Range**: 70-100%

## Flags

- `backend`: All backend code (`server/` + `shared/`)
- `frontend`: All frontend code (`apps/web/marketing/` + `apps/web/app/`)

## Troubleshooting

### Coverage not uploading

1. Check `CODECOV_TOKEN` is set in GitHub secrets
2. Verify `CODECOV_TOKEN` secret is present in the repository
3. Check Kover XML report exists at expected path

### Low coverage warnings

1. Run `./gradlew :server:smp:test :server:smp:koverXmlReport --no-daemon` locally
2. Open HTML report in `server/smp/build/reports/kover/html/index.html`
3. Add tests for uncovered code
4. Verify exclusions in the build configuration are correct

### Workflow failing

1. Check test execution: `./gradlew :server:smp:test --no-daemon`
2. Verify Kover report generation: `./gradlew :server:smp:koverXmlReport --no-daemon`
3. Check workflow logs in GitHub Actions

## Next Steps

1. ✅ Configure Codecov account
2. ✅ Add `CODECOV_TOKEN` to GitHub secrets
3. ✅ Merge this PR to enable coverage tracking
4. 📊 Monitor coverage trends in Codecov dashboard
5. 🎯 Set team coverage goals
6. 🔄 Add coverage checks to PR requirements (optional)

## Resources

- [Codecov Documentation](https://docs.codecov.com/)
- [JaCoCo Documentation](https://www.jacoco.org/jacoco/trunk/doc/)
- [GitHub Actions with Codecov](https://github.com/codecov/codecov-action)
