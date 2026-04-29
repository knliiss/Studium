import type { InputHTMLAttributes } from 'react'

import { cn } from '@/shared/lib/cn'

export function Input({ className, type, ...props }: InputHTMLAttributes<HTMLInputElement>) {
  if (type === 'checkbox') {
    return (
      <input
        className={cn('checkbox-control', className)}
        type={type}
        {...props}
      />
    )
  }

  return (
    <input
      className={cn('field-control min-h-12 px-4 text-sm placeholder:text-text-muted', className)}
      type={type}
      {...props}
    />
  )
}
