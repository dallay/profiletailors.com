# shared:presentation

Presentation-layer types for the Profile Tailors API — pagination, sorting, filtering, and response envelopes. All types are framework-agnostic (pure Kotlin).

## Overview

Provides domain-level DTOs and utilities for building consistent API responses. Used by application-layer query handlers to return paginated, sorted, and filtered results without leaking HTTP concerns into domain logic.

## Key Types

### Pagination

| Type | Purpose |
|------|---------|
| `PageResponse<T>` | Generic page envelope (items + total + page info) |
| `OffsetPageResponse<T>` | Offset-based page (page number + size) |
| `CursorPageResponse<T>` | Cursor-based page (opaque cursor for infinite scroll) |
| `RequestPageable` | Inbound pagination request (page/size or cursor) |
| `CursorEncoder` | Encode/decode opaque cursors |
| `TimestampCursor` | Time-based cursor implementation |

### Filtering

| Type | Purpose |
|------|---------|
| `Criteria` | Composable filter criteria tree |
| `CriteriaParser` | Parse filter strings into `Criteria` trees |
| `RHSFilterParser` | Parse right-hand side filter expressions |

### Sorting

| Type | Purpose |
|------|---------|
| `Sort` | Sort specification (field + direction) |

### Response Envelopes

| Type | Purpose |
|------|---------|
| `SimpleMessageResponse` | Simple message wrapper |
| `PresentationException` | Base exception for presentation errors |

## Usage

```kotlin
// Return from a query handler
data class ListPostsQuery : Query<PageResponse<PostSummary>>

class ListPostsHandler : QueryHandler<ListPostsQuery, PageResponse<PostSummary>> {
    override suspend fun handle(query: ListPostsQuery): PageResponse<PostSummary> {
        val items = repository.findAll(pageable)
        return OffsetPageResponse(
            items = items,
            total = repository.count(),
            page = pageable.page,
            size = pageable.size
        )
    }
}
```

## Dependencies

- `shared:common` (api) — domain primitives

## Related

- [shared:spring-boot-common](../spring-boot-common/README.md) — Spring HTTP serialization of PageResponse via `OffsetPagePresenter` and `SortMapper`
