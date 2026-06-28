# Proposal: R2 Dedicated Storage Adapter

## Intent

The current `S2Storage` adapter is a thin wrapper that delegates 100% to `S3Storage`, lacking its
own identity and R2-optimized configuration. This creates three problems: (1) R2-specific nuances
cannot be addressed without modifying S3 behavior, (2) the naming is misleading (`s2` refers to
DigitalOcean Spaces historically), and (3) future R2-specific features (transfer acceleration, R2
Workers integration) have no extension point.

This change creates a first-class `R2StorageAdapter` with its own implementation, proper Cloudflare
R2 branding, and a clear migration path from the legacy `s2` type.

## Scope

### In Scope

- Replace `S2Storage` with a dedicated `R2StorageAdapter`
- Maintain 100% feature parity with S3 operations (upload, download, delete, list, presign)
- Support backward-compatible configuration (`type: s2` continues to work via alias)
- Add `type: r2` as the canonical provider identifier
- Unit tests (`R2StorageUnitTests.kt`) following TDD
- Integration tests (`R2StorageIntegrationTest.kt`)
- Update `StorageContractTest.kt` to cover R2-specific scenarios
- Update Spring auto-configuration to register R2 provider

### Out of Scope

- R2-specific features beyond S3-compatible API (Workers integration, Transfer Acceleration)
- Migration tooling for existing buckets
- Multi-region R2 configuration (R2 uses `auto` region by design)
- R2 Analytics/DevDash integration

## Approach

1. **Extract R2 client creation** into a dedicated factory method with R2-specific defaults
2. **Create `R2StorageAdapter`** that implements `PresignableStorage` directly (not via delegation)
3. **Maintain backward compatibility** by treating `s2` as an alias for `r2` in configuration
4. **TDD-first**: Write failing tests before implementing the adapter
5. **Follow existing patterns** from `S3Storage.kt` and `StorageContractTest.kt`

## Affected Areas

| Area                                                                                        | Impact     | Description                          |
|---------------------------------------------------------------------------------------------|------------|--------------------------------------|
| `server/smp/modules/storage/src/main/kotlin/.../infrastructure/R2StorageAdapter.kt`         | New        | Dedicated R2 adapter implementation  |
| `server/smp/modules/storage/src/main/kotlin/.../infrastructure/S2Storage.kt`                | Deprecated | Legacy wrapper (alias behavior only) |
| `server/smp/modules/storage/src/main/kotlin/.../infrastructure/StorageAutoConfiguration.kt` | Modified   | Register R2 provider type            |
| `server/smp/modules/storage/src/test/kotlin/.../R2StorageUnitTests.kt`                      | New        | Unit tests with mocks                |
| `server/smp/modules/storage/src/test/kotlin/.../R2StorageIntegrationTest.kt`                | New        | Integration tests                    |
| `server/smp/modules/storage/src/test/kotlin/.../StorageContractTest.kt`                     | Modified   | Add R2 contract scenarios            |

## Risks

| Risk                                        | Likelihood | Mitigation                                                |
|---------------------------------------------|------------|-----------------------------------------------------------|
| Breaking existing `type: s2` configurations | Low        | Maintain `s2` as alias for `r2` in config parser          |
| R2 SDK behavior differs from AWS S3         | Low        | Use existing S3 SDK v2; R2 is S3-compatible               |
| Test environment requires R2 credentials    | Medium     | Use Docker LocalStack with R2 emulation or testcontainers |
| Performance regression vs S3Storage         | Low        | Benchmark and compare; adapter shares core logic          |

## Rollback Plan

1. Revert `S2Storage.kt` to delegate to `S3Storage` (restore previous state)
2. Remove `R2StorageAdapter.kt` from source tree
3. Update `StorageAutoConfiguration.kt` to remove R2 registration
4. Run existing `StorageContractTest` to verify S3 behavior unchanged
5. Revert test file additions

## Dependencies

- AWS SDK Kotlin v2 (`software.amazon.awssdk:s3`)
- Kotlin coroutines (`kotlinx.coroutines`)
- Spring Boot 4 (for `StorageAutoConfiguration`)
- Existing `@shared/storage` module structure

## Success Criteria

- [ ] `R2StorageAdapter` passes all `StorageContractTest` scenarios
- [ ] `R2StorageUnitTests` achieves >80% code coverage
- [ ] `R2StorageIntegrationTest` passes against real R2 or LocalStack R2 emulation
- [ ] Existing `s2` configuration type continues to work (backward compatibility)
- [ ] New `r2` configuration type is documented and functional
- [ ] All storage-related tests pass (`./gradlew :modules:storage:test`)
