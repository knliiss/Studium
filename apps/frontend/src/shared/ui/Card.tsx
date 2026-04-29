import type { HTMLAttributes, PropsWithChildren } from 'react'

import { cn } from '@/shared/lib/cn'

export function Card({
  children,
  className,
  ...props
}: PropsWithChildren<HTMLAttributes<HTMLDivElement>>) {
  return (
    <div className={cn('card-surface rounded-[20px] p-5', className)} {...props}>
      {children}
    </div>
  )
}
