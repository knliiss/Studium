import { useTranslation } from 'react-i18next'

import type { BulkAvailabilityFormState } from '@/features/assignments/access/availabilityUtils'
import type { EntityOption } from '@/shared/ui/EntityPicker'
import { Button } from '@/shared/ui/Button'
import { EntityPicker } from '@/shared/ui/EntityPicker'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { SegmentedControl } from '@/shared/ui/SegmentedControl'

export function BulkAvailabilityToolbar({
  connectedGroupsCount,
  copyDisabledReason,
  form,
  groups,
  isPending,
  onApplySame,
  onCopyFromGroup,
  onHideAll,
  onPublishAll,
  onUpdateForm,
  publishDisabledReason,
}: {
  connectedGroupsCount: number
  copyDisabledReason: string
  form: BulkAvailabilityFormState
  groups: EntityOption[]
  isPending: boolean
  onApplySame: () => void
  onCopyFromGroup: () => void
  onHideAll: () => void
  onPublishAll: () => void
  onUpdateForm: (updater: (current: BulkAvailabilityFormState) => BulkAvailabilityFormState) => void
  publishDisabledReason: string
}) {
  const { t } = useTranslation()

  return (
    <div className="space-y-4 rounded-[16px] border border-border bg-surface-muted p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="text-base font-semibold text-text-primary">{t('availability.bulkTitle')}</h3>
          <p className="mt-1 text-sm leading-6 text-text-secondary">{t('availability.bulkDescription')}</p>
        </div>
        <span className="rounded-full border border-border px-2.5 py-1 text-xs font-semibold text-text-secondary">
          {t('availability.connectedGroupsCount', { count: connectedGroupsCount })}
        </span>
      </div>
      <div className="grid gap-4 xl:grid-cols-3">
        <FormField label={t('common.labels.status')}>
          <SegmentedControl
            ariaLabel={t('availability.title')}
            value={form.visible ? 'visible' : 'hidden'}
            options={[
              { value: 'hidden', label: t('availability.hidden') },
              { value: 'visible', label: t('availability.visible') },
            ]}
            onChange={(value) => onUpdateForm((current) => ({ ...current, visible: value === 'visible' }))}
          />
        </FormField>
        <FormField label={t('availability.availableFrom')}>
          <Input
            type="datetime-local"
            value={form.availableFrom}
            onChange={(event) => onUpdateForm((current) => ({ ...current, availableFrom: event.target.value }))}
          />
        </FormField>
        <FormField label={t('common.labels.deadline')}>
          <Input
            type="datetime-local"
            value={form.deadline}
            onChange={(event) => onUpdateForm((current) => ({ ...current, deadline: event.target.value }))}
          />
        </FormField>
        <FormField label={t('assignments.maxSubmissions')}>
          <Input
            min={1}
            type="number"
            value={form.maxSubmissions}
            onChange={(event) => onUpdateForm((current) => ({ ...current, maxSubmissions: Number(event.target.value) }))}
          />
        </FormField>
        <FormField label={t('assignments.allowLateSubmissions')}>
          <Input
            type="checkbox"
            checked={form.allowLateSubmissions}
            onChange={(event) => onUpdateForm((current) => ({ ...current, allowLateSubmissions: event.target.checked }))}
          />
        </FormField>
        <FormField label={t('assignments.allowResubmit')}>
          <Input
            type="checkbox"
            checked={form.allowResubmit}
            onChange={(event) => onUpdateForm((current) => ({ ...current, allowResubmit: event.target.checked }))}
          />
        </FormField>
      </div>
      {publishDisabledReason ? (
        <p className="text-sm font-semibold text-text-secondary">
          {t('availability.bulkUnavailable', { reason: publishDisabledReason })}
        </p>
      ) : null}
      <div className="flex flex-wrap gap-3">
        <Button disabled={Boolean(publishDisabledReason) || isPending} variant="secondary" onClick={onPublishAll}>
          {t('availability.publishAllGroups')}
        </Button>
        <Button disabled={Boolean(publishDisabledReason) || isPending} variant="secondary" onClick={onHideAll}>
          {t('availability.hideAllGroups')}
        </Button>
        <Button disabled={Boolean(publishDisabledReason) || isPending} onClick={onApplySame}>
          {t('availability.applySameToAll')}
        </Button>
      </div>
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_auto]">
        <EntityPicker
          label={t('availability.copyFromGroup')}
          value={form.copyFromGroupId}
          options={groups}
          placeholder={t('availability.selectGroup')}
          emptyLabel={t('availability.noConnectedGroups')}
          onChange={(value) => onUpdateForm((current) => ({ ...current, copyFromGroupId: value }))}
        />
        <div className="flex items-end">
          <Button
            disabled={Boolean(copyDisabledReason) || isPending}
            variant="secondary"
            onClick={onCopyFromGroup}
          >
            {t('availability.copyAvailability')}
          </Button>
        </div>
      </div>
      {copyDisabledReason ? (
        <p className="text-sm font-semibold text-text-secondary">
          {t('availability.copyUnavailable', { reason: copyDisabledReason })}
        </p>
      ) : null}
    </div>
  )
}
