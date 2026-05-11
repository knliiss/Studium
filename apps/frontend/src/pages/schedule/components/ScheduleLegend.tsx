export function ScheduleLegend({ items }: { items: Array<{ label: string; tone: string }> }) {
  return (
    <div className="flex flex-wrap gap-2">
      {items.map((item) => (
        <span key={item.label} className="rounded-full border border-border px-2.5 py-1 text-xs text-text-secondary">
          {item.label}
        </span>
      ))}
    </div>
  )
}
