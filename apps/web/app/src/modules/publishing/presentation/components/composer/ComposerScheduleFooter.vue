<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DateValue } from 'reka-ui'
import { CalendarIcon } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { Calendar } from '@/components/ui/calendar'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { useComposerScheduleState } from '../../../application/useComposerScheduleState'
import type { ComposerScheduleMode } from './composer.types'

const props = defineProps<{
  scheduleMode: ComposerScheduleMode
  selectedCalendarDate: DateValue | undefined
  scheduleTime: string
  priorityMode: boolean
  createAnother: boolean
  isEditMode: boolean
  canSubmit: boolean
  isSubmitting: boolean
  submitError: string
  now: Date
}>()

const emit = defineEmits<{
  (e: 'update:scheduleMode', val: ComposerScheduleMode): void
  (e: 'update:selectedCalendarDate', val: DateValue | undefined): void
  (e: 'update:scheduleTime', val: string): void
  (e: 'update:priorityMode', val: boolean): void
  (e: 'update:createAnother', val: boolean): void
  (e: 'close'): void
  (e: 'submit'): void
}>()

const { t } = useI18n()

const { isDatePickerOpen, todayDateValue, minTimeForDate, selectedDateLabel } = useComposerScheduleState(props.now)

const scheduleHelperText = computed(() => {
  if (props.scheduleMode === 'now') return 'Publishes with the creation date and time.'
  if (props.scheduleMode === 'next') return 'Publishes in the next available schedule slot.'
  return `Publishes on ${selectedDateLabel(props.selectedCalendarDate)} at ${props.scheduleTime}.`
})

const submitLabel = computed(() => {
  if (props.isEditMode) return t('composer.saveChanges')
  if (props.scheduleMode === 'now') return 'Schedule Now'
  if (props.scheduleMode === 'next') return 'Next Schedule'
  return t('composer.scheduleBtn')
})

const localScheduleMode = computed({
  get: () => props.scheduleMode,
  set: (val) => emit('update:scheduleMode', val as ComposerScheduleMode),
})

const localScheduleTime = computed({
  get: () => props.scheduleTime,
  set: (val) => emit('update:scheduleTime', val),
})

const localPriorityMode = computed({
  get: () => props.priorityMode,
  set: (val) => emit('update:priorityMode', val),
})

const localCreateAnother = computed({
  get: () => props.createAnother,
  set: (val) => emit('update:createAnother', val),
})

function onCalendarUpdate(val: DateValue | undefined) {
  emit('update:selectedCalendarDate', val)
  isDatePickerOpen.value = false
}
</script>

