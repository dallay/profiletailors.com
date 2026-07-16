plugins {
    id("com.profiletailors.kotlin.library")
}

group = "com.profiletailors"
version = "0.0.1-SNAPSHOT"

dependencies {
    api(project(":shared:lead-capture:common"))

    testImplementation(libs.archunit.junit5)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.platform.launcher)
}
