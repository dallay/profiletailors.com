# SonarQube Setup Guide

Quick guide to configure SonarQube/SonarCloud for the Profile Tailors project.

## Prerequisites

- GitHub repository with admin access
- SonarCloud account (or self-hosted SonarQube instance)

## Step 1: Create SonarCloud Project

### Option A: SonarCloud (Recommended)

1. Go to [SonarCloud](https://sonarcloud.io)
2. Sign in with GitHub
3. Click **"+"** → **"Analyze new project"**
4. Select `profiletailors.com` repository
5. Choose **"With GitHub Actions"** as analysis method

### Option B: Self-Hosted SonarQube

1. Access your SonarQube instance
2. Create new project
3. Generate project token
4. Note the project key and host URL

## Step 2: Configure GitHub Secrets

Add these secrets in **Settings → Secrets and variables → Actions**:

### For SonarCloud:

```
SONAR_TOKEN=<your-sonarcloud-token>
SONAR_HOST_URL=https://sonarcloud.io
SONAR_PROJECT_KEY=<your-org>_profiletailors.com
SONAR_ORGANIZATION=<your-sonarcloud-org>
```

### For Self-Hosted:

```
SONAR_TOKEN=<your-sonarqube-token>
SONAR_HOST_URL=https://your-sonarqube-instance.com
SONAR_PROJECT_KEY=profiletailors-com
```

## Step 3: Update sonar-project.properties

Replace placeholder values in `sonar-project.properties`:

```properties
sonar.projectKey=<your-actual-project-key>
sonar.organization=<your-sonarcloud-org>  # Only for SonarCloud
```

## Step 4: Verify Configuration

### Local Verification (Optional)

**Backend coverage:**
```bash
cd server/smp
./gradlew test jacocoTestReport
ls -la build/reports/jacoco/test/jacocoTestReport.xml
```

**Frontend coverage:**
```bash
cd apps/web/marketing
pnpm install
pnpm test:coverage
ls -la coverage/lcov.info
```

### CI Verification

1. Push changes to a branch
2. Create a pull request
3. Check GitHub Actions → **"SonarQube Analysis"** workflow
4. Verify both coverage reports are generated
5. Check SonarQube dashboard for coverage metrics

## Step 5: Configure Quality Gate (Optional)

In SonarCloud/SonarQube:

1. Go to **Project Settings → Quality Gate**
2. Create or select a quality gate
3. Recommended conditions:
   - Coverage on New Code: **≥ 80%**
   - Duplicated Lines on New Code: **≤ 3%**
   - Maintainability Rating on New Code: **≤ A**
   - Reliability Rating on New Code: **≤ A**
   - Security Rating on New Code: **≤ A**

## Expected Coverage Metrics

### Backend (Kotlin/Spring Boot)
- **Target**: 80% minimum
- **Report**: JaCoCo XML
- **Location**: `server/smp/build/reports/jacoco/test/jacocoTestReport.xml`

### Frontend (TypeScript/Astro)
- **Target**: 80% minimum (lines, functions, branches, statements)
- **Report**: LCOV
- **Location**: `apps/web/marketing/coverage/lcov.info`

## Troubleshooting

### "Project key not found"
- Verify `SONAR_PROJECT_KEY` secret matches the key in SonarCloud/SonarQube
- Update `sonar-project.properties` with correct key

### "Coverage report not found"
- Check workflow logs for test execution errors
- Verify report paths in `sonar-project.properties`
- Run tests locally to confirm reports are generated

### "Quality gate failed"
- Review SonarQube dashboard for specific failures
- Check coverage percentage for new code
- Review code smells, bugs, and vulnerabilities

### "Authentication failed"
- Regenerate `SONAR_TOKEN` in SonarCloud/SonarQube
- Update GitHub secret with new token
- Verify token has analysis permissions

## Viewing Results

### SonarCloud Dashboard
- **URL**: `https://sonarcloud.io/project/overview?id=<your-project-key>`
- **Metrics**: Coverage, bugs, vulnerabilities, code smells, duplications
- **Trends**: Historical data and quality gate status

### GitHub Pull Requests
- Quality gate status appears as a check
- Coverage diff shows coverage change for new code
- Click "Details" to view full SonarCloud analysis

## Maintenance

### Updating Coverage Thresholds

**Backend** (`server/smp/build.gradle.kts`):
```kotlin
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.85".toBigDecimal()  // Increase to 85%
            }
        }
    }
}
```

**Frontend** (`apps/web/marketing/vitest.config.ts`):
```typescript
coverage: {
  lines: 85,      // Increase to 85%
  functions: 85,
  branches: 85,
  statements: 85,
}
```

### Excluding Files from Analysis

Add patterns to `sonar-project.properties`:

```properties
sonar.exclusions=**/generated/**,**/legacy/**
sonar.coverage.exclusions=**/test-utils/**,**/*.mock.ts
```

## Next Steps

1. ✅ Configure GitHub secrets
2. ✅ Update `sonar-project.properties`
3. ✅ Push changes and create PR
4. ✅ Verify workflow runs successfully
5. ✅ Check SonarCloud dashboard
6. ✅ Configure quality gate (optional)
7. ✅ Add SonarCloud badge to README (optional)

## Support

- [SonarCloud Documentation](https://docs.sonarsource.com/sonarcloud/)
- [SonarQube Documentation](https://docs.sonarsource.com/sonarqube/)
- [Test Coverage Guide](https://docs.sonarsource.com/sonarqube-cloud/analyzing-source-code/test-coverage/overview)
