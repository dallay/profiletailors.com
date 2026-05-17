package com.profiletailors.common.domain.bus.query

interface Response

data class QueryResponse<T>(val data: T) : Response
