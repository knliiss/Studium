import { useTranslation } from 'react-i18next'

import { Select } from '@/shared/ui/Select'

export function LanguageSwitcher() {
  const { i18n, t } = useTranslation()

  return (
    <label className="flex items-center gap-3 text-sm text-text-secondary">
      <span className="sr-only">{t('common.labels.language')}</span>
      <Select
        aria-label={t('common.labels.language')}
        className="min-h-10 min-w-34 bg-surface-muted"
        value={i18n.resolvedLanguage}
        onChange={(event) => {
          void i18n.changeLanguage(event.target.value)
        }}
      >
        <option value="en">{t('common.language.en')}</option>
        <option value="uk">{t('common.language.uk')}</option>
        <option value="pl">{t('common.language.pl')}</option>
      </Select>
    </label>
  )
}
