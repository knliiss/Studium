import { useTranslation } from 'react-i18next'

import { Button } from '@/shared/ui/Button'

export function ConfirmDialog({
  cancelLabel,
  confirmLabel,
  description,
  open,
  onCancel,
  onConfirm,
  title,
}: {
  cancelLabel?: string
  confirmLabel?: string
  description: string
  open: boolean
  onCancel: () => void
  onConfirm: () => void
  title: string
}) {
  const { t } = useTranslation()

  if (!open) {
    return null
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-overlay px-4">
      <div className="w-full max-w-lg rounded-[24px] border border-border bg-surface p-6 shadow-[var(--shadow-soft)]">
        <div className="space-y-2">
          <h2 className="text-2xl font-bold tracking-[-0.04em] text-text-primary">{title}</h2>
          <p className="text-sm leading-6 text-text-secondary">{description}</p>
        </div>
        <div className="mt-6 flex justify-end gap-3">
          <Button variant="secondary" onClick={onCancel}>
            {cancelLabel ?? t('common.actions.cancel')}
          </Button>
          <Button onClick={onConfirm}>{confirmLabel ?? t('common.actions.confirm')}</Button>
        </div>
      </div>
    </div>
  )
}
