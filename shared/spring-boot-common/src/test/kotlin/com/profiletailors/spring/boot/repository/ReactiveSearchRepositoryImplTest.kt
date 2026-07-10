package com.profiletailors.spring.boot.repository

import com.profiletailors.common.domain.presentation.pagination.TimestampCursor
import com.profiletailors.common.domain.presentation.sort.Direction
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.r2dbc.core.ReactiveSelectOperation.ReactiveSelect
import org.springframework.data.r2dbc.core.ReactiveSelectOperation.TerminatingSelect
import org.springframework.data.relational.core.query.Criteria
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import kotlin.reflect.KClass

@Suppress("LargeClass")
class ReactiveSearchRepositoryImplTest {

    data class TestEntity(val id: String, val name: String)

    data class TimestampEntity(val id: String, val name: String, val createdAt: LocalDateTime)

    private val r2dbcTemplate = mockk<R2dbcEntityTemplate>(relaxed = true)

    @Suppress("UNCHECKED_CAST")
    private val repository = ReactiveSearchRepositoryImpl<Any>(r2dbcTemplate)

    @Suppress("UNCHECKED_CAST")
    private val selectMock = mockk<ReactiveSelect<Any>>()

    @Suppress("UNCHECKED_CAST")
    private val termSelectMock = mockk<TerminatingSelect<Any>>()

    private fun setupChain(entities: List<Any>, count: Long = entities.size.toLong()) {
        val selectSlot = slot<Class<*>>()
        every { r2dbcTemplate.select(capture(selectSlot)) } returns selectMock
        every { selectMock.matching(any()) } returns termSelectMock
        every { termSelectMock.all() } returns Flux.fromIterable(entities)
        every { termSelectMock.count() } returns Mono.just(count)
    }

    // region findAll(criteria, domainType) — Flow

    @Test
    fun `should return flow of entities matching criteria`() = runTest {
        val entities = listOf(TestEntity("1", "Alice"), TestEntity("2", "Bob"))
        setupChain(entities)

        @Suppress("UNCHECKED_CAST")
        val flow = repository.findAll(Criteria.empty(), TestEntity::class as KClass<Any>)
        val result = flow.toList()

        result shouldHaveSize 2
        result shouldBe entities
    }

    @Test
    fun `should return empty flow when no entities match`() = runTest {
        setupChain(emptyList())

        @Suppress("UNCHECKED_CAST")
        val flow = repository.findAll(Criteria.empty(), TestEntity::class as KClass<Any>)
        val result = flow.toList()

        result.shouldBeEmpty()
    }

    // endregion

    // region findAll(criteria, pageable, domainType) — Page

    @Test
    fun `should return page with entities and correct total count`() = runTest {
        val entities = listOf(TestEntity("1", "Alice"), TestEntity("2", "Bob"))
        // Use count > pageSize to avoid Spring Data's PageImpl last-page mitigation
        // which overrides total with offset + content.size() when offset + pageSize > total
        val totalCount = 15L

        val freshR2dbcTemplate = mockk<R2dbcEntityTemplate>(relaxed = true)
        val freshSelect = mockk<ReactiveSelect<Any>>(relaxed = true)
        val freshTerminatingSelect = mockk<TerminatingSelect<Any>>(relaxed = true)

        every { freshR2dbcTemplate.select(any<Class<Any>>()) } returns freshSelect
        every { freshSelect.matching(any()) } returns freshTerminatingSelect
        every { freshTerminatingSelect.all() } returns Flux.fromIterable(entities)
        every { freshTerminatingSelect.count() } answers { Mono.just(totalCount) }

        @Suppress("UNCHECKED_CAST")
        val freshRepository = ReactiveSearchRepositoryImpl<Any>(freshR2dbcTemplate)
        val page = freshRepository.findAll(
            Criteria.empty(),
            PageRequest.of(0, 10),
            TestEntity::class as KClass<Any>,
        )

        page.content shouldHaveSize 2
        page.totalElements shouldBe totalCount
        page.number shouldBe 0
        page.size shouldBe 10
    }

