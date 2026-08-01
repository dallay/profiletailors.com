package com.profiletailors.smp.mcp.adapter

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.mcp.infrastructure.ApplicationError
import com.profiletailors.smp.mcp.infrastructure.McpErrorMapper
import com.profiletailors.smp.publishing.application.CalendarResponse
import com.profiletailors.smp.publishing.application.GetCalendarPublicationsQuery
import com.profiletailors.smp.publishing.application.ListPublicationsQuery
import com.profiletailors.smp.publishing.application.ListPublicationsResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant

@Tag("fast")
class PublicationToolsAdapterTest {

    private val mediator: Mediator = mockk()
    private val errorMapper = McpErrorMapper()
    private val adapter = PublicationToolsAdapter(mediator, errorMapper)

    @Test
    fun `list_publications delegates to mediator with parsed dates`() = runTest {
        val response = ListPublicationsResponse(publications = emptyList(), total = 0)
        coEvery { mediator.send(any<ListPublicationsQuery>()) } returns response

        val result = adapter.listPublications(
            from = "2024-01-01T00:00:00Z",
            to = "2024-01-31T23:59:59Z",
            status = null,
            channelId = null,
            timezone = "UTC",
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(result.data).isNotNull
        coVerify {
            mediator.send(
                match<ListPublicationsQuery> {
                    it.from == Instant.parse("2024-01-01T00:00:00Z") &&
                        it.to == Instant.parse("2024-01-31T23:59:59Z")
                },
            )
        }
    }

    @Test
    fun `list_publications returns error on invalid date format`() = runTest {
        val result = adapter.listPublications(
            from = "not-a-date",
            to = "2024-01-31T23:59:59Z",
            status = null,
            channelId = null,
            timezone = "UTC",
        )

        assertThat(result.isSuccess).isFalse()
        assertThat(result.error).isNotNull
        assertThat(result.error!!.code).isEqualTo("invalid_date_range")
    }

    @Test
    fun `get_calendar delegates to mediator`() = runTest {
        val response = CalendarResponse(publications = emptyList(), conflicts = emptyList(), activity = emptyList())
        coEvery { mediator.send(any<GetCalendarPublicationsQuery>()) } returns response

        val result = adapter.getCalendar(
            from = "2024-01-01T00:00:00Z",
            to = "2024-01-31T23:59:59Z",
            status = null,
            channelId = null,
            timezone = "America/New_York",
        )

        assertThat(result.isSuccess).isTrue()
        coVerify {
            mediator.send(
                match<GetCalendarPublicationsQuery> {
                    it.timezone == "America/New_York"
                },
            )
        }
    }

    @Test
    fun `get_calendar returns error when mediator throws`() = runTest {
        coEvery { mediator.send(any<GetCalendarPublicationsQuery>()) } throws
            RuntimeException("unexpected")

        val result = adapter.getCalendar(
            from = "2024-01-01T00:00:00Z",
            to = "2024-01-31T23:59:59Z",
            status = null,
            channelId = null,
            timezone = "UTC",
        )

        assertThat(result.isSuccess).isFalse()
        assertThat(result.error!!.code).isEqualTo("internal")
    }
}
