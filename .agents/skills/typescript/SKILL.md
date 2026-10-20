---
name: typescript
description: >-
  Design, implement, or review TypeScript types and runtime boundaries in Profile Tailors Vue and
  Astro apps. Use for unions, generics, API payloads, schema validation, and typed component or
  composable contracts; pair with vue or astrolicious-astro for framework-specific work.
allowed-tools: Read, Edit, Write, Glob, Grep, Bash, WebFetch, WebSearch, Task
metadata:
  category: languages-typing
  family: typescript
  source: local
  version: 2026-09-28
---

# TypeScript

Use TypeScript to make valid states clear and external data safe. Follow the package's effective
`tsconfig`, Biome rules, and nearby code before introducing a new convention. Pair with `zod-4`
when the change uses Zod schemas.

## Type design

- Use a literal union when only the type-level set of values is needed: `type Status = 'active' | 'inactive'`.
- Use an `as const` object when the same values are needed at runtime as well as in types, such as
  for options, lookup tables, or iteration.
- Extract a nested object into a named type when it has meaning, is reused, or has its own behavior.
  Keep a small one-off shape inline when naming it adds no clarity.
- Derive types with `Pick`, `Omit`, `Parameters`, or `ReturnType` when that removes real duplication
  and preserves the intended contract. Avoid opaque chains of derived types.
- Use discriminated unions for values with distinct states or payloads. Handle each variant
  exhaustively where correctness depends on all cases.
- Prefer `unknown` for untrusted or untyped values, then narrow it with runtime checks or a schema.
  Do not use `any`, broad assertions, or non-null assertions to bypass a mismatch.

## Runtime boundaries

- Type annotations do not validate network responses, browser storage, URL input, or other runtime
  data. Validate external values at the boundary with the schema or parser already used by that
  package.
- Keep API transport types separate from UI/domain types when they differ in meaning or lifecycle;
  add a mapping where it clarifies the boundary.
- Do not assert an API response into the desired type. Reconcile the API contract or validate and
  map the received value.
- Prefer type predicates and exhaustive narrowing over casts. A cast is acceptable only when a
  checked invariant cannot be represented to TypeScript and the local pattern documents that fact.

## Examples

```ts
type LoadState<T> =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'success'; data: T }
  | { status: 'error'; message: string }

const POST_STATUSES = ['draft', 'scheduled', 'published'] as const
type PostStatus = (typeof POST_STATUSES)[number]
```

Use `LoadState<T>` when consumers need to distinguish the lifecycle states. Use `POST_STATUSES`
when runtime code also needs to enumerate or display the values; otherwise a direct union is enough.

## Related skills

- `vue` for Vue components, composables, and Pinia.
- `astrolicious-astro` for Astro pages and islands.
- `zod-4` for Zod schema design and parsing.
- `.agents/AGENTS.md` for TypeScript, Biome, and suppression policy.
