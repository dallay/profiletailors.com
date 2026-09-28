---
date: 2026-09-28
status: ✅ Completed
---

# SonarQube Coverage Configuration & Troubleshooting Guide

## Overview

The Profile Tailors project uses SonarQube to track code quality and test coverage across the entire monorepo stack (Kotlin Spring Boot backend services and TypeScript Astro/Vue frontend applications). This document explains the technical configuration for generating and importing coverage reports and details the root causes and remedies for "Coverage on New Code 0.0%" analysis errors.

## Architecture & Coverage Tools

### Backend (Kotlin/Spring Boot)

- **Coverage Engine**: Kotlin Kover plugin (`org.jetbrains.kotlinx.kover`) producing JaCoCo-compatible XML reports.
- **Report Locations**:
  - `server/smp/build/reports/kover/report.xml`
  - `shared/common/build/reports/kover/report.xml`
  - `shared/bus/build/reports/kover/report.xml`
  - `shared/presentation/build/reports/kover/report.xml`
  - `shared/security/build/reports/kover/report.xml`
  - `shared/spring-boot-common/build/reports/kover/report.xml`
  - `shared/storage/build/reports/kover/report.xml`
  - `shared/shield/ratelimit/build/reports/kover/report.xml`

### Frontend (TypeScript / Astro / Vue 3)

- **Coverage Engine**: Vitest with `v8` coverage provider.
- **Report Locations**:
  - `apps/web/marketing/coverage/lcov.info`
  - `apps/web/app/coverage/lcov.info`

---

## Root Cause Analysis: 0.0% Coverage on New Code

When SonarQube reports `Coverage on New Code: 0.0%` or logs `No coverage report can be found`, it stems from four root configuration and path alignment issues:

### 1. Backend Kover Report Path Missing or Out of Sync

- **Symptom**:
  `No coverage report can be found with sonar.coverage.jacoco.xmlReportPaths=... Using default locations: target/site/jacoco/jacoco.xml...`
- **Root Cause**:
  The SonarQube scanner runs in CI before the Gradle Kover report tasks (`:server:smp:koverXmlReport`, etc.) have executed, or test execution fails prior to report generation. When the XML files specified in `sonar.coverage.jacoco.xmlReportPaths` are absent on disk during scanner execution, SonarQube falls back to default paths and records 0.0% coverage for Java/Kotlin files.

### 2. Frontend LCOV Relative Path Mismatch in Monorepos

- **Symptom**:
  Frontend code coverage is reported as 0.0% despite Vitest running and generating `lcov.info`.
- **Root Cause**:
  When Vitest runs inside subdirectories (`apps/web/marketing` or `apps/web/app`), the generated `lcov.info` contains file paths relative to the subpackage root (e.g., `SF:src/components/useWaitlistForm.ts`).
  However, SonarQube scanner runs from the monorepo root (`/app`), where `sonar.sources` specifies `apps/web/marketing/src,apps/web/app/src`. SonarQube's LCOV parser attempts to resolve `SF:src/components/...` against the monorepo root or report directory, looking for `/app/src/components/...` or `/app/apps/web/marketing/coverage/src/...`, which fails to match `/app/apps/web/marketing/src/...`.
  Without exact path alignment, SonarQube ignores the coverage entries in the LCOV file and assigns 0.0% coverage to modified files in the PR.

### 3. Missing `sonar.typescript.tsconfigPath` Configuration

- **Symptom**:
  SonarQube analysis log warning:
  `At least one referenced/extended tsconfig.json was not found in the project. Please run 'npm install' for a more complete analysis.`
- **Root Cause**:
  SonarQube's TypeScript analyzer requires explicit pointers to subproject `tsconfig.json` files in monorepos. Without `sonar.typescript.tsconfigPath` defined in `sonar-project.properties`, the scanner fails to build the complete AST and symbol table for frontend source files, impairing analysis and coverage mapping.

### 4. Unbuilt or Missing Subproject Reports

- **Symptom**:
  Coverage is missing for `apps/web/app` or shared packages.
- **Root Cause**:
  If the test runner step (`pnpm test:coverage`) is only executed in `apps/web/marketing` and skipped in `apps/web/app`, the expected `apps/web/app/coverage/lcov.info` report file does not exist, leading to incomplete or zero coverage for the application package.

---

## Solutions & Configuration Standards

### 1. `sonar-project.properties` Standards

Ensure `sonar-project.properties` explicitly configures `sonar.typescript.tsconfigPath` alongside `sonar.sources` and report paths:

```properties
# TypeScript Configuration for Monorepo
sonar.typescript.tsconfigPath=apps/web/marketing/tsconfig.json,apps/web/app/tsconfig.json,tools/compliance/tsconfig.json

# Report Paths
sonar.coverage.jacoco.xmlReportPaths=server/smp/build/reports/kover/report.xml,shared/common/build/reports/kover/report.xml,shared/bus/build/reports/kover/report.xml,shared/presentation/build/reports/kover/report.xml,shared/security/build/reports/kover/report.xml,shared/spring-boot-common/build/reports/kover/report.xml,shared/storage/build/reports/kover/report.xml,shared/shield/ratelimit/build/reports/kover/report.xml
sonar.javascript.lcov.reportPaths=apps/web/marketing/coverage/lcov.info,apps/web/app/coverage/lcov.info
sonar.typescript.lcov.reportPaths=apps/web/marketing/coverage/lcov.info,apps/web/app/coverage/lcov.info
```

### 2. CI Pipeline Execution Sequence (`.github/workflows/quality-gate.yml`)

Always execute coverage generation tasks and verify file existence before invoking `sonarsource/sonarqube-scan-action`:

1. **Run Backend Tests & Kover Report Tasks**:

   ```bash
   ./gradlew :server:smp:test :server:smp:koverXmlReport \
     :shared:common:test :shared:common:koverXmlReport \
     :shared:bus:test :shared:bus:koverXmlReport \
     :shared:presentation:test :shared:presentation:koverXmlReport \
     :shared:security:test :shared:security:koverXmlReport \
     :shared:spring-boot-common:test :shared:spring-boot-common:koverXmlReport \
     :shared:storage:test :shared:storage:koverXmlReport \
     :shared:shield:ratelimit:test :shared:shield:ratelimit:koverXmlReport \
     --no-daemon
   ```

2. **Run Frontend Coverage for All Web Packages**:

   ```bash
   pnpm --filter marketing test:coverage
   pnpm --filter app test:coverage
   ```

3. **Verify Report Files Exist**:
   Confirm that all expected `report.xml` and `lcov.info` files exist prior to running the SonarQube scan step.

---

## Local Verification Commands

To generate all coverage reports locally prior to running a SonarQube analysis:

```bash
# Generate backend Kover XML reports
./gradlew :server:smp:koverXmlReport :shared:common:koverXmlReport :shared:bus:koverXmlReport :shared:presentation:koverXmlReport :shared:security:koverXmlReport :shared:spring-boot-common:koverXmlReport :shared:storage:koverXmlReport :shared:shield:ratelimit:koverXmlReport --no-daemon

# Generate frontend LCOV reports
pnpm --filter marketing test:coverage
pnpm --filter app test:coverage
```

## References

- [SonarQube Setup Guide](./sonarqube-setup.md)
- [SonarQube Official Coverage Documentation](https://docs.sonarsource.com/sonarqube-cloud/analyzing-source-code/test-coverage/overview)
- [Vitest Coverage Guide](https://vitest.dev/guide/coverage.html)
- [Kotlin Kover Documentation](https://kotlin.github.io/kotlinx-kover/)
