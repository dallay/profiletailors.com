---
date: 2026-05-27
status: ✅ Completed
---

# Test Coverage Configuration Summary

## Overview

A complete test coverage configuration has been established for SonarQube in the Profile Tailors
project, covering both the backend (Kotlin/Spring Boot) and frontend (TypeScript/Astro). This
ensures continuous monitoring of code quality and testing rigor across the entire stack.

## Changes

### Testing Configuration

- ✅ `apps/web/marketing/vitest.config.ts` - Configured Vitest with v8 coverage provider.
- ✅ `apps/web/marketing/src/i18n/utils.test.ts` - Added example tests for i18n utilities.

### CI/CD Workflows

- ✅ `.github/workflows/quality-gate.yml` - Implemented full coverage and quality-gate workflow.

### Project Configuration

- ✅ `sonar-project.properties` - Updated with coverage report paths for backend and frontend.
- ✅ `apps/web/marketing/package.json` - Added test scripts and Vitest dependencies.
- ✅ `.gitignore` - Added exclusions for coverage reports.

## Usage

### Backend (Kotlin/Spring Boot)

**Coverage Tool**: Kover (JaCoCo-compatible XML output)

**Reports** (one per Gradle module):

- `server/smp/build/reports/kover/report.xml`
- `shared/common/build/reports/kover/report.xml`
- `shared/bus/build/reports/kover/report.xml`
- `shared/presentation/build/reports/kover/report.xml`
- `shared/security/build/reports/kover/report.xml`
- `shared/spring-boot-common/build/reports/kover/report.xml`
- `shared/storage/build/reports/kover/report.xml`
- `shared/shield/ratelimit/build/reports/kover/report.xml`

**Command**:

```bash
./gradlew :server:smp:test :server:smp:koverXmlReport --no-daemon
```

**Threshold**: 80% minimum

### Frontend (TypeScript/Astro)

**Coverage Tool**: Vitest with v8 provider

**Reports**:

- Marketing LCOV: `apps/web/marketing/coverage/lcov.info`
- App LCOV: `apps/web/app/coverage/lcov.info`
- HTML: `apps/web/marketing/coverage/index.html`

**Commands**:

```bash
cd apps/web/marketing && pnpm test:coverage
cd apps/web/app && pnpm test:coverage
```

**Threshold**: 80% minimum

## Troubleshooting

### "Coverage report not found"

- Verify that tests were executed successfully.
- Check if the report paths in `sonar-project.properties` match the actual generation paths.
- Ensure the respective coverage tasks (`koverXmlReport` for backend, `test:coverage` for
  frontend) were triggered.

## References

- [SonarQube Coverage Guide](./sonarqube-coverage.md)
- [SonarQube Setup Guide](./sonarqube-setup.md)
- [SonarQube Official Documentation](https://docs.sonarsource.com/sonarqube-cloud/analyzing-source-code/test-coverage/overview)
