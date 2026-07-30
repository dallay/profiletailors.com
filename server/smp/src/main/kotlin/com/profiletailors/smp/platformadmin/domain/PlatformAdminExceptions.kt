package com.profiletailors.smp.platformadmin.domain

class PlatformAccessDeniedException(permission: PlatformPermission) :
    RuntimeException("Platform permission required: ${permission.key}")

class PlatformRoleRequiredException : RuntimeException("An active platform role assignment is required.")

class WaitlistEntryNotFoundException(id: String) : RuntimeException("Waitlist entry not found: $id")

class WaitlistEntryNotInvitableException(id: String, reason: String) :
    RuntimeException("Waitlist entry $id is not invitable: $reason")

class WaitlistEntryAlreadyConvertedException(id: String) :
    RuntimeException("Waitlist entry is already converted: $id")

class WaitlistEntryAlreadyCancelledException(id: String) :
    RuntimeException("Waitlist entry is already cancelled: $id")

class WaitlistEntryVersionConflictException(id: String) :
    RuntimeException("Concurrent modification detected for waitlist entry: $id")

class InvitationNotFoundException(id: String) : RuntimeException("Invitation not found: $id")

class InvitationAlreadyActiveException(waitlistEntryId: String) :
    RuntimeException("An active invitation already exists for waitlist entry: $waitlistEntryId")

class InvitationNotResendableException(id: String) : RuntimeException("Invitation cannot be resent: $id")

class InvitationNotRevocableException(id: String) : RuntimeException("Invitation cannot be revoked: $id")

class InvitationRateLimitExceededException(waitlistEntryId: String) :
    RuntimeException("Invitation resend rate limit exceeded for entry: $waitlistEntryId")

class UserNotFoundException(principalId: String) : RuntimeException("User not found: $principalId")
