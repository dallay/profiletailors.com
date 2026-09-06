<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useWorkspaceStore } from '@modules/workspace/infrastructure/workspace.store'
import { Check, Copy, Building2, CheckCircle2, AlertCircle } from 'lucide-vue-next'

const { t } = useI18n()
const workspaceStore = useWorkspaceStore()

const workspaceName = ref(workspaceStore.activeWorkspace?.name || '')
const isSaving = ref(false)
const saveSuccess = ref(false)
const saveError = ref<string | null>(null)

const copiedId = ref(false)

watch(
  () => workspaceStore.activeWorkspace?.name,
  (newName) => {
    if (newName) {
      workspaceName.value = newName
    }
  },
  { immediate: true },
)

async function handleSaveWorkspaceName() {
  if (!workspaceName.value.trim()) return
  if (!workspaceStore.activeWorkspaceId) return

  isSaving.value = true
  saveSuccess.value = false
  saveError.value = null

  try {
    await workspaceStore.renameWorkspace(workspaceStore.activeWorkspaceId, workspaceName.value.trim())
    saveSuccess.value = true
    setTimeout(() => {
      saveSuccess.value = false
    }, 4000)
  } catch (err) {
    saveError.value = 'Failed to update workspace name.'
  } finally {
    isSaving.value = false
  }
}

async function copyWorkspaceId() {
  const id = workspaceStore.activeWorkspaceId
  if (!id) return
  try {
    await navigator.clipboard.writeText(id)
    copiedId.value = true
    setTimeout(() => {
      copiedId.value = false
    }, 2000)
  } catch (err) {
    console.error('Failed to copy workspace ID', err)
  }
}
</script>

<template>
  <div class="space-y-6">
    <!-- Header -->
    <div>
      <h1 class="text-2xl font-bold text-text-display">
        {{ t('settings.headers.generalTitle') }}
      </h1>
      <p class="text-sm text-text-secondary mt-1">
        {{ t('settings.headers.generalSubtitle') }}
      </p>
    </div>

    <!-- Workspace Identity Card -->
    <div class="rounded-xl border border-border-subtle bg-bg-surface p-6 space-y-6 shadow-sm">
      <div class="flex items-center gap-4 pb-4 border-b border-border-subtle">
        <div class="w-12 h-12 rounded-xl bg-primary-500/10 border border-primary-500/20 flex items-center justify-center text-primary-400 font-bold text-lg">
          <component
            :is="workspaceStore.activeWorkspace?.icon ? 'img' : Building2"
            v-if="workspaceStore.activeWorkspace?.icon"
            :src="workspaceStore.activeWorkspace.icon"
            alt="Workspace icon"
            class="w-full h-full object-cover rounded-xl"
          />
          <Building2 v-else class="w-6 h-6 text-primary-400" />
        </div>
        <div>
          <h2 class="text-lg font-bold text-text-display">
            {{ workspaceStore.activeWorkspace?.name || 'Workspace' }}
          </h2>
          <p class="font-mono text-xs text-text-muted">
            {{ workspaceStore.activeWorkspaceId || '—' }}
          </p>
        </div>
      </div>

      <h3 class="text-xs font-semibold text-text-muted uppercase tracking-wider">
        {{ t('settings.workspaceGeneral.identityCardTitle') }}
      </h3>

      <div v-if="saveSuccess" class="flex items-center gap-2.5 p-3 rounded-lg border border-emerald-500/30 bg-emerald-500/10 text-emerald-300 text-xs font-medium">
        <CheckCircle2 class="w-4 h-4 shrink-0" />
        <span>{{ t('settings.workspaceGeneral.updatedSuccess') }}</span>
      </div>

      <div v-if="saveError" class="flex items-center gap-2.5 p-3 rounded-lg border border-red-500/30 bg-red-500/10 text-red-400 text-xs font-medium">
        <AlertCircle class="w-4 h-4 shrink-0" />
        <span>{{ saveError }}</span>
      </div>

      <form class="space-y-5" @submit.prevent="handleSaveWorkspaceName">
        <!-- Workspace Name -->
        <div class="space-y-1.5">
          <label for="workspace-name" class="block text-xs font-medium text-text-secondary">
            {{ t('settings.workspaceGeneral.workspaceNameLabel') }}
          </label>
          <input
            id="workspace-name"
            v-model="workspaceName"
            type="text"
            required
            class="w-full rounded-lg border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-display focus:border-primary-500 focus:outline-none"
          />
        </div>

        <!-- Workspace ID -->
        <div class="space-y-1.5 pt-2">
          <label class="block text-xs font-medium text-text-secondary">
            {{ t('settings.workspaceGeneral.workspaceIdLabel') }}
          </label>
          <div class="flex items-center justify-between gap-4 p-3 rounded-lg border border-border-subtle bg-bg-primary/50">
            <span class="font-mono text-sm text-text-secondary truncate">
              {{ workspaceStore.activeWorkspaceId || '—' }}
            </span>
            <button
              v-if="workspaceStore.activeWorkspaceId"
              type="button"
              class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-border-subtle bg-bg-surface text-xs font-medium text-text-secondary hover:text-text-display hover:bg-bg-subtle transition-colors shrink-0"
              @click="copyWorkspaceId"
            >
              <Check v-if="copiedId" class="w-3.5 h-3.5 text-emerald-400" />
              <Copy v-else class="w-3.5 h-3.5" />
              <span>{{ copiedId ? t('settings.workspaceGeneral.copied') : t('settings.workspaceGeneral.copy') }}</span>
            </button>
          </div>
          <p class="text-[11px] text-text-muted">
            {{ t('settings.workspaceGeneral.workspaceIdNotice') }}
          </p>
        </div>

        <!-- Submit Button -->
        <div class="flex justify-end pt-2">
          <button
            type="submit"
            :disabled="isSaving || !workspaceName.trim()"
            class="px-4 py-2 rounded-lg bg-primary-600 text-white text-xs font-medium hover:bg-primary-500 disabled:opacity-50 transition-colors"
          >
            {{ isSaving ? t('settings.workspaceGeneral.savingChanges') : t('settings.workspaceGeneral.saveChanges') }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>
