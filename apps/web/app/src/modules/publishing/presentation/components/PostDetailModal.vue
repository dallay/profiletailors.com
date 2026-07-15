<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { CalendarClock, ExternalLink, Pencil, Trash2, X, AlertTriangle, CheckCircle2, Clock } from '@lucide/vue'
import { useFocusTrap } from '@shared/composables/useFocusTrap'
import { usePublishingStore, type Publication } from '@modules/publishing/infrastructure/publishing.store'
import type { ApiError } from '@modules/auth/infrastructure/auth-api'
import { getProviderBadge } from '@shared/lib/provider-styles'

const props = withDefaults(
  defineProps<{
    isOpen: boolean
    publication: Publication | null
  }>(),
  { isOpen: false },
)

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'deleted', id: string): void
  (e: 'reschedule', payload: { id: string; scheduledAt: string }): void
  (e: 'retried', id: string): void
  (e: 'edit', publication: Publication): void
}>()

const { t, locale: i18nLocale } = useI18n()
const publishingStore = usePublishingStore()

const FAILURE_COPY_KEYS = {
  MEDIA_NOT_FOUND: 'mediaNotFound',
  MEDIA_UNAVAILABLE: 'mediaUnavailable',
  PROVIDER_VALIDATION_FAILED: 'providerValidationFailed',
  PROVIDER_RATE_LIMITED: 'providerRateLimited',
  PROVIDER_UNAVAILABLE: 'providerUnavailable',
  ACCOUNT_RECONNECT_REQUIRED: 'accountReconnectRequired',
  ACCOUNT_UNAVAILABLE: 'accountUnavailable',
  PUBLISHING_FAILED: 'publishingFailed',
} as const

const FAILURE_COPY_I18N_KEYS = {
  mediaNotFound: {
    label: 'postDetail.failure.mediaNotFound.label',
    explanation: 'postDetail.failure.mediaNotFound.explanation',
    action: 'postDetail.failure.mediaNotFound.action',
  },
  mediaUnavailable: {
    label: 'postDetail.failure.mediaUnavailable.label',
    explanation: 'postDetail.failure.mediaUnavailable.explanation',
    action: 'postDetail.failure.mediaUnavailable.action',
  },
  providerValidationFailed: {
    label: 'postDetail.failure.providerValidationFailed.label',
    explanation: 'postDetail.failure.providerValidationFailed.explanation',
    action: 'postDetail.failure.providerValidationFailed.action',
  },
  providerRateLimited: {
    label: 'postDetail.failure.providerRateLimited.label',
    explanation: 'postDetail.failure.providerRateLimited.explanation',
    action: 'postDetail.failure.providerRateLimited.action',
  },
  providerUnavailable: {
    label: 'postDetail.failure.providerUnavailable.label',
    explanation: 'postDetail.failure.providerUnavailable.explanation',
    action: 'postDetail.failure.providerUnavailable.action',
  },
  accountReconnectRequired: {
    label: 'postDetail.failure.accountReconnectRequired.label',
    explanation: 'postDetail.failure.accountReconnectRequired.explanation',
    action: 'postDetail.failure.accountReconnectRequired.action',
  },
  accountUnavailable: {
    label: 'postDetail.failure.accountUnavailable.label',
    explanation: 'postDetail.failure.accountUnavailable.explanation',
    action: 'postDetail.failure.accountUnavailable.action',
  },
  publishingFailed: {
    label: 'postDetail.failure.publishingFailed.label',
    explanation: 'postDetail.failure.publishingFailed.explanation',
    action: 'postDetail.failure.publishingFailed.action',
  },
  unknown: {
    label: 'postDetail.failure.unknown.label',
    explanation: 'postDetail.failure.unknown.explanation',
    action: 'postDetail.failure.unknown.action',
  },
} as const

