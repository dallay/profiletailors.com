<script setup lang="ts">
import { Pencil } from '@lucide/vue'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import SocialProviderIcon from '@shared/components/SocialProviderIcon.vue'
import WorkspaceAvatar from '@shared/components/WorkspaceAvatar.vue'
import { getProviderPresentation } from '@shared/lib/provider-presentation'
import WorkspaceIconModal from '@modules/workspace/presentation/components/WorkspaceIconModal.vue'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { renameWorkspace, updateWorkspaceIcon, proxyImageUrl } from '@modules/auth/infrastructure/auth-api'
import { workspaceNameSchema } from '@shared/lib/validation/schemas'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'
import { usePublishingStore } from '@modules/publishing/infrastructure/publishing.store'
import { useSettingsStore } from '@modules/settings/infrastructure/settings.store'
import { useWorkspaceStore } from '@modules/workspace/infrastructure/workspace.store'
import AccountClosureSection from '@modules/settings/presentation/AccountClosureSection.vue'
import PrivacySection from '@modules/settings/presentation/PrivacySection.vue'

const { t } = useI18n()
const auth = useAuthStore()
const workspace = useWorkspaceStore()
const settings = useSettingsStore()
const publishing = usePublishingStore()
const route = useRoute()

const workspaceNameInput = ref('')
const editingWorkspaceName = ref(false)
const renamingWorkspace = ref(false)
const renameError = ref<string | null>(null)
const renameSuccess = ref(false)
let renameSuccessTimeout: ReturnType<typeof setTimeout> | null = null

const connectError = ref<string | null>(null)
const iconModalOpen = ref(false)
const updatingIcon = ref(false)
const iconError = ref<string | null>(null)

const displayWorkspaceName = computed(() => workspace.activeWorkspace?.name ?? t('workspace.defaultName'))
const workspaceIdentifier = computed(() => workspace.activeWorkspace?.workspaceId ?? '...')

const linkedinConnected = computed(
  () => route.query.connected === 'linkedin' && route.query.provider === 'linkedin',
)

const connectedChannels = computed(() => publishing.channels)

const channelsPanelFocused = computed(() => route.query.panel === 'channels')

watch(
  () => workspace.activeWorkspace?.name,
  (newName) => {
    if (newName) {
      workspaceNameInput.value = newName
    }
  },
  { immediate: true },
)

const connectingLinkedIn = ref(false)

async function connectLinkedInProfile() {
  connectingLinkedIn.value = true
  connectError.value = null

  try {
    await publishing.connectLinkedInPersonalProfile()
  } catch (err) {
    connectError.value = err instanceof Error ? err.message : t('channels.connectionFailed')
  } finally {
    connectingLinkedIn.value = false
  }
}

onMounted(() => {
  publishing.fetchChannels().catch(() => undefined)
  publishing.fetchConfiguredProviders().catch(() => undefined)
})

