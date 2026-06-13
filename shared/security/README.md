# shared:security

Security primitives for the Profile Tailors backend — hashing, principal context, and workspace authorization interfaces. Framework-agnostic (pure Kotlin, no Spring).

## Overview

Defines the core security abstractions used across all bounded contexts. The `Hasher` interface supports multiple algorithms (SHA-256, HMAC) with a pluggable registry, while `PrincipalContext` and `ResourceContext` provide type-safe access to the authenticated principal and workspace.

## Key Types

### Hashing

| Type | Purpose |
|------|---------|
| `Hasher` | `fun interface` — single method `hash(input: String): String` |
| `Sha256Hasher` | SHA-256 implementation |
| `HmacHasher` | HMAC-SHA256 implementation (requires non-blank secret) |
| `HasherSecurityConfig` | Named hasher bean configuration contract |

### Context

| Type | Purpose |
|------|---------|
| `PrincipalContext` | Holds `AuthenticatedPrincipal` for current request |
| `ResourceContext` | Holds `WorkspaceId` for multi-tenant resolution |
| `ContextProviders` | Reactive providers for principal and workspace |

### Authorization

| Type | Purpose |
|------|---------|
| `WorkspaceAuthorization` | Authorization check interface |

## Usage

```kotlin
// Hash a value
val hasher: Hasher = Sha256Hasher()
val digest = hasher.hash("my-api-key")

// Use the registry (with Spring auto-configuration)
val hasher: Hasher = hasherRegistry.get("sha256")
val digest = hasher.hash(secret)
```

## Dependencies

- `shared:common` (api) — domain primitives

## Related

- [shared:spring-boot-common](../spring-boot-common/README.md) — Spring auto-configuration for `HasherRegistry` and `SecurityProperties`
