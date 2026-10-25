rootProject.name = "profiletailors-build-logic"

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        register("libs") {
            from(files("../libs.versions.toml"))
        }
    }
}
