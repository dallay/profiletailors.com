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

**Coverage Tool**: Kover (JaCoCo-compatible XML output)

Backend coverage reports are generated from the Gradle Kover plugin and exported as XML files
that SonarQube can ingest via the JaCoCo XML import property.

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
./gradlew :server:smp:test :server:smp:koverXmlReport :shared:common:test :shared:common:koverXmlReport :shared:bus:test :shared:bus:koverXmlReport :shared:presentation:test :shared:presentation:koverXmlReport :shared:security:test :shared:security:koverXmlReport :shared:spring-boot-common:test :shared:spring-boot-common:koverXmlReport :shared:storage:test :shared:storage:koverXmlReport :shared:shield:ratelimit:test :shared:shield:ratelimit:koverXmlReport --no-daemon -PexcludeTags=modularity,postgres
```

**Frontend**:

```bash
cd apps/web/marketing && pnpm test:coverage
```

### SonarQube Properties

The following properties in `sonar-project.properties` link the generated reports to SonarQube:

```properties
sonar.coverage.jacoco.xmlReportPaths=server/smp/build/reports/kover/report.xml,shared/common/build/reports/kover/report.xml,shared/bus/build/reports/kover/report.xml,shared/presentation/build/reports/kover/report.xml,shared/security/build/reports/kover/report.xml,shared/spring-boot-common/build/reports/kover/report.xml,shared/storage/build/reports/kover/report.xml,shared/shield/ratelimit/build/reports/kover/report.xml
sonar.javascript.lcov.reportPaths=apps/web/marketing/coverage/lcov.info,apps/web/app/coverage/lcov.info
sonar.typescript.lcov.reportPaths=apps/web/marketing/coverage/lcov.info,apps/web/app/coverage/lcov.info
```

## Troubleshooting

### Backend Coverage Not Showing

1. Verify XML report exists: `ls -la server/smp/build/reports/kover/report.xml`
2. Check Kover task ran: `./gradlew :server:smp:koverXmlReport --info`

### Frontend Coverage Not Showing

1. Verify LCOV report exists: `ls -la apps/web/marketing/coverage/lcov.info`
2. Check Vitest ran with coverage: `pnpm test:coverage --reporter=verbose`

## References

- [SonarQube Setup Guide](./sonarqube-setup.md)
- [SonarQube Official Documentation](https://docs.sonarsource.com/sonarqube-cloud/analyzing-source-code/test-coverage/overview)
- [Vitest Coverage Guide](https://vitest.dev/guide/coverage.html)
