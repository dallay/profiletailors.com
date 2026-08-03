import { afterEach, describe, expect, it, vi } from 'vitest'
import { driver } from 'driver.js'
import i18n from '@shared/i18n'

vi.mock('driver.js', () => ({
  driver: vi.fn(() => ({ drive: vi.fn() })),
}))

import { startAppTour } from './app-tour'

const driverMock = vi.mocked(driver)

const ALL_TOUR_ELEMENTS = [
  'workspace-switcher',
  'primary-navigation',
  'channel-filters',
  'section-title',
  'main-content',
  'cookie-settings',
]

function mountTourTargets(names: string[]) {
  document.body.innerHTML = names.map((name) => `<div data-tour="${name}"></div>`).join('')
}

afterEach(() => {
  vi.clearAllMocks()
  document.body.innerHTML = ''
  i18n.global.locale.value = 'en'
})

describe('startAppTour', () => {
  it('does not create a driver when no tour targets are present', () => {
    startAppTour()

    expect(driverMock).not.toHaveBeenCalled()
  })

  it('creates the driver with only the visible steps', () => {
    mountTourTargets(['workspace-switcher', 'section-title'])

    startAppTour()

    expect(driverMock).toHaveBeenCalledOnce()
    const options = driverMock.mock.calls[0]?.[0]
    expect(options?.steps).toHaveLength(2)
    expect(options?.steps?.map((step) => step.element)).toEqual([
      '[data-tour="workspace-switcher"]',
      '[data-tour="section-title"]',
    ])
  })

  it('drives the tour after creating the driver', () => {
    mountTourTargets(ALL_TOUR_ELEMENTS)

    startAppTour()

    const instance = driverMock.mock.results[0]?.value
    expect(instance?.drive).toHaveBeenCalled()
  })

  it('resolves step copy from the default English locale', () => {
    mountTourTargets(ALL_TOUR_ELEMENTS)

    startAppTour()

    const options = driverMock.mock.calls[0]?.[0]
    expect(options?.steps?.[0]?.popover?.title).toBe('Workspace selector')
    expect(options?.steps?.[0]?.popover?.description).toBe(
      'Switch between workspaces without leaving the current screen.',
    )
  })

  it('resolves step copy from the active Spanish locale', () => {
    i18n.global.locale.value = 'es'
    mountTourTargets(ALL_TOUR_ELEMENTS)

    startAppTour()

    const options = driverMock.mock.calls[0]?.[0]
    expect(options?.steps?.[0]?.popover?.title).toBe('Selector de espacio de trabajo')
  })
})
