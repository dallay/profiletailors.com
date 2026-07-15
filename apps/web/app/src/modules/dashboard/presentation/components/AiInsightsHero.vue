<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { AiInsight } from '@modules/dashboard/domain/dashboard.types'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'

const props = defineProps<{
  insights: AiInsight[]
}>()

const emit = defineEmits<{
  dismiss: [id: string]
  action: [insight: AiInsight]
}>()

const { t } = useI18n()

const heroInsight = computed(() => props.insights[0] ?? null)
const gridInsights = computed(() => props.insights.slice(1))

const typeBadgeVariant = (type: AiInsight['type']) => {
  if (type === 'alert') return 'destructive' as const
  if (type === 'opportunity') return 'secondary' as const
  return 'outline' as const
}

const typeLabel = (type: AiInsight['type']) => {
  return t(`dashboard.insights.${type}`)
}

const priorityBorder = (priority: AiInsight['priority']) => {
  if (priority === 'high') return 'border-l-[var(--error-color)]'
  if (priority === 'medium') return 'border-l-[var(--warning-color)]'
  return 'border-l-[var(--text-secondary)]'
}

const priorityDot = (priority: AiInsight['priority']) => {
  if (priority === 'high') return 'bg-[var(--error-color)]'
  if (priority === 'medium') return 'bg-[var(--warning-color)]'
  return 'bg-[var(--text-secondary)]'
}
</script>

<template>
  <section class="space-y-4" aria-labelledby="section-insights">
    <div class="flex items-center justify-between border-b border-[var(--border-color)] pb-4">
      <div>
        <h2
          id="section-insights"
          class="font-[var(--font-space-mono)] text-xs font-bold tracking-[0.08em] text-[var(--text-display)] uppercase"
        >
          {{ t('dashboard.insights.title') }}
        </h2>
        <p class="text-[11px] text-[var(--text-secondary)] mt-1">
          {{ t('dashboard.insights.subtitle') }}
        </p>
      </div>
    </div>

    <p
      v-if="insights.length === 0"
      class="text-sm text-[var(--text-secondary)] text-center py-8"
    >
      {{ t('dashboard.insights.empty') }}
    </p>

    <template v-else>
      <Card
        v-if="heroInsight"
        :class="['border-l-2', priorityBorder(heroInsight.priority)]"
      >
        <CardHeader>
          <div class="flex items-start justify-between gap-3">
            <div class="flex items-center gap-2">
              <span
                :class="['w-2 h-2 rounded-full shrink-0', priorityDot(heroInsight.priority)]"
                aria-hidden="true"
              />
              <Badge :variant="typeBadgeVariant(heroInsight.type)">
                {{ typeLabel(heroInsight.type) }}
              </Badge>
              <Badge
                v-if="heroInsight.priority === 'high'"
                variant="destructive"
              >
                {{ t('dashboard.insights.highPriority') }}
              </Badge>
            </div>
            <button
              class="text-[10px] text-[var(--text-secondary)] hover:text-[var(--text-display)] font-[var(--font-space-mono)] uppercase tracking-wider transition-colors shrink-0"
              @click="emit('dismiss', heroInsight.id)"
            >
              {{ t('dashboard.insights.dismiss') }}
            </button>
          </div>
          <CardTitle class="text-base font-semibold text-[var(--text-display)] mt-3">
            {{ heroInsight.title }}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p class="text-sm text-[var(--text-body)] leading-relaxed">
            {{ heroInsight.description }}
          </p>
          <Button
            variant="outline"
            size="sm"
            class="mt-4"
            @click="emit('action', heroInsight)"
          >
            {{ heroInsight.actionLabel }}
          </Button>
        </CardContent>
      </Card>

      <div
        v-if="gridInsights.length > 0"
        class="grid grid-cols-1 sm:grid-cols-2 gap-3"
      >
        <Card
          v-for="insight in gridInsights"
          :key="insight.id"
          :class="['border-l-2', priorityBorder(insight.priority)]"
          size="sm"
        >
          <CardHeader class="pb-0">
            <div class="flex items-start justify-between gap-2">
              <div class="flex items-center gap-2">
                <span
                  :class="['w-1.5 h-1.5 rounded-full shrink-0', priorityDot(insight.priority)]"
                  aria-hidden="true"
                />
                <Badge :variant="typeBadgeVariant(insight.type)" class="text-[9px]">
                  {{ typeLabel(insight.type) }}
                </Badge>
              </div>
              <button
                class="text-[10px] text-[var(--text-secondary)] hover:text-[var(--text-display)] font-[var(--font-space-mono)] uppercase tracking-wider transition-colors shrink-0"
                @click="emit('dismiss', insight.id)"
              >
                {{ t('dashboard.insights.dismiss') }}
              </button>
            </div>
            <CardTitle class="text-sm font-medium text-[var(--text-display)] mt-2">
              {{ insight.title }}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <p class="text-xs text-[var(--text-secondary)] line-clamp-2">
              {{ insight.description }}
            </p>
            <Button
              variant="ghost"
              size="sm"
              class="mt-3 h-6 px-2 text-[10px]"
              @click="emit('action', insight)"
            >
              {{ insight.actionLabel }}
            </Button>
          </CardContent>
        </Card>
      </div>
    </template>
  </section>
</template>
