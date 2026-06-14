package com.profiletailors.common.domain

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe memoization utilities backed by [ConcurrentHashMap].
 *
 * Provides two overloads of [of] to memoize:
 * 1. A zero-argument supplier (lazy-init pattern)
 * 2. A one-argument function (memoized by input key)
 *
 * ## Concurrency guarantees
 * - Uses [ConcurrentHashMap.computeIfAbsent] for atomic read-write semantics,
 *   ensuring the function is executed at most once per distinct input across
 *   all threads.
 * - The function should be idempotent and free of side effects — if two threads
 *   race on the same key, one result is stored and both threads see the same value.
 *
 * ## Memory
 * Cached results live for the lifetime of the memoized wrapper. For large result
 * sets or cache eviction needs, consider a dedicated cache infrastructure instead.
 *
 * @since 1.0.0
 */
object Memoizers {
    /**
     * Memoizes a zero-argument supplier.
     *
     * The supplier is invoked once on the first call and the result is cached
     * for all subsequent calls.
     *
     * @param supplier the function to memoize
     * @return a memoized version of [supplier]
     */
    fun <Result> of(supplier: () -> Result): () -> Result {
        val memoized = of { _: Any? -> supplier() }
        return { memoized(null) }
    }

    /**
     * Memoizes a one-argument function by input key.
     *
     * Results are cached per distinct input value. Equality is determined by
     * the input's own `equals`/`hashCode`.
     *
     * @param function the function to memoize (should be pure and idempotent)
     * @return a memoized version of [function]
     */
    fun <Input, Result> of(function: (Input) -> Result): (Input) -> Result = MemoizedFunction(function)

    private class MemoizedFunction<Input, Result>(private val function: (Input) -> Result) : (Input) -> Result {
        private val results = ConcurrentHashMap<MemoizedInput<Input>, MemoizedResult<Result>>()
        override fun invoke(input: Input): Result =
            results.computeIfAbsent(MemoizedInput(input), this::toMemoizedResult).result
        private fun toMemoizedResult(input: MemoizedInput<Input>): MemoizedResult<Result> =
            MemoizedResult(function(input.input))
        private data class MemoizedInput<Input>(val input: Input)
        private data class MemoizedResult<Result>(val result: Result)
    }
}
