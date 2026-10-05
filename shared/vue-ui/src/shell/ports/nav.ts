import type { Component } from 'vue'

export interface NavItem {
  key: string
  labelKey: string
  routeName?: string
  path?: string
  iconComponent: Component
  badge?: string
}

export interface NavGroup {
  key: string
  label: string
  items: NavItem[]
}

export interface NavPort {
  readonly groups: NavGroup[]
}
