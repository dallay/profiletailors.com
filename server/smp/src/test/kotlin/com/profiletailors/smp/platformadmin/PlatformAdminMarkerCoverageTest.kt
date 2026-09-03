package com.profiletailors.smp.platformadmin

import com.profiletailors.common.domain.AggregateRoot
import com.profiletailors.common.domain.ValueObject
import com.profiletailors.smp.platformadmin.domain.AdminAuditAction
import com.profiletailors.smp.platformadmin.domain.AdminAuditResult
import com.profiletailors.smp.platformadmin.domain.Invitation
import com.profiletailors.smp.platformadmin.domain.InvitationDeliveryStatus
import com.profiletailors.smp.platformadmin.domain.InvitationId
import com.profiletailors.smp.platformadmin.domain.InvitationSource
import com.profiletailors.smp.platformadmin.domain.InvitationStatus
import com.profiletailors.smp.platformadmin.domain.PlatformPermission
import com.profiletailors.smp.platformadmin.domain.PlatformRole
import com.profiletailors.smp.platformadmin.domain.PlatformRoleAssignmentId
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitationId
import com.profiletailors.smp.platformadmin.domain.WaitlistInvitationStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("ddd-conformance")
internal class PlatformAdminMarkerCoverageTest {
    @Test
    fun platformValueObjectsAreMarked() {
        val valueObjects = listOf(
            AdminAuditAction::class.java,
            AdminAuditResult::class.java,
            InvitationDeliveryStatus::class.java,
            InvitationId::class.java,
            InvitationSource::class.java,
            InvitationStatus::class.java,
            PlatformPermission::class.java,
            PlatformRole::class.java,
            PlatformRoleAssignmentId::class.java,
            WaitlistInvitationId::class.java,
            WaitlistInvitationStatus::class.java,
        )

        assertThat(valueObjects)
            .allSatisfy { valueObject ->
                assertThat(valueObject.isAnnotationPresent(ValueObject::class.java))
                    .withFailMessage("${valueObject.simpleName} must be marked with @ValueObject")
                    .isTrue()
            }
    }

    @Test
    fun platformAggregateRootsAreMarked() {
        val aggregateRoots = listOf(Invitation::class.java)

        assertThat(aggregateRoots)
            .allSatisfy { aggregateRoot ->
                assertThat(aggregateRoot.isAnnotationPresent(AggregateRoot::class.java))
                    .withFailMessage("${aggregateRoot.simpleName} must be marked with @AggregateRoot")
                    .isTrue()
            }
    }
}
