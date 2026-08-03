package com.profiletailors.smp.analytics.infrastructure.http

import com.profiletailors.common.domain.bus.Mediator
import com.profiletailors.smp.analytics.application.ExportAnalyticsCommand
import com.profiletailors.smp.analytics.application.GetAnalyticsOverviewQuery
import com.profiletailors.smp.analytics.application.GetBestTimesQuery
import com.profiletailors.smp.analytics.application.GetPostAnalyticsQuery
import com.profiletailors.smp.analytics.domain.AnalyticsOverview
import com.profiletailors.smp.analytics.domain.BestTimesRecommendation
import com.profiletailors.smp.analytics.domain.PostAnalyticsList
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@Validated
@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Analytics", description = "Analytics and post performance endpoints")
class AnalyticsController(private val mediator: Mediator) {

    @Operation(summary = "Get analytics overview for a date range")
    @GetMapping("/overview", version = "1")
    suspend fun getOverview(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
    ): AnalyticsOverview {
        val end = endDate ?: LocalDate.now()
        val start = startDate ?: end.minusDays(DEFAULT_RANGE_DAYS)
        return mediator.send(GetAnalyticsOverviewQuery(startDate = start, endDate = end))
    }

    @Operation(summary = "Get per-post analytics with pagination")
    @GetMapping("/posts", version = "1")
    suspend fun getPostAnalytics(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): PostAnalyticsList {
        val end = endDate ?: LocalDate.now()
        val start = startDate ?: end.minusDays(DEFAULT_RANGE_DAYS)
        return mediator.send(GetPostAnalyticsQuery(startDate = start, endDate = end, page = page, size = size))
    }

    @Operation(summary = "Get recommended posting times based on engagement history")
    @GetMapping("/best-times", version = "1")
    suspend fun getBestTimes(): BestTimesRecommendation = mediator.send(GetBestTimesQuery)

    @Operation(summary = "Export analytics as CSV")
    @PostMapping("/export", version = "1")
    suspend fun exportAnalytics(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
    ): ResponseEntity<ByteArray> {
        val end = endDate ?: LocalDate.now()
        val start = startDate ?: end.minusDays(DEFAULT_RANGE_DAYS)
        val result = mediator.send(ExportAnalyticsCommand(startDate = start, endDate = end))
        val headers = HttpHeaders()
        headers.contentType = MediaType.parseMediaType("text/csv")
        headers.setContentDispositionFormData("attachment", "analytics-$start-$end.csv")
        return ResponseEntity.ok()
            .headers(headers)
            .body(result.csvContent.toByteArray(Charsets.UTF_8))
    }

    private companion object {
        const val DEFAULT_RANGE_DAYS = 29L
    }
}
