<script setup lang="ts">
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Key, Layers, Link, Activity, BookOpen } from '@lucide/vue'
import { BASE_URL } from '@/config'

const openDocs = () => {
  window.open('https://docs.omnirouter.ahome.quest', '_blank')
}
</script>

<template>
  <Card class="bg-bg-surface border-border-subtle overflow-visible">
    <CardHeader class="flex flex-col sm:flex-row items-start justify-between gap-4 pb-8">
      <div class="space-y-1">
        <CardTitle class="text-xl font-bold text-text-display">
          {{ $t('dashboard.quickStart.title') }}
        </CardTitle>
        <CardDescription class="text-sm text-text-secondary max-w-xl">
          {{ $t('dashboard.quickStart.subtitle') }}
        </CardDescription>
      </div>
      <Button variant="ghost" class="font-mono text-[10px] gap-2 uppercase tracking-widest border border-border-visible h-9 px-4" @click="openDocs">
        <BookOpen class="size-3.5" />
        {{ $t('dashboard.quickStart.fullDocs') }}
      </Button>
    </CardHeader>
    <CardContent class="p-0">
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <!-- Step 1 -->
        <Card class="bg-bg-primary/30 border-border-visible p-5 flex flex-row items-start gap-4 hover:border-text-secondary transition-all cursor-pointer group">
          <div class="p-2.5 rounded-lg bg-error/10 text-error border border-error/20 shrink-0">
            <Key class="size-5" />
          </div>
          <div class="space-y-1.5">
            <h4 class="font-bold text-[13px] text-text-display">{{ $t('dashboard.quickStart.step1Title') }}</h4>
            <p class="text-[12px] text-text-secondary leading-relaxed">
              {{ $t('dashboard.quickStart.step1Desc') }}
            </p>
          </div>
        </Card>

        <!-- Step 2 -->
        <Card class="bg-bg-primary/30 border-border-visible p-5 flex flex-row items-start gap-4 hover:border-text-secondary transition-all cursor-pointer group">
          <div class="p-2.5 rounded-lg bg-success/10 text-success border border-success/20 shrink-0">
            <Layers class="size-5" />
          </div>
          <div class="space-y-1.5">
            <h4 class="font-bold text-[13px] text-text-display">{{ $t('dashboard.quickStart.step2Title') }}</h4>
            <p class="text-[12px] text-text-secondary leading-relaxed">
              {{ $t('dashboard.quickStart.step2Desc') }}
            </p>
          </div>
        </Card>

        <!-- Step 3 -->
        <Card class="bg-bg-primary/30 border-border-visible p-5 flex flex-row items-start gap-4 hover:border-text-secondary transition-all cursor-pointer group">
          <div class="p-2.5 rounded-lg bg-info/10 text-info border border-info/20 shrink-0">
            <Link class="size-5" />
          </div>
          <div class="space-y-1.5">
            <h4 class="font-bold text-[13px] text-text-display">{{ $t('dashboard.quickStart.step3Title') }}</h4>
            <p class="text-[12px] text-text-secondary leading-relaxed" v-html="$t('dashboard.quickStart.step3Desc', { baseUrl: BASE_URL })"></p>
          </div>
        </Card>

        <!-- Step 4 -->
        <Card class="bg-bg-primary/30 border-border-visible p-5 flex flex-row items-start gap-4 hover:border-text-secondary transition-all cursor-pointer group">
          <div class="p-2.5 rounded-lg bg-warning/10 text-warning border border-warning/20 shrink-0">
            <Activity class="size-5" />
          </div>
          <div class="space-y-1.5">
            <h4 class="font-bold text-[13px] text-text-display">{{ $t('dashboard.quickStart.step4Title') }}</h4>
            <p class="text-[12px] text-text-secondary leading-relaxed">
              {{ $t('dashboard.quickStart.step4Desc') }}
            </p>
          </div>
        </Card>
      </div>
    </CardContent>
  </Card>
</template>
