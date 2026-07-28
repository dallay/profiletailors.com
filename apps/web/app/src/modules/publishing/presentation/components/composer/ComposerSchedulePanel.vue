<script setup lang="ts">
import type { DateValue } from 'reka-ui'
import { Calendar } from '@/components/ui/calendar'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { CalendarIcon } from '@lucide/vue'

type ScheduleMode = 'now' | 'next' | 'custom'

defineProps<{
  scheduleMode: ScheduleMode
  selectedCalendarDate: DateValue | undefined
  scheduleTime: string
  isDatePickerOpen: boolean
  todayDateValue: import('@internationalized/date').CalendarDate
  minTimeForDate: string
  selectedDateLabel: string
  scheduleHelperText: string
}>()

const emit = defineEmits<{
  'update:scheduleMode': [mode: ScheduleMode]
  'update:selectedCalendarDate': [date: DateValue | undefined]
  'update:scheduleTime': [time: string]
  'update:isDatePickerOpen': [open: boolean]
}>()

function setMode(mode: ScheduleMode) {
  emit('update:scheduleMode', mode)
}

function onDateChange(date: DateValue | undefined) {
  emit('update:selectedCalendarDate', date)
  emit('update:isDatePickerOpen', false)
}

function onTimeInput(event: Event) {
  const target = event.target as HTMLInputElement
  emit('update:scheduleTime', target.value)
}
</script>

<template>
  <div class="space-y-3">
    <!-- Schedule mode section -->
    <div
      data-testid="schedule-section"
      class="flex items-center gap-4 bg-bg-surface border border-border-subtle p-3 rounded-xl"
    >
      <CalendarIcon class="size-4 text-text-secondary shrink-0" />
      <div class="flex-1 space-y-2 text-xs">
        <span class="text-text-secondary">Schedule Mode:</span>
        <div
          class="grid grid-cols-3 gap-1 rounded-lg bg-bg-primary/60 p-1"
          role="radiogroup"
          aria-label="Schedule mode"
        >
          <label
            data-testid="schedule-mode-now"
            class="px-2 py-1 rounded font-mono text-[9px] uppercase tracking-wider font-bold transition-all cursor-pointer"
            :class="
              scheduleMode === 'now'
                ? 'bg-text-display text-bg-primary'
                : 'bg-transparent text-text-secondary hover:text-text-display'
            "
          >
            <input
              type="radio"
              name="schedule-mode"
              class="sr-only"
              :checked="scheduleMode === 'now'"
              @change="setMode('now')"
            />
            <span>Now</span>
          </label>
          <label
            data-testid="schedule-mode-next"
            class="px-2 py-1 rounded font-mono text-[9px] uppercase tracking-wider font-bold transition-all cursor-pointer"
            :class="
              scheduleMode === 'next'
                ? 'bg-text-display text-bg-primary'
                : 'bg-transparent text-text-secondary hover:text-text-display'
            "
          >
            <input
              type="radio"
              name="schedule-mode"
              class="sr-only"
              :checked="scheduleMode === 'next'"
              @change="setMode('next')"
            />
            <span>Next Schedule</span>
          </label>
          <label
            data-testid="schedule-mode-custom"
            class="px-2 py-1 rounded font-mono text-[9px] uppercase tracking-wider font-bold transition-all cursor-pointer"
            :class="
              scheduleMode === 'custom'
                ? 'bg-text-display text-bg-primary'
                : 'bg-transparent text-text-secondary hover:text-text-display'
            "
          >
            <input
              type="radio"
              name="schedule-mode"
              class="sr-only"
              :checked="scheduleMode === 'custom'"
              @change="setMode('custom')"
            />
            <span>Pick Date</span>
          </label>
        </div>
        <p class="text-[10px] leading-4 text-text-secondary">
          {{ scheduleHelperText }}
        </p>
      </div>
    </div>

    <!-- Date picker and time input (only in custom mode) -->
    <div
      v-if="scheduleMode === 'custom'"
      class="grid grid-cols-[1fr_112px] gap-3"
    >
      <Popover :open="isDatePickerOpen" @update:open="(open) => emit('update:isDatePickerOpen', open)">
        <PopoverTrigger as-child>
          <button
            data-testid="schedule-date-trigger"
            type="button"
            class="flex items-center justify-between gap-2 bg-bg-surface border border-border-subtle rounded-xl px-3 py-2 text-xs text-text-body hover:border-text-display focus:outline-none focus:border-text-display font-sans cursor-pointer"
          >
            <span>{{ selectedDateLabel }}</span>
            <CalendarIcon class="size-3.5 text-text-secondary" />
          </button>
        </PopoverTrigger>
        <PopoverContent
          data-testid="schedule-date-picker"
          class="w-auto p-0 bg-bg-surface border-border-subtle"
          align="start"
        >
          <Calendar
            :model-value="selectedCalendarDate"
            :min-value="todayDateValue"
            layout="month-and-year"
            initial-focus
            @update:model-value="onDateChange"
          />
        </PopoverContent>
      </Popover>

      <label for="schedule-time" class="sr-only">Schedule time</label>
      <input
        id="schedule-time"
        data-testid="schedule-time-input"
        type="time"
        :value="scheduleTime"
        :min="minTimeForDate"
        class="bg-bg-surface border border-border-subtle rounded-xl px-3 py-2 text-xs text-text-body focus:outline-none focus:border-text-display font-sans"
        @input="onTimeInput"
      />
    </div>
  </div>
</template>
