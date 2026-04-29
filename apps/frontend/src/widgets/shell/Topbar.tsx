import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { useAuth } from '@/features/auth/useAuth'
import { profileService } from '@/shared/api/services'
import { LanguageSwitcher } from '@/shared/ui/LanguageSwitcher'
import { ThemeSwitcher } from '@/shared/ui/ThemeSwitcher'
import { UserAvatar } from '@/shared/ui/UserAvatar'
import { NotificationDropdown } from '@/widgets/notifications/NotificationDropdown'

export function Topbar() {
  const { logout, primaryRole, session } = useAuth()
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const { t } = useTranslation()
  const profileQuery = useQuery({
    queryKey: ['profile', 'me'],
    queryFn: () => profileService.getMe(),
  })
  const sectionTitle = resolveSectionTitle(pathname, t)
  const profileLabel = profileQuery.data?.displayName ?? session?.user.username ?? session?.user.email ?? ''

  return (
    <header className="sticky top-0 z-30 flex flex-wrap items-center justify-between gap-4 border-b border-border bg-background/90 px-6 py-4 backdrop-blur">
      <div className="flex min-w-0 flex-1 flex-wrap items-center gap-3">
        <div className="min-w-0">
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">
            {t(`common.roles.${primaryRole}`)}
          </p>
          <h2 className="truncate text-xl font-semibold tracking-[-0.03em] text-text-primary">
            {sectionTitle}
          </h2>
        </div>
      </div>
      <div className="flex items-center gap-3">
        <NotificationDropdown targetPath="/notifications" />
        <LanguageSwitcher />
        <ThemeSwitcher />
        <div className="flex items-center gap-2 rounded-[14px] border border-border bg-surface px-3 py-2 text-sm text-text-secondary">
          <UserAvatar
            alt={t('profile.avatarFor', { name: profileLabel })}
            fileId={profileQuery.data?.avatarFileKey}
            displayName={profileQuery.data?.displayName}
            email={profileQuery.data?.email}
            size="sm"
            username={session?.user.username}
          />
          <span className="hidden text-sm text-text-secondary md:inline">{profileLabel}</span>
        </div>
        <button
          className="inline-flex min-h-10 items-center rounded-[14px] border border-border bg-surface px-4 text-sm font-semibold text-text-primary"
          type="button"
          onClick={async () => {
            await logout()
            navigate('/login', { replace: true })
          }}
        >
          {t('common.actions.logout')}
        </button>
      </div>
    </header>
  )
}

function resolveSectionTitle(pathname: string, t: (key: string) => string) {
  const titles: Array<{ prefix: string; key: string }> = [
    { prefix: '/admin/system', key: 'navigation.admin.system' },
    { prefix: '/admin/users', key: 'navigation.admin.users' },
    { prefix: '/admin/audit', key: 'navigation.admin.audit' },
    { prefix: '/search', key: 'navigation.shared.search' },
    { prefix: '/review', key: 'navigation.shared.review' },
    { prefix: '/submissions', key: 'navigation.shared.review' },
    { prefix: '/assignments', key: 'navigation.shared.assignments' },
    { prefix: '/tests', key: 'navigation.shared.tests' },
    { prefix: '/teachers', key: 'navigation.shared.teachers' },
    { prefix: '/groups', key: 'navigation.shared.groups' },
    { prefix: '/subjects', key: 'navigation.shared.subjects' },
    { prefix: '/education', key: 'navigation.shared.education' },
    { prefix: '/schedule', key: 'navigation.shared.schedule' },
    { prefix: '/notifications', key: 'navigation.shared.notifications' },
    { prefix: '/analytics', key: 'navigation.shared.analytics' },
    { prefix: '/grades', key: 'navigation.shared.grades' },
    { prefix: '/profile', key: 'navigation.shared.profile' },
    { prefix: '/dashboard', key: 'navigation.shared.dashboard' },
    { prefix: '/admin/dashboard', key: 'navigation.shared.dashboard' },
  ]

  const matched = titles.find(({ prefix }) => pathname === prefix || pathname.startsWith(`${prefix}/`))
  return matched ? t(matched.key) : t('app.name')
}
