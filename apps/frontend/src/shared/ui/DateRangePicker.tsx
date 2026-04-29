import { useTranslation } from 'react-i18next'

import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'

export function DateRangePicker({
  dateFrom,
  dateTo,
  onChange,
}: {
  dateFrom: string
  dateTo: string
  onChange: (next: { dateFrom: string; dateTo: string }) => void
}) {
  const { t } = useTranslation()

  return (
    <div className="grid gap-4 md:grid-cols-2">
      <FormField label={t('common.labels.dateFrom')}>
        <Input
          type="date"
          value={dateFrom}
          onChange={(event) => onChange({ dateFrom: event.target.value, dateTo })}
        />
      </FormField>
      <FormField label={t('common.labels.dateTo')}>
        <Input
          type="date"
          value={dateTo}
          onChange={(event) => onChange({ dateFrom, dateTo: event.target.value })}
        />
      </FormField>
    </div>
  )
}
