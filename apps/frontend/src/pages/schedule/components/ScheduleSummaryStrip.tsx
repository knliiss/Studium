export function ScheduleSummaryStrip({
  lessons,
  online,
  offline,
  conflicts,
  titles,
}: {
  lessons: number
  online: number
  offline: number
  conflicts: number
  titles: {
    conflicts: string
    lessons: string
    offline: string
    online: string
  }
}) {
  const items = [
    { title: titles.lessons, value: lessons },
    { title: titles.online, value: online },
    { title: titles.offline, value: offline },
    { title: titles.conflicts, value: conflicts },
  ]

  return (
    <div className="grid gap-2 rounded-[12px] border border-border bg-surface px-3 py-2 md:grid-cols-4">
      {items.map((item) => (
        <div key={item.title} className="flex items-center justify-between gap-3 rounded-[8px] bg-surface-muted px-3 py-2">
          <span className="truncate text-xs font-semibold uppercase tracking-[0.12em] text-text-muted">{item.title}</span>
          <span className="text-sm font-semibold text-text-primary">{item.value}</span>
        </div>
      ))}
    </div>
  )
}
