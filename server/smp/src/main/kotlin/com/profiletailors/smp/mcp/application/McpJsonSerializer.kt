package com.profiletailors.smp.mcp.application

interface McpJsonSerializer {
    fun <T> toJson(data: T): String

    fun <T> fromJson(json: String, type: Class<T>): T
}
