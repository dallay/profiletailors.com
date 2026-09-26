package com.profiletailors.buildlogic.testing

import com.profiletailors.buildlogic.ConventionPlugin
import com.profiletailors.buildlogic.extensions.catalogPlugin
import io.github.anschnapp.mutflow.gradle.MutflowExtension
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import java.util.zip.ZipFile

private const val MUTATION_TIMEOUT_MS = 60_000L
private const val MUTATION_MARKER = "io/github/anschnapp/mutflow"

class MutationTestingPlugin : ConventionPlugin {
    override fun Project.configure() {
        apply(plugin = catalogPlugin("mutflow").get().pluginId)
        val baselineTargets =
            listOf(
                "com.profiletailors.smp.publishing.domain.BulkValidationPipeline",
                "com.profiletailors.smp.publishing.domain.PublicationLifecyclePolicy",
            )
        extensions.configure<MutflowExtension> {
            targets.set(baselineTargets)
            verificationMode.set("LENIENT")
            maxMutationRuns.set(Int.MAX_VALUE)
            timeoutMs.set(MUTATION_TIMEOUT_MS)
        }
        val verificationMode =
            providers
                .environmentVariable("MUTFLOW_VERIFICATION_MODE")
                .orElse(providers.gradleProperty("mutflow.verificationMode").orElse("LENIENT"))
        tasks.withType<Test>().configureEach {
            environment("MUTFLOW_VERIFICATION_MODE", verificationMode.get())
        }
        afterEvaluate {
            extensions.findByType(SourceSetContainer::class.java)?.named("mutatedMain")?.configure {
                resources.setSrcDirs(emptySet<String>())
            }
        }
        val bootJar = layout.buildDirectory.file("libs/smp.jar")
        val jarInputs: List<Provider<RegularFile>> =
            tasks.withType<Jar>().map { it.archiveFile }.toList()
        tasks.register("verifyMutationClean") {
            group = "verification"
            description = "Fails when a production JAR contains mutflow mutation switches"
            dependsOn("jar")
            inputs.files(jarInputs)
            doLast {
                val jars = jarInputs.map { it.get().asFile } + bootJar.get().asFile
                val offenders =
                    jars.filter { it.exists() }.filter { jar ->
                        ZipFile(jar).use { zip ->
                            zip.entries().asSequence().any { entry ->
                                entry.name.endsWith(".class") &&
                                    zip.getInputStream(entry).use { input ->
                                        input.readBytes().decodeToString().contains(MUTATION_MARKER)
                                    }
                            }
                        }
                    }
                if (offenders.isNotEmpty()) {
                    throw org.gradle.api.GradleException(
                        "Production artifact contains mutflow references: ${offenders.joinToString()}",
                    )
                }
            }
        }
    }
}
