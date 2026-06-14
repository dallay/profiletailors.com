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
        envFile.asFile.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") && '=' in it }
            .map { line ->
                val (key, value) = line.split("=", limit = 2)
                key.trim() to value.trim()
            }
            .filter { (_, value) -> value.isNotBlank() }
            .forEach { (key, value) ->
                environment(key, value)
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

    implementation(libs.spring.boot.starter.data.r2dbc)
    implementation(libs.spring.boot.starter.liquibase)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.webflux)
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
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.micrometer.prometheus)

    developmentOnly(libs.spring.boot.devtools)
    developmentOnly(libs.spring.boot.docker.compose)

    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.r2dbc.postgresql)

    testImplementation(libs.r2dbc.postgresql)
    testImplementation(testFixtures(project(":shared:common")))
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation(libs.h2)
    testImplementation(libs.r2dbc.h2)
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
    
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)

    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.cucumber.junit.platform.engine)
}

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
        mavenBom(libs.spring.modulith.bom.get().toString())
    }
}
