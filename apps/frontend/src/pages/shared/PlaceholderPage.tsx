import { useTranslation } from 'react-i18next'

import { PageHeader } from '@/shared/ui/PageHeader'
import { EmptyState } from '@/shared/ui/StateViews'

export function PlaceholderPage({ title }: { title: string }) {
  const { t } = useTranslation()

  return (
    <>
      <PageHeader description={t('pages.placeholder.description')} title={title} />
      <EmptyState
        description={t('pages.placeholder.description')}
        title={t('pages.placeholder.title')}
      />
    </>
  )
}
