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
                environment(key, value)
            }
    }

    // In CI (where PostgreSQL is already running as a service container),
    // disable Spring Boot Docker Compose integration to avoid port conflicts.
    // Uses a JVM system property so Spring Boot reads it during startup.
    if (providers.environmentVariable("SPRING_DOCKER_COMPOSE_ENABLED").orNull == "false") {
        jvmArgs("-Dspring.docker.compose.enabled=false")
    }
}

val postgresTestPassword =
    providers.environmentVariable("SMP_DB_TEST_PASSWORD").orElse(
        providers.fileContents(rootProject.layout.projectDirectory.file(".env")).asText.map { contents ->
            contents
                .lineSequence()
                .firstOrNull { it.startsWith("SMP_DB_TEST_PASSWORD=") }
                ?.substringAfter('=')
                ?.trim()
                .orEmpty()
        },
    )

tasks.withType<Test>().configureEach {
    // Increase heap for integration tests that load full Spring contexts with Testcontainers.
    // Default 512m is insufficient for tests like PublishingWorkerTransactionPostgresIntegrationTest.
    maxHeapSize = "2g"
    jvmArgs("-XX:MaxMetaspaceSize=512m")
    // Expose the monorepo root so file-system tests (runbook, migration contract) find
    // project artefacts reliably in both local and CI environments.
    systemProperty("project.root", rootProject.projectDir.absolutePath)
}

// SMP_DB_TEST_PASSWORD is needed only by PostgreSQL tests (registered via build-logic's afterEvaluate);
// keep it off unit-test JVMs. These task names are not known until after project evaluation.
afterEvaluate {
    postgresTestPassword.orNull?.takeIf { it.isNotBlank() }?.let { password ->
        listOf("postgresIntegrationTest", "bddPostgresTest").forEach { name ->
            tasks.named<Test>(name) { environment("SMP_DB_TEST_PASSWORD", password) }
        }
    }
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
    implementation(libs.jackson.module.kotlin)
    // Jackson 2.x compat — PlatformBootstrapConfiguration uses kotlinModule() from the 2.x line
    @Suppress("GradleDependency")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.22.1")
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

    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)

    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.cucumber.junit.platform.engine)

    constraints {
        implementation(libs.okio.jvm)
        implementation(libs.bouncycastle.prov)
        implementation(libs.bouncycastle.pgp)
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
    }
}
