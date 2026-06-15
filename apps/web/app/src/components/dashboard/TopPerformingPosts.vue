<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { TopPost, Platform } from '@/lib/types/dashboard'
import { formatNumber, formatPercent, formatRelativeTime } from '@/lib/formatters'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'

const props = defineProps<{
  posts: TopPost[]
}>()

const { t } = useI18n()

const activeFilter = ref<Platform | 'all'>('all')

const platformLabels: Record<Platform | 'all', string> = {
  all: '',
  linkedin: 'LinkedIn',
  twitter: 'X',
  bluesky: 'Bluesky',
  threads: 'Threads',
}

const platforms = ['all', 'linkedin', 'twitter', 'bluesky', 'threads'] as const

const filteredPosts = computed(() => {
  if (activeFilter.value === 'all') return props.posts
  return props.posts.filter((p) => p.platform === activeFilter.value)
})

const platformBadgeColor = (platform: Platform) => {
  const colors: Record<Platform, string> = {
    linkedin: 'text-[#0A66C2]',
    twitter: 'text-[#1DA1F2]',
    bluesky: 'text-[#0085FF]',
    threads: 'text-[#E1306C]',
  }
  return colors[platform] ?? 'text-[var(--text-secondary)]'
}
</script>

<template>
  <Card aria-labelledby="section-top-posts">
    <CardHeader>
      <div class="flex items-center justify-between">
        <div>
          <CardTitle
            id="section-top-posts"
            class="font-[var(--font-space-mono)] text-xs font-bold tracking-[0.08em] text-[var(--text-secondary)] uppercase"
          >
            {{ t('dashboard.contentPerformance.title') }}
          </CardTitle>
          <p class="text-[11px] text-[var(--text-secondary)] mt-1">
            {{ t('dashboard.contentPerformance.subtitle') }}
          </p>
        </div>
      </div>
    </CardHeader>

    <CardContent>
      <!-- Platform filter -->
      <div class="flex items-center gap-1 mb-4 overflow-x-auto">
        <Button
          v-for="platform in platforms"
          :key="platform"
          variant="ghost"
          size="sm"
          :class="[
            'h-7 px-3 text-[10px] font-[var(--font-space-mono)] uppercase tracking-wider rounded-none shrink-0',
            activeFilter === platform
              ? 'bg-[var(--text-display)] text-[var(--background-primary)]'
              : 'text-[var(--text-secondary)] hover:text-[var(--text-display)]',
          ]"
          @click="activeFilter = platform"
        >
          {{ platform === 'all' ? t('dashboard.contentPerformance.allPlatforms') : platformLabels[platform] }}
        </Button>
      </div>

      <!-- Empty state -->
      <p
        v-if="filteredPosts.length === 0"
        class="text-sm text-[var(--text-secondary)] text-center py-8"
      >
        {{ t('dashboard.contentPerformance.noPostsMatch') }}
      </p>

      <!-- Posts list -->
      <div v-else class="space-y-3">
        <div
          v-for="(post, index) in filteredPosts"
          :key="post.id"
          class="flex items-start gap-3 p-3 rounded-lg bg-[var(--background-primary)] border border-[var(--border-color)]"
        >
          <!-- Rank number -->
          <span class="text-lg font-semibold text-[var(--text-secondary)] tabular-nums w-6 text-center shrink-0 leading-tight pt-0.5">
            {{ index + 1 }}
          </span>

          <!-- Content -->
          <div class="flex-1 min-w-0">
            <p class="text-sm text-[var(--text-display)] line-clamp-2 leading-snug">
              {{ post.content }}
            </p>
            <div class="flex items-center gap-3 mt-2 flex-wrap">
              <span
                :class="[
                  'text-[10px] font-[var(--font-space-mono)] uppercase tracking-wider font-medium',
                  platformBadgeColor(post.platform),
                ]"
              >
                {{ platformLabels[post.platform] }}
              </span>
              <span class="text-[10px] text-[var(--text-secondary)] font-[var(--font-space-mono)]">
                {{ t('dashboard.contentPerformance.publishedOn') }} {{ formatRelativeTime(post.publishedAt) }}
              </span>
            </div>
            <!-- Metrics -->
            <div class="flex items-center gap-4 mt-2">
              <div class="flex items-center gap-1">
                <span class="text-[10px] text-[var(--text-secondary)] font-[var(--font-space-mono)]">
                  {{ t('dashboard.contentPerformance.reactions') }}
                </span>
                <span class="text-xs font-medium text-[var(--text-display)] tabular-nums">
                  {{ formatNumber(post.reactions) }}
                </span>
              </div>
              <div class="flex items-center gap-1">
                <span class="text-[10px] text-[var(--text-secondary)] font-[var(--font-space-mono)]">
                  {{ t('dashboard.contentPerformance.comments') }}
                </span>
                <span class="text-xs font-medium text-[var(--text-display)] tabular-nums">
                  {{ formatNumber(post.comments) }}
                </span>
              </div>
              <div class="flex items-center gap-1">
                <span class="text-[10px] text-[var(--text-secondary)] font-[var(--font-space-mono)]">
                  {{ t('dashboard.contentPerformance.shares') }}
                </span>
                <span class="text-xs font-medium text-[var(--text-display)] tabular-nums">
                  {{ formatNumber(post.shares) }}
                </span>
              </div>
              <div class="flex items-center gap-1 ml-auto">
                <span class="text-[10px] text-[var(--text-secondary)] font-[var(--font-space-mono)]">
                  {{ t('dashboard.contentPerformance.engagementRate') }}
                </span>
                <span class="text-xs font-medium text-[var(--text-display)] tabular-nums">
                  {{ formatPercent(post.engagementRate) }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </CardContent>
  </Card>
</template>
