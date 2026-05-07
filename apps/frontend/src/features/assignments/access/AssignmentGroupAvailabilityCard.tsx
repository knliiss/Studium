import { useTranslation } from 'react-i18next'

import { AvailabilityStatusBadge } from '@/features/assignments/access/AvailabilityStatusBadge'
import { getAvailabilityStatusLabel, resolveAvailabilityStatus } from '@/features/assignments/access/availabilityUtils'
import { formatDateTime } from '@/shared/lib/format'
import type { AssignmentGroupAvailabilityResponse, AssignmentResponse } from '@/shared/types/api'

export function AssignmentGroupAvailabilityCard({
  assignment,
  availability,
  groupName,
  onEdit,
}: {
  assignment: AssignmentResponse
  availability: AssignmentGroupAvailabilityResponse | null
  groupName: string
  onEdit: () => void
}) {
  const { t } = useTranslation()
  const status = resolveAvailabilityStatus(assignment.status, availability)
  const availabilityLabel = availability?.availableFrom
    ? formatDateTime(availability.availableFrom)
    : t('availability.immediately')
  const deadlineLabel = formatDateTime(availability?.deadline ?? assignment.deadline)

  return (
    <button
      className="rounded-[14px] border border-border bg-surface-muted p-4 text-left transition hover:border-border-strong focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-accent/15"
      type="button"
      onClick={onEdit}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="space-y-1">
          <p className="font-semibold text-text-primary">{groupName}</p>
          <p className="text-sm text-text-secondary">
            {t('availability.availableFrom')}: {availabilityLabel}
          </p>
          <p className="text-sm text-text-secondary">
            {t('common.labels.deadline')}: {deadlineLabel}
          </p>
          <p className="text-sm text-text-secondary">
            {t('assignments.maxSubmissions')}: {availability?.maxSubmissions ?? assignment.maxSubmissions}
          </p>
        </div>
        <AvailabilityStatusBadge label={getAvailabilityStatusLabel(status, t)} status={status} />
      </div>
    </button>
  )
}
