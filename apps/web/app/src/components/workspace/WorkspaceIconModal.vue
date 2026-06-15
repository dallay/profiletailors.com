<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { Component } from 'vue'
import { useI18n } from 'vue-i18n'
import { X } from '@lucide/vue'
import * as LucideIcons from '@lucide/vue'
import { toPascalCase } from '@/lib/string-utils'
import {
  Dialog,
  DialogContent,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

const props = defineProps<{
  open: boolean
  currentIcon: string | null
  isUpdating?: boolean
  errorMessage?: string | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'select', icon: string | null): void
}>()

const { t } = useI18n()

const CURATED_ICONS = [
  'briefcase', 'building', 'palette', 'rocket', 'zap', 'star',
  'heart', 'flag', 'globe', 'compass', 'target', 'trending-up',
  'layers', 'folder', 'shield', 'users', 'camera', 'music',
  'code', 'book-open', 'crown', 'gem', 'sparkles', 'smile',
] as const

const localIcon = ref<string | null>(props.currentIcon)

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      // Reset selection to current icon when opening.
      localIcon.value = props.currentIcon
    }
  },
)

function closeModal() {
  emit('update:open', false)
}

function pick(icon: string) {
  localIcon.value = icon
}

function clear() {
  localIcon.value = null
}

function confirm() {
  emit('select', localIcon.value)
}

function resolveIcon(name: string): Component | null {
  const key = toPascalCase(name) as keyof typeof LucideIcons
  return (LucideIcons[key] as Component | undefined) ?? null
}

const iconComponents = computed<Record<string, Component | null>>(() => {
  const map: Record<string, Component | null> = {}
  for (const name of CURATED_ICONS) {
    map[name] = resolveIcon(name)
  }
  return map
})
</script>

<template>
  <Dialog :open="open" @update:open="(v) => emit('update:open', v)">
    <DialogContent
      class="sm:max-w-lg"
      :show-close-button="false"
    >
      <div class="flex items-start justify-between gap-4">
        <div>
          <DialogTitle class="text-base font-medium text-text-display">
            {{ t('workspace.iconModalTitle') }}
          </DialogTitle>
          <DialogDescription class="mt-1 text-xs leading-5 text-text-secondary">
            {{ t('workspace.iconModalDesc') }}
          </DialogDescription>
        </div>
        <button
          type="button"
          class="rounded-full p-1.5 text-text-secondary transition-colors hover:bg-bg-primary hover:text-text-display focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-text-display"
          :aria-label="t('common.close')"
          @click="closeModal"
        >
          <X class="size-4" />
        </button>
      </div>

      <div class="grid grid-cols-6 gap-2 sm:grid-cols-8">
        <button
          v-for="iconName in CURATED_ICONS"
          :key="iconName"
          type="button"
          :aria-label="iconName"
          :aria-pressed="localIcon === iconName ? 'true' : 'false'"
          class="flex size-10 items-center justify-center rounded-xl border transition-all focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-text-display"
          :class="localIcon === iconName
            ? 'border-text-display bg-text-display text-bg-primary'
            : 'border-border-visible text-text-secondary hover:border-text-secondary hover:text-text-display'"
          :disabled="isUpdating"
          @click="pick(iconName)"
        >
          <component
            :is="iconComponents[iconName]"
            v-if="iconComponents[iconName]"
            :size="18"
          />
        </button>
      </div>

      <p v-if="errorMessage" class="text-sm text-error">
        {{ errorMessage }}
      </p>

      <div :class="cn('flex items-center justify-between gap-3 border-t border-border-subtle pt-4')">
        <Button
          v-if="currentIcon"
          type="button"
          variant="ghost"
          size="sm"
          :disabled="isUpdating || localIcon === null"
          @click="clear"
        >
          {{ t('workspace.removeIcon') }}
        </Button>
        <div v-else />

        <div class="flex gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            :disabled="isUpdating"
            @click="closeModal"
          >
            {{ t('workspace.cancel') }}
          </Button>
          <Button
            type="button"
            size="sm"
            :disabled="isUpdating || localIcon === currentIcon"
            @click="confirm"
          >
            {{ t('workspace.save') }}
          </Button>
        </div>
      </div>
    </DialogContent>
  </Dialog>
</template>
