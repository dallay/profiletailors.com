<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useSettingsStore } from '@/stores/settings'
import { usePublishingStore } from '@/stores/publishing'
import { useAuthStore } from '@/stores/auth'
import { useWorkspaceStore } from '@/stores/workspace'
import { renameWorkspace } from '@/lib/auth-api'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'

const settings = useSettingsStore()
const publishing = usePublishingStore()
const auth = useAuthStore()
const workspace = useWorkspaceStore()
const route = useRoute()
const { t } = useI18n()
const connectError = ref<string | null>(null)
const connectingLinkedIn = ref(false)

// Workspace rename
const editingWorkspaceName = ref(false)
const workspaceNameInput = ref('')
const renamingWorkspace = ref(false)
const renameError = ref<string | null>(null)
const renameSuccess = ref(false)

const displayWorkspaceName = computed(
  () => workspace.workspaceName || t('workspace.defaultName'),
)

function startRenameWorkspace() {
  workspaceNameInput.value = workspace.workspaceName || ''
  editingWorkspaceName.value = true
  renameError.value = null
  renameSuccess.value = false
}

function cancelRenameWorkspace() {
  editingWorkspaceName.value = false
  renameError.value = null
}

async function saveWorkspaceName() {
  const newName = workspaceNameInput.value.trim()
  if (!newName || !auth.accessToken || !workspace.activeWorkspaceId) return

  renamingWorkspace.value = true
  renameError.value = null
  renameSuccess.value = false

  try {
    const result = await renameWorkspace(newName, auth.accessToken, workspace.activeWorkspaceId)
    workspace.setWorkspaceName(result.name)
    editingWorkspaceName.value = false
    renameSuccess.value = true
    setTimeout(() => { renameSuccess.value = false }, 3000)
  } catch (err) {
    renameError.value = err instanceof Error ? err.message : t('workspace.renameFailed')
  } finally {
    renamingWorkspace.value = false
  }
}

const linkedInChannels = computed(() =>
  publishing.channels.filter((channel) => channel.provider === 'linkedin' && channel.status === 'ACTIVE'),
)
const linkedinConnected = computed(() => route.query.connected === 'linkedin')

async function connectLinkedInProfile() {
  connectError.value = null
  connectingLinkedIn.value = true

  try {
    await publishing.connectLinkedInPersonalProfile()
  } catch (err: any) {
    connectError.value = err?.detail || err?.message || t('channels.connectLinkedInFailed')
    connectingLinkedIn.value = false
  }
}

onMounted(() => {
  publishing.fetchChannels().catch((err) => {
    connectError.value = err instanceof Error ? err.message : t('channels.loadFailed')
  })
  publishing.fetchConfiguredProviders()
})
</script>

