import type { ButtonHTMLAttributes, PropsWithChildren } from 'react'

import { cn } from '@/shared/lib/cn'

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  fullWidth?: boolean
}

const variantClasses: Record<ButtonVariant, string> = {
  danger: 'bg-danger text-white hover:brightness-105 focus-visible:ring-danger/20',
  ghost: 'bg-transparent text-text-secondary hover:bg-surface-muted focus-visible:ring-accent/15',
  primary:
    'bg-accent text-accent-foreground shadow-lg shadow-accent/10 hover:brightness-105 focus-visible:ring-accent/20',
  secondary:
    'border border-border bg-surface text-text-primary hover:bg-surface-muted focus-visible:ring-accent/15',
}

export function Button({
  children,
  className,
  disabled,
  fullWidth,
  type = 'button',
  variant = 'primary',
  ...props
}: PropsWithChildren<ButtonProps>) {
  return (
    <button
      className={cn(
        'inline-flex min-h-11 items-center justify-center rounded-[14px] px-4 text-sm font-semibold transition focus-visible:outline-none focus-visible:ring-4 disabled:cursor-not-allowed disabled:opacity-60',
        fullWidth && 'w-full',
        variantClasses[variant],
        className,
      )}
      disabled={disabled}
      type={type}
      {...props}
    >
      {children}
    </button>
  )
}
