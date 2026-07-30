<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { usePublicCapabilitiesStore } from '@modules/auth/infrastructure/public-capabilities.store'
import AuthShell from './AuthShell.vue'
import LoginForm from './LoginForm.vue'
import RegisterForm from './RegisterForm.vue'
import RegistrationUnavailable from './RegistrationUnavailable.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const capabilities = usePublicCapabilitiesStore()
const email = ref('')
const isRegister = computed(() => route.name === 'register')
const registrationAvailable = computed(() => capabilities.resolved && capabilities.registrationEnabled)

onMounted(() => { void capabilities.load() })

async function completeAuthentication() {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
  await router.replace(redirect)
}
</script>

<template>
  <AuthShell>
    <div class="mb-7 space-y-2 text-center">
      <h1 class="text-2xl font-semibold text-text-display">{{ t(isRegister ? 'auth.titleRegister' : 'auth.titleLogin') }}</h1>
      <p class="text-sm leading-6 text-text-secondary">{{ t(isRegister ? 'auth.subtitleRegister' : 'auth.subtitleLogin') }}</p>
    </div>
    <RegistrationUnavailable v-if="isRegister && capabilities.resolved && !registrationAvailable" />
    <RegisterForm v-else-if="isRegister && registrationAvailable" v-model:email="email" @success="completeAuthentication" />
    <template v-else>
      <LoginForm v-model:email="email" :show-forgot-password="capabilities.resolved && capabilities.passwordRecoveryEnabled" @success="completeAuthentication" />
      <p v-if="capabilities.resolved && capabilities.registrationEnabled" class="mt-6 text-center text-sm text-text-secondary">
        {{ t('auth.alternateLabelLogin') }}
        <RouterLink :to="{ name: 'register' }" class="ml-1 font-medium text-text-display underline underline-offset-4">{{ t('auth.alternateActionLogin') }}</RouterLink>
      </p>
    </template>
  </AuthShell>
</template>
