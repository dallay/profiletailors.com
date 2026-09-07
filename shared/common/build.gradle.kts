plugins {
    id("com.profiletailors.kotlin.library")
    `java-test-fixtures`
}

group = "com.profiletailors"
version = "0.0.1-SNAPSHOT"

dependencies {
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.assertj.core)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.platform.launcher)
}
