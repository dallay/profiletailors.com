# Vue Component Maintainability Guide

> Project standard for the profiletailors codebase.
>
> **Core principle:** The template describes **what** is shown, the composable describes **how** it
> works, the store describes **what state is global**.
>
> When something lives in the wrong layer, maintenance becomes expensive.

---

## 1. Single Responsibility Per Layer

Each file must have one reason to change:

| Layer        | Responsibility                                     |
|--------------|----------------------------------------------------|
| Component    | Orchestrate props, emits, and the template         |
| Composable   | Business logic and local state                     |
| Pinia Store  | Global state shared across routes / modules        |

When a `<script setup>` exceeds ~80 lines, some logic belongs in a composable.

---

## 2. Composables as a Unit of Reuse

Extract everything that is not "painting UI" into a composable.

**Signals you need a composable:**

- `computed` that does math or date formatting
- `watch` reacting to prop or state changes
- Local state (`ref`) shared by several components
- Filtering, pagination, multi-selection logic
- API calls with error handling

**Signals you do NOT need a composable:**

- Logic that lives in one place and is 3–4 lines
- Event handlers that only call `emit()`

**Recommended structure:**

```typescript
// modules/media/application/useMediaLibraryFilters.ts
export function useMediaLibraryFilters(assets: Ref<MediaAsset[]>) {
  const searchQuery = ref('')
  const statusFilter = ref<MediaStatus | 'ALL'>('ALL')

  const visibleAssets = computed(() =>
    assets.value
      .filter(a => matchesSearch(a, searchQuery.value))
      .filter(a => matchesStatus(a, statusFilter.value))
  )

  function clearFilters() {
    searchQuery.value = ''
    statusFilter.value = 'ALL'
  }

  return { searchQuery, statusFilter, visibleAssets, clearFilters }
}
```

```vue
<!-- MediaLibraryView.vue — orchestrator only -->
<script setup lang="ts">
const mediaStore = useMediaStore()
const filters = useMediaLibraryFilters(toRef(mediaStore, 'assets'))
</script>
```

---

## 3. Explicit Contracts in Props & Emits

Types in the contract are the living documentation of the component.

```typescript
// ❌ Hard to maintain
defineProps<{ data: any; config: object }>()

// ✅ Explicit, typed contract
defineProps<{
  publications: Publication[]
  isLoading: boolean
  selectedIds: string[]
}>()

defineEmits<{
  (e: 'select', id: string): void
  (e: 'delete', id: string): void
}>()
```

**Rules:**

- Never use `any` — prefer `unknown` with a type guard or generics
- Optional props with defaults MUST use `withDefaults`
- Emits MUST be typed, never `defineEmits(['click', 'change'])`

---

## 4. Parent-Orchestrator / Focused Child Hierarchy

When a component grows, decompose it into children with single responsibilities.

**Before:**

```text
CreatePostModal.vue  ← 989 lines, 5 mixed responsibilities
```

**After:**

```text
CreatePostModal.vue  ← 180 lines, orchestrator only
├── ComposerTextArea.vue         ← textarea + character count
├── ComposerAttachmentsArea.vue  ← attachments + dropzone
├── ComposerScheduleFooter.vue   ← scheduling options
└── ComposerMediaPickerShell.vue ← dialog media picker
```

**Rule of thumb:** if the template has comments like `<!-- Media section -->` or
`<!-- Toolbar -->`, each section is probably a child component.

---

## 5. Avoid Cascading `computed` Chains Inside Components

```typescript
// ❌ Computed chain in <script setup>
const rawData = computed(() => store.data)
const filtered = computed(() => rawData.value.filter(/* ... */))
const sorted = computed(() => filtered.value.sort(/* ... */))
const paginated = computed(() => sorted.value.slice(/* ... */))

// ✅ Composable encapsulates the full chain
const { visibleItems } = useFilteredList(store.data, filters, sort, page)
```

---

## 6. Centralized Types as Single Source of Truth

**Location:** `apps/web/app/src/types/index.ts`

