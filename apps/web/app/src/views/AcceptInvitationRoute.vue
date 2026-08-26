<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AcceptInvitationView from '@modules/invitation/presentation/AcceptInvitationView.vue'

const route = useRoute()
const token = computed(() => {
  const raw = route.query.token
  if (Array.isArray(raw)) return raw[0] ?? ''
  return typeof raw === 'string' ? raw : ''
})
</script>

<template>
  <AcceptInvitationView :token="token" />
</template>