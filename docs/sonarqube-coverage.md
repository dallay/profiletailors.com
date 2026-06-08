---
date: 2026-05-25
status: ✅ Completed
---

# SonarQube Coverage Configuration

## Overview

The Profile Tailors project uses SonarQube to track code quality and test coverage across the entire
stack. This document explains the technical configuration for generating and importing coverage
reports for both the backend and frontend.

## Changes

### Backend (Kotlin/Spring Boot)

**Coverage Tool**: JaCoCo

JaCoCo is configured in `server/smp/build.gradle.kts`:

```kotlin
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
```

### Frontend (TypeScript/Astro)

**Coverage Tool**: Vitest with v8 provider

Vitest is configured in `apps/web/marketing/vitest.config.ts`:

```typescript
coverage: {
  provider: 'v8',
  reporter: ['text', 'json', 'html', 'lcov'],
  reportsDirectory: './coverage',
}
```

## Usage

### Running Coverage Locally

**Backend**:

```bash
cd server/smp && ./gradlew test jacocoTestReport
```

**Frontend**:

```bash
cd apps/web/marketing && pnpm test:coverage
```

### SonarQube Properties

The following properties in `sonar-project.properties` link the generated reports to SonarQube:

```properties
sonar.coverage.jacoco.xmlReportPaths=server/smp/build/reports/jacoco/test/jacocoTestReport.xml
sonar.javascript.lcov.reportPaths=apps/web/marketing/coverage/lcov.info
sonar.typescript.lcov.reportPaths=apps/web/marketing/coverage/lcov.info
```

## Troubleshooting

### Backend Coverage Not Showing

1. Verify XML report exists: `ls -la server/smp/build/reports/jacoco/test/jacocoTestReport.xml`
2. Check JaCoCo task ran: `./gradlew test jacocoTestReport --info`

### Frontend Coverage Not Showing

1. Verify LCOV report exists: `ls -la apps/web/marketing/coverage/lcov.info`
2. Check Vitest ran with coverage: `pnpm test:coverage --reporter=verbose`

## References

- [SonarQube Setup Guide](./sonarqube-setup.md)
- [SonarQube Official Documentation](https://docs.sonarsource.com/sonarqube-cloud/analyzing-source-code/test-coverage/overview)
- [Vitest Coverage Guide](https://vitest.dev/guide/coverage.html)