onUnmounted(() => {
  if (renameSuccessTimeout) {
    clearTimeout(renameSuccessTimeout)
  }
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
    renameSuccessTimeout = setTimeout(() => {
      renameSuccess.value = false
      renameSuccessTimeout = null
    }, 3000)
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

function segmentedControlClass(active: boolean) {
  return active
    ? 'bg-bg-primary text-text-display shadow-sm'
    : 'text-text-secondary hover:text-text-body'
}
</script>

<template>
  <div data-testid="settings-shell" class="space-y-10">
    <section data-testid="settings-overview" class="space-y-6">
      <div class="flex flex-col gap-6 lg:flex-row lg:items-start lg:justify-between">
        <div class="space-y-1.5">
          <div class="inline-flex items-center gap-2 rounded-full border border-border-visible bg-bg-surface px-3 py-1 font-mono text-[10px] font-bold uppercase tracking-[0.14em] text-text-secondary">
            <span class="size-1.5 rounded-full bg-text-display" />
            {{ $t('settings.overviewBadge') }}
          </div>
          <h1 class="font-mono text-[11px] font-bold uppercase tracking-[0.16em] text-text-display">
            {{ $t('nav.settings') }}
          </h1>
          <p class="max-w-2xl text-sm leading-7 text-text-secondary">
            {{ $t('settings.subtitle') }}
          </p>
        </div>

        <aside data-testid="settings-preferences-panel" class="flex shrink-0 flex-wrap gap-4 lg:justify-end">
          <div class="rounded-2xl border border-border-subtle bg-bg-surface p-4 shadow-[0_0_0_1px_rgba(255,255,255,0.02)]">
            <p class="font-mono text-[10px] font-bold uppercase tracking-[0.14em] text-text-secondary">
              {{ $t('settings.languageLabel') }}
            </p>
            <div class="mt-3">
              <div
                class="inline-flex rounded-full border border-border-visible bg-bg-surface p-0.5 font-mono text-[10px]"
                role="radiogroup"
                :aria-label="$t('settings.languageLabel')"
              >
                <label
                  data-testid="settings-language-en"
                  class="cursor-pointer rounded-full px-3 py-1.5 font-bold uppercase tracking-[0.14em] transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-text-display"
                  :class="segmentedControlClass(settings.currentLocale === 'en')"
                >
                  <input
                    type="radio"
                    name="locale"
                    value="en"
                    :checked="settings.currentLocale === 'en'"
                    class="sr-only"
                    @change="settings.setLocale('en')"
                  />
                  EN
                </label>
                <label
                  data-testid="settings-language-es"
                  class="cursor-pointer rounded-full px-3 py-1.5 font-bold uppercase tracking-[0.14em] transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-text-display"
                  :class="segmentedControlClass(settings.currentLocale === 'es')"
                >
                  <input
                    type="radio"
                    name="locale"
                    value="es"
                    :checked="settings.currentLocale === 'es'"
                    class="sr-only"
                    @change="settings.setLocale('es')"
                  />
                  ES
                </label>
              </div>
            </div>
          </div>
        </aside>
      </div>
    </section>

    <div class="grid gap-6 xl:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)] xl:items-start">
      <Card
        data-testid="settings-channels-panel"
        class="border border-border-subtle bg-bg-surface p-6 shadow-[0_0_0_1px_rgba(255,255,255,0.02)] transition-colors"
        :class="channelsPanelFocused
          ? 'shadow-[0_0_0_1px_rgba(255,255,255,0.12)]'
          : ''"
      >
        <CardHeader class="space-y-3 border-b border-border-subtle p-0 pb-5">
          <CardTitle class="label-mono text-[10px] text-text-display">
            {{ $t('channels.title') }}
          </CardTitle>
          <p class="max-w-lg text-sm leading-6 text-text-secondary">
            {{ $t('channels.connectLinkedInProfileDesc') }}
          </p>
        </CardHeader>

        <CardContent class="mt-6 space-y-5 p-0">
          <p
            v-if="linkedinConnected"
            class="rounded-2xl border border-success/30 bg-success/10 px-4 py-3 text-sm text-success"
          >
            {{ $t('linkedinCallback.successMessage') }}
          </p>

          <p
            v-if="publishing.channelsLoading"
            class="font-mono text-[10px] uppercase tracking-[0.14em] text-text-secondary"
          >
            {{ $t('channels.loading') }}
          </p>

          <div v-if="connectedChannels.length" class="space-y-3">
            <div
              v-for="channel in connectedChannels"
              :key="channel.id"
              class="flex items-center gap-4 rounded-2xl border border-border-subtle bg-bg-primary px-4 py-4"
              data-testid="settings-connected-channel"
            >
              <img
                v-if="channel.avatarUrl"
                :src="proxyImageUrl(channel.avatarUrl)"
                :alt="`${channel.name} avatar`"
                class="size-11 rounded-full border border-border-visible object-cover"
              >
              <div
                v-else
                class="flex size-11 shrink-0 items-center justify-center rounded-full border border-border-visible bg-bg-surface font-mono text-[10px] font-bold uppercase text-text-display"
              >
                <SocialProviderIcon :provider="channel.provider" />
              </div>
              <div class="min-w-0 flex-1">
                <p class="truncate text-sm font-medium text-text-display">{{ channel.name }}</p>
                <p class="truncate font-mono text-[10px] uppercase tracking-[0.12em] text-text-secondary">
                  {{ getProviderPresentation(channel.provider).label }} · {{ channel.accountId }}
                </p>
              </div>
              <span
                class="inline-flex items-center gap-1.5 rounded-full border border-border-visible px-2.5 py-0.5 font-mono text-[9px] uppercase tracking-[0.12em]"
                :class="channel.status === 'ACTIVE' ? 'text-success' : 'text-error'"
              >
                <span
                  aria-hidden="true"
                  class="size-1.5 rounded-full"
                  :class="channel.status === 'ACTIVE' ? 'bg-success' : 'bg-error'"
                />
                {{ channel.status === 'ACTIVE' ? $t('channels.active') : $t('channels.needsReconnect') }}
              </span>
            </div>
          </div>

          <div
            v-else-if="!publishing.isLinkedInConfigured"
            class="rounded-2xl border border-dashed border-border-visible bg-bg-primary/50 p-5"
          >
            <p class="text-sm font-medium text-text-display">{{ $t('channels.notConfigured') }}</p>
            <p class="mt-1 text-xs leading-5 text-text-secondary">
              {{ $t('channels.notConfiguredDesc') }}
            </p>
          </div>

          <div
            v-else
            class="rounded-2xl border border-dashed border-border-visible bg-bg-primary/50 p-5"
          >
            <p class="text-sm font-medium text-text-display">{{ $t('channels.noChannels') }}</p>
            <p class="mt-1 text-xs leading-5 text-text-secondary">
              {{ $t('channels.connectLinkedInProfileDesc') }}
            </p>
            <Button
              v-if="publishing.isLinkedInConfigured"
              type="button"
              class="mt-4"
              :disabled="connectingLinkedIn"
              @click="connectLinkedInProfile"
            >
              {{ connectingLinkedIn ? $t('channels.connectingLinkedIn') : $t('channels.connectLinkedInProfile') }}
            </Button>
          </div>

          <p v-if="connectError || publishing.channelsError" role="alert" class="text-sm text-error">
            {{ connectError || publishing.channelsError }}
          </p>
        </CardContent>
      </Card>

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
    </div>

    <PrivacySection />

    <AccountClosureSection />

    <WorkspaceIconModal
      v-model:open="iconModalOpen"
      :current-icon="workspace.activeWorkspace?.icon ?? null"
      :is-updating="updatingIcon"
      :error-message="iconError"
      @select="selectIcon"
    />
  </div>
</template>
