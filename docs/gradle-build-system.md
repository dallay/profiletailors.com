# Gradle Build System & Conventions

**Date:** 2026-07-31
**Status:** ✅ Implemented

---

## 📖 Overview

The backend in this monorepo is managed by a centralized, root-level Gradle build system. This
architecture consolidates build scripts, Version Catalogs, and custom build-logic into the root of
the repository, enabling clean, boiler-free subprojects and consistent tool configurations across
all modules.

Inspired by robust multi-project composite builds, this architecture decouples general project
metadata from build-logic, wrapping all reusable configs in internal **Convention Plugins**.

---

## 🏗️ Folder Structure

All Gradle configurations and tools are grouped under the `/gradle` directory and the root
workspace:

```text
/
├── settings.gradle.kts                # Global Gradle settings & dynamic module discovery
├── detekt.yml                         # Unified linter rules for all Kotlin modules
├── gradlew & gradlew.bat              # Global execution wrappers
├── gradle/
│   ├── libs.versions.toml             # Unified Version Catalog
│   ├── wrapper/                       # Gradle wrapper binaries & properties
│   └── build-logic/                   # Composite build project containing our convention plugins
│       ├── settings.gradle.kts        # Registers build-logic convention modules
│       ├── build.gradle.kts           # Registers plugin IDs and implementation classes
│       └── src/main/kotlin/com/profiletailors/buildlogic/
│           ├── ConventionPlugin.kt    # Base plugin contract interface
│           ├── AppConfiguration.kt    # Toolchain target definitions (Java 21, Kotlin 2.3)
│           ├── extensions/            # Shared compiler extensions, task configurations
│           ├── library/               # com.profiletailors.kotlin.library (Base plugin)
│           └── springboot/            # com.profiletailors.spring.boot.library & .application
```

---

## 🔄 Dynamic Project Discovery

Rather than manually registering every single submodule in `/settings.gradle.kts`, the root settings
script dynamically walks through the `/server` and `/shared` directories to discover subprojects
containing a `build.gradle.kts` file:

```kotlin
// /settings.gradle.kts (Scan Loop)
val excludedProjects = listOf("build-logic", "wrapper", "build")
val scanDirectories = listOf("server", "shared")

scanDirectories.forEach { includeGradleProjectsRecursively(it) }
```

* **How exclusions are handled**: Projects inside folders defined in `excludedProjects` (like
  `build`) are recursively filtered out. This avoids accidentally treating generated output folders
  builds separate.
* **Hierarchical Naming**: Projects are registered using hierarchical paths matching their directory
  layouts. For example, `/shared/common` is resolved as `:shared:common`.

---

## 🧩 Plug-and-Play Convention Plugins

We provide three primary convention plugins that can be applied to subprojects to instantly set up
their compilers, frameworks, testing engines, and linters.

### 1. Kotlin Generic Library (`com.profiletailors.kotlin.library`)

Used for generic business logic packages that have no dependency on any framework (e.g.
`:shared:common`).

* **Configures:**
  * JVM toolchain targeting **Java 21** and **Kotlin 2.3**.
    * Dynamic compiler flags (e.g., `-Xcontext-receivers`).
    * Enforces code styles via `detekt` using the root `/detekt.yml`.
    * Configures standard JUnit Platform test task.

### 2. Spring Boot Shared Library (`com.profiletailors.spring.boot.library`)

Used for shared backend utilities, databases, and adapters (e.g., `:shared:spring-boot-common`,
`:shared:storage`).

* **Configures:**
    * Inherits and applies `com.profiletailors.kotlin.library` (toolchains, detekt, testing).
    * Applies `kotlin-spring` (all-open compiler plugin for Spring beans).
    * Applies `spring-dependency-management`.
    * Imports the **Spring Boot BOM** (`spring-boot-dependencies`) to guarantee version consistency.

### 3. Spring Boot Executable App (`com.profiletailors.spring.boot.application`)

Used exclusively for executable microservices or application servers (e.g., `:server:smp`).

* **Configures:**
    * Inherits and applies the JVM & Spring compilation engines.
    * Applies the `org.springframework.boot` application builder (`bootJar`, `bootRun`).
    * Configures **Jacoco Code Coverage** with strict exclusions (DTOs, Configurations, main
      classes) and enforces an **80% minimum coverage** requirement.
    * Registers specific BDD test tasks:
        * `bddFastTest`: Fast BDD suite for local and CI validation.
        * `bddPostgresTest`: Full integration suite running over PostgreSQL using Testcontainers.

---

## 💻 Developer Commands

Run all tasks from the monorepo root:

| Command                                 | Action                                      |
| :-------------------------------------- | :------------------------------------------ |
| `./gradlew projects`                    | Inspect the loaded subproject tree          |
| `./gradlew build`                       | Build and compile all projects              |
| `./gradlew test`                        | Execute the unit and integration test suite |
| `./gradlew :server:smp:bootRun`         | Run the backend SMP server locally          |
| `./gradlew :server:smp:bddFastTest`     | Run the fast BDD suite                      |
| `./gradlew :server:smp:bddPostgresTest` | Run the PostgreSQL Testcontainers BDD suite |
| `./gradlew detekt`                      | Run static code analysis across all modules |

---

## ➕ Adding a New Module

To plug a new module into the backend workspace:

1. Create the folder under `/shared` or `/server` (e.g., `/shared/my-new-lib`).
2. Add a `build.gradle.kts` file.
3. Apply one of the three convention plugins:
   ```kotlin
   plugins {
       id("com.profiletailors.spring.boot.library") // Or the application or kotlin plugin
   }
   
   dependencies {
       // Declared without version tags (resolved by version catalog & Spring Boot BOM)
       implementation(libs.jackson.module.kotlin)
       implementation(project(":shared:common"))
   }
   ```
4. Run `./gradlew projects` from the root. The dynamic scanning engine will automatically detect and
   register your module!

---

## ⚠️ Known Issues & Troubleshooting

### Configuration Cache Incompatibility with License Report Plugin

**Issue:** The `com.github.jk1:gradle-license-report` plugin (v3.1.4) is not compatible with Gradle's configuration cache feature due to Task.project access at execution time.

**Symptoms:**
```
17 problems were found storing the configuration cache...
- Plugin 'org.springframework.boot': execution of task ':server:smp:generateLicenseReport' 
  caused invocation of 'Task.project' in other task at execution time...
```

**Workaround:** Run the license report generation with configuration cache disabled:

```bash
./gradlew generateLicenseReport --no-configuration-cache
```

Or use the `just` helper:
```bash
just backend-licence-check --no-configuration-cache
```

**Status:** This is a known upstream issue with the license report plugin. The build remains functional, but configuration cache benefits are not available for this specific task. All other tasks in the build continue to use configuration cache normally.

**Tracking:** Monitor [jk1/Gradle-License-Report](https://github.com/jk1/Gradle-License-Report) for updates on configuration cache support.
