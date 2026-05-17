plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    id("io.spring.dependency-management") version "1.1.7"
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

extra["springBootVersion"] = "4.0.6"

dependencies {
    api(project(":shared-common"))

    implementation(platform("org.springframework.boot:spring-boot-dependencies:${property("springBootVersion")}"))

    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive")
    implementation("tools.jackson.module:jackson-module-kotlin:3.0.+")
    implementation("org.apache.commons:commons-text:1.12.0")
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:3.0.2")

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
