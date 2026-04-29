import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { searchService } from '@/shared/api/services'
import { getSearchResultTypeLabel } from '@/shared/lib/enum-labels'
import { Card } from '@/shared/ui/Card'
import { DataTable } from '@/shared/ui/DataTable'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { ErrorState, LoadingState } from '@/shared/ui/StateViews'

export function AdminSearchPage() {
  const { t } = useTranslation()
  const [query, setQuery] = useState('demo')
  const searchQuery = useQuery({
    queryKey: ['admin-search', query],
    queryFn: () => searchService.search({ q: query, page: 0, size: 20 }),
    enabled: query.trim().length > 0,
  })

  return (
    <div className="space-y-6">
      <PageHeader description={t('search.description')} title={t('navigation.admin.search')} />
      <Card className="space-y-4">
        <FormField label={t('common.labels.query')}>
          <Input value={query} onChange={(event) => setQuery(event.target.value)} />
        </FormField>
      </Card>

      {searchQuery.isLoading ? <LoadingState /> : null}
      {searchQuery.isError ? (
        <ErrorState title={t('navigation.admin.search')} description={t('common.states.error')} />
      ) : null}
      {searchQuery.data ? (
        <DataTable
          columns={[
            { key: 'type', header: t('search.resultType'), render: (item) => getSearchResultTypeLabel(item.type) },
            { key: 'title', header: t('common.labels.title'), render: (item) => item.title },
            { key: 'subtitle', header: t('search.subtitle'), render: (item) => item.subtitle ?? '-' },
            { key: 'sourceService', header: t('audit.sourceService'), render: (item) => item.sourceService },
          ]}
          rows={searchQuery.data.items}
        />
      ) : null}
    </div>
  )
}
