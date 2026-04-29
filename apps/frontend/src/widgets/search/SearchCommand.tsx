import { Search } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

export function SearchCommand() {
  const { t } = useTranslation()

  return (
    <Link
      className="inline-flex min-h-10 items-center gap-2 rounded-[14px] border border-border bg-surface px-4 text-sm font-medium text-text-secondary hover:text-text-primary"
      to="/search"
    >
      <Search className="h-4 w-4" />
      {t('navigation.shared.search')}
    </Link>
  )
}
