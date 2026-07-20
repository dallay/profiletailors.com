plugins {
    id("com.profiletailors.kotlin.library")
}

group = "com.profiletailors"
version = "0.0.1-SNAPSHOT"

base {
    archivesName.set("notifications")
}

dependencies {
    api(project(":shared:common"))
    api(project(":shared:bus"))
    api(project(":shared:lead-capture:common"))
    api(project(":shared:lead-capture:waitlist"))

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.api)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.platform.launcher)
}