    @Test
    fun `should return empty page when no entities match`() = runTest {
        setupChain(emptyList(), count = 0)

        @Suppress("UNCHECKED_CAST")
        val page = repository.findAll(
            Criteria.empty(),
            PageRequest.of(0, 10),
            TestEntity::class as KClass<Any>,
        )

        page.content.shouldBeEmpty()
        page.totalElements shouldBe 0L
    }

    @Test
    fun `should pass correct pageable to fetch query`() = runTest {
        val entities = listOf(TestEntity("1", "Alice"))
        setupChain(entities, count = 1)

        @Suppress("UNCHECKED_CAST")
        val page = repository.findAll(
            Criteria.empty(),
            PageRequest.of(2, 5),
            TestEntity::class as KClass<Any>,
        )

        page.number shouldBe 2
        page.size shouldBe 5
    }

    // endregion

    // region findAllByCursor — ASC direction

    @Test
    fun `should return entities with next cursor when more results exist`() = runTest {
        val now = LocalDateTime.now()
        val entities = listOf(
            TimestampEntity("1", "A", now),
            TimestampEntity("2", "B", now.plusHours(1)),
            TimestampEntity("3", "C", now.plusHours(2)),
        )
        setupChain(entities)

        @Suppress("UNCHECKED_CAST")
        val result = repository.findAllByCursor(
            criteria = Criteria.empty(),
            size = 2,
            domainType = TimestampEntity::class as KClass<Any>,
            sort = Sort.by(Sort.Direction.ASC, "createdAt"),
            cursor = TimestampCursor.DEFAULT_CURSOR,
        )

        result.data shouldHaveSize 2
        result.nextPageCursor shouldNotBe null
        result.prevPageCursor shouldBe null
    }

    @Test
    fun `should return entities without next cursor when they fit in page`() = runTest {
        val now = LocalDateTime.now()
        val entities = listOf(
            TimestampEntity("1", "A", now),
            TimestampEntity("2", "B", now.plusHours(1)),
        )
        setupChain(entities)

        @Suppress("UNCHECKED_CAST")
        val result = repository.findAllByCursor(
            criteria = Criteria.empty(),
            size = 5,
            domainType = TimestampEntity::class as KClass<Any>,
            sort = Sort.by(Sort.Direction.ASC, "createdAt"),
            cursor = TimestampCursor.DEFAULT_CURSOR,
        )

        result.data shouldHaveSize 2
        result.nextPageCursor shouldBe null
        result.prevPageCursor shouldBe null
    }

    @Test
    fun `should return previous cursor when not on first page`() = runTest {
        val now = LocalDateTime.now()
        val entities = listOf(
            TimestampEntity("2", "B", now.plusHours(1)),
            TimestampEntity("3", "C", now.plusHours(2)),
        )
        setupChain(entities)

        val cursor = TimestampCursor(now, Direction.ASC)

        @Suppress("UNCHECKED_CAST")
        val result = repository.findAllByCursor(
            criteria = Criteria.empty(),
            size = 2,
            domainType = TimestampEntity::class as KClass<Any>,
            sort = Sort.by(Sort.Direction.ASC, "createdAt"),
            cursor = cursor,
        )

        result.data shouldHaveSize 2
        result.prevPageCursor shouldNotBe null
    }

    @Test
    fun `should return no previous cursor on first page`() = runTest {
        val now = LocalDateTime.now()
        val entities = listOf(TimestampEntity("1", "A", now))
        setupChain(entities)

        @Suppress("UNCHECKED_CAST")
        val result = repository.findAllByCursor(
            criteria = Criteria.empty(),
            size = 5,
            domainType = TimestampEntity::class as KClass<Any>,
            sort = Sort.by(Sort.Direction.ASC, "createdAt"),
            cursor = TimestampCursor.DEFAULT_CURSOR,
        )

        result.prevPageCursor shouldBe null
    }

