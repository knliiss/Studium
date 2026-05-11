import type { ReactNode } from 'react'

interface SubgroupLane {
  action?: ReactNode
  content: ReactNode
  key: string
  label: string
}

export function SubgroupLanes({ lanes }: { lanes: SubgroupLane[] }) {
  return (
    <div className="grid h-full min-h-0 grid-rows-2 gap-1.5">
      {lanes.map((lane) => (
        <div key={lane.key} className="grid min-h-0 grid-cols-[54px_minmax(0,1fr)] gap-1.5">
          <div className="flex min-w-0 items-center justify-center rounded-[6px] bg-surface-muted px-1 text-center text-[10px] font-semibold leading-3 text-text-muted">
            {lane.label}
          </div>
          <div className="relative min-h-0 overflow-hidden">
            {lane.content}
            {lane.action ? (
              <div className="absolute right-1 top-1">{lane.action}</div>
            ) : null}
          </div>
        </div>
      ))}
    </div>
  )
}
