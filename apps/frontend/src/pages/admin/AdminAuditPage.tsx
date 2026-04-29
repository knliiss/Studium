import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { auditService } from '@/shared/api/services'
import { getAuditActionLabel, getAuditEntityTypeLabel } from '@/shared/lib/enum-labels'
import { formatDateTime } from '@/shared/lib/format'
import { Card } from '@/shared/ui/Card'
import { DataTable } from '@/shared/ui/DataTable'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { ErrorState, LoadingState } from '@/shared/ui/StateViews'

export function AdminAuditPage() {
  const { t } = useTranslation()
  const [filters, setFilters] = useState({
    actorId: '',
    entityType: '',
    entityId: '',
    dateFrom: '',
    dateTo: '',
  })
  const auditQuery = useQuery({
    queryKey: ['audit', filters],
    queryFn: () =>
      auditService.list({
        ...filters,
        actorId: filters.actorId || undefined,
        entityId: filters.entityId || undefined,
        entityType: filters.entityType || undefined,
        dateFrom: filters.dateFrom || undefined,
        dateTo: filters.dateTo || undefined,
      }),
  })

  if (auditQuery.isLoading) {
    return <LoadingState />
  }

  if (auditQuery.isError || !auditQuery.data) {
    return <ErrorState title={t('navigation.admin.audit')} description={t('common.states.error')} />
  }

  return (
    <div className="space-y-6">
      <PageHeader description={t('audit.description')} title={t('navigation.admin.audit')} />
      <Card className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
        <FormField label={t('audit.actorUserId')}>
          <Input value={filters.actorId} onChange={(event) => setFilters((current) => ({ ...current, actorId: event.target.value }))} />
        </FormField>
        <FormField label={t('audit.entityType')}>
          <Input value={filters.entityType} onChange={(event) => setFilters((current) => ({ ...current, entityType: event.target.value }))} />
        </FormField>
        <FormField label={t('audit.entityId')}>
          <Input value={filters.entityId} onChange={(event) => setFilters((current) => ({ ...current, entityId: event.target.value }))} />
        </FormField>
        <FormField label={t('common.labels.dateFrom')}>
          <Input type="date" value={filters.dateFrom} onChange={(event) => setFilters((current) => ({ ...current, dateFrom: event.target.value }))} />
        </FormField>
        <FormField label={t('common.labels.dateTo')}>
          <Input type="date" value={filters.dateTo} onChange={(event) => setFilters((current) => ({ ...current, dateTo: event.target.value }))} />
        </FormField>
      </Card>
      <DataTable
        columns={[
          { key: 'action', header: t('audit.action'), render: (item) => getAuditActionLabel(item.action) },
          { key: 'entityType', header: t('audit.entityType'), render: (item) => getAuditEntityTypeLabel(item.entityType) },
          { key: 'entityId', header: t('audit.entityId'), render: (item) => item.entityId ?? '-' },
          { key: 'sourceService', header: t('audit.sourceService'), render: (item) => item.sourceService },
          { key: 'occurredAt', header: t('audit.occurredAt'), render: (item) => formatDateTime(item.occurredAt) },
        ]}
        rows={auditQuery.data.items}
      />
    </div>
  )
}
