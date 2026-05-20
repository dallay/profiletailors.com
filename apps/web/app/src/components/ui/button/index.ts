import type { VariantProps } from 'class-variance-authority'
import { cva } from 'class-variance-authority'

export { default as Button } from './Button.vue'

export const buttonVariants = cva(
  'focus-visible:border-ring focus-visible:ring-ring/50 aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 aria-invalid:border-destructive dark:aria-invalid:border-destructive/50 rounded-full border border-transparent bg-clip-padding font-mono text-[11px] font-bold tracking-[0.06em] uppercase focus-visible:ring-3 aria-invalid:ring-3 group/button inline-flex shrink-0 items-center justify-center whitespace-nowrap transition-all outline-none select-none disabled:pointer-events-none disabled:opacity-50 [&_svg]:pointer-events-none [&_svg]:shrink-0',
  {
    variants: {
      variant: {
        default: 'bg-text-display text-bg-primary hover:opacity-90',
        outline: 'border-border-visible bg-transparent text-text-body hover:border-text-body',
        secondary: 'border-border-visible bg-transparent text-text-body hover:border-text-body',
        ghost: 'bg-transparent text-text-secondary hover:text-text-display rounded-none',
        destructive: 'bg-error text-white hover:opacity-90',
        link: 'text-text-display underline-offset-4 hover:underline',
      },
      size: {
        default: 'h-11 px-6',
        xs: 'h-7 px-3 text-[10px]',
        sm: 'h-9 px-4 text-[10px]',
        lg: 'h-12 px-8 text-xs',
        icon: 'size-11',
      },
    },
    defaultVariants: {
      variant: 'default',
      size: 'default',
    },
  },
)
export type ButtonVariants = VariantProps<typeof buttonVariants>