    @Test
    fun `should handle empty results from database`() = runTest {
        setupChain(emptyList())

        @Suppress("UNCHECKED_CAST")
        val result = repository.findAllByCursor(
            criteria = Criteria.empty(),
            size = 10,
            domainType = TimestampEntity::class as KClass<Any>,
            sort = Sort.by(Sort.Direction.ASC, "createdAt"),
            cursor = TimestampCursor.DEFAULT_CURSOR,
        )

        result.data.shouldBeEmpty()
        result.nextPageCursor shouldBe null
        result.prevPageCursor shouldBe null
    }

    @Test
    fun `should truncate to size entities even when more are returned`() = runTest {
        val now = LocalDateTime.now()
        val entities = (1..10).map {
            TimestampEntity("$it", "Entity $it", now.plusHours(it.toLong()))
        }
        setupChain(entities)

        @Suppress("UNCHECKED_CAST")
        val result = repository.findAllByCursor(
            criteria = Criteria.empty(),
            size = 3,
            domainType = TimestampEntity::class as KClass<Any>,
            sort = Sort.by(Sort.Direction.ASC, "createdAt"),
            cursor = TimestampCursor.DEFAULT_CURSOR,
        )

        result.data shouldHaveSize 3
        result.nextPageCursor shouldNotBe null
    }

    @Test
    fun `should use ASC direction for next cursor serialization`() = runTest {
        val now = LocalDateTime.now()
        val entities = listOf(
            TimestampEntity("1", "A", now),
            TimestampEntity("2", "B", now.plusHours(1)),
            TimestampEntity("3", "C", now.plusHours(2)),
        )
        setupChain(entities)

        @Suppress("UNCHECKED_CAST")
        val result = repository.findAllByCursor(
            criteria = Criteria.empty(),
            size = 2,
            domainType = TimestampEntity::class as KClass<Any>,
            sort = Sort.by(Sort.Direction.ASC, "createdAt"),
            cursor = TimestampCursor.DEFAULT_CURSOR,
        )

        result.nextPageCursor shouldNotBe null
        val nextCursor = requireNotNull(result.nextPageCursor)
        val decoded = TimestampCursor.deserialize(nextCursor)
        decoded.direction shouldBe Direction.ASC
    }

    @Test
    fun `should call cursor serialize with correct direction for prev cursor`() = runTest {
        val now = LocalDateTime.now()
        val entities = listOf(
            TimestampEntity("2", "B", now.plusHours(1)),
            TimestampEntity("3", "C", now.plusHours(2)),
        )
        setupChain(entities)

        val cursor = TimestampCursor(now, Direction.ASC)

        @Suppress("UNCHECKED_CAST")
        val result = repository.findAllByCursor(
            criteria = Criteria.empty(),
            size = 5,
            domainType = TimestampEntity::class as KClass<Any>,
            sort = Sort.by(Sort.Direction.ASC, "createdAt"),
            cursor = cursor,
        )

        result.prevPageCursor shouldNotBe null
        val prevCursor = requireNotNull(result.prevPageCursor)
        val decoded = TimestampCursor.deserialize(prevCursor)
        decoded.direction shouldBe Direction.DESC
    }

    @Test
    fun `should call cursor serialize with content last for next cursor ASC`() = runTest {
        val now = LocalDateTime.now()
        val entities = listOf(
            TimestampEntity("1", "A", now),
            TimestampEntity("2", "B", now.plusHours(1)),
            TimestampEntity("3", "C", now.plusHours(2)),
        )
        setupChain(entities)

        @Suppress("UNCHECKED_CAST")
        val result = repository.findAllByCursor(
            criteria = Criteria.empty(),
            size = 2,
            domainType = TimestampEntity::class as KClass<Any>,
            sort = Sort.by(Sort.Direction.ASC, "createdAt"),
            cursor = TimestampCursor.DEFAULT_CURSOR,
        )

        result.nextPageCursor shouldNotBe null
        val nextCursor = requireNotNull(result.nextPageCursor)
        val decoded = TimestampCursor.deserialize(nextCursor)
        decoded.createdAt shouldBe now.plusHours(1)
        decoded.direction shouldBe Direction.ASC
    }

