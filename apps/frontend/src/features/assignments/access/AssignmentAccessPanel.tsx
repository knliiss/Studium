import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { AssignmentGroupAvailabilityCard } from '@/features/assignments/access/AssignmentGroupAvailabilityCard'
import { BulkAvailabilityToolbar } from '@/features/assignments/access/BulkAvailabilityToolbar'
import type {
  AvailabilityFormState,
  AvailabilityMutationPayload,
  BulkAvailabilityFormState,
} from '@/features/assignments/access/availabilityUtils'
import {
  createAvailabilityFormState,
  createAvailabilityPayload,
  createBulkAvailabilityPayload,
} from '@/features/assignments/access/availabilityUtils'
import { getLocalizedRequestErrorMessage, normalizeApiError } from '@/shared/lib/api-errors'
import type { AssignmentGroupAvailabilityResponse, AssignmentResponse } from '@/shared/types/api'
import { Button } from '@/shared/ui/Button'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import type { EntityOption } from '@/shared/ui/EntityPicker'
import { EntityPicker } from '@/shared/ui/EntityPicker'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { SegmentedControl } from '@/shared/ui/SegmentedControl'
import { EmptyState } from '@/shared/ui/StateViews'

interface AssignmentAvailabilityGroupCard {
  group: { id: string; name: string }
  availability: AssignmentGroupAvailabilityResponse | null
}

interface FeedbackState {
  message: string
  tone: 'success' | 'error'
  requestId?: string
}

interface BulkConfirmState {
  affected: number
  items: AvailabilityMutationPayload[]
  title: string
}

