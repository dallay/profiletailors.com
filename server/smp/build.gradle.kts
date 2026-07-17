plugins {
    id("com.profiletailors.spring.boot.application")
}

group = "com.profiletailors"
version = "0.0.1-SNAPSHOT"

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

dependencies {
    implementation(project(":shared:common"))
    implementation(project(":shared:bus"))
    implementation(project(":shared:security"))
    implementation(project(":shared:presentation"))
    implementation(project(":shared:spring-boot-common"))
    implementation(project(":shared:storage"))
    implementation(project(":shared:lead-capture:common"))
    implementation(project(":shared:lead-capture:waitlist"))

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
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.2")
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
