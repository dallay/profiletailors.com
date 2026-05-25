import type { ComputedRef, InjectionKey, Ref } from 'vue'

export interface SidebarContextValue {
  state: ComputedRef<'expanded' | 'collapsed'>
  open: Ref<boolean>
  openMobile: Ref<boolean>
  isMobile: Ref<boolean>
  setOpen: (value: boolean) => void
  setOpenMobile: (value: boolean) => void
  toggleSidebar: () => void
}

export const SIDEBAR_COOKIE_NAME = 'sidebar_state'
export const SIDEBAR_COOKIE_MAX_AGE = 60 * 60 * 24 * 7
export const SIDEBAR_WIDTH = '18rem'
export const SIDEBAR_WIDTH_ICON = '4.75rem'
export const SIDEBAR_WIDTH_MOBILE = '18rem'
export const SIDEBAR_KEYBOARD_SHORTCUT = 'b'

export const sidebarContextKey: InjectionKey<SidebarContextValue> = Symbol('sidebar')
