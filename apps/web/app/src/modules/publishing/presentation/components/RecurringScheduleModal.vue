<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useFocusTrap } from '@shared/composables/useFocusTrap'
import {
  usePublishingStore,
  type Publication,
  type RecurrenceFrequency,
  type RecurringSchedule,
  type RecurringScheduleInput,
} from '@modules/publishing/infrastructure/publishing.store'

const props = withDefaults(
  defineProps<{
    isOpen?: boolean
    publication: Publication | null
    schedule?: RecurringSchedule | null
  }>(),
  { isOpen: false, schedule: null },
)

const emit = defineEmits<{
  (event: 'close'): void
  (event: 'saved', schedule: RecurringSchedule): void
}>()

const { t } = useI18n()
const publishing = usePublishingStore()
const frequency = ref<RecurrenceFrequency>('daily')
const interval = ref(1)
const daysOfWeek = ref<number[]>([1])
const dayOfMonth = ref(1)
const startsAt = ref('')
const endDate = ref('')
const maxOccurrences = ref('')
const timezone = ref(Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC')
const error = ref('')
const isSaving = ref(false)
const container = ref<HTMLElement | null>(null)
const { activate, deactivate } = useFocusTrap(container, () => emit('close'))

const weekDays = computed(() => [
  t('recurring.days.monday'), t('recurring.days.tuesday'), t('recurring.days.wednesday'),
  t('recurring.days.thursday'), t('recurring.days.friday'), t('recurring.days.saturday'), t('recurring.days.sunday'),
])
const isEditing = computed(() => Boolean(props.schedule))

function toLocalInput(iso: string): string {
  const date = new Date(iso)
  const offset = date.getTimezoneOffset()
  return new Date(date.getTime() - offset * 60_000).toISOString().slice(0, 16)
}

function initialize(): void {
  const schedule = props.schedule
  const defaultStart = props.publication?.scheduledAt || new Date(Date.now() + 10 * 60_000).toISOString()
  frequency.value = schedule?.frequency ?? 'daily'
  interval.value = schedule?.interval ?? 1
  daysOfWeek.value = schedule?.daysOfWeek?.length ? [...schedule.daysOfWeek] : [1]
  dayOfMonth.value = schedule?.dayOfMonth ?? 1
  startsAt.value = toLocalInput(schedule?.nextScheduledAt ?? defaultStart)
  endDate.value = schedule?.endDate ?? ''
  maxOccurrences.value = schedule?.maxOccurrences ? String(schedule.maxOccurrences) : ''
  timezone.value = schedule?.timezone ?? Intl.DateTimeFormat().resolvedOptions().timeZone ?? 'UTC'
  error.value = ''
}

watch(() => [props.isOpen, props.schedule, props.publication] as const, async ([open]) => {
  if (!open) {
    deactivate()
    return
  }
  initialize()
  await nextTick()
  activate()
}, { immediate: true })

function toggleDay(day: number): void {
  daysOfWeek.value = daysOfWeek.value.includes(day)
    ? daysOfWeek.value.filter((value) => value !== day)
    : [...daysOfWeek.value, day].sort((a, b) => a - b)
}

function localDateTimeToIso(value: string): string {
  return new Date(value).toISOString()
}

async function save(): Promise<void> {
  error.value = ''
  if (!props.publication) return
  if (!startsAt.value || new Date(startsAt.value) <= new Date()) {
    error.value = t('recurring.errors.futureStart')
    return
  }
  if (frequency.value === 'weekly' && daysOfWeek.value.length === 0) {
    error.value = t('recurring.errors.weekdayRequired')
    return
  }
  isSaving.value = true
  try {
    const input: RecurringScheduleInput = {
      frequency: frequency.value,
      interval: Math.max(1, interval.value),
      daysOfWeek: frequency.value === 'weekly' ? daysOfWeek.value : [],
      dayOfMonth: frequency.value === 'monthly' ? dayOfMonth.value : null,
      endDate: endDate.value || null,
      maxOccurrences: maxOccurrences.value ? Number(maxOccurrences.value) : null,
      startsAt: localDateTimeToIso(startsAt.value),
      timezone: timezone.value,
    }
    const saved = props.schedule
      ? await publishing.updateRecurringSchedule(props.schedule.id, input)
      : await publishing.createRecurringSchedule(props.publication.id, input)
    emit('saved', saved)
    emit('close')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : t('recurring.errors.saveFailed')
  } finally {
    isSaving.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="isOpen" class="fixed inset-0 z-[60] flex items-center justify-center bg-black/80 p-4">
      <button type="button" class="absolute inset-0 cursor-default" :aria-label="t('common.close')" @click="emit('close')" />
      <dialog ref="container" open class="relative m-0 w-full max-w-xl rounded-2xl border border-border-subtle bg-bg-surface p-0 text-text-body shadow-2xl" aria-modal="true" aria-labelledby="recurring-title">
        <form class="space-y-5 p-6" @submit.prevent="save">
          <header>
            <h2 id="recurring-title" class="font-mono text-sm font-bold uppercase tracking-widest text-text-display">
              {{ isEditing ? t('recurring.editTitle') : t('recurring.title') }}
            </h2>
            <p class="mt-1 text-xs text-text-secondary">{{ t('recurring.description') }}</p>
          </header>

          <div class="grid grid-cols-2 gap-3">
            <label class="space-y-1 text-xs font-mono uppercase tracking-wider">
              {{ t('recurring.frequency') }}
              <select v-model="frequency" class="w-full rounded-xl border border-border-visible bg-bg-primary px-3 py-2 text-sm normal-case tracking-normal">
                <option value="daily">{{ t('recurring.daily') }}</option>
                <option value="weekly">{{ t('recurring.weekly') }}</option>
                <option value="monthly">{{ t('recurring.monthly') }}</option>
              </select>
            </label>
            <label class="space-y-1 text-xs font-mono uppercase tracking-wider">
              {{ t('recurring.interval') }}
              <input v-model.number="interval" min="1" type="number" class="w-full rounded-xl border border-border-visible bg-bg-primary px-3 py-2 text-sm normal-case tracking-normal" />
            </label>
          </div>

          <fieldset v-if="frequency === 'weekly'" class="space-y-2">
            <legend class="text-xs font-mono uppercase tracking-wider">{{ t('recurring.weekdays') }}</legend>
            <div class="grid grid-cols-7 gap-1">
              <label v-for="(label, day) in weekDays" :key="day" class="flex cursor-pointer flex-col items-center gap-1 rounded-lg border border-border-subtle px-1 py-2 text-[10px]">
                <input type="checkbox" :checked="daysOfWeek.includes(day)" @change="toggleDay(day)" />
                <span>{{ label }}</span>
              </label>
            </div>
          </fieldset>

          <label v-if="frequency === 'monthly'" class="block space-y-1 text-xs font-mono uppercase tracking-wider">
            {{ t('recurring.dayOfMonth') }}
            <input v-model.number="dayOfMonth" min="1" max="31" type="number" class="w-full rounded-xl border border-border-visible bg-bg-primary px-3 py-2 text-sm normal-case tracking-normal" />
          </label>

          <div class="grid grid-cols-2 gap-3">
            <label class="space-y-1 text-xs font-mono uppercase tracking-wider">
              {{ t('recurring.startsAt') }}
              <input v-model="startsAt" type="datetime-local" class="w-full rounded-xl border border-border-visible bg-bg-primary px-3 py-2 text-sm normal-case tracking-normal" />
            </label>
            <label class="space-y-1 text-xs font-mono uppercase tracking-wider">
              {{ t('recurring.endDate') }}
              <input v-model="endDate" type="date" class="w-full rounded-xl border border-border-visible bg-bg-primary px-3 py-2 text-sm normal-case tracking-normal" />
            </label>
          </div>

          <label class="block space-y-1 text-xs font-mono uppercase tracking-wider">
            {{ t('recurring.maxOccurrences') }}
            <input v-model="maxOccurrences" min="1" type="number" :placeholder="t('recurring.optional')" class="w-full rounded-xl border border-border-visible bg-bg-primary px-3 py-2 text-sm normal-case tracking-normal" />
          </label>
          <p class="text-xs text-text-secondary">{{ t('recurring.timezone', { timezone }) }}</p>
          <p v-if="error" role="alert" class="text-xs text-error">{{ error }}</p>
          <footer class="flex justify-end gap-2 border-t border-border-subtle pt-4">
            <button type="button" class="rounded-xl border border-border-visible px-3 py-2 text-xs font-mono uppercase tracking-wider" @click="emit('close')">{{ t('recurring.cancel') }}</button>
            <button type="submit" :disabled="isSaving" class="rounded-xl bg-text-display px-3 py-2 text-xs font-mono font-bold uppercase tracking-wider text-bg-primary disabled:opacity-50">{{ t('recurring.save') }}</button>
          </footer>
        </form>
      </dialog>
    </div>
  </Teleport>
</template>