<template>
  <div class="border-t border-border-subtle pt-6 space-y-4">
    <div class="space-y-3">
      <!-- Schedule mode selector -->
      <div class="flex items-center gap-4 bg-bg-surface border border-border-subtle p-3 rounded-xl">
        <CalendarIcon class="size-4 text-text-secondary shrink-0" />
        <div class="flex-1 space-y-2 text-xs">
          <span class="text-text-secondary">Schedule Mode:</span>
          <div
            class="grid grid-cols-3 gap-1 rounded-lg bg-bg-primary/60 p-1"
            role="radiogroup"
            aria-label="Schedule mode"
          >
            <label
              class="px-2 py-1 rounded font-mono text-[9px] uppercase tracking-wider font-bold transition-all cursor-pointer"
              :class="scheduleMode === 'now' ? 'bg-text-display text-bg-primary' : 'bg-transparent text-text-secondary hover:text-text-display'"
            >
              <input type="radio" v-model="localScheduleMode" value="now" class="sr-only" />
              Now
            </label>
            <label
              class="px-2 py-1 rounded font-mono text-[9px] uppercase tracking-wider font-bold transition-all cursor-pointer"
              :class="scheduleMode === 'next' ? 'bg-text-display text-bg-primary' : 'bg-transparent text-text-secondary hover:text-text-display'"
            >
              <input type="radio" v-model="localScheduleMode" value="next" class="sr-only" />
              Next Schedule
            </label>
            <label
              class="px-2 py-1 rounded font-mono text-[9px] uppercase tracking-wider font-bold transition-all cursor-pointer"
              :class="scheduleMode === 'custom' ? 'bg-text-display text-bg-primary' : 'bg-transparent text-text-secondary hover:text-text-display'"
            >
              <input type="radio" v-model="localScheduleMode" value="custom" class="sr-only" />
              Pick Date
            </label>
          </div>
          <p class="text-[10px] leading-4 text-text-secondary">
            {{ scheduleHelperText }}
          </p>
        </div>
      </div>

      <!-- Custom date + time picker -->
      <div
        v-if="scheduleMode === 'custom'"
        class="grid grid-cols-[1fr_112px] gap-3 animate-slide-down"
      >
        <Popover v-model:open="isDatePickerOpen">
          <PopoverTrigger as-child>
            <button
              type="button"
              class="flex items-center justify-between gap-2 bg-bg-surface border border-border-subtle rounded-xl px-3 py-2 text-xs text-text-body hover:border-text-display focus:outline-none focus:border-text-display font-sans"
            >
              <span>{{ selectedDateLabel(selectedCalendarDate) }}</span>
              <CalendarIcon class="size-3.5 text-text-secondary" />
            </button>
          </PopoverTrigger>
          <PopoverContent class="w-auto p-0 bg-bg-surface border-border-subtle" align="start">
            <Calendar
              :model-value="selectedCalendarDate"
              :min-value="todayDateValue"
              layout="month-and-year"
              initial-focus
              @update:model-value="onCalendarUpdate"
            />
          </PopoverContent>
        </Popover>
        <label for="composer-schedule-time" class="sr-only">Schedule time</label>
        <input
          id="composer-schedule-time"
          v-model="localScheduleTime"
          type="time"
          :min="minTimeForDate(selectedCalendarDate)"
          class="bg-bg-surface border border-border-subtle rounded-xl px-3 py-2 text-xs text-text-body focus:outline-none focus:border-text-display font-sans"
        />
      </div>

      <!-- Priority + Create Another -->
      <div class="flex items-center justify-between text-[10px] font-mono text-text-secondary px-1">
        <label class="flex items-center gap-1.5 cursor-pointer hover:text-text-display select-none">
          <input type="checkbox" v-model="localPriorityMode" class="accent-text-display" />
          <span>Priority Queue</span>
        </label>
        <label
          v-if="!isEditMode"
          class="flex items-center gap-1.5 cursor-pointer hover:text-text-display select-none"
        >
          <input type="checkbox" v-model="localCreateAnother" class="accent-text-display" />
          <span>Create Another</span>
        </label>
      </div>
    </div>

    <!-- Submit error -->
    <p
      v-if="submitError"
      class="rounded-xl border border-error/30 bg-error/10 px-3 py-2 text-xs text-error"
    >
      {{ submitError }}
    </p>

    <!-- Action buttons -->
    <div class="grid grid-cols-3 gap-3">
      <button
        type="button"
        class="col-span-1 border border-border-visible text-text-body hover:border-text-display hover:text-text-display font-mono text-[10px] font-bold uppercase tracking-wider rounded-full py-2.5 transition-all text-center cursor-pointer"
        @click="emit('close')"
      >
        {{ $t('composer.cancelBtn') }}
      </button>
      <Button
        :disabled="!canSubmit"
        class="col-span-2 justify-center py-2.5 font-bold"
        @click="emit('submit')"
      >
        {{ submitLabel }}
      </Button>
    </div>
  </div>
</template>

<style scoped>
.animate-slide-down {
  animation: slideDown 0.15s ease-out forwards;
}

@keyframes slideDown {
  from { height: 0; opacity: 0; overflow: hidden; }
  to { height: 38px; opacity: 1; }
}
</style>
