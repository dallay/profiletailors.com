package com.profiletailors.smp.platformadmin.infrastructure

import com.profiletailors.smp.platformadmin.application.handler.AssignPlatformRoleHandler
import com.profiletailors.smp.platformadmin.application.handler.CancelWaitlistEntryHandler
import com.profiletailors.smp.platformadmin.application.handler.InviteWaitlistEntryHandler
import com.profiletailors.smp.platformadmin.application.handler.ResendWaitlistInvitationHandler
import com.profiletailors.smp.platformadmin.application.handler.RevokePlatformRoleHandler
import com.profiletailors.smp.platformadmin.application.handler.RevokeWaitlistInvitationHandler
import com.profiletailors.smp.platformadmin.application.ports.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.ports.PlatformRoleAssignmentRepository
import com.profiletailors.smp.platformadmin.application.ports.TokenHasher
import com.profiletailors.smp.platformadmin.application.ports.WaitlistEntryAdminPort
import com.profiletailors.smp.platformadmin.application.ports.WaitlistInvitationRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.Duration

@Configuration(proxyBeanMethods = false)
class PlatformAdminBootstrapConfiguration {

    @Bean
    fun tokenHasher(): TokenHasher = BCryptTokenHasher()

    @Bean
    fun inviteWaitlistEntryHandler(
        waitlistEntryPort: WaitlistEntryAdminPort,
        invitationRepository: WaitlistInvitationRepository,
        auditPublisher: AdministrativeAuditPublisher,
        clock: Clock,
        tokenHasher: TokenHasher,
        @Value("\${platform.admin.invitation.ttl-days:7}") ttlDays: Long,
    ): InviteWaitlistEntryHandler = InviteWaitlistEntryHandler(
        waitlistEntryPort = waitlistEntryPort,
        invitationRepository = invitationRepository,
        auditPublisher = auditPublisher,
        clock = clock,
        invitationTtl = Duration.ofDays(ttlDays),
        tokenHasher = tokenHasher,
    )

    @Bean
    fun resendWaitlistInvitationHandler(
        invitationRepository: WaitlistInvitationRepository,
        auditPublisher: AdministrativeAuditPublisher,
        clock: Clock,
        tokenHasher: TokenHasher,
        @Value("\${platform.admin.invitation.ttl-days:7}") ttlDays: Long,
        @Value("\${platform.admin.invitation.resend-limit:3}") resendLimit: Int,
        @Value("\${platform.admin.invitation.resend-window-hours:24}") resendWindowHours: Int,
    ): ResendWaitlistInvitationHandler = ResendWaitlistInvitationHandler(
        invitationRepository = invitationRepository,
        auditPublisher = auditPublisher,
        clock = clock,
        invitationTtl = Duration.ofDays(ttlDays),
        resendLimit = resendLimit,
        resendWindowHours = resendWindowHours,
        tokenHasher = tokenHasher,
    )

    @Bean
    fun revokeWaitlistInvitationHandler(
        invitationRepository: WaitlistInvitationRepository,
        auditPublisher: AdministrativeAuditPublisher,
        clock: Clock,
    ): RevokeWaitlistInvitationHandler = RevokeWaitlistInvitationHandler(
        invitationRepository = invitationRepository,
        auditPublisher = auditPublisher,
        clock = clock,
    )

    @Bean
    fun cancelWaitlistEntryHandler(
        waitlistEntryPort: WaitlistEntryAdminPort,
        invitationRepository: WaitlistInvitationRepository,
        auditPublisher: AdministrativeAuditPublisher,
        clock: Clock,
    ): CancelWaitlistEntryHandler = CancelWaitlistEntryHandler(
        waitlistEntryPort = waitlistEntryPort,
        invitationRepository = invitationRepository,
        auditPublisher = auditPublisher,
        clock = clock,
    )

    @Bean
    fun assignPlatformRoleHandler(
        roleAssignmentRepository: PlatformRoleAssignmentRepository,
        auditPublisher: AdministrativeAuditPublisher,
        clock: Clock,
    ): AssignPlatformRoleHandler = AssignPlatformRoleHandler(
        roleAssignmentRepository = roleAssignmentRepository,
        auditPublisher = auditPublisher,
        clock = clock,
    )

    @Bean
    fun revokePlatformRoleHandler(
        roleAssignmentRepository: PlatformRoleAssignmentRepository,
        auditPublisher: AdministrativeAuditPublisher,
        clock: Clock,
    ): RevokePlatformRoleHandler = RevokePlatformRoleHandler(
        roleAssignmentRepository = roleAssignmentRepository,
        auditPublisher = auditPublisher,
        clock = clock,
    )
}
