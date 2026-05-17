plugins {
    kotlin("jvm") version "2.2.21"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

group = "com.profiletailors"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("tools.jackson.module:jackson-module-kotlin:3.0.+")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.slf4j:slf4j-api:2.0.17")

    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

detekt {
    toolVersion.set("1.23.8")
    config.setFrom(files("../../server/smp/detekt.yml"))
    baseline.set(file("../../server/smp/config/detekt/baseline.xml"))
    reports {
        html.required.set(true)
        xml.required.set(true)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
