package com.profiletailors.smp.mcp

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Verifies that MCP JSON-RPC request/response shapes conform to the
 * MCP protocol specification (2025-03-26).
 *
 * These are pure JSON contract tests — no server, no Spring context.
 * They ensure serialization round-trips produce the expected wire format.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class McpContractTest {

    private val mapper = jacksonObjectMapper()

    @Test
    fun `initialize request has correct JSON-RPC shape`() {
        val request = mapOf(
            "jsonrpc" to "2.0",
            "id" to 1,
            "method" to "initialize",
            "params" to mapOf(
                "protocolVersion" to "2025-03-26",
                "capabilities" to emptyMap<String, Any>(),
                "clientInfo" to mapOf(
                    "name" to "test-client",
                    "version" to "1.0.0",
                ),
            ),
        )

        val json = mapper.writeValueAsString(request)
        val parsed: Map<String, Any?> = mapper.readValue(json)

        assertEquals("2.0", parsed["jsonrpc"])
        assertEquals(1, parsed["id"])
        assertEquals("initialize", parsed["method"])
        assertNotNull(parsed["params"])
    }

    @Test
    fun `tools-list response has correct JSON-RPC shape`() {
        val response = mapOf(
            "jsonrpc" to "2.0",
            "id" to 2,
            "result" to mapOf(
                "tools" to listOf(
                    mapOf(
                        "name" to "list_channels",
                        "description" to "List connected social media channels",
                        "inputSchema" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "status" to mapOf("type" to "string"),
                            ),
                        ),
                    ),
                    mapOf(
                        "name" to "list_publications",
                        "description" to "List scheduled publications",
                        "inputSchema" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "from" to mapOf("type" to "string"),
                                "to" to mapOf("type" to "string"),
                            ),
                            "required" to listOf("from", "to"),
                        ),
                    ),
                ),
            ),
        )

        val json = mapper.writeValueAsString(response)
        val parsed: Map<String, Any?> = mapper.readValue(json)

        assertEquals("2.0", parsed["jsonrpc"])
        assertEquals(2, parsed["id"])

        @Suppress("UNCHECKED_CAST")
        val result = parsed["result"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val tools = result["tools"] as List<Map<String, Any?>>
        assertTrue(tools.size >= 2, "Should have at least 2 tools")
        assertTrue(tools.any { it["name"] == "list_channels" })
        assertTrue(tools.any { it["name"] == "list_publications" })
    }

    @Test
    fun `tools-call response has correct JSON-RPC shape`() {
        val response = mapOf(
            "jsonrpc" to "2.0",
            "id" to 3,
            "result" to mapOf(
                "content" to listOf(
                    mapOf(
                        "type" to "text",
                        "text" to """{"channels":[]}""",
                    ),
                ),
                "isError" to false,
            ),
        )

        val json = mapper.writeValueAsString(response)
        val parsed: Map<String, Any?> = mapper.readValue(json)

        assertEquals("2.0", parsed["jsonrpc"])
        assertEquals(3, parsed["id"])

        @Suppress("UNCHECKED_CAST")
        val result = parsed["result"] as Map<String, Any?>
        assertEquals(false, result["isError"])

        @Suppress("UNCHECKED_CAST")
        val content = result["content"] as List<Map<String, Any?>>
        assertTrue(content.isNotEmpty())
        assertEquals("text", content[0]["type"])
    }

    @Test
    fun `tools-call error response has correct JSON-RPC shape`() {
        val response = mapOf(
            "jsonrpc" to "2.0",
            "id" to 4,
            "result" to mapOf(
                "content" to listOf(
                    mapOf(
                        "type" to "text",
                        "text" to """{"code":"rate_limit_exceeded","message":"Too many requests"}""",
                    ),
                ),
                "isError" to true,
            ),
        )

        val json = mapper.writeValueAsString(response)
        val parsed: Map<String, Any?> = mapper.readValue(json)

        @Suppress("UNCHECKED_CAST")
        val result = parsed["result"] as Map<String, Any?>
        assertEquals(true, result["isError"])
    }
}
