import { useTranslation } from 'react-i18next'

import { normalizeRole } from '@/shared/lib/roles'

export function RoleBadge({ role }: { role: string }) {
  const { t } = useTranslation()
  const normalizedRole = normalizeRole(role)

  return (
    <span className="inline-flex rounded-full bg-accent-muted px-2.5 py-1 text-xs font-semibold uppercase tracking-[0.16em] text-accent">
      {normalizedRole ? t(`common.roles.${normalizedRole}`) : role}
    </span>
  )
}
