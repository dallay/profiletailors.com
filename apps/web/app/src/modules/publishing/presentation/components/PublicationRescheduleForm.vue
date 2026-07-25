<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  initialScheduledAt: string
  apiError: string
}>()

const emit = defineEmits<{
  (e: 'confirm', isoDate: string): void
  (e: 'cancel'): void
}>()

const { t } = useI18n()
const newScheduledAt = ref(props.initialScheduledAt)
const validationError = ref('')

function handleConfirm() {
  validationError.value = ''
  const newDate = new Date(newScheduledAt.value)
  if (Number.isNaN(newDate.getTime()) || newDate <= new Date()) {
    validationError.value = t('postDetail.rescheduleInvalidDate')
    return
  }
  emit('confirm', newDate.toISOString())
}
</script>

<template>
  <div class="px-6 pt-3 pb-2 space-y-2">
    <label for="reschedule-datetime" class="font-mono text-[9px] font-bold tracking-widest text-text-secondary uppercase">
      {{ $t('postDetail.scheduledFor') }}
    </label>
    <input
      id="reschedule-datetime"
      v-model="newScheduledAt"
      type="datetime-local"
      class="w-full rounded-xl border border-border-visible bg-bg-surface text-text-body px-3 py-2 text-xs font-mono focus:outline-none focus:ring-2 focus:ring-text-display/30"
      :aria-label="$t('postDetail.scheduledFor')"
    />
    <p v-if="validationError" role="alert" class="text-[10px] font-mono text-error">{{ validationError }}</p>
    <p v-if="apiError" role="alert" class="text-[10px] font-mono text-error">{{ apiError }}</p>
    <div class="flex gap-2">
      <button
        type="button"
        class="px-3 py-2 rounded-xl bg-text-display text-bg-primary hover:opacity-90 transition-opacity text-xs font-mono uppercase tracking-wider font-bold cursor-pointer"
        @click="handleConfirm"
      >
        {{ $t('postDetail.rescheduleConfirm') }}
      </button>
      <button
        type="button"
        class="px-3 py-2 rounded-xl border border-border-visible text-text-body hover:border-text-display hover:text-text-display transition-colors bg-bg-surface text-xs font-mono uppercase tracking-wider font-bold cursor-pointer"
        @click="emit('cancel')"
      >
        {{ $t('postDetail.rescheduleCancel') }}
      </button>
    </div>
  </div>
</template>
