package com.profiletailors.smp.platformadmin.application

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class PlatformPrincipalIdsTest {
    private val uuid = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `should prefix a bare uuid when fromUuid receives one`() {
        PlatformPrincipalIds.fromUuid(uuid) shouldBe "user-$uuid"
    }

    @Test
    fun `should keep an already prefixed principal id when fromUuid receives one`() {
        PlatformPrincipalIds.fromUuid("user-$uuid") shouldBe "user-$uuid"
    }

    @Test
    fun `should never double prefix when fromUuid receives a prefixed id`() {
        val once = PlatformPrincipalIds.fromUuid(uuid.toString())

        once shouldBe "user-$uuid"
        once.startsWith("user-user-").shouldBeFalse()
        PlatformPrincipalIds.fromUuid(once) shouldBe once
    }

    @Test
    fun `should strip the prefix and parse bare uuids when toUuid receives either form`() {
        PlatformPrincipalIds.toUuid("user-$uuid") shouldBe uuid
        PlatformPrincipalIds.toUuid(uuid.toString()) shouldBe uuid
    }
}
