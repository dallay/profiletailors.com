plugins {
    kotlin("jvm") version "2.3.21"
    id("dev.detekt") version "2.0.0-alpha.3"
}

group = "com.profiletailors"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
		languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("tools.jackson.module:jackson-module-kotlin:3.0.+")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.slf4j:slf4j-api:2.0.18")

    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

detekt {
    config.setFrom(files("../../server/smp/detekt.yml"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}
