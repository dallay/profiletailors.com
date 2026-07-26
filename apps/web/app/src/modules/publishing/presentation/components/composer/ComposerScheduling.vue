<script setup lang="ts">
import type { DateValue } from 'reka-ui'
import type { ComposerScheduleMode } from '@modules/publishing/presentation/components/composer/composer.types'

interface Props {
  scheduleMode?: ComposerScheduleMode
  selectedCalendarDate?: DateValue
  scheduleTime?: string
  priorityMode?: boolean
  createAnother?: boolean
  isEditMode?: boolean
  canSubmit?: boolean
  isSubmitting?: boolean
  submitError?: string
  now: Date
}

interface Emits {
  (e: 'update:scheduleMode', value: ComposerScheduleMode): void
  (e: 'update:selectedCalendarDate', value: DateValue | undefined): void
  (e: 'update:scheduleTime', value: string): void
  (e: 'update:priorityMode', value: boolean): void
  (e: 'update:createAnother', value: boolean): void
  (e: 'close'): void
  (e: 'submit'): void
}

const _props = withDefaults(defineProps<Props>(), {
  scheduleMode: 'now',
  scheduleTime: '10:00',
  priorityMode: false,
  createAnother: false,
  isEditMode: false,
  canSubmit: false,
  isSubmitting: false,
  submitError: '',
})

const emit = defineEmits<Emits>()
function handleScheduleModeChange(mode: ComposerScheduleMode) {
  emit('update:scheduleMode', mode)
}

function _handleCalendarDateChange(date: DateValue | undefined) {
  emit('update:selectedCalendarDate', date)
}

function handleTimeChange(time: string) {
  emit('update:scheduleTime', time)
}
</script>

<template>
  <div class="space-y-4">
    <!-- Schedule Mode Selection -->
    <fieldset class="space-y-3">
      <legend class="font-mono text-[9px] tracking-widest text-text-secondary uppercase block">
        {{ $t('composer.schedule.mode') }}
      </legend>
      <div class="grid grid-cols-3 gap-2">
        <button
          type="button"
          @click="handleScheduleModeChange('now')"
          :class="[
            'px-3 py-2.5 rounded-lg text-xs font-medium transition-all',
            scheduleMode === 'now'
              ? 'bg-primary text-white'
              : 'bg-bg-secondary text-text-display hover:bg-bg-tertiary',
          ]"
        >
          {{ $t('composer.schedule.now') }}
        </button>
        <button
          type="button"
          @click="handleScheduleModeChange('next')"
          :class="[
            'px-3 py-2.5 rounded-lg text-xs font-medium transition-all',
            scheduleMode === 'next'
              ? 'bg-primary text-white'
              : 'bg-bg-secondary text-text-display hover:bg-bg-tertiary',
          ]"
        >
          {{ $t('composer.schedule.nextSlot') }}
        </button>
        <button
          type="button"
          @click="handleScheduleModeChange('custom')"
          :class="[
            'px-3 py-2.5 rounded-lg text-xs font-medium transition-all',
            scheduleMode === 'custom'
              ? 'bg-primary text-white'
              : 'bg-bg-secondary text-text-display hover:bg-bg-tertiary',
          ]"
        >
          {{ $t('composer.schedule.custom') }}
        </button>
      </div>
    </fieldset>

    <!-- Custom Date/Time (only when custom mode) -->
    <div v-if="scheduleMode === 'custom'" class="space-y-3 p-3 rounded-lg bg-bg-secondary/50">
      <div class="space-y-2">
        <label for="schedule-date" class="font-mono text-[9px] tracking-widest text-text-secondary uppercase block">
          {{ $t('composer.schedule.date') }}
        </label>
        <input
          id="schedule-date"
          type="date"
          class="w-full bg-bg-primary border border-border-visible rounded-lg px-3 py-2 text-xs text-text-display font-sans"
        />
      </div>
      <div class="space-y-2">
        <label for="schedule-time" class="font-mono text-[9px] tracking-widest text-text-secondary uppercase block">
          {{ $t('composer.schedule.time') }}
        </label>
        <input
          id="schedule-time"
          type="time"
          :value="scheduleTime"
          @input="handleTimeChange(($event.target as HTMLInputElement).value)"
          class="w-full bg-bg-primary border border-border-visible rounded-lg px-3 py-2 text-xs text-text-display font-sans"
        />
      </div>
    </div>

    <!-- Priority Mode -->
    <div class="flex items-center gap-3 p-2.5 rounded-lg bg-bg-secondary/50">
      <input
        id="priority-mode"
        type="checkbox"
        :checked="priorityMode"
        @change="emit('update:priorityMode', ($event.target as HTMLInputElement).checked)"
        class="w-4 h-4 rounded border-border-visible text-primary"
      />
      <label for="priority-mode" class="text-xs text-text-body cursor-pointer">
        {{ $t('composer.priority') }}
      </label>
    </div>

    <!-- Create Another Checkbox -->
    <div class="flex items-center gap-3 p-2.5 rounded-lg bg-bg-secondary/50">
      <input
        id="create-another"
        type="checkbox"
        :checked="createAnother"
        @change="emit('update:createAnother', ($event.target as HTMLInputElement).checked)"
        class="w-4 h-4 rounded border-border-visible text-primary"
      />
      <label for="create-another" class="text-xs text-text-body cursor-pointer">
        {{ $t('composer.createAnother') }}
      </label>
    </div>

    <!-- Error Message -->
    <div
      v-if="submitError"
      class="rounded-lg border border-error/40 bg-error/10 px-3 py-2 text-xs text-error"
    >
      {{ submitError }}
    </div>

    <!-- Submit Actions -->
    <div class="grid grid-cols-2 gap-2 pt-2">
      <button
        type="button"
        @click="emit('close')"
        class="px-4 py-2.5 rounded-lg bg-bg-secondary text-text-display text-xs font-medium hover:bg-bg-tertiary transition-colors"
      >
        {{ $t('common.cancel') }}
      </button>
      <button
        type="button"
        @click="emit('submit')"
        :disabled="!canSubmit || isSubmitting"
        class="px-4 py-2.5 rounded-lg bg-primary text-white text-xs font-medium hover:bg-primary-dark transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
      >
        {{ isSubmitting ? $t('common.submitting') : $t('common.schedule') }}
      </button>
    </div>
  </div>
</template>
