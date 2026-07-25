import { ref } from 'vue'

export interface DragData {
  id: string
  previousScheduledAt: string
}

export function useDragAndDrop() {
  const dragData = ref<DragData | null>(null)

  const onDragStart = (e: DragEvent, id: string, previousScheduledAt: string) => {
    if (!e.dataTransfer) return

    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', id)
    dragData.value = { id, previousScheduledAt }

    const el = e.target as HTMLElement
    if (el) el.style.opacity = '0.4'
  }

  const onDragEnd = (e: DragEvent) => {
    const el = e.target as HTMLElement
    if (el) el.style.opacity = '1'
    dragData.value = null
  }

  const extractDroppedId = (e: DragEvent): string | null => {
    if (!e.dataTransfer) return null
    return e.dataTransfer.getData('text/plain') || null
  }

  const resolveDateFromDrop = (
    targetDate: Date,
    targetHour: number | undefined,
    preserveTimeFromDrag: boolean = false,
  ): Date => {
    const result = new Date(targetDate)

    if (targetHour !== undefined) {
      result.setHours(targetHour, 0, 0, 0)
    } else if (preserveTimeFromDrag && dragData.value?.previousScheduledAt) {
      const prev = new Date(dragData.value.previousScheduledAt)
      result.setHours(prev.getHours(), prev.getMinutes(), 0, 0)
    }

    return result
  }

  const clear = () => {
    dragData.value = null
  }

  return {
    dragData,
    onDragStart,
    onDragEnd,
    extractDroppedId,
    resolveDateFromDrop,
    clear,
  }
}
