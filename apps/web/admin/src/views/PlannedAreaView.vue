<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { NAV_REGISTRY } from '@/router/nav-registry'

const route = useRoute()
const { t } = useI18n()

const areaLabel = computed(() => {
  const entry = NAV_REGISTRY.find(candidate => candidate.routeName === String(route.name ?? ''))
  return entry ? t(entry.labelKey) : ''
})
</script>

<template>
  <section class="mx-auto max-w-2xl p-8" data-testid="planned-area" aria-labelledby="planned-area-title">
    <h1 id="planned-area-title" class="text-xl font-semibold text-text-primary">
      {{ areaLabel }}
    </h1>
    <p class="mt-2 text-sm text-text-secondary" data-testid="planned-area-message">
      {{ t('planned.message') }}
    </p>
  </section>
</template>
