# Tasks: R2 Dedicated Storage Adapter

## Phase 1: Infrastructure (Prerequisites)

### 1.1 Create R2ClientFactory

- [ ] Create `R2ClientFactory.kt` in `infrastructure/` package
- [ ] Implement `create(config: StorageProperties.R2Provider): S3AsyncClient`
- [ ] Configure path-style access for R2 compatibility
- [ ] Set default region to "auto"
- [ ] Add unit tests for factory

### 1.2 Extend StorageProperties

- [ ] Add `R2Provider` data class (or sealed class variant)
- [ ] Ensure `region` field defaults to "auto" for R2
- [ ] Update YAML configuration parsing to handle `type: r2`

## Phase 2: Implementation

### 2.1 Create R2StorageAdapter

- [ ] Create `R2StorageAdapter.kt` implementing `PresignableStorage`
- [ ] Inject `S3AsyncClient` via constructor
- [ ] Implement `upload()` with streaming and metadata support
- [ ] Implement `download()` returning `ByteReadChannel`
- [ ] Implement `delete()` single object deletion
- [ ] Implement `list()` with prefix and delimiter support
- [ ] Implement `presignGet()` returning R2-specific presigned URL
- [ ] Implement `exists()` object existence check

### 2.2 Implement Error Mapping

- [ ] Add exception mapping for `NoSuchKey` → `StorageObjectNotFoundException`
- [ ] Add exception mapping for `AccessDenied` → `StorageAccessDeniedException`
- [ ] Add exception mapping for network errors → `StorageConnectionException`
- [ ] Add exception mapping for other SDK errors → `StorageException`
- [ ] Write tests for all error mapping scenarios

### 2.3 Implement Path Traversal Protection

- [ ] Add `validateKey(key: String)` utility function
- [ ] Check for `../` and `..\\` sequences
- [ ] Throw `StorageSecurityException` for invalid keys
- [ ] Add unit tests for path traversal prevention

## Phase 3: Spring Integration

### 3.1 Update StorageAutoConfiguration

- [ ] Add `type: r2` case in provider registration
- [ ] Keep `type: s2` as deprecated alias with warning log
- [ ] Register `R2StorageAdapter` bean with proper scope
- [ ] Update `BucketRegistry` to handle R2 provider type

### 3.2 Update Configuration YAML Structure

- [ ] Document `type: r2` configuration format
- [ ] Add example R2 configuration to `application-example.yml`
- [ ] Ensure existing `type: s2` configurations continue to work

## Phase 4: Testing (TDD)

### 4.1 Unit Tests

- [ ] Create `R2StorageUnitTests.kt`
- [ ] Write tests for `upload()` - success case
- [ ] Write tests for `upload()` - streaming large files
- [ ] Write tests for `download()` - success case
- [ ] Write tests for `download()` - not found case
- [ ] Write tests for `delete()` - success case
- [ ] Write tests for `delete()` - not found case
- [ ] Write tests for `list()` - returns objects
- [ ] Write tests for `list()` - empty result
- [ ] Write tests for `presignGet()` - URL format verification
- [ ] Write tests for `exists()` - true case
- [ ] Write tests for `exists()` - false case
- [ ] Write tests for error mapping - all exception types
- [ ] Write tests for path traversal blocking
- [ ] Achieve >80% code coverage

### 4.2 Integration Tests

- [ ] Create `R2StorageIntegrationTest.kt`
- [ ] Configure testcontainer or LocalStack for R2 emulation
- [ ] Write end-to-end upload/download test
- [ ] Write presigned URL integration test
- [ ] Write list with prefix test
- [ ] Write delete and verify test
- [ ] Write concurrent upload test

### 4.3 Contract Tests

- [ ] Update `StorageContractTest.kt` to support R2 provider
- [ ] Add R2 to test parameter matrix
- [ ] Verify all platform specification scenarios pass for R2
- [ ] Add R2-specific contract scenarios if needed

## Phase 5: Documentation

### 5.1 Code Documentation

- [ ] Add KDoc to `R2StorageAdapter` class
- [ ] Add KDoc to `R2ClientFactory` methods
- [ ] Document R2-specific configuration requirements
- [ ] Document deprecation of `type: s2`

### 5.2 Migration Guide

- [ ] Create migration notes in module README
- [ ] Document how to migrate from `type: s2` to `type: r2`
- [ ] Document R2-specific environment variables

## Phase 6: Verification

### 6.1 Build & Test

- [ ] Run `./gradlew :modules:storage:compileKotlin`
- [ ] Run `./gradlew :modules:storage:test`
- [ ] Run `./gradlew :modules:storage:koverReport` (verify >80% coverage)
- [ ] Run `./gradlew :modules:storage:build`

### 6.2 Manual Verification

- [ ] Verify R2 credentials work with test bucket
- [ ] Test upload from application code
- [ ] Test presigned URL generation
- [ ] Verify logs show R2 adapter initialization

## Execution Order

```
1.1 → 1.2 → 2.1 → 2.2 → 2.3 → 3.1 → 3.2 → 4.1 → 4.2 → 4.3 → 5.1 → 5.2 → 6.1 → 6.2
```

**Note**: Phases 4.1-4.3 follow TDD - tests are written before implementation in real execution, but documented here as verification of implementation.

## Estimated Effort

| Phase | Tasks | Estimated Time |
|-------|-------|---------------|
| Infrastructure | 2 | 1 hour |
| Implementation | 6 | 3 hours |
| Spring Integration | 4 | 2 hours |
| Unit Tests | 14 | 4 hours |
| Integration Tests | 5 | 2 hours |
| Contract Tests | 3 | 1 hour |
| Documentation | 5 | 1 hour |
| Verification | 7 | 1 hour |
| **Total** | **46** | **~15 hours** |
