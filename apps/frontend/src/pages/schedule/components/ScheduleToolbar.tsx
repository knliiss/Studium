import type { ReactNode } from 'react'

export function ScheduleToolbar({ children }: { children: ReactNode }) {
  return <div className="flex flex-wrap items-center gap-3">{children}</div>
}
