import { ArrowRight, CircleAlert, Copy, Pencil, RotateCcw, Trash2 } from 'lucide-react'

import { cn } from '@/shared/lib/cn'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'

interface LessonDetailsPanelProps {
  title: string
  emptyText: string
  emptyHints: string[]
  subject: string
  lessonType: string
  details: Array<{ label: string; value: string }>
  conflict: {
    hints: string[]
    messages: string[]
    status: 'idle' | 'checking' | 'clear' | 'conflict' | 'error'
    statusLabel: string
  } | null
  deleted: boolean
  hasSelection: boolean
  moving: boolean
  onEdit: () => void
  onMove: () => void
  onCopy: () => void
  onDelete: () => void
  onRestore: () => void
  onStopMove: () => void
  actions: {
    copy: string
    delete: string
    edit: string
    move: string
    restore: string
    stopMoving: string
  }
}

export function LessonDetailsPanel({
  title,
  emptyText,
  emptyHints,
  subject,
  lessonType,
  details,
  conflict,
  deleted,
  hasSelection,
  moving,
  onEdit,
  onMove,
  onCopy,
  onDelete,
  onRestore,
  onStopMove,
  actions,
}: LessonDetailsPanelProps) {
  const conflictTone = conflict?.status === 'conflict'
    ? 'border-danger/30 bg-danger/5 text-danger'
    : conflict?.status === 'error'
      ? 'border-warning/35 bg-warning/10 text-warning'
      : conflict?.status === 'clear'
        ? 'border-success/30 bg-success/5 text-success'
        : 'border-border bg-surface-muted text-text-secondary'

  return (
    <Card className="h-fit w-full space-y-4 rounded-[12px] border border-border bg-surface p-4 xl:sticky xl:top-4 xl:max-h-[calc(100vh-2rem)] xl:w-[380px] xl:overflow-y-auto">
      {hasSelection ? (
        <>
          <div className="space-y-2 border-b border-border pb-3">
            <span className="inline-flex rounded-full border border-accent/30 bg-accent-muted/40 px-2.5 py-1 text-xs font-semibold text-accent">
              {lessonType}
            </span>
            <h3 className="break-words text-base font-semibold leading-6 text-text-primary">{subject}</h3>
          </div>
          <div className="space-y-2.5">
            {details.map((detail) => (
              <div key={detail.label} className="grid grid-cols-[104px_minmax(0,1fr)] gap-3 text-sm">
                <span className="text-text-muted">{detail.label}</span>
                <span className="min-w-0 break-words text-right text-text-primary">{detail.value}</span>
              </div>
            ))}
          </div>
          {conflict ? (
            <div className={cn('space-y-2 rounded-[10px] border px-3 py-2.5', conflictTone)}>
              <div className="flex items-center gap-2 text-sm font-semibold">
                <CircleAlert className="h-4 w-4 shrink-0" />
                <span>{conflict.statusLabel}</span>
              </div>
              {conflict.messages.map((message) => (
                <p key={message} className="text-xs leading-5 text-text-secondary">{message}</p>
              ))}
              {conflict.hints.map((hint) => (
                <p key={hint} className="text-xs leading-5 text-text-secondary">{hint}</p>
              ))}
            </div>
          ) : null}
          <div className="grid gap-2">
            {deleted ? (
              <Button className="min-h-10 rounded-[10px]" variant="secondary" onClick={onRestore}>
                <RotateCcw className="mr-2 h-4 w-4" />
                {actions.restore}
              </Button>
            ) : (
              <>
                <Button className="min-h-10 rounded-[10px]" variant="secondary" onClick={onEdit}>
                  <Pencil className="mr-2 h-4 w-4" />
                  {actions.edit}
                </Button>
                <div className="grid grid-cols-2 gap-2">
                  <Button className="min-h-10 rounded-[10px] px-3" variant="secondary" onClick={moving ? onStopMove : onMove}>
                    {moving ? <RotateCcw className="mr-2 h-4 w-4" /> : <ArrowRight className="mr-2 h-4 w-4" />}
                    {moving ? actions.stopMoving : actions.move}
                  </Button>
                  <Button className="min-h-10 rounded-[10px] px-3" variant="secondary" onClick={onCopy}>
                    <Copy className="mr-2 h-4 w-4" />
                    {actions.copy}
                  </Button>
                </div>
                <Button className="min-h-10 rounded-[10px]" variant="danger" onClick={onDelete}>
                  <Trash2 className="mr-2 h-4 w-4" />
                  {actions.delete}
                </Button>
              </>
            )}
          </div>
        </>
      ) : (
        <div className="space-y-3">
          <div className="space-y-1">
            <h3 className="text-base font-semibold text-text-primary">{title}</h3>
            <p className="text-sm leading-6 text-text-secondary">{emptyText}</p>
          </div>
          <div className="space-y-2 rounded-[10px] border border-border bg-surface-muted px-3 py-3">
            {emptyHints.map((hint) => (
              <p key={hint} className="text-xs leading-5 text-text-secondary">{hint}</p>
            ))}
          </div>
        </div>
      )}
    </Card>
  )
}
