import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { PageHeader } from '@/shared/ui/PageHeader'

export function AdminSystemPage() {
  const { t } = useTranslation()

  return (
    <div className="space-y-6">
      <PageHeader
        description={t('adminSystem.description')}
        title={t('navigation.admin.system')}
      />

      <div className="grid gap-5 xl:grid-cols-3">
        <Card className="space-y-3">
          <div className="space-y-1">
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">
              {t('adminSystem.blocks.access.eyebrow')}
            </p>
            <h2 className="text-lg font-semibold text-text-primary">
              {t('adminSystem.blocks.access.title')}
            </h2>
            <p className="text-sm leading-6 text-text-secondary">
              {t('adminSystem.blocks.access.description')}
            </p>
          </div>
          <Link to="/admin/users">
            <Button variant="secondary">{t('navigation.admin.users')}</Button>
          </Link>
        </Card>

        <Card className="space-y-3">
          <div className="space-y-1">
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">
              {t('adminSystem.blocks.audit.eyebrow')}
            </p>
            <h2 className="text-lg font-semibold text-text-primary">
              {t('adminSystem.blocks.audit.title')}
            </h2>
            <p className="text-sm leading-6 text-text-secondary">
              {t('adminSystem.blocks.audit.description')}
            </p>
          </div>
          <Link to="/admin/audit">
            <Button variant="secondary">{t('navigation.admin.audit')}</Button>
          </Link>
        </Card>

        <Card className="space-y-3">
          <div className="space-y-1">
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">
              {t('adminSystem.blocks.search.eyebrow')}
            </p>
            <h2 className="text-lg font-semibold text-text-primary">
              {t('adminSystem.blocks.search.title')}
            </h2>
            <p className="text-sm leading-6 text-text-secondary">
              {t('adminSystem.blocks.search.description')}
            </p>
          </div>
          <Link to="/search">
            <Button variant="secondary">{t('navigation.shared.search')}</Button>
          </Link>
        </Card>
      </div>
    </div>
  )
}
