package com.profiletailors.common.domain

/**
 * Marks code as auto-generated, excluding it from code coverage and static analysis.
 *
 * Use on `equals`, `hashCode`, `toString`, and other boilerplate methods that are
 * intentionally generated and should not trigger coverage gaps or linter warnings.
 *
 * @param reason optional explanation of why this element is generated
 * @since 1.0.0
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS, AnnotationTarget.CONSTRUCTOR)
annotation class Generated(val reason: String = "")
