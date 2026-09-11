package com.profiletailors.smp

import com.profiletailors.smp.integration.support.RepositoryRoot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files

class KotlinSourceLayoutTest {

    @Test
    fun `keeps the SMP source tree free of Java sources`() {
        val repositoryRoot = RepositoryRoot.path()
        val sourceRoot = repositoryRoot.resolve("server/smp/src")
        val javaSources = Files.walk(sourceRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".java") }
                .map { repositoryRoot.relativize(it).toString() }
                .sorted()
                .toList()
        }

        assertThat(javaSources).isEmpty()
    }
}
