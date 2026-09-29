package com.profiletailors.leadcapture.waitlist.application.contracts

interface WaitlistTransactionRunner {
    suspend fun <T : Any> runAtomically(block: suspend () -> T): T

    companion object {
        val noop: WaitlistTransactionRunner = object : WaitlistTransactionRunner {
            override suspend fun <T : Any> runAtomically(block: suspend () -> T): T = block()
        }
    }
}
