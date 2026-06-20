plugins {
    id("com.profiletailors.spring.boot.library")
}

group = "com.profiletailors"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":shared:common"))
    implementation(project(":shared:bus"))
    implementation(project(":shared:spring-boot-common"))
    
    // Spring Boot
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.kotlinx.coroutines.reactor)
    
    // Bucket4j for rate limiting
    implementation(libs.bucket4j.core)
    
    // Caffeine cache
    implementation(libs.caffeine)
    
    // Jackson for JSON
    implementation(libs.jackson.module.kotlin)
    
    // Micrometer for metrics
    implementation(libs.micrometer.prometheus)
    
    // Testing
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlin.reflect)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.reactor.test)
    testImplementation(libs.spring.boot.starter.test)
}
