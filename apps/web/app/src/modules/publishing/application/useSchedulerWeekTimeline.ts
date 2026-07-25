import { useI18n } from 'vue-i18n'

/**
 * Provides utility functions for week timeline slot management and date formatting
 */
export function useSchedulerWeekTimeline() {
  const { locale } = useI18n()

  const dateKey = (date: Date): string => {
    const y = date.getFullYear()
    const m = String(date.getMonth() + 1).padStart(2, '0')
    const d = String(date.getDate()).padStart(2, '0')
    return `${y}-${m}-${d}`
  }

  const slotKey = (date: Date, hour: number): string => {
    return `${dateKey(date)}#${hour}`
  }

  const formatDayName = (date: Date): string => {
    const days = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']
    const daysEs = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado']
    const index = date.getDay()
    return (locale.value === 'es' ? daysEs : days)[index] ?? ''
  }

  const isToday = (date: Date): boolean => {
    const now = new Date()
    return (
      date.getDate() === now.getDate() &&
      date.getMonth() === now.getMonth() &&
      date.getFullYear() === now.getFullYear()
    )
  }

  const isPastSlot = (date: Date, hour: number): boolean => {
    const now = new Date()
    const slotDate = new Date(date)
    slotDate.setHours(hour, 0, 0, 0)
    return slotDate.getTime() < now.getTime() + 5 * 60_000
  }

  return {
    dateKey,
    slotKey,
    formatDayName,
    isToday,
    isPastSlot,
  }
}
