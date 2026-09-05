package com.profiletailors.smp.platformadmin.infrastructure

import com.profiletailors.common.domain.bus.event.DomainEvent
import com.profiletailors.common.domain.bus.event.EventPublisher
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.platformadmin.application.AcceptInvitationHandler
import com.profiletailors.smp.platformadmin.application.InvitationAcceptanceRepository
import com.profiletailors.smp.platformadmin.application.InvitationAcceptanceRepositoryFacade
import com.profiletailors.smp.platformadmin.application.contracts.AcceptUrlTemplate
import com.profiletailors.smp.platformadmin.application.contracts.AdministrativeAuditPublisher
import com.profiletailors.smp.platformadmin.application.contracts.InvitationRepository
import com.profiletailors.smp.platformadmin.application.contracts.PlatformRoleAssignmentRepository
import com.profiletailors.smp.platformadmin.application.contracts.TokenHasher
import com.profiletailors.smp.platformadmin.application.contracts.WaitlistEntryAdmin
import com.profiletailors.smp.platformadmin.application.contracts.WaitlistInvitationRepository
import com.profiletailors.smp.platformadmin.application.handler.AssignPlatformRoleHandler
import com.profiletailors.smp.platformadmin.application.handler.CancelWaitlistEntryHandler
import com.profiletailors.smp.platformadmin.application.handler.InviteWaitlistEntryHandler
import com.profiletailors.smp.platformadmin.application.handler.ResendWaitlistInvitationHandler
import com.profiletailors.smp.platformadmin.application.handler.RevokePlatformRoleHandler
import com.profiletailors.smp.platformadmin.application.handler.RevokeWaitlistInvitationHandler
import com.profiletailors.smp.tenancy.application.R2dbcWorkspaceMembershipProvisioner
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipProvisioner
import com.profiletailors.smp.tenancy.application.WorkspaceMembershipRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.Duration

@Configuration(proxyBeanMethods = false)
@Suppress("TooManyFunctions")
class PlatformAdminBootstrapConfiguration {

    @Bean
    fun operatorAccessResolver(
        roleAssignmentRepository: PlatformRoleAssignmentRepository,
    ): com.profiletailors.smp.platformadmin.application.OperatorAccessResolver =
        com.profiletailors.smp.platformadmin.application.OperatorAccessResolver(roleAssignmentRepository)

    @Bean
    fun tokenHasher(): TokenHasher = BCryptTokenHasher()

    @Bean
    fun workspaceMembershipProvisioner(repository: WorkspaceMembershipRepository): WorkspaceMembershipProvisioner =
        R2dbcWorkspaceMembershipProvisioner(repository)

    @Bean
    fun invitationAcceptanceRepository(repository: InvitationRepository): InvitationAcceptanceRepository =
        InvitationAcceptanceRepositoryFacade(repository)

    @Bean
    fun acceptInvitationHandler(
        invitationRepository: InvitationAcceptanceRepository,
        tokenHasher: TokenHasher,
        principalIdentityLookup: PrincipalIdentityLookup,
        membershipProvisioner: WorkspaceMembershipProvisioner,
        transactionRunner: AtomicTransactionRunner,
        clock: Clock,
    ): AcceptInvitationHandler = AcceptInvitationHandler(
        invitationRepository = invitationRepository,
        tokenHasher = tokenHasher,
        principalIdentityLookup = principalIdentityLookup,
        membershipProvisioner = membershipProvisioner,
        transactionRunner = transactionRunner,
        clock = clock,
    )

    @Bean
    fun acceptUrlTemplate(
        @Value("\${platform.admin.accept-url-base:https://app.profiletailors.com/invitations/accept}") base: String,
    ): AcceptUrlTemplate = AcceptUrlTemplate { rawToken -> "$base?token=$rawToken" }

    @Bean
    fun inviteWaitlistEntryHandler(
        waitlistEntryAdmin: WaitlistEntryAdmin,
        invitationRepository: WaitlistInvitationRepository,
        auditPublisher: AdministrativeAuditPublisher,
        eventPublisher: EventPublisher<DomainEvent>,
        clock: Clock,
        tokenHasher: TokenHasher,
        acceptUrlTemplate: AcceptUrlTemplate,
        @Value("\${platform.admin.invitation.ttl-days:7}") ttlDays: Long,
    ): InviteWaitlistEntryHandler = InviteWaitlistEntryHandler(
        waitlistEntryAdmin = waitlistEntryAdmin,
        invitationRepository = invitationRepository,
        auditPublisher = auditPublisher,
        eventPublisher = eventPublisher,
        clock = clock,
        invitationTtl = Duration.ofDays(ttlDays),
        tokenHasher = tokenHasher,
        acceptUrlTemplate = acceptUrlTemplate,
    )

    @Bean
    fun resendWaitlistInvitationHandler(
        invitationRepository: WaitlistInvitationRepository,
        auditPublisher: AdministrativeAuditPublisher,
        eventPublisher: EventPublisher<DomainEvent>,
        clock: Clock,
        tokenHasher: TokenHasher,
        acceptUrlTemplate: AcceptUrlTemplate,
        waitlistEntryAdmin: WaitlistEntryAdmin,
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
        eventPublisher = eventPublisher,
        acceptUrlTemplate = acceptUrlTemplate,
        waitlistEntryAdmin = waitlistEntryAdmin,
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
        waitlistEntryAdmin: WaitlistEntryAdmin,
        invitationRepository: WaitlistInvitationRepository,
        auditPublisher: AdministrativeAuditPublisher,
        clock: Clock,
    ): CancelWaitlistEntryHandler = CancelWaitlistEntryHandler(
        waitlistEntryAdmin = waitlistEntryAdmin,
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
