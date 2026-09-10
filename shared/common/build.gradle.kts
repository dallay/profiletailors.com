plugins {
    id("com.profiletailors.kotlin.library")
    `java-test-fixtures`
}

group = "com.profiletailors"
version = "0.0.1-SNAPSHOT"

dependencies {
    testFixturesImplementation(libs.archunit.junit5)
    testImplementation(libs.archunit.junit5)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.assertj.core)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.platform.launcher)
}

sourceSets {
    test {
        compileClasspath += sourceSets.testFixtures.get().output
        runtimeClasspath += sourceSets.testFixtures.get().output
    }
}