    // endregion

    // region findAllByCursor — DESC direction

    @Test
    fun `should use DESC direction for cursor serialization`() = runTest {
        val now = LocalDateTime.now()
        val entities = listOf(
            TimestampEntity("3", "C", now.plusHours(2)),
            TimestampEntity("2", "B", now.plusHours(1)),
        )
        setupChain(entities)

        val cursor = TimestampCursor(now.plusHours(3), Direction.DESC)

        @Suppress("UNCHECKED_CAST")
        val result = repository.findAllByCursor(
            criteria = Criteria.empty(),
            size = 1,
            domainType = TimestampEntity::class as KClass<Any>,
            sort = Sort.by(Sort.Direction.DESC, "createdAt"),
            cursor = cursor,
        )

        result.data shouldHaveSize 1
        result.nextPageCursor shouldNotBe null
    }

    @Test
    fun `should return next and prev cursors for DESC direction on middle page`() = runTest {
        val now = LocalDateTime.now()
        val entities = listOf(
            TimestampEntity("1", "A", now),
            TimestampEntity("2", "B", now.plusHours(1)),
            TimestampEntity("3", "C", now.plusHours(2)),
        )
        setupChain(entities)

        val cursor = TimestampCursor(now.plusHours(3), Direction.DESC)

        @Suppress("UNCHECKED_CAST")
        val result = repository.findAllByCursor(
            criteria = Criteria.empty(),
            size = 2,
            domainType = TimestampEntity::class as KClass<Any>,
            sort = Sort.by(Sort.Direction.DESC, "createdAt"),
            cursor = cursor,
        )

        result.data shouldHaveSize 2
        result.nextPageCursor shouldNotBe null
        result.prevPageCursor shouldNotBe null
    }

    @Test
    fun `should not return prev cursor when content is empty even with non-default cursor`() = runTest {
        setupChain(emptyList())

        val cursor = TimestampCursor(LocalDateTime.now(), Direction.ASC)

        @Suppress("UNCHECKED_CAST")
        val result = repository.findAllByCursor(
            criteria = Criteria.empty(),
            size = 10,
            domainType = TimestampEntity::class as KClass<Any>,
            sort = Sort.by(Sort.Direction.ASC, "createdAt"),
            cursor = cursor,
        )

        result.data.shouldBeEmpty()
        result.prevPageCursor shouldBe null
        result.nextPageCursor shouldBe null
    }

    // endregion

    // region findAllByCursor — criteria and sort

    @Test
    fun `should truncate results to page size and indicate more pages when extra row exists`() = runTest {
        val now = LocalDateTime.now()
        val entities = (1..6).map {
            TimestampEntity("$it", "Entity $it", now.plusHours(it.toLong()))
        }
        setupChain(entities)

        @Suppress("UNCHECKED_CAST")
        val result = repository.findAllByCursor(
            criteria = Criteria.empty(),
            size = 5,
            domainType = TimestampEntity::class as KClass<Any>,
            sort = Sort.by(Sort.Direction.ASC, "createdAt"),
            cursor = TimestampCursor.DEFAULT_CURSOR,
        )

        result.data shouldHaveSize 5
        result.nextPageCursor shouldNotBe null
    }

    @Test
    fun `should combine base criteria with cursor criteria`() = runTest {
        val now = LocalDateTime.now()
        val entities = listOf(TimestampEntity("1", "A", now))
        setupChain(entities)

        val baseCriteria = Criteria.empty().and(
            Criteria.where("name").`is`("A"),
        )

        @Suppress("UNCHECKED_CAST")
        val result = repository.findAllByCursor(
            criteria = baseCriteria,
            size = 10,
            domainType = TimestampEntity::class as KClass<Any>,
            sort = Sort.by(Sort.Direction.ASC, "createdAt"),
            cursor = TimestampCursor.DEFAULT_CURSOR,
        )

        result.data shouldHaveSize 1
    }

    // endregion
}
