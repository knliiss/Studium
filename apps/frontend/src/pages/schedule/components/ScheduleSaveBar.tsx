import type { ReactNode } from 'react'

import { Card } from '@/shared/ui/Card'

export function ScheduleSaveBar({ children }: { children: ReactNode }) {
  return <Card className="sticky bottom-4 z-10 rounded-[12px] border border-border bg-surface/95 px-4 py-3 shadow-[var(--shadow-card)] backdrop-blur">{children}</Card>
}
