plugins {
    `kotlin-dsl`
}

group = "com.profiletailors.buildlogic"
version = "0.0.1"

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.gradle.kotlin)
    implementation("org.jetbrains.kotlin:kotlin-allopen:${libs.versions.kotlin.get()}")
    implementation(libs.gradle.spring.boot)
    implementation(libs.gradle.spring.dependency)
    implementation(libs.gradle.detekt)
    implementation(libs.gradle.owasp.depcheck)
    implementation(libs.gradle.kover)
}

gradlePlugin {
    plugins {
        register("kotlin-library") {
            id = "com.profiletailors.kotlin.library"
            implementationClass = "com.profiletailors.buildlogic.library.KotlinLibraryPlugin"
        }
        register("spring-boot-library") {
            id = "com.profiletailors.spring.boot.library"
            implementationClass = "com.profiletailors.buildlogic.springboot.SpringBootLibraryPlugin"
        }
        register("spring-boot-application") {
            id = "com.profiletailors.spring.boot.application"
            implementationClass = "com.profiletailors.buildlogic.springboot.SpringBootApplicationPlugin"
        }
        register("owasp-dependency-check") {
            id = "com.profiletailors.security.owasp"
            implementationClass = "com.profiletailors.buildlogic.security.OwaspPlugin"
        }
    }
}
