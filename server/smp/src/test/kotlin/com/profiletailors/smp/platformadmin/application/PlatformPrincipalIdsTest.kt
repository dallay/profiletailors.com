package com.profiletailors.smp.platformadmin.application

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class PlatformPrincipalIdsTest {
    private val uuid = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `fromUuid prefixes a bare uuid`() {
        PlatformPrincipalIds.fromUuid(uuid) shouldBe "user-$uuid"
    }

    @Test
    fun `fromUuid keeps an already prefixed principal id`() {
        PlatformPrincipalIds.fromUuid("user-$uuid") shouldBe "user-$uuid"
    }

    @Test
    fun `fromUuid never double prefixes`() {
        val once = PlatformPrincipalIds.fromUuid(uuid.toString())

        once shouldBe "user-$uuid"
        once.startsWith("user-user-").shouldBeFalse()
        PlatformPrincipalIds.fromUuid(once) shouldBe once
    }

    @Test
    fun `toUuid strips the prefix and parses bare uuids`() {
        PlatformPrincipalIds.toUuid("user-$uuid") shouldBe uuid
        PlatformPrincipalIds.toUuid(uuid.toString()) shouldBe uuid
    }
}
