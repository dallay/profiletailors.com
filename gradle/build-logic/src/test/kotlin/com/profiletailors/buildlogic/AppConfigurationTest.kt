package com.profiletailors.buildlogic

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class AppConfigurationTest {

    @Test
    fun `kotlinVersion is pinned to KOTLIN_2_4`() {
        assertEquals(KotlinVersion.KOTLIN_2_4, AppConfiguration.kotlinVersion)
    }

    @Test
    fun `kotlinVersion is no longer pinned to the previous KOTLIN_2_0 baseline`() {
        assertNotEquals(KotlinVersion.KOTLIN_2_0, AppConfiguration.kotlinVersion)
    }

    @Test
    fun `jvmTarget and jvmTargetStr remain consistent with JVM 21`() {
        assertEquals(JvmTarget.JVM_21, AppConfiguration.jvmTarget)
        assertEquals("21", AppConfiguration.jvmTargetStr)
    }

    @Test
    fun `jvmTargetStr parses as the integer used for JavaLanguageVersion lookups`() {
        assertEquals(21, AppConfiguration.jvmTargetStr.toInt())
    }

    @Test
    fun `app and package names are unchanged`() {
        assertEquals("smp", AppConfiguration.APP_NAME)
        assertEquals("com.profiletailors", AppConfiguration.PACKAGE_NAME)
    }
}