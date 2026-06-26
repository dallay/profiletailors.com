package com.profiletailors.smp.identity.infrastructure.security

/**
 * Resolves the local-JWT signing secret at runtime.
 *
 * Resolution order:
 * 1. The explicitly configured `app.security.local-jwt.secret` value (when non-blank).
 * 2. The `SMP_LOCAL_JWT_DEV_FALLBACK` environment variable (when non-blank). This is the
 *    safety net for local development when the operator forgot to set a real secret in
 *    `application.yaml` or `.env`.
 * 3. Fail fast — the application refuses to start rather than silently fall back to a
 *    hardcoded default that would end up committed in source control.
 *
 * The fallback is sourced from the environment (not a string literal) so this file
 * holds no hardcoded credentials and stays clear of static-analysis rules that flag
 * secret literals in production source.
 */
fun resolveLocalJwtSecret(configuredSecret: String, envSupplier: (String) -> String? = System::getenv): String {
    if (configuredSecret.isNotBlank()) return configuredSecret
    val fromEnv = envSupplier(SMP_LOCAL_JWT_DEV_FALLBACK_ENV).orEmpty()
    if (fromEnv.isNotBlank()) return fromEnv
    error(
        "JWT secret is not configured. Set app.security.local-jwt.secret or " +
            "$SMP_LOCAL_JWT_DEV_FALLBACK_ENV.",
    )
}

internal const val SMP_LOCAL_JWT_DEV_FALLBACK_ENV = "SMP_LOCAL_JWT_DEV_FALLBACK"
