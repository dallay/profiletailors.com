<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Card } from '@/components/ui/card'
import CalendarCell from '@modules/publishing/presentation/components/CalendarCell.vue'
import type { Publication, ActivityEntry } from '@modules/publishing/infrastructure/publishing.store'

const props = defineProps<{
  monthGrid: Date[][]
  currentBaseDate: Date
  publications: Publication[]
  activityByDate: Map<string, ActivityEntry>
}>()

const emit = defineEmits<{
  (e: 'click-day', date: Date): void
  (e: 'click-publication', pub: Publication): void
  (e: 'dragstart', payload: { event: DragEvent; pub: Publication }): void
  (e: 'dragend', event: DragEvent): void
  (e: 'drop-cell', payload: { event: DragEvent; date: Date }): void
}>()

const { locale } = useI18n()

function dateKey(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

function formatDayName(date: Date): string {
  const days = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']
  const daysEs = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado']
  const index = date.getDay()
  return (locale.value === 'es' ? daysEs : days)[index] ?? ''
}

function isCurrentMonth(date: Date): boolean {
  return (
    date.getMonth() === props.currentBaseDate.getMonth() &&
    date.getFullYear() === props.currentBaseDate.getFullYear()
  )
}

function isToday(date: Date): boolean {
  const now = new Date()
  return (
    date.getDate() === now.getDate() &&
    date.getMonth() === now.getMonth() &&
    date.getFullYear() === now.getFullYear()
  )
}

function isPastDate(date: Date): boolean {
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const cellDate = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  return cellDate < today
}

function getPublicationsForDate(date: Date): Publication[] {
  return props.publications.filter((pub) => {
    const pubDate = new Date(pub.scheduledAt)
    return (
      pubDate.getDate() === date.getDate() &&
      pubDate.getMonth() === date.getMonth() &&
      pubDate.getFullYear() === date.getFullYear()
    )
  })
}

function activityForDate(date: Date): ActivityEntry | undefined {
  return props.activityByDate.get(dateKey(date))
}
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <Card class="bg-bg-surface border border-border-subtle p-0 overflow-hidden flex min-h-0 flex-1 flex-col">
      <div class="grid grid-cols-7 border-b border-border-subtle bg-bg-primary shrink-0">
        <div
          v-for="(_, idx) in 7"
          :key="idx"
          class="py-2.5 text-center border-r border-border-subtle last:border-r-0"
        >
          <span class="font-mono text-[8px] font-bold tracking-widest text-text-secondary uppercase">
            {{ formatDayName(new Date(2026, 0, idx + 1)).substring(0, 3) }}
          </span>
        </div>
      </div>

      <div class="thin-scrollbar min-h-0 flex-1 overflow-y-auto divide-y divide-border-subtle">
        <div
          v-for="(week, wkIdx) in monthGrid"
          :key="wkIdx"
          class="grid grid-cols-7"
        >
          <CalendarCell
            v-for="day in week"
            :key="day.toISOString()"
            :date="day"
            :is-current-month="isCurrentMonth(day)"
            :is-today="isToday(day)"
            :is-past="isPastDate(day)"
            :publications="getPublicationsForDate(day)"
            :activity-entry="activityForDate(day) ?? null"
            :draggable="true"
            @click-day="emit('click-day', $event)"
            @click-publication="emit('click-publication', $event)"
            @dragstart="emit('dragstart', $event)"
            @dragend="emit('dragend', $event)"
            @drop-cell="emit('drop-cell', $event)"
          />
        </div>
      </div>
    </Card>
  </div>
</template>
