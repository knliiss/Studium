import type { TextareaHTMLAttributes } from 'react'

import { cn } from '@/shared/lib/cn'

export function Textarea({ className, ...props }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return (
    <textarea
      className={cn(
        'field-control min-h-28 resize-y px-4 py-3 text-sm placeholder:text-text-muted',
        className,
      )}
      {...props}
    />
  )
}
