package com.profiletailors.common.domain.vo.name

data class Name(val firstName: FirstName, val lastName: LastName?) : Comparable<Name> {
    fun fullName(): String = "${firstName.value} ${lastName?.value ?: ""}".trim()
    override operator fun compareTo(other: Name): Int = fullName().compareTo(other.fullName())
    override fun toString(): String = fullName()
    companion object {
        fun of(firstName: String, lastName: String?): Name =
            Name(FirstName(firstName), lastName?.let { LastName(it) })
    }
}
