import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
    id("com.profiletailors.spring.boot.application")
    id("com.profiletailors.legal.licence-report")
}

group = "com.profiletailors"
val defaultVersion = "0.0.1-SNAPSHOT"
version = providers.gradleProperty("releaseVersion").getOrElse(defaultVersion)

tasks.named<BootBuildImage>("bootBuildImage") {
    builder.set(
        "paketobuildpacks/builder-noble-java-tiny@sha256:" +
            "c320b12e4d7c9097834090b3e7420e0dd606ac3f55288418ced6b93e348a78cf",
    )
    runImage.set(
        "paketobuildpacks/ubuntu-noble-run-tiny@sha256:" +
            "ce96dbed676cede92b8c043bf6892edfcf6c96e825437fdd92a179ffa66fad5e",
    )
}

// ── .env loader for local development ────────────────────────────────────────
// Reads the root .env (linked via bin/setup-env.sh) and exports each variable
// to the forked bootRun JVM so Spring Boot picks them up as environment vars.
// This works for both CLI (./gradlew bootRun) and IntelliJ Gradle runner.
tasks.bootRun {
    val envFile = layout.projectDirectory.file(".env")
    if (envFile.asFile.exists()) {
        envFile.asFile
            .readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") && '=' in it }
            .map { line ->
                val (key, value) = line.split("=", limit = 2)
                key.trim() to value.trim()
            }.filter { (_, value) -> value.isNotBlank() }
            .forEach { (key, value) ->
                if (providers.environmentVariable(key).orNull == null) environment(key, value)
            }
    }

    // In CI (where PostgreSQL is already running as a service container),
    // disable Spring Boot Docker Compose integration to avoid port conflicts.
    // Uses a JVM system property so Spring Boot reads it during startup.
    if (providers.environmentVariable("SPRING_DOCKER_COMPOSE_ENABLED").orNull == "false") {
        jvmArgs("-Dspring.docker.compose.enabled=false")
    }
}

tasks.withType<Test>().configureEach {
    // Increase heap for integration tests that load full Spring contexts with Testcontainers.
    // Default 512m is insufficient for tests like PublishingWorkerTransactionPostgresIntegrationTest.
    maxHeapSize = "2g"
    jvmArgs("-XX:MaxMetaspaceSize=512m")
    // Expose the monorepo root so file-system tests (runbook, migration contract) find
    // project artefacts reliably in both local and CI environments.
    systemProperty("project.root", rootProject.projectDir.absolutePath)
}

dependencies {
    implementation(project(":shared:common"))
    implementation(project(":shared:bus"))
    implementation(project(":shared:security"))
    implementation(project(":shared:presentation"))
    implementation(project(":shared:spring-boot-common"))
    implementation(project(":shared:storage"))
    implementation(project(":shared:lead-capture:common"))
    implementation(project(":shared:lead-capture:waitlist"))
    implementation(project(":shared:notifications"))
    implementation(project(":shared:shield:ratelimit"))

    implementation(libs.spring.boot.starter.data.r2dbc)
    implementation(libs.spring.boot.starter.liquibase)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.validation)
    implementation("org.springframework.security:spring-security-crypto")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.reactor)
    implementation(libs.springdoc.openapi.webflux)
    implementation(libs.spring.modulith.starter.core)
    implementation(libs.spring.ai.starter.mcp.server.webflux)
    implementation(libs.jackson.module.kotlin)
    // Jackson 2.x compat — PlatformBootstrapConfiguration uses kotlinModule() from the 2.x line
    @Suppress("GradleDependency")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.22.2")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation(libs.resend.java)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.micrometer.prometheus)

    developmentOnly(libs.spring.boot.devtools)
    developmentOnly(libs.spring.boot.docker.compose)

    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.r2dbc.postgresql)

    testImplementation(libs.r2dbc.postgresql)
    testImplementation(testFixtures(project(":shared:common")))
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-data-r2dbc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.junit.platform.suite)

    testImplementation(libs.cucumber.java)
    testImplementation(libs.cucumber.spring)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.mockk)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.konsist)

    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)

    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.cucumber.junit.platform.engine)

    constraints {
        // BOMs do not manage these — plain constraints win.
        implementation(libs.okio.jvm)
        implementation(libs.bouncycastle.prov)
        implementation(libs.bouncycastle.pgp)
        implementation("com.ongres.scram:scram-client:3.4")
        implementation("com.ongres.scram:scram-common:3.4")
    }
}

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
        mavenBom(
            libs.spring.modulith.bom
                .get()
                .toString(),
        )
        mavenBom(
            libs.spring.ai.bom
                .get()
                .toString(),
        )
    }
}

