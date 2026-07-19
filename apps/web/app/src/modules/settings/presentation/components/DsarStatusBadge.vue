<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DsarRequestStatus } from '@modules/settings/infrastructure/privacy.store'
import { Badge } from '@/components/ui/badge'
import type { BadgeVariants } from '@/components/ui/badge'

const props = defineProps<{
  status: DsarRequestStatus
}>()

const { t } = useI18n()

const variantMap: Record<DsarRequestStatus, BadgeVariants['variant']> = {
  PENDING: 'secondary',
  PROCESSING: 'outline',
  COMPLETED: 'default',
  REJECTED: 'destructive',
  FAILED: 'ghost',
}

const badgeVariant = computed(() => variantMap[props.status] ?? 'secondary')
</script>

<template>
  <Badge :variant="badgeVariant" data-testid="dsar-status-badge">
    {{ t(`settings.privacy.status.${status}`) }}
  </Badge>
</template>
