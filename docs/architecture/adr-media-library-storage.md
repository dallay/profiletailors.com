# ADR: Media Library Storage Configuration

## Status: Accepted

## Context

The centralized media library MVP requires storage for uploaded media assets. The design decision (Decision 3) states that `shared/storage` remains canonical for binary storage while media owns metadata and lifecycle.

Before reusing the existing `attachments` bucket, four audit points must be verified.

## Audit Findings

### 1. Object Lifecycle Rules

**Finding**: The current bucket `profiletailors-attachments` is used for publishing assets. No lifecycle rules are visible in the codebase that auto-expire objects under the `assets/` prefix.

**Action**: ✅ No conflict found for MVP. Production deployment should verify bucket lifecycle rules in the cloud provider console.

### 2. CORS Configuration

**Finding**: The storage module uses backend-managed upload (not direct browser-to-storage presigned URLs), so CORS is not a concern for the upload flow.

**Action**: ✅ Not applicable for backend-managed upload. If future migrations use presigned URLs, CORS configuration must be verified.

### 3. IAM/Access Policies

**Finding**: Storage access is controlled through the application layer, not at the bucket policy level. The `BucketRegistry` provides access to configured buckets, and all storage operations go through the application service.

**Action**: ✅ No inadvertent exposure found. Media assets are workspace-scoped and require authentication.

### 4. Server-Side Encryption

**Finding**: The S3/R2 compatible storage uses the default encryption settings for the provider. No custom encryption configuration is visible in the codebase.

**Action**: ✅ Compatible with MVP requirements. Production deployment should verify SSE settings match requirements.

## Decision

The existing `attachments` bucket (`profiletailors-attachments`) is **reusable for MVP** with the following conditions:

1. No lifecycle rules auto-expire objects under `assets/` prefix (verify in production)
2. Storage keys follow the pattern `assets/{workspaceId}/{assetId}` with UUID v4 identifiers
3. Backend-managed upload means CORS is not a concern for the MVP flow
4. IAM policies at the bucket level are not used; application-level access control applies

## Post-MVP Considerations

If future requirements include:
- Direct browser-to-storage presigned uploads → verify CORS configuration
- Public asset CDN → review bucket policies and access controls
- Retention policies → configure lifecycle rules carefully to avoid premature deletion

## References

- Design: Decision 3 - `shared/storage` remains canonical for binary storage
- Design: Storage write retry policy - single-attempt streaming for MVP
- Spec: Media Library Requirement - media type validation and 500 MB limit
