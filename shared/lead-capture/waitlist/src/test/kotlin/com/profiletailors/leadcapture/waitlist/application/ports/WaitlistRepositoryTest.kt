package com.profiletailors.leadcapture.waitlist.application.ports

import com.profiletailors.leadcapture.waitlist.domain.Waitlist
import com.profiletailors.leadcapture.waitlist.domain.WaitlistId
import com.profiletailors.leadcapture.waitlist.domain.WaitlistKey
import com.profiletailors.leadcapture.waitlist.domain.WaitlistStatus
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Contract tests for [WaitlistRepository] exercised against an in-memory
 * implementation, verifying the port's behavioral contract independently
 * of any infrastructure adapter.
 */
internal class WaitlistRepositoryTest {

    private class InMemoryWaitlistRepository(
        private val store: Map<WaitlistKey, Waitlist>,
    ) : WaitlistRepository {
        override fun findByKey(key: WaitlistKey): Waitlist? = store[key]
    }

    private fun waitlist(key: String, status: WaitlistStatus = WaitlistStatus.ACTIVE) = Waitlist(
        id = WaitlistId("w-$key"),
        key = WaitlistKey(key),
        name = "Waitlist $key",
        context = "profile-tailors",
        status = status,
    )

    @Test
    fun `findByKey returns the waitlist when the key exists`() {
        val active = waitlist("profile-tailors-launch")
        val repository: WaitlistRepository = InMemoryWaitlistRepository(mapOf(active.key to active))

        val result = repository.findByKey(WaitlistKey("profile-tailors-launch"))

        assertEquals(active, result)
    }

    @Test
    fun `findByKey returns null when the key does not exist`() {
        val repository: WaitlistRepository = InMemoryWaitlistRepository(emptyMap())

        val result = repository.findByKey(WaitlistKey("unknown-key"))

        assertNull(result)
    }

    @Test
    fun `findByKey distinguishes between different waitlists`() {
        val launch = waitlist("profile-tailors-launch")
        val beta = waitlist("profile-tailors-beta", status = WaitlistStatus.PAUSED)
        val repository: WaitlistRepository = InMemoryWaitlistRepository(
            mapOf(launch.key to launch, beta.key to beta),
        )

        assertEquals(launch, repository.findByKey(WaitlistKey("profile-tailors-launch")))
        assertEquals(beta, repository.findByKey(WaitlistKey("profile-tailors-beta")))
    }
}
