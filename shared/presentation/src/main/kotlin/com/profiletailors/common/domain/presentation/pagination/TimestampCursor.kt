package com.profiletailors.common.domain.presentation.pagination

import com.profiletailors.common.domain.criteria.Criteria
import com.profiletailors.common.domain.presentation.sort.Direction
import com.profiletailors.common.domain.presentation.sort.Sort
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import kotlin.reflect.full.declaredMemberProperties

data class TimestampCursor(
    val createdAt: LocalDateTime,
    override val direction: Direction = Direction.ASC
) : Cursor {
    override fun getCursor(): String = String.format(CURSOR_FORMAT, createdAt, direction)
    override fun getSort(): Sort = Sort.by(Direction.ASC, CREATED_AT)
    override fun getCriteria(): Criteria = if (direction == Direction.ASC) {
        Criteria.GreaterThan(CREATED_AT, createdAt)
    } else {
        Criteria.LessThan(CREATED_AT, createdAt)
    }

    override fun <T : Any> serialize(it: T, direction: Direction): String {
        val createdAtProperty = it::class.declaredMemberProperties.find { it.name == CREATED_AT }
        val createdAtValue = createdAtProperty?.getter?.call(it)
            ?: throw IllegalArgumentException("Invalid cursor object")
        return encode(String.format(CURSOR_FORMAT, createdAtValue, direction))
    }

    override fun isDefault(): Boolean = this == DEFAULT_CURSOR

    override fun toString(): String =
        """
            {
            createdAt: $createdAt,
            cursor: ${getCursor()},
            sort: ${getSort()},
            criteria: ${getCriteria()}
            }
        """.trimIndent()

    companion object {
        private const val SEPARATOR = "<—>"
        private const val CREATED_AT = "createdAt"
        private const val CURSOR_FORMAT = "%s$SEPARATOR%s"
        private val encoder: CursorEncoder = Base64CursorEncoder()
        val DEFAULT_CURSOR: Cursor = TimestampCursor(LocalDateTime.MIN)

        fun encode(data: String): String = encoder.encode(data)
        fun decode(encodeData: String): String = encoder.decode(encodeData)

        fun deserialize(serializedData: String): TimestampCursor {
            val decoded = decode(serializedData)
            val parts = decoded.split(SEPARATOR)
            val createdAt = LocalDateTime.parse(parts[0].trim())
            val direction = Direction.valueOf(parts[1].trim())
            return TimestampCursor(createdAt, direction)
        }
    }
}
