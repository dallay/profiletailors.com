# API Versioning - Spring Boot 4 Media Type Versioning

**Date:** 2026-05-25  
**Status:** ✅ Completed

## Summary

Complete migration of the API versioning system to the new native Spring Boot 4 system using **Media Type Versioning** via the `Accept` header with the format `application/vnd.api.v{version}+json`.

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

**Advantages:**
- ✅ Declarative and clean versioning using the `version` attribute
- ✅ Native Spring Boot 4 support
- ✅ Easy evolution to multiple versions
- ✅ Centralized YAML configuration
- ✅ Semantic media-type (`application/vnd.api.v1+json`)
- ✅ Follows RFC 6838 (vendor media types)
- ✅ Compatible with standard content negotiation
- ✅ Cacheable via `Accept` header

## Changes Made

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

**Behavior:**
- If client sends `Accept: application/vnd.api.v1+json` → uses version 1
- If client sends `Accept: application/vnd.api.v2+json` → uses version 2
- If client DOES NOT send Accept or sends `Accept: */*` → uses default version "1"
- Spring Boot automatically routes to the correct endpoint
- Responds with `Content-Type: application/vnd.api.v1+json`

### 2. Updated Controllers

**7 controllers** were updated to use the new system:

#### LocalAuthController

```kotlin
@RestController
@RequestMapping(value = ["/api/auth"])  // ← No produces
class LocalAuthController {
    @PostMapping("/register", consumes = ["application/json"], version = "1")
    @PostMapping("/login", consumes = ["application/json"], version = "1")
    @PostMapping("/refresh", version = "1")
    @PostMapping("/logout", version = "1")
}
```

#### CurrentUserProfileController

```kotlin
@GetMapping("/me", version = "1")
```

#### WorkspaceOwnershipController

```kotlin
@PostMapping("/owners", consumes = ["application/json"], version = "1")
@DeleteMapping("/owners/{principalId}", version = "1")
@PostMapping("/owners/transfer", consumes = ["application/json"], version = "1")
```

#### WorkspaceMembershipController

```kotlin
@PatchMapping("/{principalId}/status", consumes = ["application/json"], version = "1")
```

#### AuditEventController

```kotlin
@GetMapping(version = "1")
```

#### WorkspaceAccessSummaryController

```kotlin
@GetMapping("/current", version = "1")
```

#### ResourcePreviewController

```kotlin
@GetMapping("/{resourceId}/preview", version = "1")
```

### 3. KDoc Documentation

All controllers updated their KDoc:

```kotlin
/**
 * Uses media-type versioning via Accept header: application/vnd.api.v1+json
 */
```

## How to Use the API

### HTTP Client

**Option 1: With explicit Accept header (Recommended)**

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -H "Accept: application/vnd.api.v1+json" \
  -d '{"email":"user@example.com","password":"secret"}'
```

**Response:**

```http
HTTP/1.1 200 OK
Content-Type: application/vnd.api.v1+json

{
  "accessToken": "eyJhbGc...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

**Option 2: Without Accept header (uses default v1)**

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"secret"}'
```

**Option 3: With generic Accept (uses default v1)**

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -H "Accept: */*" \
  -d '{"email":"user@example.com","password":"secret"}'
```

### JavaScript/TypeScript

```typescript
const response = await fetch('http://localhost:8080/api/auth/login', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/vnd.api.v1+json',  // Explicit
  },
  body: JSON.stringify({
    email: 'user@example.com',
    password: 'secret',
  }),
});

// Verify response Content-Type
console.log(response.headers.get('Content-Type')); 
// → "application/vnd.api.v1+json"
```

### Axios

```typescript
import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080',
  headers: {
    'Accept': 'application/vnd.api.v1+json',
  },
});

const response = await api.post('/api/auth/login', {
  email: 'user@example.com',
  password: 'secret',
});
```

### Swagger UI

Swagger UI automatically detects and documents the `application/vnd.api.v1+json` media-type in all endpoints.

## Future Evolution: Multiple Versions

When you need to add a v2:

