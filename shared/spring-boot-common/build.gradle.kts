plugins {
    id("com.profiletailors.spring.boot.library")
}

group = "com.profiletailors"
version = "0.0.1-SNAPSHOT"

dependencies {
    api(project(":shared:common"))
    api(project(":shared:bus"))
    api(project(":shared:security"))
    api(project(":shared:presentation"))

    implementation(libs.spring.boot.starter.data.r2dbc)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.kotlinx.coroutines.slf4j)
    implementation(libs.kotlinx.coroutines.reactor)
    implementation(libs.kotlinx.coroutines.reactive)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.commons.text)
    implementation(libs.springdoc.openapi.webflux)

    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}