<template>
  <div class="space-y-12">
    <!-- Header -->
    <div class="space-y-2">
      <h2 class="text-3xl font-light tracking-tight text-text-display">
        {{ $t('nav.settings') }}
      </h2>
      <p class="text-sm text-text-secondary">
        {{ $t('settings.subtitle') }}
      </p>
    </div>

    <Card class="bg-bg-surface border border-border-subtle max-w-xl">
      <CardHeader class="p-0 border-b border-border-subtle pb-4">
        <CardTitle class="label-mono text-text-display text-[10px]">
          {{ $t('settings.interfacePreferences') }}
        </CardTitle>
      </CardHeader>
      <CardContent class="p-0 mt-6 space-y-6">
        <!-- Locale preference -->
        <div class="flex items-center justify-between">
          <div>
            <span class="text-sm font-medium text-text-display block">{{ $t('settings.languageLabel') }}</span>
            <span class="text-xs text-text-secondary">{{ $t('settings.languageDesc') }}</span>
          </div>
          <div class="flex gap-2">
            <Button
              @click="settings.setLocale('en')"
              variant="outline"
              size="sm"
              :class="{
                'bg-text-display text-bg-primary font-bold border-transparent hover:bg-text-display/90': settings.currentLocale === 'en'
              }"
            >
              EN
            </Button>
            <Button
              @click="settings.setLocale('es')"
              variant="outline"
              size="sm"
              :class="{
                'bg-text-display text-bg-primary font-bold border-transparent hover:bg-text-display/90': settings.currentLocale === 'es'
              }"
            >
              ES
            </Button>
          </div>
        </div>

        <div class="border-t border-border-visible my-4"></div>

        <!-- Theme preference -->
        <div class="flex items-center justify-between">
          <div>
            <span class="text-sm font-medium text-text-display block">{{ $t('settings.themeLabel') }}</span>
            <span class="text-xs text-text-secondary">{{ $t('settings.themeDesc') }}</span>
          </div>
          <div class="flex gap-2">
            <Button
              @click="settings.setTheme('dark')"
              variant="outline"
              size="sm"
              :class="{
                'bg-text-display text-bg-primary font-bold border-transparent hover:bg-text-display/90': settings.currentTheme === 'dark'
              }"
            >
              {{ $t('settings.dark') }}
            </Button>
            <Button
              @click="settings.setTheme('light')"
              variant="outline"
              size="sm"
              :class="{
                'bg-text-display text-bg-primary font-bold border-transparent hover:bg-text-display/90': settings.currentTheme === 'light'
              }"
            >
              {{ $t('settings.light') }}
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>

    <Card class="max-w-xl border border-border-subtle bg-bg-surface">
      <CardHeader class="border-b border-border-subtle p-0 pb-4">
        <CardTitle class="label-mono text-[10px] text-text-display">
          {{ $t('workspace.title') }}
        </CardTitle>
      </CardHeader>
      <CardContent class="mt-6 space-y-4 p-0">
        <p v-if="renameSuccess" class="rounded-xl border border-success/30 bg-success/10 px-4 py-3 text-sm text-success">
          {{ $t('workspace.renameSuccess') }}
        </p>

        <div v-if="!editingWorkspaceName" class="flex items-center justify-between">
          <div class="min-w-0">
            <p class="text-sm font-medium text-text-display">{{ displayWorkspaceName }}</p>
            <p class="font-mono text-[10px] uppercase tracking-[0.12em] text-text-secondary">
              {{ workspace.activeWorkspaceId }}
            </p>
          </div>
          <Button variant="outline" size="sm" @click="startRenameWorkspace">
            {{ $t('workspace.rename') }}
          </Button>
        </div>

        <div v-else class="space-y-3">
          <input
            v-model="workspaceNameInput"
            type="text"
            :placeholder="$t('workspace.namePlaceholder')"
            class="w-full rounded-2xl border border-border-visible bg-bg-primary px-4 py-3 text-sm text-text-body placeholder:text-text-secondary focus:border-text-display focus:outline-none"
            maxlength="255"
            @keyup.enter="saveWorkspaceName"
            @keyup.escape="cancelRenameWorkspace"
          >
          <p v-if="renameError" class="text-sm text-error">{{ renameError }}</p>
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

    <Card class="max-w-xl border border-border-subtle bg-bg-surface">
      <CardHeader class="border-b border-border-subtle p-0 pb-4">
        <CardTitle class="label-mono text-[10px] text-text-display">
          {{ $t('channels.title') }}
        </CardTitle>
      </CardHeader>
      <CardContent class="mt-6 space-y-5 p-0">
        <p v-if="linkedinConnected" class="rounded-xl border border-success/30 bg-success/10 px-4 py-3 text-sm text-success">
          {{ $t('linkedinCallback.successMessage') }}
        </p>

        <p v-if="publishing.channelsLoading" class="font-mono text-[10px] uppercase tracking-[0.14em] text-text-secondary">
          {{ $t('channels.loading') }}
        </p>

        <div v-if="linkedInChannels.length" class="space-y-3">
          <div
            v-for="channel in linkedInChannels"
            :key="channel.id"
            class="flex items-center justify-between rounded-xl border border-border-subtle bg-bg-primary px-4 py-3"
          >
            <div class="min-w-0">
              <p class="truncate text-sm font-medium text-text-display">{{ channel.name }}</p>
              <p class="truncate font-mono text-[10px] uppercase tracking-[0.12em] text-text-secondary">
                {{ channel.handle }}
              </p>
            </div>
            <span class="rounded-full border border-border-visible px-2 py-0.5 font-mono text-[9px] uppercase tracking-[0.12em] text-success">
              {{ $t('channels.active') }}
            </span>
          </div>
        </div>

        <div v-else-if="!publishing.isLinkedInConfigured" class="rounded-xl border border-dashed border-border-visible bg-bg-primary/50 p-5">
          <p class="text-sm font-medium text-text-display">{{ $t('channels.notConfigured') }}</p>
          <p class="mt-1 text-xs leading-5 text-text-secondary">
            {{ $t('channels.notConfiguredDesc') }}
          </p>
        </div>

        <div v-else class="rounded-xl border border-dashed border-border-visible bg-bg-primary/50 p-5">
          <p class="text-sm font-medium text-text-display">{{ $t('channels.noChannels') }}</p>
          <p class="mt-1 text-xs leading-5 text-text-secondary">
            {{ $t('channels.connectLinkedInProfileDesc') }}
          </p>
        </div>

        <p v-if="connectError || publishing.channelsError" class="text-sm text-error">
          {{ connectError || publishing.channelsError }}
        </p>

        <Button
          v-if="publishing.isLinkedInConfigured"
          type="button"
          :disabled="connectingLinkedIn"
          @click="connectLinkedInProfile"
        >
          {{ connectingLinkedIn ? $t('channels.connectingLinkedIn') : $t('channels.connectLinkedInProfile') }}
        </Button>
      </CardContent>
    </Card>
  </div>
</template>
