import { cn } from '@/shared/lib/cn'

interface SegmentedControlOption<TValue extends string> {
  value: TValue
  label: string
  disabled?: boolean
}

interface SegmentedControlProps<TValue extends string> {
  value: TValue
  options: Array<SegmentedControlOption<TValue>>
  onChange: (value: TValue) => void
  ariaLabel: string
  className?: string
}

export function SegmentedControl<TValue extends string>({
  ariaLabel,
  className,
  onChange,
  options,
  value,
}: SegmentedControlProps<TValue>) {
  return (
    <div
      aria-label={ariaLabel}
      className={cn('inline-flex flex-wrap gap-1 rounded-[14px] border border-border bg-surface p-1', className)}
      role="group"
    >
      {options.map((option) => {
        const active = option.value === value

        return (
          <button
            key={option.value}
            aria-pressed={active}
            className={cn(
              'inline-flex min-h-9 items-center justify-center rounded-[10px] px-3 text-sm font-semibold text-text-secondary transition hover:bg-surface-muted hover:text-text-primary focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-accent/15 disabled:cursor-not-allowed disabled:opacity-55',
              active && 'bg-accent text-accent-foreground hover:bg-accent hover:text-accent-foreground',
            )}
            disabled={option.disabled}
            type="button"
            onClick={() => onChange(option.value)}
          >
            {option.label}
          </button>
        )
      })}
    </div>
  )
}
