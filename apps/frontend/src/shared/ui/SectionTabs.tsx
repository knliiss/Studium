import { cn } from '@/shared/lib/cn'

interface SectionTab {
  id: string
  label: string
}

export function SectionTabs({
  activeId,
  items,
  onChange,
}: {
  activeId: string
  items: SectionTab[]
  onChange: (id: string) => void
}) {
  return (
    <div className="overflow-x-auto rounded-[20px] border border-border bg-surface p-2 shadow-[var(--shadow-card)]">
      <div className="flex min-w-max flex-nowrap gap-2">
        {items.map((item) => (
          <button
            key={item.id}
            className={cn(
              'inline-flex min-h-10 items-center rounded-[14px] px-4 text-sm font-semibold transition',
              item.id === activeId
                ? 'bg-accent text-accent-foreground shadow-lg shadow-accent/10'
                : 'text-text-secondary hover:bg-surface-muted hover:text-text-primary',
            )}
            type="button"
            onClick={() => onChange(item.id)}
          >
            {item.label}
          </button>
        ))}
      </div>
    </div>
  )
}
