<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { Plus } from '@lucide/vue'
import SocialProviderIcon from '@shared/components/SocialProviderIcon.vue'
import type { Publication } from '@modules/publishing/infrastructure/publishing.store'

type HourSlot = { hour: number; label: string }
const props = defineProps<{
  days: Date[]
  hourSlots: HourSlot[]
  publicationsForSlot: (day: Date, hour: number) => Publication[]
  isToday: (day: Date) => boolean
  formatDayName: (day: Date) => string
  isPastSlot: (day: Date, hour: number) => boolean
  hasNoChannels: boolean
  now: Date
  formatCurrentTime: (date: Date) => string
}>()
const emit = defineEmits<{
  (event: 'openPostDetail', publication: Publication): void
  (event: 'openNewPost', day: Date, hour: number): void
}>()
const viewport = ref<HTMLElement | null>(null)
function formatSlotTime(value: string): string {
  return new Date(value).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}
function isCurrentHour(day: Date, hour: number): boolean {
  return props.isToday(day) && props.now.getHours() === hour
}
const nowMarkerStyle = computed(() => ({ top: `${(props.now.getMinutes() / 60) * 100}%` }))
function revealToday(): void {
  const todayIndex = props.days.findIndex((day) => props.isToday(day))
  if (todayIndex < 0 || !viewport.value) return
  const dayWidth = 120
  const todayStart = 48 + todayIndex * dayWidth
  const todayEnd = todayStart + dayWidth
  const visibleStart = viewport.value.scrollLeft
  const visibleEnd = visibleStart + viewport.value.clientWidth
  if (todayStart < visibleStart) viewport.value.scrollLeft = todayStart
  else if (todayEnd > visibleEnd) viewport.value.scrollLeft = todayEnd - viewport.value.clientWidth
}
watch(() => props.days, () => nextTick(revealToday), { immediate: true })
onMounted(revealToday)
</script>
<template>
  <div ref="viewport" data-testid="scheduler-timeline-viewport" class="thin-scrollbar min-h-0 flex-1 overflow-x-auto overflow-y-auto">
    <div class="min-w-max">
      <div data-testid="scheduler-day-header-row" class="sticky top-0 z-30 grid border-b border-border-subtle bg-bg-primary" :style="{ gridTemplateColumns: `48px repeat(${days.length}, minmax(120px, 1fr))` }">
        <div data-testid="scheduler-time-corner" class="sticky left-0 z-40 border-r border-border-subtle bg-bg-primary py-3.5" />
        <div v-for="day in days" :key="day.toISOString()" class="border-r border-border-subtle py-3.5 text-center last:border-r-0">
          <span class="font-mono text-[11px] font-bold uppercase tracking-widest text-text-secondary">{{ formatDayName(day).substring(0, 3) }}</span>
          <span class="mx-auto flex size-6 items-center justify-center rounded-full font-mono text-xs font-bold" :class="isToday(day) ? 'bg-text-display text-bg-primary' : 'text-text-display'">{{ day.getDate() }}</span>
        </div>
      </div>
      <div v-for="slot in hourSlots" :key="slot.hour" class="grid h-[96px] border-b border-border-subtle" :style="{ gridTemplateColumns: `48px repeat(${days.length}, minmax(120px, 1fr))` }">
        <div class="sticky left-0 z-10 border-r border-border-subtle bg-bg-surface py-2 text-center"><span class="font-mono text-[11px] tracking-wider text-text-secondary">{{ slot.label }}</span></div>
        <div v-for="day in days" :key="`${day.toISOString()}-${slot.hour}`" :data-past-slot="isPastSlot(day, slot.hour) ? 'true' : 'false'" class="relative overflow-hidden border-r border-border-subtle p-2 last:border-r-0" :class="isPastSlot(day, slot.hour) ? 'bg-text-secondary/5 text-text-secondary cursor-not-allowed after:pointer-events-none after:absolute after:inset-0 after:bg-[repeating-linear-gradient(-45deg,transparent,transparent_10px,var(--border-color)_10px,var(--border-color)_11px)] after:opacity-20' : 'hover:bg-bg-primary/20'">
          <button type="button" class="absolute inset-0 z-0 flex items-start justify-end p-1.5 focus:outline-none" :disabled="isPastSlot(day, slot.hour) || props.hasNoChannels" :aria-label="`Slot for ${formatDayName(day)} at ${slot.label}`" :title="props.hasNoChannels ? $t('scheduler.noChannelTitle') : undefined" @click="emit('openNewPost', day, slot.hour)">
            <span v-if="!isPastSlot(day, slot.hour) && !props.hasNoChannels" data-testid="slot-add-affordance" class="flex size-7 items-center justify-center rounded-full border border-dashed border-text-secondary/40 bg-bg-primary/70 text-lg leading-none text-text-secondary/70" :aria-label="$t('scheduler.addPost')"><Plus class="size-3" /></span>
          </button>
          <span v-if="isCurrentHour(day, slot.hour)" data-testid="scheduler-now-indicator" class="pointer-events-none absolute left-0 right-0 z-[3] flex -translate-y-1/2 items-center text-[10px] font-mono font-bold uppercase tracking-wider text-error" :style="nowMarkerStyle">
            <span class="size-2 rounded-full bg-error" />
            <span class="ml-1 bg-bg-surface px-1">{{ $t('scheduler.now') }} {{ props.formatCurrentTime(props.now) }}</span>
            <span class="h-px flex-1 bg-error" />
          </span>
          <button v-for="publication in publicationsForSlot(day, slot.hour).slice(0, 2)" :key="publication.id" type="button" class="relative z-10 grid min-h-11 w-full min-w-0 gap-1 overflow-hidden rounded-md border bg-bg-surface p-2 text-left" @click.stop="emit('openPostDetail', publication)">
            <span class="flex min-w-0 items-center gap-1.5">
              <span v-for="channel in publication.channels.slice(0, 3)" :key="channel" class="flex size-4 shrink-0 items-center justify-center rounded-[4px]"><SocialProviderIcon :provider="channel" /></span>
              <span class="shrink-0 font-mono text-[11px] font-bold uppercase tracking-wider text-text-secondary">{{ formatSlotTime(publication.scheduledAt) }}</span>
              <span class="shrink-0 font-mono text-[11px] font-bold uppercase tracking-wider text-text-secondary">{{ publication.status }}</span>
            </span>
            <span class="block truncate text-xs text-text-display">{{ publication.title || publication.content }}</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