const ACTION_REASON_KEYS = {
  unauthorized: 'postDetail.actionErrors.reasons.unauthorized',
  notFound: 'postDetail.actionErrors.reasons.notFound',
  stateConflict: 'postDetail.actionErrors.reasons.stateConflict',
  validation: 'postDetail.actionErrors.reasons.validation',
  temporarilyUnavailable: 'postDetail.actionErrors.reasons.temporarilyUnavailable',
  unknown: 'postDetail.actionErrors.reasons.unknown',
} as const

const ACTION_OPERATION_KEYS = {
  retry: 'postDetail.actionErrors.operations.retry',
  delete: 'postDetail.actionErrors.operations.delete',
  reschedule: 'postDetail.actionErrors.operations.reschedule',
} as const

type FailureCopyKey = keyof typeof FAILURE_COPY_I18N_KEYS
type ActionOperation = keyof typeof ACTION_OPERATION_KEYS
type ActionReason = keyof typeof ACTION_REASON_KEYS

function mapFailureCopyKey(value: string | undefined): FailureCopyKey {
  if (!value) return 'unknown'
  if (!Object.hasOwn(FAILURE_COPY_KEYS, value)) return 'unknown'
  return FAILURE_COPY_KEYS[value as keyof typeof FAILURE_COPY_KEYS]
}

function isApiError(error: unknown): error is Error & ApiError {
  return typeof error === 'object' && error !== null
}

function mapActionReason(error: unknown): ActionReason {
  if (!isApiError(error)) return 'unknown'
  const code = error.errorCode ?? error.code
  if (code === 'UNAUTHORIZED' || code === 'FORBIDDEN') return 'unauthorized'
  if (code === 'NOT_FOUND') return 'notFound'
  if (code === 'STATE_CONFLICT' || code === 'CONFLICT') return 'stateConflict'
  if (code === 'VALIDATION_FAILED' || code === 'VALIDATION_ERROR') return 'validation'
  if (code === 'RATE_LIMITED' || code === 'SERVICE_UNAVAILABLE') return 'temporarilyUnavailable'
  if (error instanceof Error && error.status === undefined) return 'temporarilyUnavailable'
  switch (error.status) {
    case 401:
    case 403:
      return 'unauthorized'
    case 404:
      return 'notFound'
    case 409:
      return 'stateConflict'
    case 400:
    case 422:
      return 'validation'
    case 429:
      return 'temporarilyUnavailable'
    default:
      return typeof error.status === 'number' && error.status >= 500 ? 'temporarilyUnavailable' : 'unknown'
  }
}

function actionErrorMessage(error: unknown, operation: ActionOperation): string {
  const reason = mapActionReason(error)
  return t('postDetail.actionErrors.message', {
    reason: t(ACTION_REASON_KEYS[reason]),
    operation: t(ACTION_OPERATION_KEYS[operation]),
  })
}

const isReadOnly = computed(() => props.publication?.status === 'PUBLISHED')
const canEditPublication = computed(() =>
  props.publication ? publishingStore.isPublicationEditable(props.publication.status) : false,
)
const canDelete = computed(() =>
  props.publication ? publishingStore.isPublicationDeletable(props.publication.status) : false,
)

const statusLabel = computed(() => {
  if (!props.publication) return ''
  switch (props.publication.status) {
    case 'DRAFT':
      return t('postDetail.status.draft')
    case 'QUEUED':
      return t('postDetail.status.queued')
    case 'SCHEDULED':
      return t('postDetail.status.scheduled')
    case 'PROCESSING':
      return t('postDetail.status.processing')
    case 'PUBLISHED':
      return t('postDetail.status.published')
    case 'BLOCKED':
      return t('postDetail.status.blocked')
    case 'FAILED':
      return t('postDetail.status.failed')
    case 'CANCELLED':
      return t('postDetail.status.cancelled')
    default:
      return props.publication.status
  }
})

