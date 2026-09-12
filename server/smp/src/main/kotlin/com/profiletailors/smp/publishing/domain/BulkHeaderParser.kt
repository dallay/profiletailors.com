package com.profiletailors.smp.publishing.domain

data class BulkHeaderColumns(val bodyIdx: Int, val scheduledIdx: Int, val mediaIdx: Int)

sealed interface BulkHeaderParseResult {
    data class Valid(val columns: BulkHeaderColumns) : BulkHeaderParseResult
    data object Invalid : BulkHeaderParseResult
}

object BulkHeaderParser {
    fun parse(headerLine: String): BulkHeaderParseResult {
        val headerColumns = splitHeader(headerLine).map { it.trim() }
        val canonical = BulkTemplate.canonicalHeader().split(",")
        val matches = headerColumns.size == canonical.size &&
            headerColumns.map { it.lowercase() } == canonical.map { it.lowercase() }
        if (!matches) return BulkHeaderParseResult.Invalid
        val indexByName = canonical.associateWith { col ->
            headerColumns.indexOfFirst { it.equals(col, ignoreCase = true) }
        }
        return BulkHeaderParseResult.Valid(
            BulkHeaderColumns(
                bodyIdx = indexByName["bodyText"] ?: 0,
                scheduledIdx = indexByName["scheduledFor"] ?: 1,
                mediaIdx = indexByName["media_urls"] ?: 3,
            ),
        )
    }

    private fun splitHeader(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> {
                    current.append(c)
                }
            }
            i++
        }
        result.add(current.toString())
        return result.map { it.trim() }
    }
}
