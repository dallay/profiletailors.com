plugins {
    id("com.profiletailors.spring.boot.library")
}

group = "com.profiletailors"
version = "0.0.1-SNAPSHOT"

dependencies {
    implementation(project(":shared:common"))
    implementation(project(":shared:bus"))
    implementation(project(":shared:shield:ratelimit"))

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.reactive)
    implementation(libs.kotlinx.coroutines.reactor)

    implementation(libs.spring.boot.starter.webflux)
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")

    // AWS SDK v2
    implementation(libs.aws.s3)
    implementation(libs.kotlinx.coroutines.jdk8)

    // Micrometer for metrics
    implementation(libs.micrometer.prometheus)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.mockk)
    
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.localstack)
    
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
