<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Pencil } from '@lucide/vue'
import { renameWorkspace, updateWorkspaceIcon } from '@modules/auth/infrastructure/auth-api'
import { workspaceNameSchema } from '@shared/lib/validation/schemas'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'
import { useWorkspaceStore } from '@modules/workspace/infrastructure/workspace.store'
import WorkspaceAvatar from '@shared/components/WorkspaceAvatar.vue'
import WorkspaceIconModal from '@modules/workspace/presentation/components/WorkspaceIconModal.vue'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

const { t } = useI18n()
const auth = useAuthStore()
const workspace = useWorkspaceStore()

const workspaceNameInput = ref('')
const editingWorkspaceName = ref(false)
const renamingWorkspace = ref(false)
const renameError = ref<string | null>(null)
const renameSuccess = ref(false)
let renameSuccessTimeout: ReturnType<typeof setTimeout> | null = null

const iconModalOpen = ref(false)
const updatingIcon = ref(false)
const iconError = ref<string | null>(null)

const displayWorkspaceName = computed(() => workspace.activeWorkspace?.name ?? t('workspace.defaultName'))
const workspaceIdentifier = computed(() => workspace.activeWorkspace?.workspaceId ?? '...')

watch(
  () => workspace.activeWorkspace?.name,
  (newName) => { if (newName) workspaceNameInput.value = newName },
  { immediate: true },
)

onUnmounted(() => {
  if (renameSuccessTimeout) clearTimeout(renameSuccessTimeout)
})

function startRenameWorkspace() {
  workspaceNameInput.value = workspace.activeWorkspace?.name ?? ''
  editingWorkspaceName.value = true
  renameError.value = null
  renameSuccess.value = false
}

function cancelRenameWorkspace() {
  editingWorkspaceName.value = false
  renameError.value = null
}

async function saveWorkspaceName() {
  const workspaceId = workspace.activeWorkspaceId
  const accessToken = auth.accessToken
  if (!workspaceId || !accessToken) { renameError.value = t('workspace.renameFailed'); return }

  const rawName = workspaceNameInput.value.trim()
  const validation = workspaceNameSchema.safeParse(rawName)
  if (!validation.success) {
    const errorKey = validation.error.issues[0]?.message
    renameError.value = errorKey ? t(`workspace.${errorKey}`) : t('workspace.renameFailed')
    return
  }

  renamingWorkspace.value = true
  renameError.value = null
  try {
    const updated = await renameWorkspace(validation.data, accessToken, workspaceId)
    workspace.setWorkspaceName(updated.name)
    editingWorkspaceName.value = false
    renameSuccess.value = true
    renameSuccessTimeout = setTimeout(() => { renameSuccess.value = false; renameSuccessTimeout = null }, 3000)
  } catch (err) {
    renameError.value = err instanceof Error ? err.message : t('workspace.renameFailed')
  } finally {
    renamingWorkspace.value = false
  }
}

async function selectIcon(icon: string | null) {
  const workspaceId = workspace.activeWorkspaceId
  const accessToken = auth.accessToken
  if (!workspaceId || !accessToken) return

  updatingIcon.value = true
  iconError.value = null
  try {
    const updated = await updateWorkspaceIcon(icon, accessToken, workspaceId)
    workspace.updateWorkspaceIcon(workspaceId, updated.icon)
    iconModalOpen.value = false
  } catch (err) {
    iconError.value = err instanceof Error ? err.message : t('workspace.updateIconFailed')
  } finally {
    updatingIcon.value = false
  }
}
</script>

<template>
  <Card class="border border-border-subtle bg-bg-surface p-6 shadow-[0_0_0_1px_rgba(255,255,255,0.02)]">
    <CardHeader class="space-y-3 border-b border-border-subtle p-0 pb-5">
      <CardTitle class="label-mono text-[10px] text-text-display">
        {{ $t('workspace.title') }}
      </CardTitle>
      <div class="flex items-center gap-4">
        <WorkspaceAvatar
          :name="workspace.activeWorkspace?.name ?? 'W'"
          :icon="workspace.activeWorkspace?.icon"
          size="md"
        />
        <div class="min-w-0">
          <p class="truncate text-sm font-medium text-text-display">{{ displayWorkspaceName }}</p>
          <p class="truncate font-mono text-[10px] uppercase tracking-[0.12em] text-text-secondary">
            {{ workspaceIdentifier }}
          </p>
        </div>
        <Button
          type="button"
          variant="outline"
          size="sm"
          class="ml-auto"
          :disabled="updatingIcon"
          data-testid="settings-open-icon-modal"
          @click="iconError = null; iconModalOpen = true"
        >
          <Pencil class="size-3.5" />
          {{ $t('workspace.editIdentity') }}
        </Button>
      </div>
    </CardHeader>

    <CardContent class="mt-6 space-y-6 p-0">
      <output
        v-if="renameSuccess"
        class="block rounded-2xl border border-success/30 bg-success/10 px-4 py-3 text-sm text-success"
      >
        {{ $t('workspace.renameSuccess') }}
      </output>

      <div v-if="!editingWorkspaceName" class="flex items-center justify-between gap-4">
        <div class="min-w-0">
          <p class="text-sm font-medium text-text-display">{{ displayWorkspaceName }}</p>
          <p class="font-mono text-[10px] uppercase tracking-[0.12em] text-text-secondary">
            {{ workspaceIdentifier }}
          </p>
        </div>
        <Button variant="outline" size="sm" @click="startRenameWorkspace">
          {{ $t('workspace.rename') }}
        </Button>
      </div>

      <div v-else class="space-y-3">
        <label for="workspace-name-input" class="sr-only">{{ $t('workspace.rename') }}</label>
        <input
          id="workspace-name-input"
          v-model="workspaceNameInput"
          type="text"
          :placeholder="$t('workspace.namePlaceholder')"
          class="w-full rounded-2xl border border-border-visible bg-bg-primary px-4 py-3 text-sm text-text-body placeholder:text-text-secondary focus:border-text-display focus:outline-none"
          maxlength="255"
          @keyup.enter="saveWorkspaceName"
          @keyup.escape="cancelRenameWorkspace"
        >
        <p v-if="renameError" role="alert" class="text-sm text-error">{{ renameError }}</p>
        <div class="flex gap-2">
          <Button
            type="button"
            size="sm"
            :disabled="renamingWorkspace || !workspaceNameInput.trim()"
            @click="saveWorkspaceName"
          >
            {{ renamingWorkspace ? '...' : $t('workspace.save') }}
          </Button>
          <Button variant="outline" size="sm" @click="cancelRenameWorkspace">
            {{ $t('workspace.cancel') }}
          </Button>
        </div>
      </div>
    </CardContent>
  </Card>

  <WorkspaceIconModal
    v-model:open="iconModalOpen"
    :current-icon="workspace.activeWorkspace?.icon ?? null"
    :is-updating="updatingIcon"
    :error-message="iconError"
    @select="selectIcon"
  />
</template>
