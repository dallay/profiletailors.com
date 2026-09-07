plugins {
    id("com.profiletailors.kotlin.library")
}

group = "com.profiletailors"
version = "0.0.1-SNAPSHOT"

dependencies {
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}