// Security overrides — Spring Boot BOM lags behind patched releases. The BOM
// dependency-management plugin applies its managed versions AFTER Gradle
// constraints, so plain/strict constraints cannot override it. Configuration-level
// resolution rules (eachDependency) run at the end of resolution and always win.
// `verifySecurityVersions` fails the build if any patched dependency ever resolves
// below its patched version.
configurations.all {
    resolutionStrategy {
        eachDependency {
            if (requested.group == "io.netty" && requested.name.startsWith("netty-codec")) {
                useVersion("4.2.16.Final")
            }
            if (requested.group.startsWith("com.fasterxml.jackson")) {
                useVersion(
                    when (requested.name) {
                        "jackson-annotations" -> "2.22"
                        else -> "2.22.1"
                    },
                )
            }
            if (requested.group == "tools.jackson.core") {
                useVersion("3.1.5")
            }
            if (requested.group == "org.postgresql" && requested.name == "postgresql") {
                useVersion("42.7.12")
            }
        }
    }
}
val verifySecurityVersions =
    tasks.register("verifySecurityVersions") {
        val classpathProvider = configurations.named("runtimeClasspath")
        // Resolving through a Provider turns the classpath into a task input, so the
        // graph wires the compile dependencies and config cache stays valid.
        val resolvedVersions: Provider<Map<String, String>> =
            classpathProvider.map { configuration ->
                configuration.incoming.resolutionResult.allComponents
                    .mapNotNull { it.id as? ModuleComponentIdentifier }
                    .associate { "${it.group}:${it.module}" to it.version }
            }
        inputs.property("resolvedVersions", resolvedVersions)
        doLast {
            val expected =
                mapOf(
                    "com.ongres.scram:scram-client" to "3.4",
                    "com.ongres.scram:scram-common" to "3.4",
                    "io.netty:netty-codec-http" to "4.2.16.Final",
                    "io.netty:netty-codec-http2" to "4.2.16.Final",
                    "io.netty:netty-codec-http3" to "4.2.16.Final",
                    "io.netty:netty-codec-dns" to "4.2.16.Final",
                    "io.netty:netty-codec-compression" to "4.2.16.Final",
                    "com.fasterxml.jackson:jackson-bom" to "2.22.1",
                    "com.fasterxml.jackson.core:jackson-annotations" to "2.22",
                    "com.fasterxml.jackson.core:jackson-core" to "2.22.1",
                    "com.fasterxml.jackson.core:jackson-databind" to "2.22.1",
                    "com.fasterxml.jackson.datatype:jackson-datatype-jsr310" to "2.22.1",
                    "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml" to "2.22.1",
                    "com.fasterxml.jackson.module:jackson-module-kotlin" to "2.22.1",
                    "tools.jackson.core:jackson-core" to "3.1.5",
                    "tools.jackson.core:jackson-databind" to "3.1.5",
                    "org.postgresql:postgresql" to "42.7.12",
                )
            val violations =
                expected.filterNot { (coordinate, version) ->
                    resolvedVersions.get()[coordinate] == version
                }
            if (violations.isNotEmpty()) {
                throw GradleException(
                    "Security version check failed — expected patched versions not resolved:\n" +
                        violations.entries.joinToString("\n") { (coordinate, version) ->
                            "  $coordinate expected $version, resolved ${resolvedVersions.get()[coordinate] ?: "MISSING"}"
                        } +
                        "\nRun: ./gradlew :server:smp:dependencies --configuration runtimeClasspath",
                )
            }
        }
    }
tasks.named("check") { dependsOn(verifySecurityVersions) }
