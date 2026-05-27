rootProject.name = "profiletailors-build-logic"

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
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
