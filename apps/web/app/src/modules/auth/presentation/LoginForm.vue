<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'

const { t } = useI18n()
const auth = useAuthStore()
const pending = ref(false)

function beginLogin(): void {
  if (pending.value) return
  pending.value = true
  auth.startBffLogin()
}
</script>

<template>
  <button
    type="button"
    :disabled="pending"
    class="min-h-11 w-full rounded-2xl bg-text-display px-4 font-semibold text-bg-primary disabled:opacity-60"
    @click="beginLogin"
  >
    {{ t('auth.submitLogin') }}
  </button>
</template>
