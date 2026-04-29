import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/shared/lib/cn'
import { Button } from '@/shared/ui/Button'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { EmptyState } from '@/shared/ui/StateViews'

export interface CardPickerItem {
  id: string
  title: string
  description?: string | null
  meta?: ReactNode
  leading?: ReactNode
  disabled?: boolean
}

interface CardPickerProps {
  label?: string
  searchLabel?: string
  searchPlaceholder?: string
  searchValue?: string
  onSearchChange?: (value: string) => void
  items: CardPickerItem[]
  selectedIds: string[]
  onToggle: (id: string) => void
  emptyTitle: string
  emptyDescription?: string
  loading?: boolean
  loadingLabel?: string
  multiple?: boolean
  page?: number
  totalPages?: number
  onPageChange?: (page: number) => void
}

export function CardPicker({
  emptyDescription,
  emptyTitle,
  items,
  label,
  loading,
  loadingLabel,
  multiple,
  onPageChange,
  onSearchChange,
  onToggle,
  page = 0,
  searchLabel,
  searchPlaceholder,
  searchValue,
  selectedIds,
  totalPages = 1,
}: CardPickerProps) {
  const { t } = useTranslation()
  const showPagination = typeof onPageChange === 'function' && totalPages > 1
  const content = (
    <div className="space-y-3">
      {typeof onSearchChange === 'function' ? (
        <Input
          aria-label={searchLabel ?? label}
          placeholder={searchPlaceholder}
          value={searchValue ?? ''}
          onChange={(event) => onSearchChange(event.target.value)}
        />
      ) : null}

      {loading ? (
        <p className="rounded-[14px] border border-border bg-surface-muted px-4 py-3 text-sm text-text-secondary">
          {loadingLabel ?? t('common.states.loading')}
        </p>
      ) : items.length === 0 ? (
        <EmptyState description={emptyDescription} title={emptyTitle} />
      ) : (
        <div className="grid gap-3">
          {items.map((item) => {
            const selected = selectedIds.includes(item.id)

            return (
              <button
                key={item.id}
                className={cn(
                  'flex w-full items-start gap-3 rounded-[16px] border border-border bg-surface-muted p-4 text-left transition hover:border-border-strong focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-accent/15 disabled:cursor-not-allowed disabled:opacity-60',
                  selected && 'border-accent bg-accent-muted/45',
                )}
                disabled={item.disabled}
                type="button"
                aria-pressed={selected}
                onClick={() => onToggle(item.id)}
              >
                {item.leading ? <span className="shrink-0">{item.leading}</span> : null}
                <span className="min-w-0 flex-1 space-y-1">
                  <span className="block font-semibold text-text-primary">{item.title}</span>
                  {item.description ? (
                    <span className="block text-sm leading-6 text-text-secondary">{item.description}</span>
                  ) : null}
                  {item.meta ? <span className="block text-xs font-medium text-text-muted">{item.meta}</span> : null}
                </span>
                <span
                  className={cn(
                    'mt-1 inline-flex h-[18px] w-[18px] shrink-0 items-center justify-center border border-border-strong bg-surface',
                    multiple ? 'rounded-[5px]' : 'rounded-full',
                    selected && 'border-accent bg-accent',
                  )}
                  aria-hidden="true"
                >
                  {selected ? <span className="h-2 w-2 rounded-full bg-white" /> : null}
                </span>
              </button>
            )
          })}
        </div>
      )}

      {showPagination ? (
        <div className="flex items-center justify-between gap-3">
          <Button
            disabled={page <= 0}
            variant="secondary"
            onClick={() => onPageChange(page - 1)}
          >
            {t('common.actions.previous')}
          </Button>
          <span className="text-sm font-medium text-text-secondary">
            {page + 1} / {totalPages}
          </span>
          <Button
            disabled={page >= totalPages - 1}
            variant="secondary"
            onClick={() => onPageChange(page + 1)}
          >
            {t('common.actions.next')}
          </Button>
        </div>
      ) : null}
    </div>
  )

  if (!label) {
    return content
  }

  return (
    <FormField label={label}>
      {content}
    </FormField>
  )
}
