import { useDroppable } from '@dnd-kit/core'
import { ArrowRight, Copy, Plus } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import type { DragMoveTargetState } from '@/features/schedule/lib/moveTarget'
import { cn } from '@/shared/lib/cn'
import type { LessonSlotResponse } from '@/shared/types/api'
import {
  resolveWeekDraftSections,
  type DraftSlotLessonLike,
} from '@/pages/schedule/components/editPairDropZone.helpers'
import { SubgroupLanes } from '@/pages/schedule/components/SubgroupLanes'

export interface PairLike {
  pairNumber: number
  startTime: string
  endTime: string
}

interface EditPairDropZoneProps<TDay, TDraft extends DraftSlotLessonLike> {
  copiedLessonId: string | null
  dayOfWeek: TDay
  pair: PairLike
  pairDrafts: TDraft[]
  selectedWeekType: 'ODD' | 'EVEN'
  movingLessonId: string | null
  hoverDropTargetKey: string | null
  slot: LessonSlotResponse | undefined
  resolveMoveTargetState: (targetDay: TDay, pairNumber: number, sourceDraftId?: string | null) => DragMoveTargetState
  renderDraftCards: (items: TDraft[], density: 'normal' | 'lane') => ReactNode
  onPasteLesson: (dayOfWeek: TDay, pairNumber: number) => void
  onUseMoveTarget: (dayOfWeek: TDay, pairNumber: number, source: 'action' | 'drag', sourceDraftId?: string | null) => void
  onAddLesson: (pairNumber: number, weekType: 'ODD' | 'EVEN', subgroupChoice: 'FIRST' | 'SECOND') => void
  getDragMoveBlockedMessage: (reason: DragMoveTargetState['reason']) => string
}

