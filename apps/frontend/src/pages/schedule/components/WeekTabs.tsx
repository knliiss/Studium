import { cn } from '@/shared/lib/cn'

interface WeekTabsProps {
  items: Array<{ key: string; title: string; subtitle: string; active: boolean }>
  onSelect: (key: string) => void
}

export function WeekTabs({ items, onSelect }: WeekTabsProps) {
  return (
    <div className="grid gap-2 md:grid-cols-3 xl:grid-cols-6">
      {items.map((item) => (
        <button
          key={item.key}
          aria-current={item.active ? 'date' : undefined}
          className={cn(
            'min-w-0 rounded-[8px] border px-2.5 py-2 text-left transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/25',
            item.active ? 'border-accent bg-accent-muted/50' : 'border-border bg-surface hover:border-border-strong',
          )}
          type="button"
          onClick={() => onSelect(item.key)}
        >
          <p className="truncate text-xs font-semibold text-text-primary">{item.title}</p>
          <p className="truncate text-[11px] text-text-secondary">{item.subtitle}</p>
        </button>
      ))}
    </div>
  )
}
