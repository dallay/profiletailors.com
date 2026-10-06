<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { CircleHelp } from '@lucide/vue'
import { Button } from '@profiletailors/vue-ui/shell/button'
import { DashboardHeader } from '@profiletailors/vue-ui/shell'

const emit = defineEmits<{
  startTour: []
}>()

/**
 * Maps sub-route names to their parent nav key so the header shows the
 * correct section label (e.g. "Scheduler") for all scheduler views.
 * Direct matches (dashboard, analytics, media, settings) pass through.
 */
const routeNameToNavKey: Record<string, string> = {
  'scheduler-calendar-week': 'scheduler',
  'scheduler-calendar-month': 'scheduler',
  'scheduler-calendar-day': 'scheduler',
  'scheduler-list': 'scheduler',
  'governance-takedown': 'governance',
  ideas: 'ideas',
}

const route = useRoute()
const { t } = useI18n()

const currentSectionLabel = computed(() => {
  if (!route.name) return 'dashboard'
  const name = String(route.name)
  return routeNameToNavKey[name] ?? name
})
</script>

<template>
  <DashboardHeader
    :eyebrow="t('workspace.title')"
    :title="t(`nav.${currentSectionLabel}`)"
    :toggle-label="t('nav.openNavigation')"
    title-data-tour="section-title"
  >
    <template #actions>
      <Button
        type="button"
        variant="outline"
        size="sm"
        class="gap-2"
        data-testid="start-tour-btn"
        @click="emit('startTour')"
      >
        <CircleHelp class="size-4" />
        {{ t('tour.button') }}
      </Button>
    </template>
  </DashboardHeader>
</template>
