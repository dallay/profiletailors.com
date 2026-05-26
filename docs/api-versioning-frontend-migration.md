# API Versioning - Frontend Migration Guide

## Overview

The backend API now uses **Media Type Versioning** with the vendor media type `application/vnd.api.v1+json`. All HTTP requests to the backend **must** include the `Accept` header with this media type.

## What Changed

### Before
```typescript
headers: {
  'Content-Type': 'application/json',
  'Authorization': 'Bearer <token>'
}
```

### After
```typescript
headers: {
  'Content-Type': 'application/json',
  'Accept': 'application/vnd.api.v1+json',  // ← NEW: Required for API versioning
  'Authorization': 'Bearer <token>'
}
```

## Migration Status

### ✅ Already Updated

- `apps/web/app/src/lib/auth-api.ts` - The `request()` function now includes the `Accept` header automatically
- All authentication endpoints (`/api/auth/*`) are covered
- All authenticated requests using `apiFetch` from the auth store are covered

### ⚠️ Action Required

If you have **any other HTTP clients** in the frontend (e.g., separate API modules, direct `fetch` calls, axios instances), you **must** add the `Accept: application/vnd.api.v1+json` header to all requests.

## How to Test

1. Start the backend: `cd server/smp && ./gradlew bootRun`
2. Start the frontend: `cd apps/web/app && pnpm dev`
3. Test authentication flows:
   - Register a new user
   - Login with credentials
   - Refresh session
   - Logout
4. Verify in browser DevTools Network tab that all requests include:
   - **Request Header:** `Accept: application/vnd.api.v1+json`
   - **Response Header:** `Content-Type: application/vnd.api.v1+json`

## What Happens Without the Header?

If a request is sent **without** the `Accept: application/vnd.api.v1+json` header:

- **Status:** `400 Bad Request` or `406 Not Acceptable`
- **Reason:** Spring Boot cannot route the request to the versioned endpoint

## Backend Endpoints Affected

All REST endpoints now require the versioned media type:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `GET /api/authorization/workspace-access/current`
- `GET /api/authorization/resources/{resourceId}/preview`
- `GET /api/governance/audit-events`
- `PATCH /api/tenancy/workspaces/{workspaceId}/memberships/{membershipId}/status`
- All other API endpoints

## Future Versions

When we release API v2, clients can request it by changing the header to:

```typescript
'Accept': 'application/vnd.api.v2+json'
```

The backend will continue to support v1 for backward compatibility.

## Questions?

Contact the backend team or check `docs/api-versioning.md` for technical details.