export function AssignmentAccessPanel({
  assignment,
  availabilityGroupCards,
  availabilityRows,
  groupOptions,
  connectedGroupOptions,
  connectedGroups,
  isTeacher,
  isBulkSaving,
  isSaving,
  searchValue,
  onSearchChange,
  onUpsertAvailability,
  onBulkUpsertAvailability,
}: {
  assignment: AssignmentResponse
  availabilityGroupCards: AssignmentAvailabilityGroupCard[]
  availabilityRows: AssignmentGroupAvailabilityResponse[]
  groupOptions: EntityOption[]
  connectedGroupOptions: EntityOption[]
  connectedGroups: Array<{ id: string; name: string }>
  isTeacher: boolean
  isBulkSaving: boolean
  isSaving: boolean
  searchValue: string
  onSearchChange: (value: string) => void
  onUpsertAvailability: (payload: AvailabilityMutationPayload) => Promise<unknown>
  onBulkUpsertAvailability: (items: AvailabilityMutationPayload[]) => Promise<unknown>
}) {
  const { t } = useTranslation()
  const [feedback, setFeedback] = useState<FeedbackState | null>(null)
  const [confirmState, setConfirmState] = useState<BulkConfirmState | null>(null)
  const [availabilityForm, setAvailabilityForm] = useState<AvailabilityFormState>({
    groupId: '',
    visible: false,
    availableFrom: '',
    deadline: '',
    allowLateSubmissions: false,
    maxSubmissions: 1,
    allowResubmit: false,
  })
  const [bulkAvailabilityForm, setBulkAvailabilityForm] = useState<BulkAvailabilityFormState>({
    visible: true,
    availableFrom: '',
    deadline: '',
    allowLateSubmissions: false,
    maxSubmissions: 1,
    allowResubmit: false,
    copyFromGroupId: '',
  })
  const availabilityByGroupId = useMemo(
    () => new Map(availabilityRows.map((availability) => [availability.groupId, availability])),
    [availabilityRows],
  )
  const connectedGroupsCount = connectedGroups.length
  const hasConnectedGroups = connectedGroupsCount > 0
  const availabilitySaveDisabledReason = !availabilityForm.groupId
    ? t('availability.selectGroupReason')
    : !availabilityForm.deadline
      ? t('availability.selectDeadlineReason')
      : ''
  const bulkCommonDisabledReason = !hasConnectedGroups
    ? t('availability.noConnectedGroupsReason')
    : !bulkAvailabilityForm.deadline
      ? t('availability.selectDeadlineReason')
      : ''
  const copyDisabledReason = !hasConnectedGroups
    ? t('availability.noConnectedGroupsReason')
    : !bulkAvailabilityForm.copyFromGroupId
      ? t('availability.selectSourceGroupReason')
      : ''

  const saveSingleAvailability = async () => {
    if (availabilitySaveDisabledReason) {
      return
    }
    setFeedback(null)
    try {
      await onUpsertAvailability(createAvailabilityPayload(availabilityForm))
      setFeedback({
        tone: 'success',
        message: t('availability.saveSuccess'),
      })
    } catch (error) {
      const normalizedError = normalizeApiError(error)
      setFeedback({
        tone: 'error',
        message: getLocalizedRequestErrorMessage(error, t),
        requestId: normalizedError?.requestId,
      })
    }
  }

  const submitBulkUpdate = async (items: AvailabilityMutationPayload[]) => {
    setFeedback(null)
    try {
      await onBulkUpsertAvailability(items)
      setFeedback({
        tone: 'success',
        message: t('availability.bulkSuccess'),
      })
    } catch (error) {
      const normalizedError = normalizeApiError(error)
      setFeedback({
        tone: 'error',
        message: getLocalizedRequestErrorMessage(error, t),
        requestId: normalizedError?.requestId,
      })
    }
  }

  const confirmBulkUpdateIfNeeded = (title: string, items: AvailabilityMutationPayload[]) => {
    if (items.length === 0) {
      return
    }
    if (items.length >= 5) {
      setConfirmState({ title, items, affected: items.length })
      return
    }
    void submitBulkUpdate(items)
  }

  const handlePublishAll = () => {
    if (bulkCommonDisabledReason) {
      return
    }
    const items = connectedGroups.map((group) =>
      createBulkAvailabilityPayload(group.id, bulkAvailabilityForm, assignment.deadline, true))
    confirmBulkUpdateIfNeeded(t('availability.publishAllGroups'), items)
  }

  const handleHideAll = () => {
    if (bulkCommonDisabledReason) {
      return
    }
    const items = connectedGroups.map((group) =>
      createBulkAvailabilityPayload(group.id, bulkAvailabilityForm, assignment.deadline, false))
    confirmBulkUpdateIfNeeded(t('availability.hideAllGroups'), items)
  }

  const handleApplySame = () => {
    if (bulkCommonDisabledReason) {
      return
    }
    const items = connectedGroups.map((group) =>
      createBulkAvailabilityPayload(group.id, bulkAvailabilityForm, assignment.deadline))
    confirmBulkUpdateIfNeeded(t('availability.applySameToAll'), items)
  }

  const handleCopyFromGroup = () => {
    if (copyDisabledReason) {
      return
    }
    const source = availabilityRows.find((availability) => availability.groupId === bulkAvailabilityForm.copyFromGroupId)
    if (!source) {
      setFeedback({
        tone: 'error',
        message: t('availability.sourceAvailabilityMissing'),
      })
      return
    }
    const items = connectedGroups
      .filter((group) => group.id !== source.groupId)
      .map((group) => ({
        groupId: group.id,
        visible: source.visible,
        availableFrom: source.availableFrom,
        deadline: source.deadline,
        allowLateSubmissions: source.allowLateSubmissions,
        maxSubmissions: source.maxSubmissions,
        allowResubmit: source.allowResubmit,
      }))
    confirmBulkUpdateIfNeeded(t('availability.copyAvailability'), items)
  }

  return (
    <div className="space-y-4">
      <PageHeader description={t('availability.assignmentDescription')} title={t('availability.title')} />
      {availabilityGroupCards.length === 0 ? (
        <EmptyState description={t('availability.noConnectedGroups')} title={t('availability.title')} />
      ) : (
        <div className="grid gap-3 md:grid-cols-2">
          {availabilityGroupCards.map(({ availability, group }) => (
            <AssignmentGroupAvailabilityCard
              key={group.id}
              assignment={assignment}
              availability={availability}
              groupName={group.name}
              onEdit={() => {
                setAvailabilityForm(createAvailabilityFormState(group.id, assignment, availability))
                setFeedback(null)
              }}
            />
          ))}
        </div>
      )}

      <BulkAvailabilityToolbar
        connectedGroupsCount={connectedGroupsCount}
        copyDisabledReason={copyDisabledReason}
        form={bulkAvailabilityForm}
        groups={connectedGroupOptions}
        isPending={isBulkSaving}
        publishDisabledReason={bulkCommonDisabledReason}
        onApplySame={handleApplySame}
        onCopyFromGroup={handleCopyFromGroup}
        onHideAll={handleHideAll}
        onPublishAll={handlePublishAll}
        onUpdateForm={(updater) => setBulkAvailabilityForm((current) => updater(current))}
      />

      <div className="grid gap-4 xl:grid-cols-3">
        <EntityPicker
          label={t('navigation.shared.groups')}
          value={availabilityForm.groupId}
          options={groupOptions}
          placeholder={t('availability.selectGroup')}
          emptyLabel={t('education.noGroups')}
          searchLabel={t('common.actions.search')}
          searchPlaceholder={t('availability.groupSearchPlaceholder')}
          searchValue={searchValue}
          onChange={(value) => {
            const sourceAvailability = availabilityByGroupId.get(value) ?? null
            setAvailabilityForm(createAvailabilityFormState(value, assignment, sourceAvailability))
            setFeedback(null)
          }}
          onSearchChange={isTeacher ? undefined : onSearchChange}
        />
        <FormField label={t('common.labels.status')}>
          <SegmentedControl
            ariaLabel={t('availability.title')}
            value={availabilityForm.visible ? 'visible' : 'hidden'}
            options={[
              { value: 'hidden', label: t('availability.hidden') },
              { value: 'visible', label: t('availability.visible') },
            ]}
            onChange={(value) => setAvailabilityForm((current) => ({ ...current, visible: value === 'visible' }))}
          />
        </FormField>
        <FormField label={t('availability.availableFrom')}>
          <Input
            type="datetime-local"
            value={availabilityForm.availableFrom}
            onChange={(event) => setAvailabilityForm((current) => ({ ...current, availableFrom: event.target.value }))}
          />
        </FormField>
        <FormField label={t('common.labels.deadline')}>
          <Input
            type="datetime-local"
            value={availabilityForm.deadline}
            onChange={(event) => setAvailabilityForm((current) => ({ ...current, deadline: event.target.value }))}
          />
        </FormField>
        <FormField label={t('assignments.maxSubmissions')}>
          <Input
            min={1}
            type="number"
            value={availabilityForm.maxSubmissions}
            onChange={(event) => setAvailabilityForm((current) => ({ ...current, maxSubmissions: Number(event.target.value) }))}
          />
        </FormField>
        <FormField label={t('assignments.allowLateSubmissions')}>
          <Input
            type="checkbox"
            checked={availabilityForm.allowLateSubmissions}
            onChange={(event) => setAvailabilityForm((current) => ({ ...current, allowLateSubmissions: event.target.checked }))}
          />
        </FormField>
        <FormField label={t('assignments.allowResubmit')}>
          <Input
            type="checkbox"
            checked={availabilityForm.allowResubmit}
            onChange={(event) => setAvailabilityForm((current) => ({ ...current, allowResubmit: event.target.checked }))}
          />
        </FormField>
      </div>
      {availabilitySaveDisabledReason ? (
        <p className="text-sm font-semibold text-text-secondary">
          {t('availability.saveUnavailable', { reason: availabilitySaveDisabledReason })}
        </p>
      ) : null}
      <Button disabled={Boolean(availabilitySaveDisabledReason) || isSaving} onClick={() => void saveSingleAvailability()}>
        {t('common.actions.save')}
      </Button>

      {feedback ? (
        <div className={`rounded-[14px] border p-3 text-sm ${feedback.tone === 'success' ? 'border-success/30 bg-success/10 text-success' : 'border-danger/30 bg-danger/10 text-danger'}`}>
          <p className="font-semibold">{feedback.message}</p>
          {feedback.requestId ? (
            <p className="mt-1 text-xs opacity-80">{t('common.labels.requestId')}: {feedback.requestId}</p>
          ) : null}
        </div>
      ) : null}

      <ConfirmDialog
        description={confirmState
          ? t('availability.bulkConfirmDescription', { count: confirmState.affected })
          : ''}
        open={Boolean(confirmState)}
        title={confirmState?.title ?? t('availability.bulkTitle')}
        onCancel={() => setConfirmState(null)}
        onConfirm={() => {
          if (!confirmState) {
            return
          }
          const { items } = confirmState
          setConfirmState(null)
          void submitBulkUpdate(items)
        }}
      />
    </div>
  )
}
