import { driver } from 'driver.js'
import i18n from '@shared/i18n'

type TourStep = {
  element: string
  popover: {
    title: string
    description: string
    side?: 'top' | 'right' | 'bottom' | 'left'
    align?: 'start' | 'center' | 'end'
  }
}

/**
 * Starts the guided product tour over the shell chrome.
 *
 * Steps are resolved at call time so copy follows the active locale when the
 * user switches languages from settings. Steps whose target element is not
 * present on the current screen are skipped.
 */
export function startAppTour() {
  const t = i18n.global.t

  const steps: TourStep[] = [
    {
      element: '[data-tour="workspace-switcher"]',
      popover: {
        title: t('tour.workspaceSwitcher.title'),
        description: t('tour.workspaceSwitcher.description'),
        side: 'right',
        align: 'start',
      },
    },
    {
      element: '[data-tour="primary-navigation"]',
      popover: {
        title: t('tour.primaryNavigation.title'),
        description: t('tour.primaryNavigation.description'),
        side: 'right',
        align: 'start',
      },
    },
    {
      element: '[data-tour="channel-filters"]',
      popover: {
        title: t('tour.channelFilters.title'),
        description: t('tour.channelFilters.description'),
        side: 'right',
        align: 'start',
      },
    },
    {
      element: '[data-tour="section-title"]',
      popover: {
        title: t('tour.sectionTitle.title'),
        description: t('tour.sectionTitle.description'),
        side: 'bottom',
        align: 'start',
      },
    },
    {
      element: '[data-tour="main-content"]',
      popover: {
        title: t('tour.mainContent.title'),
        description: t('tour.mainContent.description'),
        side: 'top',
        align: 'center',
      },
    },
    {
      element: '[data-tour="cookie-settings"]',
      popover: {
        title: t('tour.cookieSettings.title'),
        description: t('tour.cookieSettings.description'),
        side: 'top',
        align: 'center',
      },
    },
  ]

  const visibleSteps = steps.filter((step) => document.querySelector(step.element))
  if (visibleSteps.length === 0) return

  const appTour = driver({
    showProgress: true,
    allowClose: true,
    animate: true,
    steps: visibleSteps,
  })

  appTour.drive()
}
