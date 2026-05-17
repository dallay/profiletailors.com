# Detekt Integration Design

## 1. Overview

Integrate Detekt (static analysis for Kotlin) into the monorepo to improve code quality and enforce consistent coding standards across all Kotlin modules.

## 2. Scope

- **Modules**: smp, shared-common, shared-spring-boot-common
- **Build Tool**: Gradle with Kotlin DSL
- **Detekt Version**: 1.23.8 (stable, compatible with Kotlin 2.2.21)

## 3. Integration Levels

### 3.1 Local Execution
- Task: `./gradlew detekt` for manual execution
- Output: Console report with findings

### 3.2 Build Integration
- Attach to `check` task: `tasks.check { dependsOn("detekt") }`
- Fails build on errors (severity: error)

### 3.3 CI/CD Integration
- Generate HTML report: `./gradlew detektReport`
- Generate XML report: `./gradlew detektXmlReport`
- GitHub Actions workflow for PR/push triggers

## 4. Initial Rule Set (Baseline Profile)

### Style Rules (~40 rules)
- Naming conventions (class, function, property)
- Import ordering
- Comment formatting
- Code formatting (indentation, braces)

### Performance Rules (~20 rules)
- Avoid unnecessary object creation
- Lazy properties
- Collection operations

### Best Practices (~30 rules)
- Missing else in when
- Unused parameters
- Empty functions
- Throws clauses

### Excluded Initially
- Complexity rules (too strict for initial rollout)
- Documentation rules (optional)
- Exception rules (review later)

## 5. Configuration Files

### Directory Structure
```
server/smp/
├── build.gradle.kts        # add detekt plugin
├── detekt.yml              # main config
├── config/detekt/
│   ├── baseline.xml        # whitelist existing issues
└── .github/workflows/
    └── detekt.yml          # GitHub Action
```

### Gradle Tasks
- `./gradlew detekt` - basic analysis
- `./gradlew detektMain` - production code only
- `./gradlew detektTest` - test code only
- `./gradlew detektBaseline` - create baseline
- `./gradlew detektReport` - HTML report

## 6. GitHub Actions

```yaml
name: Detekt
on: [pull_request, push]
jobs:
  detekt:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: java-version: 24
      - run: ./gradlew detekt
      - uses: actions/upload-artifact@v4
        with:
          name: detekt-report
          path: build/reports/detekt/
```

## 7. Baseline Strategy

1. Run initial detekt scan
2. Generate baseline.xml with current issues
3. Commit baseline to suppress existing technical debt
4. Enforce no new issues in future builds
5. Plan incremental cleanup in future iterations

## 8. Severity Levels

- **Errors**: Build fails (blocking)
- **Warnings**: Logged but doesn't fail build (initially)
- **Info**: Advisory only

Future iteration: enable warnings-as-errors after baseline stabilizes.