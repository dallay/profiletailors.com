package com.profiletailors.common.domain.presentation.pagination

import com.profiletailors.common.domain.criteria.Criteria
import com.profiletailors.common.domain.presentation.sort.Direction
import com.profiletailors.common.domain.presentation.sort.Sort

interface Cursor {
    val direction: Direction get() = Direction.ASC
    fun getCursor(): String
    fun getSort(): Sort
    fun getCriteria(): Criteria
    fun serialize(): String = encode(getCursor())
    fun <T : Any> serialize(it: T, direction: Direction = Direction.ASC): String
    fun isDefault(): Boolean

    companion object {
        private val encoder: CursorEncoder = Base64CursorEncoder()
        fun encode(data: String): String = encoder.encode(data)
        fun decode(encodedData: String): String = encoder.decode(encodedData)
        fun default(): Cursor = TimestampCursor.DEFAULT_CURSOR
    }
}
