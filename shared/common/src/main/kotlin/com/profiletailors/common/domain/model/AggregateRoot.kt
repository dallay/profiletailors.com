package com.profiletailors.common.domain.model

/**
 * Base class for DDD Aggregate Roots.
 *
 * An aggregate root is the entry point and consistency boundary for a cluster of domain
 * objects (entities and value objects). External references to the aggregate are only
 * allowed to the root, ensuring that invariants are enforced consistently.
 *
 * Unlike a plain [BaseEntity], an AggregateRoot:
 * - Serves as the transactional boundary: all changes within the aggregate are committed
 *   atomically.
 * - Acts as the event source: domain events recorded via [record] represent state changes
 *   that cross the aggregate boundary.
 *
 * @param ID the type of the aggregate root's identity
 * @since 1.0.0
 * @see BaseEntity
 */
@Suppress("unused")
abstract class AggregateRoot<ID> : BaseEntity<ID>()
