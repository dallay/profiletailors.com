<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useDashboardStore } from '@/stores/dashboard'
import { useAnalyticsStore } from '@/stores/analytics'
import { useInsightsStore } from '@/stores/insights'
import { useContentPipelineStore } from '@/stores/contentPipeline'

// Mock data for sections without dedicated stores
import { upcomingSchedule } from '@/lib/mockData/scheduling'
import { inboxItems, teamMembers, teamActivity } from '@/lib/mockData/engagement'

// Section components
import ExecutiveOverview from './ExecutiveOverview.vue'
import AiInsightsHero from './AiInsightsHero.vue'
import GrowthScore from './GrowthScore.vue'
import TopPerformingPosts from './TopPerformingPosts.vue'
import CrossChannelAnalytics from './CrossChannelAnalytics.vue'
import AudienceGrowthChart from './AudienceGrowthChart.vue'
import UpcomingSchedule from './UpcomingSchedule.vue'
import BestPostingTimes from './BestPostingTimes.vue'
import ContentPipeline from './ContentPipeline.vue'
import InboxSummary from './InboxSummary.vue'
import TeamActivity from './TeamActivity.vue'

const { t } = useI18n()
const dashboard = useDashboardStore()
const analytics = useAnalyticsStore()
const insights = useInsightsStore()
const pipeline = useContentPipelineStore()

onMounted(() => {
  dashboard.refreshAll()
})

function handleDismissInsight(id: string): void {
  insights.dismiss(id)
}

function handleMoveCard(cardId: string, fromColumn: string, toColumn: string, toIndex?: number): void {
  pipeline.moveCard(cardId, fromColumn, toColumn, toIndex)
}
</script>

<template>
  <!-- Loading skeleton gate -->
  <div v-if="dashboard.isLoading" class="space-y-6">
    <div class="h-8 w-48 rounded bg-[var(--background-surface)] animate-pulse" />
    <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
      <div
        v-for="i in 4"
        :key="i"
        class="h-28 rounded-xl bg-[var(--background-surface)] animate-pulse"
      />
    </div>
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <div
        v-for="i in 4"
        :key="`row-${i}`"
        class="h-64 rounded-xl bg-[var(--background-surface)] animate-pulse"
      />
    </div>
    <div class="h-80 rounded-xl bg-[var(--background-surface)] animate-pulse" />
  </div>

  <!-- Dashboard content -->
  <div v-else class="space-y-8">
    <!-- Section: Executive Overview (KPI row) -->
    <ExecutiveOverview
      v-if="dashboard.sectionVisibility.executiveOverview"
      :kpis="analytics.kpiMetrics"
    />

    <!-- Section: AI Insights Hero -->
    <AiInsightsHero
      v-if="dashboard.sectionVisibility.aiInsights"
      :insights="insights.activeInsights"
      @dismiss="handleDismissInsight"
    />

    <!-- Row: Growth Score + Top Performing Posts -->
    <div
      v-if="dashboard.sectionVisibility.growthScore || dashboard.sectionVisibility.contentPerformance"
      class="grid grid-cols-1 lg:grid-cols-3 gap-4"
    >
      <GrowthScore
        v-if="dashboard.sectionVisibility.growthScore"
        :score="analytics.growthScore"
        class="lg:col-span-1"
      />
      <TopPerformingPosts
        v-if="dashboard.sectionVisibility.contentPerformance"
        :posts="analytics.topPosts"
        class="lg:col-span-2"
      />
    </div>

    <!-- Row: Cross-Channel Analytics + Audience Growth -->
    <div
      v-if="dashboard.sectionVisibility.crossChannel || dashboard.sectionVisibility.audienceGrowth"
      class="grid grid-cols-1 lg:grid-cols-2 gap-4"
    >
      <CrossChannelAnalytics
        v-if="dashboard.sectionVisibility.crossChannel"
        :channels="analytics.channelPerformance"
      />
      <AudienceGrowthChart
        v-if="dashboard.sectionVisibility.audienceGrowth"
        :data="analytics.audienceGrowth"
      />
    </div>

    <!-- Row: Upcoming Schedule + Best Posting Times -->
    <div
      v-if="dashboard.sectionVisibility.upcomingSchedule || dashboard.sectionVisibility.postingTimes"
      class="grid grid-cols-1 lg:grid-cols-2 gap-4"
    >
      <UpcomingSchedule
        v-if="dashboard.sectionVisibility.upcomingSchedule"
        :items="upcomingSchedule"
      />
      <BestPostingTimes
        v-if="dashboard.sectionVisibility.postingTimes"
        :slots="analytics.postingTimeSlots"
      />
    </div>

    <!-- Section: Content Pipeline (full width, horizontal scroll on mobile) -->
    <ContentPipeline
      v-if="dashboard.sectionVisibility.contentPipeline"
      :columns="pipeline.columns"
      @move-card="handleMoveCard"
    />

    <!-- Row: Inbox Summary + Team Activity -->
    <div
      v-if="dashboard.sectionVisibility.inbox || dashboard.sectionVisibility.teamActivity"
      class="grid grid-cols-1 lg:grid-cols-2 gap-4"
    >
      <InboxSummary
        v-if="dashboard.sectionVisibility.inbox"
        :items="inboxItems"
      />
      <TeamActivity
        v-if="dashboard.sectionVisibility.teamActivity"
        :events="teamActivity"
        :members="teamMembers"
      />
    </div>
  </div>
</template>
