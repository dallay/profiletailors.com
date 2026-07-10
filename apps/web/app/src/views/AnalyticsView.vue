<script setup lang="ts">
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
</script>

<template>
  <div class="mx-auto w-full max-w-7xl space-y-12">
    <div class="space-y-2">
      <h2 class="text-3xl font-light tracking-tight text-text-display">
        {{ $t('nav.analytics') }}
      </h2>
      <p class="text-sm text-text-secondary">
        {{ $t('analytics.subtitle') }}
      </p>
    </div>

    <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
      <Card class="md:col-span-2">
        <CardHeader class="p-0 border-b border-border-subtle pb-4 flex flex-row items-center justify-between">
          <CardTitle class="label-mono text-text-display text-[10px]">
            {{ $t('analytics.audienceGrowth') }}
          </CardTitle>
          <span class="font-mono text-[9px] text-text-secondary uppercase">[ {{ $t('analytics.live') }} ]</span>
        </CardHeader>
        <CardContent class="p-0 mt-6 h-48 flex items-end justify-between gap-2 px-2">
          <div class="w-full bg-border-visible h-12 hover:bg-text-display transition-colors rounded-sm"></div>
          <div class="w-full bg-border-visible h-16 hover:bg-text-display transition-colors rounded-sm"></div>
          <div class="w-full bg-border-visible h-24 hover:bg-text-display transition-colors rounded-sm"></div>
          <div class="w-full bg-border-visible h-20 hover:bg-text-display transition-colors rounded-sm"></div>
          <div class="w-full bg-border-visible h-32 hover:bg-text-display transition-colors rounded-sm"></div>
          <div class="w-full bg-border-visible h-40 hover:bg-text-display transition-colors rounded-sm"></div>
          <div class="w-full bg-text-display h-44 rounded-sm"></div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader class="p-0 border-b border-border-subtle pb-4">
          <CardTitle class="label-mono text-text-display text-[10px]">
            {{ $t('analytics.platformBreakdown') }}
          </CardTitle>
        </CardHeader>
        <CardContent class="p-0 mt-6 space-y-4 font-mono text-[10px] uppercase">
          <div class="space-y-1">
            <div class="flex justify-between text-text-secondary">
              <span>X / Twitter</span>
              <span>42%</span>
            </div>
            <div class="w-full bg-border-visible h-1 rounded-full overflow-hidden">
              <div class="bg-text-display h-full" style="width: 42%"></div>
            </div>
          </div>

          <div class="space-y-1">
            <div class="flex justify-between text-text-secondary">
              <span>Instagram</span>
              <span>30%</span>
            </div>
            <div class="w-full bg-border-visible h-1 rounded-full overflow-hidden">
              <div class="bg-text-display h-full" style="width: 30%"></div>
            </div>
          </div>

          <div class="space-y-1">
            <div class="flex justify-between text-text-secondary">
              <span>LinkedIn</span>
              <span>20%</span>
            </div>
            <div class="w-full bg-border-visible h-1 rounded-full overflow-hidden">
              <div class="bg-text-display h-full" style="width: 20%"></div>
            </div>
          </div>

          <div class="space-y-1">
            <div class="flex justify-between text-text-secondary">
              <span>Facebook</span>
              <span>8%</span>
            </div>
            <div class="w-full bg-border-visible h-1 rounded-full overflow-hidden">
              <div class="bg-text-display h-full" style="width: 8%"></div>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
