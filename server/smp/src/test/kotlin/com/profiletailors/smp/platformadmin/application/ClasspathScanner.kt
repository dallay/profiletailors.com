package com.profiletailors.smp.platformadmin.application

import java.io.File
import java.io.IOException
import java.net.URL
import java.util.jar.JarFile
import java.util.zip.ZipException

internal object ClasspathScanner {

    fun scan(packageNames: Set<String>): Set<Class<*>> {
        val classLoader = Thread.currentThread().contextClassLoader ?: ClassLoader.getSystemClassLoader()
        val roots = mutableSetOf<URL>()
        for (packageName in packageNames) {
            val resource = classLoader.getResource(packageName.replace('.', '/')) ?: continue
            roots.add(resource)
        }
        if (roots.isEmpty()) return emptySet()

        val result = mutableSetOf<Class<*>>()
        for (packageName in packageNames) {
            val relativePath = packageName.replace('.', '/')
            for (root in roots) {
                val entries = listClassEntries(root, relativePath)
                for (entry in entries) {
                    val className = "$packageName.${entry.removeSuffix(".class")}"
                    loadClass(classLoader, className)?.let(result::add)
                }
            }
        }
        return result
    }

    private fun listClassEntries(resource: URL, packagePath: String): List<String> {
        val protocol = resource.protocol
        return when (protocol) {
            "file" -> {
                val directory = File(resource.toURI())
                if (!directory.isDirectory) {
                    emptyList()
                } else {
                    directory.walkTopDown()
                        .filter { it.isFile && it.name.endsWith(".class") && !it.name.contains("$") }
                        .map { it.name }
                        .toList()
                }
            }
            "jar", "jrt" -> {
                val path = resource.path.substringBeforeLast('!').removePrefix("file:")
                try {
                    JarFile(path).use { jar ->
                        jar.entries().asSequence()
                            .filter { entry ->
                                entry.name.startsWith("$packagePath/") &&
                                    entry.name.endsWith(".class") &&
                                    !entry.name.contains("$")
                            }
                            .map { it.name.removePrefix("$packagePath/") }
                            .toList()
                    }
                } catch (_: IOException) {
                    emptyList()
                } catch (_: ZipException) {
                    emptyList()
                } catch (_: IllegalStateException) {
                    emptyList()
                }
            }
            else -> {
                emptyList()
            }
        }
    }

    private fun loadClass(classLoader: ClassLoader, className: String): Class<*>? = try {
        Class.forName(className, false, classLoader)
    } catch (_: ClassNotFoundException) {
        null
    } catch (_: LinkageError) {
        null
    }
}
