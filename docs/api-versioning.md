---
date: 2026-05-25
status: ✅ Completed
---

# API Versioning - Spring Boot 4 Media Type Versioning

## Overview

This document specifies the migration of the API versioning system to the new native Spring Boot 4
system using **Media Type Versioning** via the `Accept` header with the format
`application/vnd.api.v{version}+json`.

## Motivation

### Before (Manual with produces)

```kotlin
@RestController
@RequestMapping(value = ["/api/auth"], produces = ["application/vnd.api.v1+json"])
class LocalAuthController {
    @PostMapping("/login", consumes = ["application/json"])
    suspend fun login(@RequestBody request: LoginUserRequest): ResponseEntity<AuthTokens>
}
```

**Problems:**

- ❌ Repeating `produces` in every class-level `@RequestMapping`
- ❌ Does not leverage new Spring Boot 4 features
- ❌ Difficult to evolve multiple versions on the same endpoint

### Now (Spring Boot 4 Native)

```kotlin
@RestController
@RequestMapping(value = ["/api/auth"])
class LocalAuthController {
    @PostMapping("/login", consumes = ["application/json"], version = "1")
    suspend fun login(@RequestBody request: LoginUserRequest): ResponseEntity<AuthTokens>
}
```

## Changes

### 1. YAML Configuration

**File:** `server/smp/src/main/resources/application.yaml`

```yaml
spring:
  webflux:
    apiversion:
      default: "1"
      use:
        media-type: "application/vnd.api.v{version}+json"
```

### 2. Updated Controllers

**7 controllers** were updated to use the new system:

- `LocalAuthController` (4 endpoints)
- `CurrentUserProfileController` (1 endpoint)
- `WorkspaceOwnershipController` (3 endpoints)
- `WorkspaceMembershipController` (1 endpoint)
- `AuditEventController` (1 endpoint)
- `WorkspaceAccessSummaryController` (1 endpoint)
- `ResourcePreviewController` (1 endpoint)

## Usage

### HTTP Client (Recommended)

```bash
curl -X POST http://localhost:7638/api/auth/login \
  -H "Content-Type: application/json" \
  -H "Accept: application/vnd.api.v1+json" \
  -d '{"email":"user@example.com","password":"secret"}'
```

### Future Evolution: Multiple Versions

When you need to add a v2:

```kotlin
@RestController
@RequestMapping(value = ["/api/auth"])
class LocalAuthController {
    
    // Version 1 (existing)
    @PostMapping("/login", consumes = ["application/json"], version = "1")
    suspend fun loginV1(@RequestBody request: LoginUserRequestV1): ResponseEntity<AuthTokensV1> { ... }
    
    // Version 2 (new)
    @PostMapping("/login", consumes = ["application/json"], version = "2")
    suspend fun loginV2(@RequestBody request: LoginUserRequestV2): ResponseEntity<AuthTokensV2> { ... }
}
```

## Troubleshooting

### "Accept header not matching"

- Ensure the header format is exactly `application/vnd.api.v{version}+json`.
- Verify the `version` attribute in the controller method matches the requested version.
- Check if the default version is being used when no `Accept` header is provided.

## References

- [Spring Boot 4 API Versioning Documentation](https://docs.spring.io/spring-boot/reference/web/reactive.html#web.reactive.webflux.api-versioning)
- [RFC 6838 - Media Type Specifications](https://tools.ietf.org/html/rfc6838)
- [REST API Versioning Best Practices](https://restfulapi.net/versioning/)
