package com.profiletailors.smp.mcp.application

/**
 * Marker for the `mcp` bounded context's application layer.
 *
 * The application layer is intentionally empty in PR 1 (foundation only).
 * The first application artefact is `McpWorkspaceContextResolver` introduced in
 * PR 2 (security). This file exists today so the bounded context satisfies
 * `HexagonalArchTest.boundedContextsShouldExposeAllLayers`.
 */
internal object McpApplicationMarker
