# Test Tags and Environment Variables

This document explains how to run the full backend test suite locally, which test tags exist, and
how to use them correctly without hiding failures.

## Overview

Profile Tailors backend tests run with **no exclusions by default** in CI and local workflows. All
test tags serve as **defense mechanisms** for specific infrastructure requirements, but they do not
hide failures — tests must pass unconditionally before merge.

## Required Environment Variables

### `SMP_DB_TEST_PASSWORD`

**Required for:** Testcontainers-backed PostgreSQL integration tests.

**Setup:**

1. Copy `.env.example` → `.env` at the project root
2. Set `SMP_DB_TEST_PASSWORD` to any non-empty value (e.g., the same as `SMP_POSTGRES_PASSWORD`)

**Example:**

```bash
SMP_DB_TEST_PASSWORD=CHANGE_ME_gK2fcFZg5cgVu9U
```

The `bootRun` and test tasks automatically load this variable from the root `.env` file.

## Running Tests Locally

### Fast Unit Tests (No BDD)

```bash
just backend-test-fast
```

Excludes BDD scenarios but runs all unit and integration tests, including Testcontainers-backed
PostgreSQL tests.

### Full CI Suite (Including BDD)

```bash
# First, start infrastructure
just infra-up

# Then run the full suite
just ci-full
```

Runs everything CI runs: unit tests, integration tests, BDD scenarios, and Postgres-backed tests.

### Selective Test Execution

```bash
# Run a specific test class
./gradlew :server:smp:test --tests "com.profiletailors.smp.ModularStructureTest"

# Exclude a tag (for local experimentation only — not used in CI)
just backend-test exclude-tags=slowtest
```

**Important:** Tag exclusions are available for local debugging but **must never be used to hide
failures** in CI or pre-push hooks.

## Test Tags Reference

### `@Tag("postgres")`

**Purpose:** Marks tests that require a running PostgreSQL database via Testcontainers.

**When to use:** Integration tests that exercise R2DBC repositories, Liquibase migrations, or
database-backed features.

**CI behavior:** These tests **run by default** in CI. No exclusions.

**Local requirement:** `SMP_DB_TEST_PASSWORD` must be set in `.env`.

### `@Tag("modularity")` (RESOLVED)

**Historical note:** This tag was used to exclude `ModularStructureTest` when it failed due to
Spring Modulith named-interface issues. The root cause was fixed
in [#275](https://github.com/dallay/profiletailors.com/issues/275), and the exclusion was removed.
The tag may still exist in test code but is no longer excluded anywhere.

**Current status:** No active exclusions for this tag.

### `@Tag("bdd")`

**Purpose:** Marks Cucumber BDD scenario tests.

**When to use:** Behavior-driven tests written in Gherkin that verify end-to-end flows.

**CI behavior:** Runs in `just ci-full` and full CI pipelines.

**Local shortcuts:** Use `just backend-test-fast` to skip BDD for faster iteration during unit work.

## Adding New Test Tags

When adding a new test tag:

1. **Document the purpose** in this file under "Test Tags Reference"
2. **Never exclude it in CI** unless there is a concrete infrastructure blocker (e.g., external API
   not available in CI)
3. **If you must exclude temporarily**, create a tracking issue immediately and link it in
   `AGENTS.md`
4. **Remove the exclusion as soon as possible** — exclusions are a code smell

### Example: Adding a `@Tag("external-api")`

```kotlin
@Tag("external-api")
@Test
fun `should fetch data from LinkedIn API`() {
    // Test that requires LinkedIn API credentials
}
```

**Decision flow:**

- Can this test be mocked or use a local fake? → **Prefer that**
- Must it call the real API? → **Add the tag and document it here**
- Should it run in CI? → **Yes, unless the credentials are unavailable in CI**
- If excluded from CI, create an issue to track re-enabling it

## Changes

- **2026-07-12:** Created initial version documenting no-exclusions posture after #275 fix and
  storage refactor
- **2026-07-12:** Confirmed `postgres` tag is no longer excluded in CI

## Troubleshooting

### "Testcontainers could not start PostgreSQL container"

**Cause:** Docker is not running, or `SMP_DB_TEST_PASSWORD` is missing.

**Fix:**

1. Start Docker Desktop or Docker daemon
2. Verify `.env` contains `SMP_DB_TEST_PASSWORD`

### "Test passed locally but failed in CI"

**Cause:** Local environment has different exclusions or missing infrastructure setup.

**Fix:**

1. Run `just ci-local` or `just ci-full` to simulate CI exactly
2. Check that your `.env` is not overriding test behavior
3. Verify no tag exclusions are set in your local Gradle invocation

### "Spring Modulith named-interface error"

**Cause:** This was a historical issue (#275) where `ModularStructureTest` failed due to missing
`@NamedInterface` declarations.

**Status:** **RESOLVED** — If you encounter this, it is a new regression. Report it immediately.

## References

- **Issue #275:
  ** [Fix pre-existing failure: ModularStructureTest (Spring Modulith named-interface)](https://github.com/dallay/profiletailors.com/issues/275) —
  **CLOSED**
- **PR (storage refactor):** Removed `postgres` exclusions and established no-exclusions posture
- **AGENTS.md:** Contains test command reference and backend architecture rules
- **.env.example:** Canonical template for all environment variables
- **Justfile:** Command hub for all test recipes (`backend-test-fast`, `ci-full`, etc.)