const statusTone = computed(() => {
  if (!props.publication) return 'text-text-secondary'
  switch (props.publication.status) {
    case 'PUBLISHED':
      return 'text-success'
    case 'BLOCKED':
    case 'FAILED':
      return 'text-error'
    case 'CANCELLED':
      return 'text-text-secondary'
    case 'PROCESSING':
    case 'QUEUED':
    case 'SCHEDULED':
      return 'text-warning'
    default:
      return 'text-text-secondary'
  }
})

const viewPostUrl = computed(() => {
  if (!props.publication) return null
  // Prefer the public URL the backend provides (e.g. https://www.linkedin.com/feed/update/...)
  if (props.publication.publicUrl) return props.publication.publicUrl
  // Fallback: build a LinkedIn share URL from the URN when the backend did not return one
  const urn = props.publication.externalPublicationId
  if (urn?.startsWith('urn:li:')) {
    return `https://www.linkedin.com/feed/update/${encodeURIComponent(urn)}`
  }
  return null
})

const dateLocale = computed(() => (i18nLocale.value === 'es' ? 'es-ES' : 'en-US'))

const scheduledAtLabel = computed(() => {
  if (!props.publication?.scheduledAt) return ''
  const d = new Date(props.publication.scheduledAt)
  return d.toLocaleString(dateLocale.value, {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
})

const publishedAtLabel = computed(() => {
  if (!props.publication?.publishedAt) return ''
  const d = new Date(props.publication.publishedAt)
  return d.toLocaleString(dateLocale.value, {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
})

const isDeleting = ref(false)
const deleteError = ref('')

const showReschedule = ref(false)
const newScheduledAt = ref('')
const rescheduleError = ref('')
const isRetrying = ref(false)
const retryError = ref('')

const failureCopyKey = computed<FailureCopyKey | null>(() => {
  if (!props.publication) return null
  if (props.publication.status === 'BLOCKED') return mapFailureCopyKey(props.publication.blockedReason)
  if (props.publication.status === 'FAILED') return mapFailureCopyKey(props.publication.errorCode)
  return null
})

const failureCopy = computed(() => (failureCopyKey.value ? FAILURE_COPY_I18N_KEYS[failureCopyKey.value] : null))

const modalContainer = ref<HTMLElement | null>(null)
const { activate: activateFocusTrap, deactivate: deactivateFocusTrap } = useFocusTrap(modalContainer, closeModal)

watch(
  () => [props.isOpen, props.publication] as const,
  async ([open]) => {
    if (open) {
      deleteError.value = ''
      retryError.value = ''
      await nextTick()
      // Guard against the modal being closed during the await window
      if (props.isOpen) {
        activateFocusTrap()
      }
    } else {
      deactivateFocusTrap()
    }
  },
  { immediate: true },
)

function closeModal() {
  emit('close')
}

async function deletePublication() {
  if (!props.publication || isDeleting.value || !canDelete.value) return
  isDeleting.value = true
  deleteError.value = ''
  try {
    await publishingStore.deletePost(props.publication.id)
    emit('deleted', props.publication.id)
    closeModal()
  } catch (err) {
    deleteError.value = actionErrorMessage(err, 'delete')
    console.error('Failed to delete publication', err)
  } finally {
    isDeleting.value = false
  }
}

function openReschedule() {
  if (!props.publication?.scheduledAt) return
  // Pre-fill with current scheduled date, local datetime-local format
  const d = new Date(props.publication.scheduledAt)
  const offset = d.getTimezoneOffset()
  const local = new Date(d.getTime() - offset * 60_000)
  newScheduledAt.value = local.toISOString().slice(0, 16)
  showReschedule.value = true
  rescheduleError.value = ''
}

async function retryPublication() {
  if (!props.publication || isRetrying.value || props.publication.status !== 'FAILED') return
  isRetrying.value = true
  retryError.value = ''
  try {
    await publishingStore.retryPublication(props.publication.id)
    emit('retried', props.publication.id)
    closeModal()
  } catch (err) {
    retryError.value = actionErrorMessage(err, 'retry')
  } finally {
    isRetrying.value = false
  }
}

async function confirmReschedule() {
  if (!props.publication || !newScheduledAt.value) return
  rescheduleError.value = ''
  const newDate = new Date(newScheduledAt.value)
  if (Number.isNaN(newDate.getTime()) || newDate <= new Date()) {
    rescheduleError.value = t('postDetail.rescheduleInvalidDate')
    return
  }
  try {
    const newIso = newDate.toISOString()
    await publishingStore.reschedulePublication(props.publication.id, newIso)
    emit('reschedule', { id: props.publication.id, scheduledAt: newIso })
    showReschedule.value = false
    closeModal()
  } catch (err) {
    rescheduleError.value = actionErrorMessage(err, 'reschedule')
  }
}

function cancelReschedule() {
  showReschedule.value = false
  rescheduleError.value = ''
}
</script>

<template>
  <Teleport to="body">
    <!-- biome-ignore lint/a11y/noStaticElementInteractions: modal backdrop intentionally uses click only -->
    <!-- biome-ignore lint/a11y/useKeyWithClickEvents: modal backdrop intentionally uses click only -->
    <div
      v-if="isOpen && publication"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4"
      @click.self="closeModal"
    >
      <div
        ref="modalContainer"
        class="flex flex-col w-full max-w-2xl max-h-[90vh] bg-bg-surface border border-border-subtle rounded-2xl overflow-hidden shadow-2xl m-0 relative"
        role="dialog"
        aria-modal="true"
        aria-labelledby="post-detail-title"
        @keydown.escape="closeModal"
      >
        <header class="flex items-center justify-between p-6 border-b border-border-subtle">
          <div class="flex items-center gap-3">
            <span
              class="flex size-9 items-center justify-center rounded-full border border-border-visible bg-bg-primary font-mono text-xs font-bold uppercase text-text-display"
            >
              {{ getProviderBadge(publication.channels[0] || 'linkedin') }}
            </span>
            <div class="space-y-0.5">
              <h3 id="post-detail-title" class="font-mono text-xs font-bold tracking-widest text-text-display uppercase">
                {{ t('postDetail.title') }}
              </h3>
              <div class="flex items-center gap-2 text-[10px] font-mono uppercase tracking-wider">
                <span :class="statusTone">
                  <span v-if="publication.status === 'PUBLISHED'" class="inline-flex items-center gap-1">
                    <CheckCircle2 class="size-3" />
                    {{ statusLabel }}
                  </span>
                  <span v-else-if="publication.status === 'BLOCKED' || publication.status === 'FAILED'" class="inline-flex items-center gap-1">
                    <AlertTriangle class="size-3" />
                    {{ statusLabel }}
                  </span>
                  <span v-else class="inline-flex items-center gap-1">
                    <Clock class="size-3" />
                    {{ statusLabel }}
                  </span>
                </span>
                <span v-if="isReadOnly" class="px-1.5 py-0.5 rounded bg-text-secondary/15 text-text-secondary">
                  {{ t('postDetail.readOnly') }}
                </span>
              </div>
            </div>
          </div>
          <button
            @click="closeModal"
            class="flex size-8 items-center justify-center rounded-xl border border-border-subtle bg-bg-primary text-text-secondary hover:text-text-display cursor-pointer"
            :aria-label="t('postDetail.close')"
          >
            <X class="size-4" />
          </button>
        </header>

        <div class="flex-1 overflow-y-auto p-6 space-y-5">
          <div class="space-y-1">
            <span class="font-mono text-[9px] font-bold tracking-widest text-text-secondary uppercase">
              {{ t('postDetail.titleLabel') }}
            </span>
            <p v-if="publication.title" class="text-sm font-semibold text-text-display">{{ publication.title }}</p>
          </div>

          <div class="space-y-1">
            <span class="font-mono text-[9px] font-bold tracking-widest text-text-secondary uppercase">
              {{ t('postDetail.bodyLabel') }}
            </span>
            <p class="text-sm font-light leading-relaxed text-text-body whitespace-pre-wrap">
              {{ publication.content }}
            </p>
          </div>

          <div v-if="publication.thumbnail" class="space-y-2">
            <span class="font-mono text-[9px] font-bold tracking-widest text-text-secondary uppercase">
              {{ t('postDetail.mediaLabel') }}
            </span>
            <div class="overflow-hidden rounded-2xl border border-border-subtle bg-bg-primary/30">
              <img
                :src="publication.thumbnail"
                :alt="t('postDetail.mediaPreviewAlt')"
                class="block max-h-80 w-full object-cover"
              />
            </div>
          </div>

          <div v-if="failureCopy" class="space-y-2 rounded-2xl border border-border-visible bg-bg-primary/40 px-4 py-3">
            <span class="font-mono text-[9px] font-bold tracking-widest text-text-secondary uppercase">
              {{ t('postDetail.failure.title') }}
            </span>
            <p class="text-xs font-semibold text-text-display">
              {{ t(failureCopy.label) }}
            </p>
            <p class="text-xs text-text-body">
              {{ t(failureCopy.explanation) }}
            </p>
            <p class="text-xs text-text-secondary">
              {{ t(failureCopy.action) }}
            </p>
          </div>

          <div class="grid grid-cols-2 gap-3 pt-2 border-t border-border-subtle">
            <div class="space-y-1">
              <span class="font-mono text-[9px] font-bold tracking-widest text-text-secondary uppercase">
                {{ t('postDetail.scheduledFor') }}
              </span>
              <p class="text-xs text-text-body flex items-center gap-1.5">
                <CalendarClock class="size-3" />
                {{ scheduledAtLabel }}
              </p>
            </div>
            <div v-if="publication.publishedAt" class="space-y-1">
              <span class="font-mono text-[9px] font-bold tracking-widest text-text-secondary uppercase">
                {{ t('postDetail.publishedAt') }}
              </span>
              <p class="text-xs text-text-body flex items-center gap-1.5">
                <CheckCircle2 class="size-3 text-success" />
                {{ publishedAtLabel }}
              </p>
            </div>
            <div v-if="publication.externalPublicationId" class="space-y-1 col-span-2">
              <span class="font-mono text-[9px] font-bold tracking-widest text-text-secondary uppercase">
                {{ t('postDetail.externalId') }}
              </span>
              <p class="text-xs text-text-body font-mono break-all">
                {{ publication.externalPublicationId }}
              </p>
            </div>
          </div>
        </div>

        <footer class="border-t border-border-subtle bg-bg-primary/40">
          <div v-if="deleteError || retryError" class="px-6 pt-3 space-y-1">
            <p v-if="deleteError" class="text-[10px] font-mono text-error">{{ deleteError }}</p>
            <p v-if="retryError" role="alert" class="text-[10px] font-mono text-error">{{ retryError }}</p>
          </div>
          <div v-if="showReschedule" class="px-6 pt-3 pb-2 space-y-2">
            <!-- biome-ignore lint/a11y/noLabelWithoutControl: t() provides accessible text, Biome can't resolve i18n keys statically -->
            <label for="reschedule-datetime" class="font-mono text-[9px] font-bold tracking-widest text-text-secondary uppercase">
              {{ t('postDetail.scheduledFor') }}
            </label>
            <input
              id="reschedule-datetime"
              v-model="newScheduledAt"
              type="datetime-local"
              class="w-full rounded-xl border border-border-visible bg-bg-surface text-text-body px-3 py-2 text-xs font-mono focus:outline-none focus:ring-2 focus:ring-text-display/30"
              :aria-label="t('postDetail.scheduledFor')"
            />
            <p v-if="rescheduleError" role="alert" class="text-[10px] font-mono text-error">{{ rescheduleError }}</p>
            <div class="flex gap-2">
              <button
                @click="confirmReschedule"
                class="px-3 py-2 rounded-xl bg-text-display text-bg-primary hover:opacity-90 transition-opacity text-xs font-mono uppercase tracking-wider font-bold cursor-pointer"
              >
                {{ t('postDetail.rescheduleConfirm') }}
              </button>
              <button
                @click="cancelReschedule"
                class="px-3 py-2 rounded-xl border border-border-visible text-text-body hover:border-text-display hover:text-text-display transition-colors bg-bg-surface text-xs font-mono uppercase tracking-wider font-bold cursor-pointer"
              >
                {{ t('postDetail.rescheduleCancel') }}
              </button>
            </div>
          </div>
          <div v-if="!showReschedule" class="flex items-center justify-between gap-3 p-6">
        <div class="flex items-center gap-2">
          <button
            v-if="canDelete"
            @click="deletePublication"
            :disabled="isDeleting"
            class="flex items-center gap-1.5 px-3 py-2 rounded-xl border border-border-visible text-text-secondary hover:border-error hover:text-error transition-colors bg-bg-surface text-xs font-mono uppercase tracking-wider font-bold cursor-pointer"
          >
            <Trash2 class="size-3.5" />
            {{ t('postDetail.delete') }}
          </button>
          <button
            v-if="canEditPublication && props.publication"
            @click="emit('edit', props.publication)"
            class="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-text-display text-bg-primary hover:opacity-90 transition-opacity text-xs font-mono uppercase tracking-wider font-bold cursor-pointer"
          >
            <Pencil class="size-3.5" />
            {{ t('postDetail.edit') }}
          </button>
          <button
            v-else-if="!isReadOnly && publication?.status === 'FAILED'"
            @click="retryPublication"
            :disabled="isRetrying"
            class="flex items-center gap-1.5 px-3 py-2 rounded-xl border border-border-visible text-text-secondary hover:border-text-display hover:text-text-display transition-colors bg-bg-surface text-xs font-mono uppercase tracking-wider font-bold cursor-pointer"
          >
            <CalendarClock class="size-3.5" />
            {{ t('postDetail.retry') }}
          </button>
          <button
            v-else-if="!isReadOnly && publication?.scheduledAt"
            @click="openReschedule"
            class="flex items-center gap-1.5 px-3 py-2 rounded-xl border border-border-visible text-text-secondary hover:border-text-display hover:text-text-display transition-colors bg-bg-surface text-xs font-mono uppercase tracking-wider font-bold cursor-pointer"
          >
            <CalendarClock class="size-3.5" />
            {{ t('postDetail.reschedule') }}
          </button>
          <div v-else-if="isReadOnly" class="text-[10px] font-mono uppercase tracking-wider text-text-secondary">
            {{ t('postDetail.readOnlyHint') }}
          </div>
        </div>
          <div class="flex items-center gap-2">
            <button
              @click="closeModal"
              class="px-3 py-2 rounded-xl border border-border-visible text-text-body hover:border-text-display hover:text-text-display transition-colors bg-bg-surface text-xs font-mono uppercase tracking-wider font-bold cursor-pointer"
            >
              {{ t('postDetail.close') }}
            </button>
            <a
              v-if="viewPostUrl"
              :href="viewPostUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-text-display text-bg-primary hover:opacity-90 transition-opacity text-xs font-mono uppercase tracking-wider font-bold cursor-pointer"
            >
              <ExternalLink class="size-3.5" />
              {{ t('postDetail.viewPost') }}
            </a>
            <button
              v-else
              disabled
              class="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-text-display/40 text-bg-primary/60 cursor-not-allowed text-xs font-mono uppercase tracking-wider font-bold"
              :title="t('postDetail.viewPostUnavailable')"
            >
              <ExternalLink class="size-3.5" />
              {{ t('postDetail.viewPost') }}
            </button>
          </div>
          </div>
        </footer>
      </div>
    </div>
  </Teleport>
</template>
