package com.profiletailors.buildlogic

import org.jetbrains.kotlin.gradle.dsl.JvmTarget as KtJvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion as KtVersion

object AppConfiguration {
    const val APP_NAME = "smp"
    const val PACKAGE_NAME = "com.profiletailors"

    val jvmTarget = KtJvmTarget.JVM_25
    val jvmTargetStr = "25"
    val kotlinVersion = KtVersion.KOTLIN_2_4
}
