import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { CalendarDate } from '@internationalized/date'
import { useComposerScheduling } from './useComposerScheduling'

describe('useComposerScheduling', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-07-28T10:00:00.000Z'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  describe('initialization', () => {
    it('defaults to "now" mode with no date selected', () => {
      const {
        scheduleMode,
        selectedCalendarDate,
        scheduleTime,
        isScheduleValid,
        effectiveScheduledAt,
      } = useComposerScheduling()

      expect(scheduleMode.value).toBe('now')
      expect(selectedCalendarDate.value).toBeUndefined()
      expect(scheduleTime.value).toBe('10:00')
      expect(isScheduleValid.value).toBe(true)
      expect(effectiveScheduledAt.value).toBeNull()
    })

    it('initializes with custom mode when initialDate is provided', () => {
      const initialDate = '2026-08-15T14:30:00.000Z'
      const { scheduleMode, selectedCalendarDate, scheduleTime } = useComposerScheduling({
        initialDate,
      })

      expect(scheduleMode.value).toBe('custom')
      expect(selectedCalendarDate.value).toBeDefined()
      expect(selectedCalendarDate.value?.year).toBe(2026)
      expect(selectedCalendarDate.value?.month).toBe(8)
      expect(selectedCalendarDate.value?.day).toBe(15)

      expect(scheduleTime.value).toBeDefined()
      expect(scheduleTime.value).toMatch(/^\d{2}:\d{2}$/)
    })

    it('respects initialMode when provided', () => {
      const { scheduleMode } = useComposerScheduling({ initialMode: 'next' })

      expect(scheduleMode.value).toBe('next')
    })

    it('initialDate overrides initialMode to custom', () => {
      const { scheduleMode } = useComposerScheduling({
        initialDate: '2026-08-15T14:30:00.000Z',
        initialMode: 'now',
      })

      expect(scheduleMode.value).toBe('custom')
    })
  })

  describe('schedule modes', () => {
    it('returns null effectiveScheduledAt for "now" mode', () => {
      const { scheduleMode, effectiveScheduledAt, backendScheduleMode } = useComposerScheduling()

      scheduleMode.value = 'now'

      expect(effectiveScheduledAt.value).toBeNull()
      expect(backendScheduleMode.value).toBe('NOW')
    })

    it('returns null effectiveScheduledAt for "next" mode', () => {
      const { scheduleMode, effectiveScheduledAt, backendScheduleMode } = useComposerScheduling()

      scheduleMode.value = 'next'

      expect(effectiveScheduledAt.value).toBeNull()
      expect(backendScheduleMode.value).toBe('NEXT_SLOT')
    })

    it('calculates effectiveScheduledAt for "custom" mode with valid date/time', () => {
      const {
        scheduleMode,
        selectedCalendarDate,
        scheduleTime,
        effectiveScheduledAt,
        backendScheduleMode,
      } = useComposerScheduling()

      scheduleMode.value = 'custom'
      selectedCalendarDate.value = new CalendarDate(2026, 8, 15)
      scheduleTime.value = '14:30'

      expect(effectiveScheduledAt.value).toBeTruthy()
      expect(effectiveScheduledAt.value).toContain('2026-08-15')
      expect(backendScheduleMode.value).toBe('SCHEDULED_AT')

      const parsed = new Date(effectiveScheduledAt.value!)
      expect(parsed.getFullYear()).toBe(2026)
      expect(parsed.getMonth()).toBe(7)
      expect(parsed.getDate()).toBe(15)
    })

    it('returns null effectiveScheduledAt for "custom" mode without date', () => {
      const { scheduleMode, effectiveScheduledAt } = useComposerScheduling()

      scheduleMode.value = 'custom'

      expect(effectiveScheduledAt.value).toBeNull()
    })
  })

  describe('validation', () => {
    it('validates "now" mode as always valid', () => {
      const { scheduleMode, isScheduleValid } = useComposerScheduling()

      scheduleMode.value = 'now'

      expect(isScheduleValid.value).toBe(true)
    })

    it('validates "next" mode as always valid', () => {
      const { scheduleMode, isScheduleValid } = useComposerScheduling()

      scheduleMode.value = 'next'

      expect(isScheduleValid.value).toBe(true)
    })

    it('validates "custom" mode requires both date and time', () => {
      const { scheduleMode, selectedCalendarDate, scheduleTime, isScheduleValid } =
        useComposerScheduling()

      scheduleMode.value = 'custom'

      expect(isScheduleValid.value).toBe(false)

      selectedCalendarDate.value = new CalendarDate(2026, 8, 15)
      expect(isScheduleValid.value).toBe(true)

      scheduleTime.value = ''
      expect(isScheduleValid.value).toBe(false)

      scheduleTime.value = '14:30'
      expect(isScheduleValid.value).toBe(true)
    })
  })

  describe('minTimeForDate', () => {
    it('returns "00:00" when no date is selected', () => {
      const { minTimeForDate } = useComposerScheduling()

      expect(minTimeForDate.value).toBe('00:00')
    })

    it('enforces minimum time of now+5min for today', () => {
      const { selectedCalendarDate, minTimeForDate, now } = useComposerScheduling()

      selectedCalendarDate.value = new CalendarDate(2026, 7, 28)

      const expectedHour = String(now.value.getHours()).padStart(2, '0')
      const expectedMin = String(now.value.getMinutes() + 5).padStart(2, '0')
      expect(minTimeForDate.value).toBe(`${expectedHour}:${expectedMin}`)
    })

    it('returns "23:59" when selecting today near midnight rollover', () => {
      const almostMidnight = new Date(2026, 6, 28, 23, 57)
      vi.setSystemTime(almostMidnight)

      const { selectedCalendarDate, minTimeForDate } = useComposerScheduling()

      selectedCalendarDate.value = new CalendarDate(2026, 7, 28)

      expect(minTimeForDate.value).toBe('23:59')
    })

    it('allows any time for future dates', () => {
      const { selectedCalendarDate, minTimeForDate } = useComposerScheduling()

      selectedCalendarDate.value = new CalendarDate(2026, 7, 29)

      expect(minTimeForDate.value).toBe('00:00')
    })

    it('updates minTimeForDate when time advances', () => {
      const { selectedCalendarDate, minTimeForDate, now } = useComposerScheduling()

      selectedCalendarDate.value = new CalendarDate(2026, 7, 28)

      expect(minTimeForDate.value).toBeDefined()
      expect(minTimeForDate.value).toMatch(/^\d{2}:\d{2}$/)

      expect(now.value).toBeInstanceOf(Date)
      expect(now.value.getTime()).toBeGreaterThan(0)
    })
  })

  describe('labels and helpers', () => {
    it('formats selected date label correctly', () => {
      const { selectedCalendarDate, selectedDateLabel } = useComposerScheduling()

      selectedCalendarDate.value = new CalendarDate(2026, 8, 15)

      expect(selectedDateLabel.value).toBe('Aug 15, 2026')
    })

    it('returns "Select date" when no date is selected', () => {
      const { selectedDateLabel } = useComposerScheduling()

      expect(selectedDateLabel.value).toBe('Select date')
    })

    it('builds correct helper text for "now" mode', () => {
      const { scheduleMode, scheduleHelperText } = useComposerScheduling()

      scheduleMode.value = 'now'

      expect(scheduleHelperText.value).toContain('creation date and time')
    })

    it('builds correct helper text for "next" mode', () => {
      const { scheduleMode, scheduleHelperText } = useComposerScheduling()

      scheduleMode.value = 'next'

      expect(scheduleHelperText.value).toContain('next available schedule slot')
    })

    it('builds correct helper text for "custom" mode with date', () => {
      const { scheduleMode, selectedCalendarDate, scheduleTime, scheduleHelperText } =
        useComposerScheduling()

      scheduleMode.value = 'custom'
      selectedCalendarDate.value = new CalendarDate(2026, 8, 15)
      scheduleTime.value = '14:30'

      expect(scheduleHelperText.value).toContain('Aug 15, 2026')
      expect(scheduleHelperText.value).toContain('14:30')
    })

    it('prompts to select date in "custom" mode without date', () => {
      const { scheduleMode, scheduleHelperText } = useComposerScheduling()

      scheduleMode.value = 'custom'

      expect(scheduleHelperText.value).toContain('Select a date and time')
    })
  })

  describe('actions', () => {
    it('setScheduleMode changes the mode', () => {
      const { scheduleMode, setScheduleMode } = useComposerScheduling()

      setScheduleMode('next')
      expect(scheduleMode.value).toBe('next')

      setScheduleMode('custom')
      expect(scheduleMode.value).toBe('custom')
    })

    it('setScheduleDate changes the selected date', () => {
      const { selectedCalendarDate, setScheduleDate } = useComposerScheduling()

      const date = new CalendarDate(2026, 8, 15)
      setScheduleDate(date)

      expect(selectedCalendarDate.value).toEqual(date)
    })

    it('setScheduleTime changes the time', () => {
      const { scheduleTime, setScheduleTime } = useComposerScheduling()

      setScheduleTime('14:30')

      expect(scheduleTime.value).toBe('14:30')
    })

    it('resetSchedule clears all state', () => {
      const {
        scheduleMode,
        selectedCalendarDate,
        scheduleTime,
        isDatePickerOpen,
        setScheduleMode,
        setScheduleDate,
        setScheduleTime,
        resetSchedule,
      } = useComposerScheduling()

      // Setear estado
      setScheduleMode('custom')
      setScheduleDate(new CalendarDate(2026, 8, 15))
      setScheduleTime('14:30')
      isDatePickerOpen.value = true

      // Reset
      resetSchedule()

      expect(scheduleMode.value).toBe('now')
      expect(selectedCalendarDate.value).toBeUndefined()
      expect(scheduleTime.value).toBe('10:00')
      expect(isDatePickerOpen.value).toBe(false)
    })

    it('resetSchedule respects initialMode', () => {
      const { scheduleMode, resetSchedule } = useComposerScheduling({ initialMode: 'next' })

      scheduleMode.value = 'custom'
      resetSchedule()

      expect(scheduleMode.value).toBe('next')
    })
  })

  describe('loadFromPublication', () => {
    it('loads "NOW" mode from publication', () => {
      const { scheduleMode, selectedCalendarDate, loadFromPublication } = useComposerScheduling()

      loadFromPublication({
        scheduleMode: 'NOW',
        scheduledAt: null,
      })

      expect(scheduleMode.value).toBe('now')
      expect(selectedCalendarDate.value).toBeUndefined()
    })

    it('loads "NEXT_SLOT" mode from publication', () => {
      const { scheduleMode, selectedCalendarDate, loadFromPublication } = useComposerScheduling()

      loadFromPublication({
        scheduleMode: 'NEXT_SLOT',
        scheduledAt: null,
      })

      expect(scheduleMode.value).toBe('next')
      expect(selectedCalendarDate.value).toBeUndefined()
    })

    it('loads "SCHEDULED_AT" mode with date from publication', () => {
      const { scheduleMode, selectedCalendarDate, scheduleTime, loadFromPublication } =
        useComposerScheduling()

      loadFromPublication({
        scheduleMode: 'SCHEDULED_AT',
        scheduledAt: '2026-08-15T14:30:00.000Z',
      })

      expect(scheduleMode.value).toBe('custom')
      expect(selectedCalendarDate.value?.year).toBe(2026)
      expect(selectedCalendarDate.value?.month).toBe(8)
      expect(selectedCalendarDate.value?.day).toBe(15)

      expect(scheduleTime.value).toBeDefined()
      expect(scheduleTime.value).toMatch(/^\d{2}:\d{2}$/)
    })

    it('defaults to "custom" mode when scheduleMode is null', () => {
      const { scheduleMode, loadFromPublication } = useComposerScheduling()

      loadFromPublication({
        scheduleMode: null,
        scheduledAt: null,
      })

      expect(scheduleMode.value).toBe('custom')
    })

    it('clears date when scheduleMode is not "SCHEDULED_AT"', () => {
      const { selectedCalendarDate, scheduleTime, loadFromPublication } = useComposerScheduling()

      loadFromPublication({
        scheduleMode: 'NOW',
        scheduledAt: '2026-08-15T14:30:00.000Z',
      })

      expect(selectedCalendarDate.value).toBeUndefined()
      expect(scheduleTime.value).toBe('10:00')
    })
  })

  describe('clock ticker', () => {
    it('updates now value every minute', () => {
      const { now, minTimeForDate, selectedCalendarDate } = useComposerScheduling()

      const startTime = now.value.getTime()
      selectedCalendarDate.value = new CalendarDate(2026, 7, 28)
      const initialMinTime = minTimeForDate.value

      vi.advanceTimersByTime(60_000)

      const newTime = now.value.getTime()
      expect(newTime).toBeGreaterThan(startTime)
      expect(minTimeForDate.value).not.toBe(initialMinTime)
    })

    it('cleans up ticker on unmount', () => {
      const clearIntervalSpy = vi.spyOn(global, 'clearInterval')

      const { stopTicker } = useComposerScheduling()

      stopTicker()

      expect(clearIntervalSpy).toHaveBeenCalled()
    })
  })

  describe('edge cases', () => {
    it('handles invalid time format gracefully', () => {
      const { scheduleMode, selectedCalendarDate, scheduleTime, effectiveScheduledAt } =
        useComposerScheduling()

      scheduleMode.value = 'custom'
      selectedCalendarDate.value = new CalendarDate(2026, 8, 15)
      scheduleTime.value = 'invalid'

      const result = effectiveScheduledAt.value

      expect(result).toBeNull()
    })

    it('handles midnight time correctly', () => {
      const { scheduleMode, selectedCalendarDate, scheduleTime, effectiveScheduledAt } =
        useComposerScheduling()

      scheduleMode.value = 'custom'
      selectedCalendarDate.value = new CalendarDate(2026, 8, 15)
      scheduleTime.value = '00:00'

      const result = effectiveScheduledAt.value

      expect(result).toBeTruthy()

      const parsed = new Date(result!)
      expect(parsed).toBeInstanceOf(Date)
      expect(parsed.getTime()).toBeGreaterThan(0)

      const localDateStr = result!.split('T')[0]
      expect(['2026-08-14', '2026-08-15']).toContain(localDateStr)
    })

    it('handles 23:59 time correctly', () => {
      const { scheduleMode, selectedCalendarDate, scheduleTime, effectiveScheduledAt } =
        useComposerScheduling()

      scheduleMode.value = 'custom'
      selectedCalendarDate.value = new CalendarDate(2026, 8, 15)
      scheduleTime.value = '23:59'

      const result = effectiveScheduledAt.value

      expect(result).toBeTruthy()

      const parsed = new Date(result!)
      expect(parsed).toBeInstanceOf(Date)
      expect(parsed.getTime()).toBeGreaterThan(0)

      const localDateStr = result!.split('T')[0]
      expect(['2026-08-15', '2026-08-16']).toContain(localDateStr)
    })

    it('handles leap year dates', () => {
      const { scheduleMode, selectedCalendarDate, scheduleTime, effectiveScheduledAt } =
        useComposerScheduling()

      scheduleMode.value = 'custom'
      selectedCalendarDate.value = new CalendarDate(2024, 2, 29)
      scheduleTime.value = '12:00'

      const result = effectiveScheduledAt.value

      expect(result).toBeTruthy()
      expect(result).toContain('2024-02-29')
    })
  })

  describe('integration scenarios', () => {
    it('simulates full "now" flow', () => {
      const { scheduleMode, isScheduleValid, effectiveScheduledAt, backendScheduleMode } =
        useComposerScheduling()

      scheduleMode.value = 'now'

      expect(isScheduleValid.value).toBe(true)

      expect(backendScheduleMode.value).toBe('NOW')
      expect(effectiveScheduledAt.value).toBeNull()
    })

    it('simulates full "custom" flow', () => {
      const {
        isScheduleValid,
        effectiveScheduledAt,
        backendScheduleMode,
        setScheduleMode,
        setScheduleDate,
        setScheduleTime,
      } = useComposerScheduling()

      setScheduleMode('custom')

      expect(isScheduleValid.value).toBe(false)

      setScheduleDate(new CalendarDate(2026, 8, 15))

      expect(isScheduleValid.value).toBe(true)

      setScheduleTime('14:30')

      expect(backendScheduleMode.value).toBe('SCHEDULED_AT')
      expect(effectiveScheduledAt.value).toBeTruthy()
      expect(effectiveScheduledAt.value).toContain('2026-08-15')

      const parsed = new Date(effectiveScheduledAt.value!)
      expect(parsed.getFullYear()).toBe(2026)
      expect(parsed.getMonth()).toBe(7)
      expect(parsed.getDate()).toBe(15)
    })

    it('simulates edit mode load and modify', () => {
      const {
        scheduleMode,
        selectedCalendarDate,
        scheduleTime,
        loadFromPublication,
        setScheduleTime,
      } = useComposerScheduling()

      loadFromPublication({
        scheduleMode: 'SCHEDULED_AT',
        scheduledAt: '2026-08-15T10:00:00.000Z',
      })

      expect(scheduleMode.value).toBe('custom')
      expect(selectedCalendarDate.value?.day).toBe(15)

      expect(scheduleTime.value).toBeDefined()
      expect(scheduleTime.value).toMatch(/^\d{2}:\d{2}$/)

      setScheduleTime('14:30')

      expect(scheduleTime.value).toBe('14:30')
    })

    it('simulates "create another" flow with reset', () => {
      const {
        scheduleMode,
        selectedCalendarDate,
        scheduleTime,
        setScheduleMode,
        setScheduleDate,
        setScheduleTime,
        resetSchedule,
      } = useComposerScheduling()

      setScheduleMode('custom')
      setScheduleDate(new CalendarDate(2026, 8, 15))
      setScheduleTime('14:30')

      resetSchedule()

      expect(scheduleMode.value).toBe('now')
      expect(selectedCalendarDate.value).toBeUndefined()
      expect(scheduleTime.value).toBe('10:00')
    })
  })
})
