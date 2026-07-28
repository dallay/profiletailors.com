interface PrivacyControlNavigator extends Navigator {
  globalPrivacyControl?: boolean
}

interface LegacyDoNotTrackWindow extends Window {
  doNotTrack?: string | null
}

/**
 * Check if Do Not Track (DNT) is enabled.
 * Returns false in SSR context.
 */
export function isDNTEnabled(): boolean {
  if (typeof navigator === 'undefined') {
    return false
  }

  const legacyWindowDnt =
    typeof window === 'undefined' ? undefined : (window as LegacyDoNotTrackWindow).doNotTrack

  return navigator.doNotTrack === '1' || navigator.doNotTrack === 'yes' || legacyWindowDnt === '1'
}

/**
 * Check if Global Privacy Control (GPC) is enabled.
 * Returns false in SSR context.
 */
export function isGPCEnabled(): boolean {
  if (typeof navigator === 'undefined') {
    return false
  }

  return (navigator as PrivacyControlNavigator).globalPrivacyControl === true
}

/**
 * Check if any privacy signal (DNT or GPC) is active.
 * Returns true if either signal is present.
 */
export function hasPrivacySignal(): boolean {
  return isDNTEnabled() || isGPCEnabled()
}
