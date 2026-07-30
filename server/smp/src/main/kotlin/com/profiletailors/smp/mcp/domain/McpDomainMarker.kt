package com.profiletailors.smp.mcp.domain

/**
 * Marker for the `mcp` bounded context's domain layer.
 *
 * The domain layer is intentionally empty in PR 1 (foundation only).
 * The first domain artefact is `McpError` introduced alongside `McpErrorMapper`
 * in PR 3 (tool implementation). This file exists today so the bounded context
 * satisfies `HexagonalArchTest.boundedContextsShouldExposeAllLayers`.
 */
internal object McpDomainMarker
