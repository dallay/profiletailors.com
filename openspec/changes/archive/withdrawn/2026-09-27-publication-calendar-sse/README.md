# Publication Calendar SSE

Worker and inter-client calendar invalidations delivered over a workspace-scoped
Server-Sent Event stream. This change is the slice that the previous change
explicitly deferred; it composes with the existing `reactive-calendar-browser-sync`
artifacts without re-implementing them.

Follow the same SDD flow:

1. `exploration.md` — confirm the SSE substrate, auth, and Sinks usage in the
   current Spring Modulith/SSE infrastructure before designing the change.
2. `proposal.md` — confirm scope, in/out, and decomposition.
3. `specs/calendar-publication-sse/spec.md` — durable contract for the stream.
4. `design.md` — chosen approach, alternatives, payload contract, file table.
5. `tasks.md` — strict-TDD phases and review units.
6. `state.yaml` — phase tracking; starts at `explore`.
