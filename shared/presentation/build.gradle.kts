plugins {
    id("com.profiletailors.kotlin.library")
    `java-test-fixtures`
}

group = "com.profiletailors"
version = "0.0.1-SNAPSHOT"

dependencies {
    testImplementation(testFixtures(project(":shared:common")))

    api(project(":shared:common"))

    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockk)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.platform.launcher)
}
