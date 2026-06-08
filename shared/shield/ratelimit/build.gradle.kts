plugins {
    id("com.profiletailors.spring.boot.library")
}

group = "com.profiletailors"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":shared:common"))
    implementation(project(":shared:spring-boot-common"))
    
    // Spring Boot
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.kotlinx.coroutines.reactor)
    
    // Bucket4j for rate limiting
    implementation("com.bucket4j:bucket4j-core:8.10.1")
    
    // Caffeine cache
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    
    // Jackson for JSON
    implementation(libs.jackson.module.kotlin)
    
    // Micrometer for metrics
    implementation(libs.micrometer.prometheus)
    
    // Testing
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlin.reflect)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation("io.kotest:kotest-assertions-core:6.1.11")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
