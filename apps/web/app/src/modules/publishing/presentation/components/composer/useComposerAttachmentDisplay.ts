import { computed } from 'vue'
import type { ComposerInlineAttachment } from './composer.types'

const MAX_VISIBLE = 3

/**
 * Provides attachment display calculations for composer attachments area
 */
export function useComposerAttachmentDisplay(attachments: ComposerInlineAttachment[]) {
  const visibleAttachments = computed(() => attachments.slice(0, MAX_VISIBLE))
  const hiddenCount = computed(() => Math.max(0, attachments.length - MAX_VISIBLE))

  return {
    visibleAttachments,
    hiddenCount,
  }
}
