<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { CalendarClock, ExternalLink, Pencil, Trash2, X, AlertTriangle, CheckCircle2, Clock } from '@lucide/vue'
import { useFocusTrap, useDeleteConfirmation } from '@shared/composables'
import { usePublishingStore, type Publication } from '@modules/publishing/infrastructure/publishing.store'
import { usePublishingErrors } from '@modules/publishing/presentation/composables/usePublishingErrors'
import { usePostDetailDisplay } from './usePostDetailDisplay'
import { getProviderBadge } from '@shared/lib/provider-styles'
import PublicationRescheduleForm from '@modules/publishing/presentation/components/PublicationRescheduleForm.vue'

const props = withDefaults(
  defineProps<{
    isOpen?: boolean
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
const { failureCopy, actionErrorMessage } = usePublishingErrors(() => props.publication)

// Use composables for delete and reschedule state
const deleteConfirm = useDeleteConfirmation({
  onConfirm: async () => {
    if (!props.publication) return
    await publishingStore.deletePost(props.publication.id)
    emit('deleted', props.publication.id)
    closeModal()
  },
  onCancel: () => {},
})

const isReadOnly = computed(() => props.publication?.status === 'PUBLISHED')
const canEditPublication = computed(() =>
  props.publication ? publishingStore.isPublicationEditable(props.publication.status) : false,
)
const canDelete = computed(() =>
  props.publication ? publishingStore.isPublicationDeletable(props.publication.status) : false,
)

const { statusLabel, statusTone, viewPostUrl, scheduledAtLabel, publishedAtLabel } = usePostDetailDisplay(
  props.publication,
  i18nLocale.value,
)

const isDeleting = computed(() => deleteConfirm.isDeleting.value)
const deleteError = computed(() => deleteConfirm.error.value)

const showReschedule = ref(false)
const rescheduleInitialDate = ref('')
const rescheduleError = ref('')
const isRetrying = ref(false)
const retryError = ref('')

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

async function requestDelete() {
  if (!props.publication || isDeleting.value || !canDelete.value) return
  try {
    await deleteConfirm.confirm()
  } catch (err) {
    deleteConfirm.setError(actionErrorMessage(err, 'delete'))
    console.error('Failed to delete publication', err)
  }
}

function openReschedule() {
  if (!props.publication?.scheduledAt) return
  const d = new Date(props.publication.scheduledAt)
  const offset = d.getTimezoneOffset()
  const local = new Date(d.getTime() - offset * 60_000)
  rescheduleInitialDate.value = local.toISOString().slice(0, 16)
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

async function confirmReschedule(newIso: string) {
  rescheduleError.value = ''
  try {
    await publishingStore.reschedulePublication(props.publication!.id, newIso)
    emit('reschedule', { id: props.publication!.id, scheduledAt: newIso })
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
      <dialog
        ref="modalContainer"
        open
        class="flex flex-col w-full max-w-2xl max-h-[90vh] bg-bg-surface border border-border-subtle rounded-2xl overflow-hidden shadow-2xl m-0 relative"
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
          <button type="button"
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
          <PublicationRescheduleForm
            v-if="showReschedule"
            :initial-scheduled-at="rescheduleInitialDate"
            :api-error="rescheduleError"
            @confirm="confirmReschedule"
            @cancel="cancelReschedule"
          />
          <div v-if="!showReschedule" class="flex items-center justify-between gap-3 p-6">
        <div class="flex items-center gap-2">
          <button type="button"
            v-if="canDelete"
            @click="requestDelete"
            :disabled="isDeleting"
            class="flex items-center gap-1.5 px-3 py-2 rounded-xl border border-border-visible text-text-secondary hover:border-error hover:text-error transition-colors bg-bg-surface text-xs font-mono uppercase tracking-wider font-bold cursor-pointer"
          >
            <Trash2 class="size-3.5" />
            {{ t('postDetail.delete') }}
          </button>
          <button type="button"
            v-if="canEditPublication && props.publication"
            @click="emit('edit', props.publication)"
            class="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-text-display text-bg-primary hover:opacity-90 transition-opacity text-xs font-mono uppercase tracking-wider font-bold cursor-pointer"
          >
            <Pencil class="size-3.5" />
            {{ t('postDetail.edit') }}
          </button>
          <button type="button"
            v-else-if="!isReadOnly && publication?.status === 'FAILED'"
            @click="retryPublication"
            :disabled="isRetrying"
            class="flex items-center gap-1.5 px-3 py-2 rounded-xl border border-border-visible text-text-secondary hover:border-text-display hover:text-text-display transition-colors bg-bg-surface text-xs font-mono uppercase tracking-wider font-bold cursor-pointer"
          >
            <CalendarClock class="size-3.5" />
            {{ t('postDetail.retry') }}
          </button>
          <button type="button"
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
            <button type="button"
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
            <button type="button"
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
      </dialog>
    </div>
  </Teleport>
</template>
