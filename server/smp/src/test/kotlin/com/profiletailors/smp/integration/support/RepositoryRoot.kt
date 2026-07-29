package com.profiletailors.smp.integration.support

import java.nio.file.Files
import java.nio.file.Path

object RepositoryRoot {
    fun path(): Path = System.getProperty("project.root")
        ?.let { Path.of(it) }
        ?: generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) {
            it.parent
        }.firstOrNull { Files.isRegularFile(it.resolve("justfile")) }
        ?: error("Repository root containing justfile was not found")
}
