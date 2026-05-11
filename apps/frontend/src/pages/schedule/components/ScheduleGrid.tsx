import { Fragment } from 'react'
import type { ReactNode } from 'react'

interface PairLike {
  pairNumber: number
  startTime: string
  endTime: string
}

interface ScheduleGridProps<TDay> {
  days: Array<{ dateLabel: string; dayKey: TDay; dayLabel: string }>
  pairs: PairLike[]
  renderCell: (day: TDay, pair: PairLike) => ReactNode
  timeLabel: string
}

export function ScheduleGrid<TDay>({ days, pairs, renderCell, timeLabel }: ScheduleGridProps<TDay>) {
  const timeColumnWidth = 84
  const dayColumnMinWidth = 190
  const headerRowHeight = 58
  const slotRowHeight = 140

  return (
    <div className="overflow-x-auto rounded-[10px] border border-border bg-surface-muted/70">
      <div
        className="grid"
        style={{
          gridTemplateColumns: `${timeColumnWidth}px repeat(${days.length}, minmax(${dayColumnMinWidth}px, 1fr))`,
          gridTemplateRows: `${headerRowHeight}px repeat(${pairs.length}, ${slotRowHeight}px)`,
          minWidth: `${timeColumnWidth + days.length * dayColumnMinWidth}px`,
        }}
      >
        <div className="sticky left-0 z-20 flex items-center border-b border-r border-border bg-background-elevated px-3 text-xs font-semibold uppercase tracking-[0.14em] text-text-muted">
          {timeLabel}
        </div>
        {days.map((day) => (
          <div key={String(day.dayKey)} className="flex min-w-0 flex-col justify-center border-b border-r border-border bg-background-elevated px-3">
            <p className="truncate text-xs font-semibold uppercase tracking-[0.12em] text-text-muted">{day.dayLabel}</p>
            <p className="truncate text-sm font-semibold text-text-primary">{day.dateLabel}</p>
          </div>
        ))}

        {pairs.map((pair) => (
          <Fragment key={pair.pairNumber}>
            <div key={`time-${pair.pairNumber}`} className="sticky left-0 z-10 flex min-h-0 flex-col justify-center border-b border-r border-border bg-surface px-2">
              <p className="text-sm font-semibold text-text-primary">{pair.pairNumber}</p>
              <p className="text-[11px] leading-4 text-text-secondary">{pair.startTime}</p>
              <p className="text-[11px] leading-4 text-text-secondary">{pair.endTime}</p>
            </div>
            {days.map((day) => (
              <div key={`${String(day.dayKey)}-${pair.pairNumber}`} className="min-h-0 border-b border-r border-border bg-surface p-1.5">
                {renderCell(day.dayKey, pair)}
              </div>
            ))}
          </Fragment>
        ))}
      </div>
    </div>
  )
}
