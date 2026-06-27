package com.profiletailors.spring.boot.infrastructure.http

import org.springframework.http.server.reactive.ServerHttpRequest
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Utility for extracting and validating client IP addresses from HTTP requests.
 *
 * Provides secure IP extraction from request headers (X-Forwarded-For, X-Real-IP)
 * with validation to prevent DNS resolution attacks and ensure data integrity.
 *
 * Security Features:
 * - Pre-filters IP strings to contain only valid IP address characters (hex digits, dots, colons)
 * - Prevents DNS resolution of hostnames which could lead to DoS via thread exhaustion
 * - Handles IPv6 address normalization (e.g., "::1" vs "0:0:0:0:0:0:0:1")
 * - Falls back to direct remote address from connection when headers are missing or invalid
 *
 * Usage:
 * ```kotlin
 * val clientIp = ClientIpExtractor.extract(request)
 * ```
 */
object ClientIpExtractor {

    /**
     * Compiled regex for IP address format validation.
     * Matches only hexadecimal digits, dots (IPv4), colons (IPv6) and '%' for scope ids.
     * Prevents DNS resolution of arbitrary hostnames.
     *
     * Compiled once at class loading for efficiency.
     */
    private val IP_FORMAT_REGEX = Regex("^[0-9a-fA-F:.%]+$")

    // Strict IPv4 regex: exactly 4 octets, each 0-255
    private val IPV4_STRICT_REGEX = """^((25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(25[0-5]|2[0-4]\d|1?\d?\d)$""".toRegex()

    /**
     * Extracts the client's real IP address from the request.
     *
     * Checks X-Forwarded-For and X-Real-IP headers (typically set by proxies/load balancers),
     * validates the format to prevent DNS attacks, and falls back to the direct remote address.
     *
     * Header Processing:
     * - X-Forwarded-For: Can contain multiple IPs (comma-separated); takes the first valid one
     * - X-Real-IP: Contains a single IP; used if X-Forwarded-For is absent or invalid
     * - Remote Address: Used as final fallback for direct connections
     *
     * @param request The server HTTP request to extract IP from.
     * @return The validated client's IP address, or "unknown" if unable to determine.
     */
    fun extract(request: ServerHttpRequest): String {
        val xForwardedFor = request.headers.getFirst("X-Forwarded-For")
        if (!xForwardedFor.isNullOrBlank()) {
            val ip = xForwardedFor.split(",").first().trim()
            if (isValidIp(ip)) {
                return ip
            }
        }

        val xRealIp = request.headers.getFirst("X-Real-IP")
        if (!xRealIp.isNullOrBlank()) {
            val ip = xRealIp.trim()
            if (isValidIp(ip)) {
                return ip
            }
        }

        // Fallback to remote address (already validated by Java networking layer)
        return request.remoteAddress?.address?.hostAddress ?: "unknown"
    }

    /**
     * Validates if a string is a well-formed IP address (IPv4 or IPv6).
     *
     * Strategy:
     * 1. Fast-path IPv4: Use a strict IPv4 regex to accept only four octets (0-255).
     * 2. Reject IPv4-like strings (contains dots but doesn't match strict pattern) to prevent
     *    InetAddress.getByName from accepting shorthand forms like "192.168.1" → "192.168.0.1".
     * 3. Reject bare numbers without dots/colons (InetAddress interprets "192" as "0.0.0.192").
     * 4. Character pre-filter: allow only hex digits, dots, colons and percent (for IPv6 scope ids).
     * 5. IPv6 parsing: attempt to parse with InetAddress after the pre-filter to verify correctness.
     *    We avoid calling getByName on arbitrary hostnames because that can perform DNS resolution;
     *    the character pre-filter prevents hostnames here.
     *
     * @param ip The IP address string to validate.
     * @return true if the string is a valid IP address format, false otherwise.
     */
    fun isValidIp(ip: String): Boolean {
        if (ip.isBlank()) return false
        if (IPV4_STRICT_REGEX.matches(ip)) return true

        val hasDot = ip.contains('.')
        val hasColon = ip.contains(':')

        val isMalformedIpv4 = hasDot && !hasColon
        val isBareNumber = !hasDot && !hasColon
        if (isMalformedIpv4 || isBareNumber) return false

        return ip.matches(IP_FORMAT_REGEX) &&
            try {
                InetAddress.getByName(ip)
                true
            } catch (@Suppress("SwallowedException") e: UnknownHostException) {
                false
            }
    }
}
