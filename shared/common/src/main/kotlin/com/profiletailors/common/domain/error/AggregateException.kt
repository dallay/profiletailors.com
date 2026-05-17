package com.profiletailors.common.domain.error

class AggregateException(val exceptions: Collection<Throwable>) : RuntimeException() {
    constructor(exceptions: Array<Throwable>) : this(exceptions.toList())
}
