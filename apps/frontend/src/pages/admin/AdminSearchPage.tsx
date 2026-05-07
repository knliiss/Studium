import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { searchService } from '@/shared/api/services'
import { getLocalizedRequestErrorMessage, normalizeApiError } from '@/shared/lib/api-errors'
import { getSearchResultTypeLabel } from '@/shared/lib/enum-labels'
import { useDebouncedValue } from '@/shared/lib/useDebouncedValue'
import { Card } from '@/shared/ui/Card'
import { DataTable } from '@/shared/ui/DataTable'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'

export function AdminSearchPage() {
  const { t } = useTranslation()
  const [query, setQuery] = useState('')
  const debouncedQuery = useDebouncedValue(query.trim(), 350)
  const searchQuery = useQuery({
    queryKey: ['admin-search', debouncedQuery],
    queryFn: () => searchService.search({ q: debouncedQuery, page: 0, size: 20 }),
    enabled: debouncedQuery.length > 0,
  })
  const apiError = normalizeApiError(searchQuery.error)

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
        <ErrorState
          title={t('navigation.admin.search')}
          description={[
            getLocalizedRequestErrorMessage(searchQuery.error, t),
            apiError?.requestId ? t('common.conflictRequestId', { id: apiError.requestId }) : '',
          ].filter(Boolean).join(' ')}
          onRetry={() => void searchQuery.refetch()}
        />
      ) : null}
      {!searchQuery.isLoading && !searchQuery.isError && debouncedQuery.length === 0 ? (
        <EmptyState title={t('navigation.admin.search')} description={t('search.enterQuery')} />
      ) : null}
      {searchQuery.data ? (
        <DataTable
          columns={[
            { key: 'type', header: t('search.resultType'), render: (item) => getSearchResultTypeLabel(item.type) },
            { key: 'title', header: t('common.labels.title'), render: (item) => item.title },
            { key: 'subtitle', header: t('search.subtitle'), render: (item) => getSearchSubtitle(item.subtitle) },
          ]}
          rows={searchQuery.data.items}
        />
      ) : null}
    </div>
  )
}

function getSearchSubtitle(subtitle: string | null | undefined) {
  if (!subtitle || containsUuid(subtitle)) {
    return '-'
  }

  return subtitle
}

function containsUuid(value: string) {
  return /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i.test(value)
}
