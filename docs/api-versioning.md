---
date: 2026-05-25
status: ✅ Completed
---

# API Versioning - Spring Boot 4 Media Type Versioning

## Overview

This document specifies the implementation of the API versioning system using **Media Type
Versioning** via the custom `Accept` header with the format `application/vnd.api.v{version}+json`.
This leverages the native Spring Boot 4 / WebFlux API versioning support.

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

### 1. Programmatic WebFlux Configuration

API Versioning is configured programmatically rather than via YAML.

**File:**
`server/smp/src/main/kotlin/com/profiletailors/smp/platform/infrastructure/http/WebFluxConfiguration.kt`

```kotlin
@Configuration
class WebFluxConfiguration : WebFluxConfigurer {

    @Bean
    fun mediaTypeVersionResolver(): ApiVersionResolver = MediaTypeVersionResolver()

    /**
     * Configures a default API version so requests without an explicit
     * `Accept: application/vnd.api.vN+json` header still match.
     */
    override fun configureApiVersioning(configurer: ApiVersionConfigurer) {
        configurer.setDefaultVersion(DEFAULT_API_VERSION)
    }

    companion object {
        private const val DEFAULT_API_VERSION = "1"
    }

    /**
     * Resolver that extracts the version from the custom vendor media type
     * in the Accept header (e.g., application/vnd.api.v1+json).
     */
    class MediaTypeVersionResolver : ApiVersionResolver {
        private val versionRegex = Regex("^vnd\\.api\\.v(\\d+)\\+json$")

        override fun resolveVersion(exchange: ServerWebExchange): String? {
            val acceptHeaders = exchange.request.headers.accept
            for (mediaType in acceptHeaders) {
                val matchResult = versionRegex.matchEntire(mediaType.subtype)
                if (matchResult != null) {
                    return matchResult.groupValues[1]
                }
            }
            return null
        }
    }
}
```

### 2. Updated Controllers

**15 controllers** are configured to use this system:

- `LocalAuthController` (4 endpoints)
- `CurrentUserProfileController` (1 endpoint)
- `WorkspaceOwnershipController` (3 endpoints)
- `WorkspaceMembershipController` (1 endpoint)
- `AuditEventController` (1 endpoint)
- `WorkspaceAccessSummaryController` (1 endpoint)
- `ResourcePreviewController` (1 endpoint)
- `AnalyticsController` (analytics endpoints)
- `HashtagsController` (hashtag endpoints)
- `IdeasController` (9 endpoints — see below)
- `PublicCapabilitiesController` (public capability endpoint)
- `MediaAssetController` (media asset endpoints)
- `HealthcheckController` (health check endpoint)
- `PublishingControllers` (publishing endpoints)
- `BulkPublishingController` (bulk validate/schedule/job/templates — 4 endpoints, `bulkScheduling.enabled`)
- `WorkspaceController` (workspace endpoints)

#### Bulk Scheduling API (`/api/v1/workspaces/{workspaceId}/bulk`)

Flag `bulkScheduling.enabled` *(deferred — docs-only, code guard not yet implemented; rollback drop `022` then `021`; existing jobs remain)*. See `openspec/changes/dallay-413-bulk-scheduling/design.md` and ADRs 0002, 0015-0017 for bulk module placement.

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/v1/workspaces/{workspaceId}/bulk/validate` | Per-row validate CSV, no persistence, 200 |
| POST | `/api/v1/workspaces/{workspaceId}/bulk/schedule` | Chunked schedule 50-100/tx, 200/207, 409 on duplicate sha256(ws+principal+csvHash) |
| GET | `/api/v1/workspaces/{workspaceId}/bulk/jobs/{jobId}` | Job workspace-scoped 200, 404 cross-workspace |
| GET | `/api/v1/workspaces/{workspaceId}/bulk/templates` | Templates list |
| GET | `/api/v1/workspaces/{workspaceId}/bulk/templates/{id}/csv` | Template CSV `text/csv` canonical `bodyText,scheduledFor,timezone,media_urls,hashtags` |

#### Ideas API (`/api/ideas`)

All Ideas Canvas endpoints use `version = "1"` and require
`Accept: application/vnd.api.v1+json`:

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/ideas` | List all ideas grouped by column |
| POST | `/api/ideas` | Create new idea (defaults to first column) |
| GET | `/api/ideas/{ideaId}` | Get idea details |
| PATCH | `/api/ideas/{ideaId}` | Update idea (title, notes, tags, links) |
| PATCH | `/api/ideas/{ideaId}/move` | Move idea to column + position |
| DELETE | `/api/ideas/{ideaId}` | Delete idea |
| POST | `/api/ideas/{ideaId}/convert` | Convert idea to publication |
| GET | `/api/ideas/columns` | Get column config for workspace |
| PUT | `/api/ideas/columns` | Update column config (add, rename, reorder, delete) |

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
- Check if the default version (which defaults to `"1"`) is being used when no `Accept` header is
  provided.

## References

- [Spring Boot 4 API Versioning Documentation](https://docs.spring.io/spring-boot/reference/web/reactive.html#web.reactive.webflux.api-versioning)
- [RFC 6838 - Media Type Specifications](https://tools.ietf.org/html/rfc6838)
- [REST API Versioning Best Practices](https://restfulapi.net/versioning/)
