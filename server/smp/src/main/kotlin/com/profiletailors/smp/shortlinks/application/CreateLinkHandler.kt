package com.profiletailors.smp.shortlinks.application

import com.profiletailors.common.domain.Service
import com.profiletailors.common.domain.bus.command.CommandWithResultHandler
import com.profiletailors.common.domain.context.ResourceContextProvider
import com.profiletailors.common.domain.persistence.AtomicTransactionRunner
import com.profiletailors.smp.shortlinks.domain.AliasAlreadyExistsException
import com.profiletailors.smp.shortlinks.domain.CustomAlias
import com.profiletailors.smp.shortlinks.domain.DestinationUrl
import com.profiletailors.smp.shortlinks.domain.DomainId
import com.profiletailors.smp.shortlinks.domain.IdempotencyKeyConflictException
import com.profiletailors.smp.shortlinks.domain.IdempotencyPort
import com.profiletailors.smp.shortlinks.domain.IdempotencyRecord
import com.profiletailors.smp.shortlinks.domain.IdempotencyRequestInProgressException
import com.profiletailors.smp.shortlinks.domain.InvalidDestinationUrlException
import com.profiletailors.smp.shortlinks.domain.Link
import com.profiletailors.smp.shortlinks.domain.LinkCachePort
import com.profiletailors.smp.shortlinks.domain.LinkId
import com.profiletailors.smp.shortlinks.domain.LinkPolicies
import com.profiletailors.smp.shortlinks.domain.LinkRepository
import com.profiletailors.smp.shortlinks.domain.OwnerId
import com.profiletailors.smp.shortlinks.domain.ReservedAliasException
import com.profiletailors.smp.shortlinks.domain.ShortCode
import com.profiletailors.smp.shortlinks.domain.ShortCodeCollisionException
import com.profiletailors.smp.shortlinks.domain.ShortCodeCollisionExhaustedException
import com.profiletailors.smp.shortlinks.domain.ShortCodeGenerator
import com.profiletailors.smp.tenancy.application.requireWorkspaceContext
import java.security.MessageDigest
import java.time.Clock

private const val ID_FIELD = 0
private const val SHORT_CODE_FIELD = 1
private const val SHORT_URL_FIELD = 2
private const val DESTINATION_URL_FIELD = 3
private const val STATUS_FIELD = 4
private const val CREATED_AT_FIELD = 5
private const val EXPIRES_AT_FIELD = 6
private const val VERSION_FIELD = 7

