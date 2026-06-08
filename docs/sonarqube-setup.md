---
date: 2026-05-25
status: ✅ Completed
---

# SonarQube Setup Guide

## Overview

This guide provides step-by-step instructions to configure SonarQube or SonarCloud for the Profile
Tailors project. It covers project creation, GitHub secret configuration, and verification of the
scanning setup.

## Prerequisites

- GitHub repository with admin access.
- SonarCloud account or a self-hosted SonarQube instance.

## Usage

### Step 1: Create SonarCloud Project

1. Go to [SonarCloud](https://sonarcloud.io) and sign in with GitHub.
2. Click **"+"** → **"Analyze new project"**.
3. Select the `profiletailors.com` repository.
4. Choose **"With GitHub Actions"** as the analysis method.

### Step 2: Configure GitHub Secrets

Add the following secrets in **Settings → Secrets and variables → Actions**:

```env
SONAR_TOKEN=<your-sonarcloud-token>
SONAR_HOST_URL=https://sonarcloud.io
SONAR_PROJECT_KEY=<your-org>_profiletailors.com
SONAR_ORGANIZATION=<your-sonarcloud-org>
```

### Step 3: Update sonar-project.properties

Replace placeholder values in `sonar-project.properties` at the root of the repository:

```properties
sonar.projectKey=<your-actual-project-key>
sonar.organization=<your-sonarcloud-org>
```

### Step 4: Verify Configuration

Run tests locally to ensure reports are generated correctly:

**Backend:**

```bash
cd server/smp && ./gradlew test jacocoTestReport
```

**Frontend:**

```bash
cd apps/web/marketing && pnpm install && pnpm test:coverage
```

## Troubleshooting

### "Project key not found"

- Verify `SONAR_PROJECT_KEY` secret matches the key in SonarCloud/SonarQube.
- Update `sonar-project.properties` with the correct key.

### "Coverage report not found"

- Check workflow logs for test execution errors.
- Verify report paths in `sonar-project.properties`.

### "Authentication failed"

- Regenerate `SONAR_TOKEN` in SonarCloud/SonarQube.
- Update the GitHub secret with the new token.

## References

- [SonarCloud Documentation](https://docs.sonarsource.com/sonarcloud/)
- [SonarQube Documentation](https://docs.sonarsource.com/sonarqube/)
- [Test Coverage Guide](./sonarqube-coverage.md)
