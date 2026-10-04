package com.profiletailors.smp.shortlinks.domain

sealed class LinkException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class LinkNotFoundException(val linkId: LinkId) : LinkException("Link not found: ${linkId.value}")

class AliasAlreadyExistsException(val alias: String, val domainId: DomainId, cause: Throwable? = null) :
    LinkException("Alias '$alias' already exists for domain ${domainId.value}", cause)

class ReservedAliasException(val alias: String) : LinkException("Alias '$alias' is reserved")

class InvalidDestinationUrlException(val url: String, val reason: String, cause: Throwable? = null) :
    LinkException("Invalid destination URL '$url': $reason", cause)

class ShortCodeCollisionException(cause: Throwable) :
    LinkException("Short code already exists for the domain", cause)

class ShortCodeCollisionExhaustedException(val retries: Int, cause: Throwable? = null) :
    LinkException("Failed to generate unique short code after $retries attempts", cause)

class LinkVersionConflictException(val linkId: LinkId, val expected: Long, val actual: Long) :
    LinkException("Version conflict for link ${linkId.value}: expected $expected, got $actual")

class LinkStateTransitionException(val linkId: LinkId, val from: LinkStatus, val to: LinkStatus) :
    LinkException("Cannot transition link ${linkId.value} from $from to $to")

class IdempotencyKeyConflictException(val key: String) :
    LinkException("Idempotency key '$key' was already used with a different payload")

class IdempotencyRequestInProgressException(val key: String) :
    LinkException("A request with idempotency key '$key' is still in progress")

class DeletedCodeRetentionException(val shortCode: ShortCode, val domainId: DomainId) :
    LinkException("Short code '${shortCode.value}' on domain ${domainId.value} is within the retention window")
