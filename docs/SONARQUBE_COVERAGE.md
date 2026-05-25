# SonarQube Configuration for Profile Tailors

This document explains the test coverage configuration for SonarQube analysis.

## Overview

The project uses SonarQube to track code quality and test coverage across:
- **Backend**: Kotlin/Spring Boot with JaCoCo
- **Frontend**: TypeScript/Astro with Vitest + v8 coverage

## Backend Coverage (Kotlin)

### Configuration

JaCoCo is configured in `server/smp/build.gradle.kts`:

```kotlin
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)  // Required for SonarQube
        html.required.set(true)
        csv.required.set(false)
    }
}
```

### Coverage Report Location

- **XML Report**: `server/smp/build/reports/jacoco/test/jacocoTestReport.xml`
- **HTML Report**: `server/smp/build/reports/jacoco/test/html/index.html`

### Running Backend Tests with Coverage

```bash
cd server/smp
./gradlew test jacocoTestReport
```

### Exclusions

The following are excluded from coverage:
- `**/config/**` - Configuration classes
- `**/dto/**` - Data transfer objects
- `**/entity/**` - JPA entities
- `**/Application.kt` - Main application class

## Frontend Coverage (TypeScript/Astro)

### Configuration

Vitest with v8 coverage provider is configured in `apps/web/marketing/vitest.config.ts`:

```typescript
coverage: {
  provider: 'v8',
  reporter: ['text', 'json', 'html', 'lcov'],
  reportsDirectory: './coverage',
  lines: 80,
  functions: 80,
  branches: 80,
  statements: 80,
}
```

### Coverage Report Location

- **LCOV Report**: `apps/web/marketing/coverage/lcov.info`
- **HTML Report**: `apps/web/marketing/coverage/index.html`

### Running Frontend Tests with Coverage

```bash
cd apps/web/marketing
pnpm test:coverage
```

### Exclusions

The following are excluded from coverage:
- `node_modules/**`
- `dist/**`
- `.astro/**`
- `**/*.config.{js,ts}`
- `**/env.d.ts`

## SonarQube Configuration

### sonar-project.properties

Key coverage properties:

```properties
# Coverage report paths
sonar.coverage.jacoco.xmlReportPaths=server/smp/build/reports/jacoco/test/jacocoTestReport.xml
sonar.javascript.lcov.reportPaths=apps/web/marketing/coverage/lcov.info
sonar.typescript.lcov.reportPaths=apps/web/marketing/coverage/lcov.info

# Source directories
sonar.sources=server/smp/src/main,shared,apps/web/marketing/src
sonar.tests=server/smp/src/test,apps/web/marketing/src
```

## CI/CD Integration

### GitHub Actions Workflow

The `.github/workflows/sonarqube.yml` workflow:

1. **Backend**:
   - Sets up JDK 21
   - Runs tests with `./gradlew test jacocoTestReport`
   - Verifies XML report exists

2. **Frontend**:
   - Sets up Node.js 22 and pnpm
   - Installs dependencies
   - Runs tests with `pnpm test:coverage`
   - Verifies LCOV report exists

3. **SonarQube**:
   - Runs SonarQube scanner
   - Checks quality gate
   - Uploads coverage artifacts

### Required Secrets

Configure these in GitHub repository settings:

- `SONAR_TOKEN` - SonarQube authentication token
- `SONAR_HOST_URL` - SonarQube server URL (e.g., `https://sonarcloud.io`)
- `SONAR_PROJECT_KEY` - Your project key
- `SONAR_ORGANIZATION` - Your SonarCloud organization (if using SonarCloud)

## Local Development

### View Coverage Reports Locally

**Backend**:
```bash
cd server/smp
./gradlew test jacocoTestReport
open build/reports/jacoco/test/html/index.html
```

**Frontend**:
```bash
cd apps/web/marketing
pnpm test:coverage
open coverage/index.html
```

### Run SonarQube Scanner Locally

```bash
# Install SonarQube scanner
brew install sonar-scanner  # macOS

# Run analysis (requires SONAR_TOKEN)
sonar-scanner \
  -Dsonar.projectKey=your-project-key \
  -Dsonar.organization=your-org \
  -Dsonar.host.url=https://sonarcloud.io \
  -Dsonar.token=$SONAR_TOKEN
```

## Coverage Thresholds

### Backend (JaCoCo)

Minimum coverage: **80%**

```kotlin
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}
```

### Frontend (Vitest)

Minimum coverage: **80%** for all metrics:
- Lines
- Functions
- Branches
- Statements

## Troubleshooting

### Backend Coverage Not Showing

1. Verify XML report exists:
   ```bash
   ls -la server/smp/build/reports/jacoco/test/jacocoTestReport.xml
   ```

2. Check JaCoCo task ran:
   ```bash
   ./gradlew test jacocoTestReport --info
   ```

### Frontend Coverage Not Showing

1. Verify LCOV report exists:
   ```bash
   ls -la apps/web/marketing/coverage/lcov.info
   ```

2. Check Vitest ran with coverage:
   ```bash
   pnpm test:coverage --reporter=verbose
   ```

### SonarQube Not Picking Up Coverage

1. Check file paths in `sonar-project.properties` are correct
2. Verify reports are generated before SonarQube scan runs
3. Check SonarQube logs for coverage import errors
4. Ensure `fetch-depth: 0` in checkout action for accurate blame information

## References

- [SonarQube Test Coverage](https://docs.sonarsource.com/sonarqube-cloud/analyzing-source-code/test-coverage/overview)
- [JaCoCo Documentation](https://www.jacoco.org/jacoco/trunk/doc/)
- [Vitest Coverage](https://vitest.dev/guide/coverage.html)
