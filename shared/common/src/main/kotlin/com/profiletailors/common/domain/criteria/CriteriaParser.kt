package com.profiletailors.common.domain.criteria

fun interface CriteriaParser<Out : Any?> {
    fun parse(criteria: Criteria): Out
}