```typescript
// Domain types
export interface Publication {
  id: string
  status: PublicationStatus
  scheduledAt: string | null
}

// Utility types
export type Nullable<T> = T | null
export type ApiResponse<T> = { data: T; error: null } | { data: null; error: string }

// Form state
export interface FormState<T> {
  values: T
  errors: Partial<Record<keyof T, string>>
  isSubmitting: boolean
}
```

When the backend changes a field, update it in one place and TypeScript flags every affected
consumer.

---

## 7. Module Export Indexes

Each module exposes only its public API through an `index.ts`. Internal implementation details
stay unexported.

```typescript
// modules/media/application/index.ts
export { useMediaLibraryFilters } from './useMediaLibraryFilters'
export { useMediaLibrarySelection } from './useMediaLibrarySelection'
export { useMediaAssetDisplay } from './useMediaAssetDisplay'
// Internal helpers are NOT exported
```

**Benefit:** when internals are refactored later, consuming code does not change.

---

## 8. Size Semaphores — Line-Count Thresholds

| Lines   | Signal       | Action                                              |
|---------|--------------|-----------------------------------------------------|
| < 100   | ✅ Healthy   | None                                                |
| 100–200 | ⚠️ Review   | Check for extractable composable logic              |
| 200–400 | 🔴 Candidate | Plan extraction next sprint                         |
| > 400   | 🚨 Critical  | Refactor before adding features                    |

---

## 9. Testing as Design Validation

A well-designed component is naturally easy to test.

- **Pure composable** → unit test without mounting, no DOM
- **Component with props/emits** → integration test with `mountComponent`

```typescript
// useMediaAssetDisplay.test.ts — no Vue, no DOM
it('returns error status class for FAILED assets', () => {
  const { statusClass } = useMediaAssetDisplay({
    status: 'FAILED', mediaType: 'image/png',
  })
  expect(statusClass.value).toContain('text-error')
})

it('formats file size correctly', () => {
  expect(formatFileSize(1536)).toBe('1.5 KB')
  expect(formatFileSize(null)).toBeNull()
})
```

If a composable depends on DOM or the store, evaluate whether it has mixed responsibilities or excessive scope — continue extracting if needed.

---

## 10. Import Organization

Consistent order inside every `<script setup>`:

```typescript
// 1. Vue core
import { ref, computed, watch } from 'vue'

// 2. UI libraries / icons
import { Button } from '@/components/ui/button'
import { Loader2 } from '@lucide/vue'

// 3. Stores
import { useMediaStore } from '@modules/media/infrastructure/media.store'

// 4. Module's own composables
import { useMediaLibraryFilters } from '@modules/media/application'

// 5. Shared composables
import { usePagination } from '@shared/composables'

// 6. Child components
import MediaAssetGrid from './MediaAssetGrid.vue'

// 7. Types
import type { MediaAsset } from '@/types'
```

---

## Pre-Merge Checklist

- [ ] `<script setup>` under ~80 effective lines (extract composable if exceeded)
- [ ] No `any`, no `as unknown as X` without a type guard
- [ ] Props and emits typed with explicit interfaces
- [ ] Calculation / filtering logic in composable, not inline in template
- [ ] No `watch(..., { deep: true })` over full objects
- [ ] Imports ordered per project convention
- [ ] Composable exported from the module's `index.ts`
- [ ] If over 200 lines, a comment or issue exists for refactoring

---

## Reference Module Structure

```text
modules/media/
├── infrastructure/
│   ├── media.store.ts         ← global Pinia state
│   └── media-api.ts           ← HTTP calls
├── application/
│   ├── useMediaLibraryFilters.ts
│   ├── useMediaLibrarySelection.ts
│   ├── useMediaAssetDisplay.ts
│   ├── useMediaAssetActions.ts
│   └── index.ts               ← module public API
└── presentation/
    ├── views/
    │   └── MediaLibraryView.vue   ← view orchestrator
    └── components/
        ├── MediaAssetGrid.vue     ← focused component
        └── MediaAttribution.vue   ← focused component
```

This structure separates **what** (presentation), **how** (application), and **where**
(infrastructure), following layered architecture principles applied to the frontend.