@Service
internal class CreateLinkHandler(
    private val resourceContextProvider: ResourceContextProvider,
    private val linkRepository: LinkRepository,
    private val shortCodeGenerator: ShortCodeGenerator,
    private val transactionRunner: AtomicTransactionRunner,
    private val clock: Clock,
    private val shortLinksProperties: ShortLinksConfigProperties,
    private val idempotencyPort: IdempotencyPort,
    private val linkCachePort: LinkCachePort,
) : CommandWithResultHandler<CreateLinkCommand, LinkResult> {

    override suspend fun handle(command: CreateLinkCommand): LinkResult {
        val context = resourceContextProvider.requireWorkspaceContext()
        val ownerId = OwnerId.from(requireNotNull(context.workspaceId))
        val domainId = DomainId.fromHost(shortLinksProperties.publicHost)
        val payloadHash = payloadHash(command)

        var attempts = 0
        var shortCode = resolveShortCode(command.customAlias)
        while (true) {
            try {
                val outcome = transactionRunner.runAtomically {
                    val existing = command.idempotencyKey?.let {
                        idempotencyPort.findStoredResult(it, ownerId)
                    }
                    val claimed = claimIdempotencyKey(command, ownerId, payloadHash, existing)
                    if (claimed != null) {
                        val replayed = existingResult(
                            command.idempotencyKey,
                            payloadHash,
                            claimed.payloadHash,
                            claimed.resultJson,
                        )
                        CreateOutcome(replayed, null)
                    } else {
                        val created = createLink(command, ownerId, domainId, payloadHash, shortCode)
                        CreateOutcome(created, created.shortCode)
                    }
                }
                outcome.createdShortCode?.let {
                    linkCachePort.evict(shortLinksProperties.publicHost, it)
                }
                return outcome.result
            } catch (collision: ShortCodeCollisionException) {
                attempts += 1
                if (command.customAlias != null) {
                    throw AliasAlreadyExistsException(command.customAlias, domainId, collision)
                }
                if (attempts >= LinkPolicies.MAX_COLLISION_RETRIES) {
                    throw ShortCodeCollisionExhaustedException(LinkPolicies.MAX_COLLISION_RETRIES, collision)
                }
                shortCode = shortCodeGenerator.generate()
            }
        }
    }

    private suspend fun claimIdempotencyKey(
        command: CreateLinkCommand,
        ownerId: OwnerId,
        payloadHash: String,
        existing: IdempotencyRecord?,
    ): IdempotencyRecord? {
        if (existing != null) return existing
        val key = command.idempotencyKey ?: return null
        if (idempotencyPort.claim(key, ownerId, payloadHash)) return null
        val claimed = idempotencyPort.findStoredResult(key, ownerId)
            ?: throw IdempotencyRequestInProgressException(key)
        if (claimed.resultJson.isEmpty()) throw IdempotencyRequestInProgressException(key)
        return claimed
    }

    private suspend fun createLink(
        command: CreateLinkCommand,
        ownerId: OwnerId,
        domainId: DomainId,
        payloadHash: String,
        shortCode: ShortCode,
    ): LinkResult {
        val destinationUrl = try {
            DestinationUrl(command.destinationUrl)
        } catch (e: IllegalArgumentException) {
            throw InvalidDestinationUrlException(command.destinationUrl, e.message ?: "Invalid URL", e)
        }

        val link = Link.create(
            id = LinkId.generate(),
            ownerId = ownerId,
            domainId = domainId,
            shortCode = shortCode,
            destinationUrl = destinationUrl,
            expiresAt = command.expiresAt,
            now = clock.instant(),
        )

        val saved = linkRepository.save(link)
        command.idempotencyKey?.let { key ->
            val result = saved.toResult(shortLinksProperties.shortUrlBase)
            idempotencyPort.store(key, ownerId, payloadHash, result.toStoredJson())
        }
        return saved.toResult(shortLinksProperties.shortUrlBase)
    }

    private fun existingResult(key: String?, payloadHash: String, storedHash: String, resultJson: String): LinkResult {
        if (storedHash != payloadHash) {
            throw IdempotencyKeyConflictException(requireNotNull(key))
        }
        if (resultJson.isEmpty()) {
            throw IdempotencyRequestInProgressException(requireNotNull(key))
        }
        return storedResult(resultJson)
    }

    private fun payloadHash(command: CreateLinkCommand): String {
        val normalized = listOf(
            command.destinationUrl,
            command.customAlias.orEmpty(),
            command.expiresAt?.toString().orEmpty(),
        ).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun storedResult(json: String): LinkResult {
        val fields = json.split("|")
        require(fields.size == VERSION_FIELD + 1) { "Stored idempotency result is malformed" }
        return LinkResult(
            id = java.util.UUID.fromString(fields[ID_FIELD]),
            shortCode = fields[SHORT_CODE_FIELD],
            shortUrl = fields[SHORT_URL_FIELD],
            destinationUrl = fields[DESTINATION_URL_FIELD],
            status = fields[STATUS_FIELD],
            createdAt = java.time.Instant.parse(fields[CREATED_AT_FIELD]),
            expiresAt = fields[EXPIRES_AT_FIELD]
                .takeIf(String::isNotEmpty)
                ?.let(java.time.Instant::parse),
            version = fields[VERSION_FIELD].toLong(),
        )
    }

    private fun LinkResult.toStoredJson(): String = listOf(
        id.toString(),
        shortCode,
        shortUrl,
        destinationUrl,
        status,
        createdAt.toString(),
        expiresAt?.toString().orEmpty(),
        version.toString(),
    ).joinToString("|")

    private fun resolveShortCode(customAlias: String?): ShortCode {
        if (customAlias == null) {
            return shortCodeGenerator.generate()
        }
        if (customAlias in LinkPolicies.RESERVED_ALIASES) {
            throw ReservedAliasException(customAlias)
        }
        return CustomAlias(customAlias).toShortCode()
    }

    private data class CreateOutcome(val result: LinkResult, val createdShortCode: String?)
}
