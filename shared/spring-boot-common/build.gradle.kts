plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("io.spring.dependency-management") version "1.1.7"
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
    implementation("org.apache.commons:commons-text:1.15.0")
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:3.0.3")

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
