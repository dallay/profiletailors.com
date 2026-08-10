package com.profiletailors.smp.identity

import com.profiletailors.common.domain.ValueObject
import com.profiletailors.smp.identity.domain.EmailStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("ddd-conformance")
internal class IdentityMarkerCoverageTest {
    @Test
    fun emailStatusIsMarkedAsValueObject() {
        assertThat(EmailStatus::class.java.isAnnotationPresent(ValueObject::class.java)).isTrue()
    }
}
