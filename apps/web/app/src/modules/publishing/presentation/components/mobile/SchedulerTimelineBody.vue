<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
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
}>()
const emit = defineEmits<{
  (event: 'openPostDetail', publication: Publication): void
  (event: 'openNewPost', day: Date, hour: number): void
}>()

const viewportRef = ref<HTMLElement | null>(null)
const todayHeaderRef = ref<HTMLElement | null>(null)

const now = ref(new Date())
let timer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  timer = setInterval(() => {
    now.value = new Date()
  }, 30_000)
  scrollToCurrentTimeAndToday()
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

watch(
  () => [props.days, props.hourSlots],
  () => {
    nextTick(() => {
      scrollToCurrentTimeAndToday()
    })
  },
  { deep: true },
)

const hasToday = computed(() => props.days.some((d) => props.isToday(d)))

const nowTopPx = computed(() => {
  const current = now.value
  const minutes = current.getHours() * 60 + current.getMinutes()
  return (minutes / 60) * 96
})

const nowFormattedTime = computed(() => {
  return now.value.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false })
})

function formatSlotTime(value: string): string {
  return new Date(value).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

function scrollToCurrentTimeAndToday() {
  if (!viewportRef.value) return

  const currentHour = now.value.getHours()
  const targetTop = Math.max(0, (currentHour - 1) * 96)
  viewportRef.value.scrollTop = targetTop

  if (todayHeaderRef.value) {
    todayHeaderRef.value.scrollIntoView?.({
      behavior: 'smooth',
      block: 'nearest',
      inline: 'center',
    })
  }
}
</script>

<template>
  <div
    ref="viewportRef"
    data-testid="scheduler-timeline-viewport"
    class="thin-scrollbar relative min-h-0 flex-1 overflow-x-auto overflow-y-auto"
  >
    <div class="min-w-max relative">
      <div
        data-testid="scheduler-day-header-row"
        class="sticky top-0 z-20 grid border-b border-border-subtle bg-bg-primary"
        :style="{ gridTemplateColumns: `48px repeat(${days.length}, minmax(120px, 1fr))` }"
      >
        <div class="sticky left-0 top-0 z-30 border-r border-border-subtle bg-bg-primary py-3.5" />
        <div
          v-for="day in days"
          :key="day.toISOString()"
          :ref="(el) => { if (isToday(day)) todayHeaderRef = el as HTMLElement }"
          class="border-r border-border-subtle py-3.5 text-center last:border-r-0"
          :data-is-today="isToday(day) ? 'true' : 'false'"
        >
          <span class="font-mono text-[11px] font-bold uppercase tracking-widest text-text-secondary">{{ formatDayName(day).substring(0, 3) }}</span>
          <span
            class="mx-auto flex size-6 items-center justify-center rounded-full font-mono text-xs font-bold"
            :class="isToday(day) ? 'bg-text-display text-bg-primary' : 'text-text-display'"
          >
            {{ day.getDate() }}
          </span>
        </div>
      </div>

      <div class="relative">
        <div
          v-if="hasToday"
          data-testid="scheduler-now-indicator"
          class="absolute left-0 right-0 z-20 flex items-center pointer-events-none"
          :style="{ top: `${nowTopPx}px` }"
        >
          <div class="sticky left-0 z-30 flex h-5 items-center justify-center bg-error px-1.5 text-[9px] font-mono font-bold text-white shadow-sm rounded-r-md">
            {{ nowFormattedTime }}
          </div>
          <div class="h-[2px] flex-1 bg-error/80 shadow-[0_0_4px_rgba(239,68,68,0.5)]" />
        </div>

        <div
          v-for="slot in hourSlots"
          :key="slot.hour"
          class="grid h-[96px] border-b border-border-subtle"
          :style="{ gridTemplateColumns: `48px repeat(${days.length}, minmax(120px, 1fr))` }"
        >
          <div class="sticky left-0 z-10 border-r border-border-subtle bg-bg-surface py-2 text-center">
            <span class="font-mono text-[11px] tracking-wider text-text-secondary">{{ slot.label }}</span>
          </div>
          <div
            v-for="day in days"
            :key="`${day.toISOString()}-${slot.hour}`"
            class="relative border-r border-border-subtle p-2 last:border-r-0 overflow-hidden"
            :class="isPastSlot(day, slot.hour) ? 'bg-text-secondary/5 text-text-secondary cursor-not-allowed after:absolute after:inset-0 after:bg-[repeating-linear-gradient(-45deg,transparent,transparent_10px,var(--border-color)_10px,var(--border-color)_11px)] after:opacity-10 after:z-0 pointer-events-none' : 'hover:bg-bg-primary/20'"
            :data-past-slot="isPastSlot(day, slot.hour) ? 'true' : 'false'"
          >
            <button
              type="button"
              class="absolute inset-0 z-0 flex items-start justify-end p-1.5 focus:outline-none"
              :disabled="isPastSlot(day, slot.hour)"
              :aria-label="`Slot for ${formatDayName(day)} at ${slot.label}`"
              @click="emit('openNewPost', day, slot.hour)"
            >
              <span
                v-if="!isPastSlot(day, slot.hour)"
                data-testid="slot-add-affordance"
                class="flex size-5 items-center justify-center rounded border border-dashed border-text-secondary/30 text-text-secondary/50 hover:border-text-display/50 hover:text-text-display transition-colors"
                title="Add post"
              >
                <Plus class="size-3" />
              </span>
            </button>

            <button
              v-for="publication in publicationsForSlot(day, slot.hour).slice(0, 2)"
              :key="publication.id"
              type="button"
              class="relative z-10 grid min-h-11 w-full min-w-0 gap-1 overflow-hidden rounded-md border bg-bg-surface p-2 text-left shadow-sm"
              @click.stop="emit('openPostDetail', publication)"
            >
              <span class="flex min-w-0 items-center gap-1.5">
                <span
                  v-for="channel in publication.channels.slice(0, 3)"
                  :key="channel"
                  class="flex size-4 shrink-0 items-center justify-center rounded-[4px]"
                >
                  <SocialProviderIcon :provider="channel" />
                </span>
                <span class="shrink-0 font-mono text-[11px] font-bold uppercase tracking-wider text-text-secondary">{{ formatSlotTime(publication.scheduledAt) }}</span>
                <span class="shrink-0 font-mono text-[11px] font-bold uppercase tracking-wider text-text-secondary">{{ publication.status }}</span>
              </span>
              <span class="block truncate text-xs text-text-display">{{ publication.title || publication.content }}</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
