package com.profiletailors.common.domain.vo.name

/**
 * A person's full name composed of a required [firstName] and optional [lastName].
 *
 * ## Formatting
 * - [fullName] returns "FirstName LastName" when both parts are present, or just the
 *   first name when last name is null.
 * - [compareTo] sorts by full name (lexicographic).
 *
 * ## Factory
 * [of] creates a [Name] from raw strings, wrapping them in validated [FirstName] and
 * [LastName] value objects.
 *
 * @since 1.0.0
 */
data class Name(val firstName: FirstName, val lastName: LastName?) : Comparable<Name> {
    /**
     * Returns the full name as a formatted string.
     *
     * Examples:
     * - `Name(FirstName("Yuniel"), LastName("Acosta")).fullName()` → `"Yuniel Acosta"`
     * - `Name(FirstName("Yuniel"), null).fullName()` → `"Yuniel"`
     */
    fun fullName(): String = "${firstName.value} ${lastName?.value ?: ""}".trim()
    override operator fun compareTo(other: Name): Int = fullName().compareTo(other.fullName())
    override fun toString(): String = fullName()
    companion object {
        /**
         * Creates a [Name] from raw string values.
         *
         * @param firstName the first name (validated by [FirstName])
         * @param lastName the optional last name (validated by [LastName])
         * @return a validated [Name]
         */
        fun of(firstName: String, lastName: String?): Name =
            Name(FirstName(firstName), lastName?.let { LastName(it) })
    }
}
