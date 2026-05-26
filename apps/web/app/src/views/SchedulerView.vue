<script setup lang="ts">
import { Card } from '@/components/ui/card'

const dayKeys = ['scheduler.mon', 'scheduler.tue', 'scheduler.wed', 'scheduler.thu', 'scheduler.fri', 'scheduler.sat', 'scheduler.sun'] as const
</script>

<template>
  <div class="space-y-12">
    <!-- Header -->
    <div class="space-y-2">
      <h2 class="text-3xl font-light tracking-tight text-text-display">
        {{ $t('nav.scheduler') }}
      </h2>
      <p class="text-sm text-text-secondary">
        {{ $t('scheduler.subtitle') }}
      </p>
    </div>

    <!-- Calendar Mock -->
    <Card class="bg-bg-surface border border-border-subtle p-0 overflow-hidden">
      <div class="grid grid-cols-7 border-b border-border-subtle bg-bg-primary">
        <div
          v-for="day in dayKeys"
          :key="day"
          class="py-3 text-center font-mono text-[9px] font-bold tracking-widest text-text-secondary border-r border-border-subtle last:border-r-0"
        >
          {{ $t(day) }}
        </div>
      </div>

      <div class="grid grid-cols-7 grid-rows-4 min-h-[300px]">
        <div
          v-for="day in 28"
          :key="day"
          class="p-3 border-r border-b border-border-subtle last:border-r-0 hover:bg-bg-primary/30 transition-colors flex flex-col justify-between"
          :class="{
            'bg-bg-primary/20': day % 7 === 0 || day % 7 === 6,
          }"
        >
          <span class="font-mono text-[10px] text-text-secondary">{{ day }}</span>
          <div v-if="day === 12" class="bg-text-display text-bg-primary px-1.5 py-0.5 rounded font-mono text-[8px] tracking-wider uppercase font-bold truncate">
            {{ $t('scheduler.earlyAccess') }}
          </div>
          <div v-if="day === 13" class="border border-border-visible bg-bg-primary text-text-secondary px-1.5 py-0.5 rounded font-mono text-[8px] tracking-wider uppercase truncate">
            {{ $t('scheduler.swissDesign') }}
          </div>
          <div class="min-h-[14px]"></div>
        </div>
      </div>
    </Card>
  </div>
</template>