export function EditPairDropZone<TDay extends string, TDraft extends DraftSlotLessonLike>({
  copiedLessonId,
  dayOfWeek,
  pair,
  pairDrafts,
  selectedWeekType,
  movingLessonId,
  hoverDropTargetKey,
  slot,
  resolveMoveTargetState,
  renderDraftCards,
  onPasteLesson,
  onUseMoveTarget,
  onAddLesson,
  getDragMoveBlockedMessage,
}: EditPairDropZoneProps<TDay, TDraft>) {
  const { t } = useTranslation()
  const dropTargetId = `schedule-drop:${String(dayOfWeek)}:${pair.pairNumber}`
  const { isOver, setNodeRef } = useDroppable({
    id: dropTargetId,
    data: {
      dayOfWeek,
      pairNumber: pair.pairNumber,
    },
    disabled: !slot,
  })

  const dropActive = Boolean(movingLessonId)
  const moveTargetState = resolveMoveTargetState(dayOfWeek, pair.pairNumber)
  const validDropTarget = moveTargetState.valid
  const invalidDropTarget = dropActive && !validDropTarget && moveTargetState.reason !== 'NO_SOURCE'
  const isHoverDropTarget = hoverDropTargetKey === dropTargetId || (isOver && validDropTarget)

  const weekSection = resolveWeekDraftSections(pairDrafts, selectedWeekType)
  const showFullGroup = weekSection.sharedDrafts.length > 0 && !weekSection.hasAnySubgroupDrafts
  const showSubgroups = weekSection.hasAnySubgroupDrafts
  const showEmpty = !showFullGroup && !showSubgroups
  const unavailable = !slot

  const addButton = (subgroup: 'FIRST' | 'SECOND', compact = false) => (
    <button
      className={cn(
        'inline-flex items-center justify-center rounded-[6px] border border-dashed border-border-strong bg-surface text-text-secondary transition hover:border-accent/60 hover:bg-accent-muted/20 hover:text-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/25',
        compact ? 'h-7 w-7' : 'h-7 gap-1.5 px-2 text-[11px] font-semibold',
      )}
      disabled={unavailable}
      title={unavailable ? t('schedule.slotSetupError') : t('schedule.addLessonHere')}
      type="button"
      onClick={() => onAddLesson(pair.pairNumber, selectedWeekType, subgroup)}
    >
      <Plus className="h-3.5 w-3.5" />
      {compact ? <span className="sr-only">{t('schedule.addLessonHere')}</span> : <span>{t('common.actions.add')}</span>}
    </button>
  )

  const emptyLane = (subgroup: 'FIRST' | 'SECOND') => (
    <button
      className="flex h-full min-h-0 w-full items-center justify-center rounded-[7px] border border-dashed border-border bg-surface-muted/60 px-1 text-[11px] font-semibold text-text-muted transition hover:border-accent/60 hover:bg-accent-muted/20 hover:text-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/25"
      disabled={unavailable}
      title={unavailable ? t('schedule.slotSetupError') : t('schedule.addLessonHere')}
      type="button"
      onClick={() => onAddLesson(pair.pairNumber, selectedWeekType, subgroup)}
    >
      <Plus className="mr-1 h-3.5 w-3.5" />
      <span className="truncate">{t(`schedule.subgroupChoice.${subgroup}`)}</span>
    </button>
  )

  const pasteButton = copiedLessonId && slot ? (
    <button
      className="inline-flex h-7 w-7 items-center justify-center rounded-[6px] border border-border bg-surface text-text-secondary shadow-sm transition hover:border-accent/50 hover:text-accent"
      title={t('schedule.pasteLesson')}
      type="button"
      onClick={() => onPasteLesson(dayOfWeek, pair.pairNumber)}
    >
      <Copy className="h-3.5 w-3.5" />
      <span className="sr-only">{t('schedule.pasteLesson')}</span>
    </button>
  ) : null

  const moveButton = movingLessonId && slot ? (
    <button
      className={cn(
        'inline-flex h-7 w-7 items-center justify-center rounded-[6px] border bg-surface shadow-sm transition',
        validDropTarget ? 'border-accent/50 text-accent hover:bg-accent-muted/40' : 'border-border text-text-muted opacity-60',
      )}
      disabled={!validDropTarget}
      title={!validDropTarget && moveTargetState.reason ? getDragMoveBlockedMessage(moveTargetState.reason) : t('schedule.moveHere')}
      type="button"
      onClick={() => onUseMoveTarget(dayOfWeek, pair.pairNumber, 'action')}
    >
      <ArrowRight className="h-3.5 w-3.5" />
      <span className="sr-only">{t('schedule.moveHere')}</span>
    </button>
  ) : null

  return (
    <div
      ref={setNodeRef}
      className={cn(
        'relative h-full min-h-0 overflow-hidden rounded-[8px] border bg-surface p-1.5 transition',
        dropActive && validDropTarget ? 'border-accent/60 bg-accent-muted/15' : 'border-border',
        dropActive && validDropTarget && isHoverDropTarget ? 'border-success/50 bg-success/10 ring-2 ring-success/25' : '',
        invalidDropTarget ? 'border-border-strong bg-surface-muted opacity-75' : '',
      )}
    >
      <div className="absolute right-1 top-1 z-10 flex gap-1">
        {pasteButton}
        {moveButton}
      </div>
      {!slot ? (
        <div className="flex h-full items-center justify-center rounded-[7px] border border-dashed border-danger/40 bg-danger/5 px-2 text-center text-xs font-semibold text-danger">
          {t('schedule.slotSetupError')}
        </div>
      ) : null}
      {slot && showFullGroup ? (
        <div className="h-full min-h-0">
          {renderDraftCards(weekSection.sharedDrafts, 'normal')}
        </div>
      ) : null}
      {slot && showSubgroups ? (
        <SubgroupLanes
          lanes={[
            {
              action: weekSection.showFirstAdd ? addButton('FIRST', true) : null,
              content: weekSection.firstDrafts.length > 0 ? renderDraftCards(weekSection.firstDrafts, 'lane') : emptyLane('FIRST'),
              key: 'FIRST',
              label: t('schedule.subgroupChoice.FIRST'),
            },
            {
              action: weekSection.showSecondAdd ? addButton('SECOND', true) : null,
              content: weekSection.secondDrafts.length > 0 ? renderDraftCards(weekSection.secondDrafts, 'lane') : emptyLane('SECOND'),
              key: 'SECOND',
              label: t('schedule.subgroupChoice.SECOND'),
            },
          ]}
        />
      ) : null}
      {slot && showEmpty ? (
        <div className="flex h-full min-h-0 items-center justify-center rounded-[7px] border border-dashed border-border bg-surface-muted/50 transition hover:border-accent/50">
          {addButton('FIRST')}
        </div>
      ) : null}
    </div>
  )
}
