import type { PropsWithChildren, ReactNode } from 'react'

import { cn } from '@/shared/lib/cn'

interface FormFieldProps {
  label: string
  error?: string | null
  hint?: ReactNode
  className?: string
}

export function FormField({
  children,
  className,
  error,
  hint,
  label,
}: PropsWithChildren<FormFieldProps>) {
  return (
    <label className={cn('flex flex-col gap-2', className)}>
      <span className="text-sm font-semibold text-text-secondary">{label}</span>
      {children}
      {error ? (
        <span className="text-sm text-danger">{error}</span>
      ) : hint ? (
        <span className="text-sm text-text-muted">{hint}</span>
      ) : null}
    </label>
  )
}
