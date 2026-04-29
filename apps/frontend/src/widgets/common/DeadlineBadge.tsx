import { formatDateTime } from '@/shared/lib/format'

export function DeadlineBadge({ deadline }: { deadline: string | null | undefined }) {
  return (
    <span className="inline-flex rounded-full bg-warning/10 px-2.5 py-1 text-xs font-semibold text-warning">
      {formatDateTime(deadline)}
    </span>
  )
}
