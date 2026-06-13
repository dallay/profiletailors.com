package com.profiletailors.spring.boot.infrastructure.http

import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.springframework.mock.http.server.reactive.MockServerHttpRequest

class ClientIpExtractorTest {

    @Test
    fun `should extract first valid IP from X-Forwarded-For`() {
        val request = MockServerHttpRequest.get("/")
            .header("X-Forwarded-For", "203.0.113.10, 198.51.100.20")
            .remoteAddress(InetSocketAddress("127.0.0.1", 8080))
            .build()

        assertEquals("203.0.113.10", ClientIpExtractor.extract(request))
    }

    @Test
    fun `should extract X-Real-IP when forwarded header is missing`() {
        val request = MockServerHttpRequest.get("/")
            .header("X-Real-IP", "198.51.100.20")
            .remoteAddress(InetSocketAddress("127.0.0.1", 8080))
            .build()

        assertEquals("198.51.100.20", ClientIpExtractor.extract(request))
    }

    @Test
    fun `should fall back to remote address when headers are invalid`() {
        val request = MockServerHttpRequest.get("/")
            .header("X-Forwarded-For", "not-a-hostname")
            .header("X-Real-IP", "999.999.999.999")
            .remoteAddress(InetSocketAddress("127.0.0.1", 8080))
            .build()

        assertEquals("127.0.0.1", ClientIpExtractor.extract(request))
    }

    @Test
    fun `should return unknown when no address is available`() {
        val request = MockServerHttpRequest.get("/").build()

        assertEquals("unknown", ClientIpExtractor.extract(request))
    }

    @Test
    fun `should validate strict IPv4 addresses`() {
        assertTrue(ClientIpExtractor.isValidIp("192.168.1.1"))
        assertTrue(ClientIpExtractor.isValidIp("255.255.255.255"))
        assertFalse(ClientIpExtractor.isValidIp("999.1.1.1"))
        assertFalse(ClientIpExtractor.isValidIp("192.168.1"))
        assertFalse(ClientIpExtractor.isValidIp("192"))
    }

    @Test
    fun `should validate IPv6 and reject hostnames`() {
        assertTrue(ClientIpExtractor.isValidIp("::1"))
        assertTrue(ClientIpExtractor.isValidIp("2001:db8::1"))
        assertFalse(ClientIpExtractor.isValidIp("example.com"))
        assertFalse(ClientIpExtractor.isValidIp(""))
    }
}
