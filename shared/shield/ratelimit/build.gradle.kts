plugins {
    kotlin("jvm")
}

group = "com.profiletailors"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":shared:common"))
    implementation(project(":shared:spring-boot-common"))
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}