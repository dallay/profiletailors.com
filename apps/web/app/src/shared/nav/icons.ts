import type { Component } from 'vue'
import {
  BarChart3,
  CalendarDays,
  Images,
  LayoutGrid,
  Lightbulb,
  Settings,
  Shield,
} from '@lucide/vue'

export const navIconByName: Record<string, Component> = {
  BarChart3,
  CalendarDays,
  Images,
  LayoutGrid,
  Lightbulb,
  Settings,
  Shield,
}

export function resolveNavIcon(name: string): Component {
  return navIconByName[name] ?? LayoutGrid
}
