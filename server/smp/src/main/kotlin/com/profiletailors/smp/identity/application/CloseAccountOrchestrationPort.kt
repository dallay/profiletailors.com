package com.profiletailors.smp.identity.application

/**
 * Port for orchestrating post-validation account closure operations.
 *
 * Implementations execute the irreversible deletion steps that happen after
 * the [CloseAccountHandler] has confirmed the request and passed the rate
 * limiter. Defined in [com.profiletailors.smp.identity.application] so the
 * identity module can invoke it without depending on the privacy module.
 */
fun interface CloseAccountOrchestrationPort {

    /**
     * Execute all post-validation account closure steps for [principalId].
     */
    suspend fun execute(principalId: String)
}