```kotlin
@RestController
@RequestMapping(value = ["/api/auth"])
class LocalAuthController {
    
    // Version 1 (existing)
    @PostMapping("/login", consumes = ["application/json"], version = "1")
    suspend fun loginV1(@RequestBody request: LoginUserRequestV1): ResponseEntity<AuthTokensV1> {
        // v1 implementation
    }
    
    // Version 2 (new)
    @PostMapping("/login", consumes = ["application/json"], version = "2")
    suspend fun loginV2(@RequestBody request: LoginUserRequestV2): ResponseEntity<AuthTokensV2> {
        // v2 implementation with new fields
    }
}
```

**Spring Boot automatically routes:**
- `Accept: application/vnd.api.v1+json` → `loginV1()`
- `Accept: application/vnd.api.v2+json` → `loginV2()`
- No Accept or `Accept: */*` → `loginV1()` (default)

## Compatibility with Existing Clients

### What happens if clients do not send the Accept header?

**Answer:** They use the default version "1" automatically.

### What happens if clients send `Accept: application/json`?

**Answer:** Spring Boot uses full content negotiation and will:
1. Use the default version "1"
2. Or return 406 Not Acceptable if the configuration is strict

**Recommendation:** Configure to use default when there is no exact match.

## Content Negotiation

Spring Boot 4 supports full content negotiation:

```bash
# Client asks for v1
Accept: application/vnd.api.v1+json
→ Responds: Content-Type: application/vnd.api.v1+json

# Client asks for v2
Accept: application/vnd.api.v2+json
→ Responds: Content-Type: application/vnd.api.v2+json

# Client asks for anything
Accept: */*
→ Responds: Content-Type: application/vnd.api.v1+json (default)

# Client asks for generic JSON
Accept: application/json
→ Responds: Content-Type: application/vnd.api.v1+json (default)
```

## Testing

### Verify Compilation

```bash
cd server/smp
./gradlew compileKotlin
```

### Verify Swagger UI

```bash
./gradlew bootRun
```

Open: http://localhost:8080/swagger-ui.html

**Verify:**
- ✅ All endpoints appear
- ✅ `application/vnd.api.v1+json` media-type is documented
- ✅ Examples work correctly

### Integration Test

```kotlin
@Test
fun `should accept request with Accept header`() = runTest {
    webTestClient
        .post()
        .uri("/api/auth/login")
        .header("Accept", "application/vnd.api.v1+json")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(LoginUserRequest("user@example.com", "password"))
        .exchange()
        .expectStatus().isOk
        .expectHeader().contentType("application/vnd.api.v1+json")
}
```

## Advantages of Media Type Versioning

### 1. REST Standard
- Uses standard HTTP `Accept` header
- Follows REST best practices
- Compatible with content negotiation

### 2. Vendor Media Types (RFC 6838)
- Format: `application/vnd.{vendor}.{version}+{format}`
- Example: `application/vnd.api.v1+json`
- Clearly identifies the vendor (your API)

### 3. Clean Evolution
- Stable URLs
- Same endpoint, multiple versions
- Client controls which version it uses

### 4. Cacheable
- CDNs and proxies can cache by media-type
- `Vary: Accept` header indicates variations

## References

- [Spring Boot 4 API Versioning Documentation](https://docs.spring.io/spring-boot/reference/web/reactive.html#web.reactive.webflux.api-versioning)
- [RFC 6838 - Media Type Specifications](https://tools.ietf.org/html/rfc6838)
- [REST API Versioning Best Practices](https://restfulapi.net/versioning/)
- [Content Negotiation in HTTP](https://developer.mozilla.org/en-US/docs/Web/HTTP/Content_negotiation)

## Migration Checklist

- [x] Add YAML configuration for API versioning
- [x] Update LocalAuthController (4 endpoints)
- [x] Update CurrentUserProfileController (1 endpoint)
- [x] Update WorkspaceOwnershipController (3 endpoints)
- [x] Update WorkspaceMembershipController (1 endpoint)
- [x] Update AuditEventController (1 endpoint)
- [x] Update WorkspaceAccessSummaryController (1 endpoint)
- [x] Update ResourcePreviewController (1 endpoint)
- [x] Update KDoc documentation in all controllers
- [x] Verify successful compilation
- [ ] Update integration tests
- [ ] Update API documentation for clients
- [ ] Communicate changes to frontend team
- [ ] Update Postman/Insomnia collections
