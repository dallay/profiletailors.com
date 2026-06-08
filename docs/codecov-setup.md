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

- **Coverage targets**: 80% project, 70% patch
- **Flags**: `backend`, `smp` for component tracking
- **Ignore patterns**: Tests, docs, build artifacts

### `.github/workflows/test-coverage.yml`

CI workflow that:

- Runs backend tests with JaCoCo
- Generates XML coverage reports
- Uploads to Codecov with flags
- Stores test results as artifacts

### `server/smp/build.gradle.kts`

Gradle configuration with:

- JaCoCo plugin enabled
- XML report generation (required by Codecov)
- HTML reports for local viewing
- Coverage verification (80% minimum)
- Exclusions for config/dto/entity classes

## Local Usage

### Run tests with coverage

```bash
cd server/smp
./gradlew test jacocoTestReport
```

### View coverage report

```bash
open server/smp/build/reports/jacoco/test/html/index.html
```

### Verify coverage thresholds

```bash
./gradlew jacocoTestCoverageVerification
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
- **Patch coverage**: 70% (±5% threshold)
- **Precision**: 2 decimal places
- **Range**: 70-100%

## Flags

- `backend`: All backend code (server/ + shared/)
- `smp`: Social Media Platform service (server/smp/)

## Troubleshooting

### Coverage not uploading

1. Check `CODECOV_TOKEN` is set in GitHub secrets
2. Verify workflow has `contents: read` permission
3. Check JaCoCo XML report exists at expected path

### Low coverage warnings

1. Run `./gradlew jacocoTestReport` locally
2. Open HTML report to see uncovered lines
3. Add tests for uncovered code
4. Verify exclusions in `build.gradle.kts` are correct

### Workflow failing

1. Check test execution: `./gradlew test`
2. Verify JaCoCo report generation: `./gradlew jacocoTestReport`
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
