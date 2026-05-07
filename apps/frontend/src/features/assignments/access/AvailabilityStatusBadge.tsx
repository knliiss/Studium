import { cn } from '@/shared/lib/cn'
import type { AvailabilityStatus } from '@/features/assignments/access/availabilityUtils'

const toneByStatus: Record<AvailabilityStatus, string> = {
  HIDDEN: 'bg-text-muted/15 text-text-muted',
  OPENS_LATER: 'bg-warning/10 text-warning',
  OPEN: 'bg-success/10 text-success',
  CLOSED: 'bg-text-muted/15 text-text-secondary',
  DEADLINE_PASSED: 'bg-danger/10 text-danger',
}

export function AvailabilityStatusBadge({
  label,
  status,
}: {
  label: string
  status: AvailabilityStatus
}) {
  return (
    <span className={cn('rounded-full px-2.5 py-1 text-xs font-semibold', toneByStatus[status])}>
      {label}
    </span>
  )
}
