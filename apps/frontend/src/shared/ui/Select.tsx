import type { SelectHTMLAttributes } from 'react'

import { cn } from '@/shared/lib/cn'

export function Select({ className, ...props }: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select className={cn('field-control min-h-12 px-4 text-sm', className)} {...props} />
}
