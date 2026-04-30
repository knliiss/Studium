import { Menu, X } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { NavLink, useLocation } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { useAuth } from '@/features/auth/useAuth'
import { profileService } from '@/shared/api/services'
import { navigationGroups } from '@/shared/config/navigation'
import { cn } from '@/shared/lib/cn'
import { hasAnyRole } from '@/shared/lib/roles'
import { Button } from '@/shared/ui/Button'
import { UserAvatar } from '@/shared/ui/UserAvatar'

interface SidebarProps {
  open: boolean
  onToggle: () => void
}

export function Sidebar({ onToggle, open }: SidebarProps) {
  const { primaryRole, roles, session } = useAuth()
  const { t } = useTranslation()
  const { pathname } = useLocation()
  const profileQuery = useQuery({
    queryKey: ['profile', 'me'],
    queryFn: () => profileService.getMe(),
  })
  const profileLabel = profileQuery.data?.displayName ?? session?.user.username ?? session?.user.email ?? ''
  const groups = navigationGroups
    .filter((group) => hasAnyRole(roles, group.roles))
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => hasAnyRole(roles, item.roles)),
    }))
    .filter((group) => group.items.length > 0)

  return (
    <>
      <button
        aria-label={open ? t('common.actions.closeNavigation') : t('common.actions.openNavigation')}
        className="fixed left-4 top-4 z-50 inline-flex min-h-11 min-w-11 items-center justify-center rounded-[14px] border border-border bg-surface shadow-[var(--shadow-card)] lg:hidden"
        type="button"
        onClick={onToggle}
      >
        {open ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
      </button>
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-40 h-[100dvh] w-72 overflow-hidden border-r border-border bg-background-elevated/90 p-4 shadow-[var(--shadow-soft)] backdrop-blur transition-transform lg:sticky lg:top-0 lg:translate-x-0 lg:self-stretch lg:shadow-none',
          open ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div className="grid h-full min-h-0 grid-rows-[auto_minmax(0,1fr)_auto] gap-4">
          <div className="shrink-0 rounded-[24px] border border-border bg-surface px-4 py-5 shadow-[var(--shadow-card)]">
            <p className="text-xs font-semibold uppercase tracking-[0.24em] text-accent">Studium</p>
            <div className="mt-3 space-y-2">
              <h1 className="text-2xl font-bold tracking-[-0.04em] text-text-primary">
                {t(`common.roles.${primaryRole}`)}
              </h1>
              <p className="text-sm leading-6 text-text-secondary">{t('app.tagline')}</p>
            </div>
          </div>

          <nav className="min-h-0 space-y-4 overflow-y-auto pr-1">
            {groups.map((group) => (
              <section key={group.id} className="rounded-[22px] border border-border bg-surface p-2 shadow-[var(--shadow-card)]">
                <p className="px-3 pb-2 pt-1 text-[11px] font-semibold uppercase tracking-[0.22em] text-text-muted">
                  {t(group.labelKey)}
                </p>
                <div className="space-y-1">
                  {group.items.map((item) => {
                    const isActive = (item.matchPrefixes ?? [item.to]).some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`))
                    const Icon = item.icon

                    return (
                      <NavLink
                        key={item.to}
                        className={cn(
                          'flex min-h-11 items-center gap-3 rounded-[16px] border px-3 py-2 text-sm font-medium transition',
                          isActive
                            ? 'border-accent/20 bg-accent-muted text-accent shadow-[0_10px_24px_rgba(91,91,214,0.12)]'
                            : 'border-transparent text-text-secondary hover:border-border hover:bg-surface-muted hover:text-text-primary',
                        )}
                        to={item.to}
                        onClick={() => {
                          if (open) {
                            onToggle()
                          }
                        }}
                      >
                        <span
                          className={cn(
                            'inline-flex min-h-9 min-w-9 items-center justify-center rounded-[12px]',
                            isActive ? 'bg-accent text-accent-foreground' : 'bg-surface-muted text-text-secondary',
                          )}
                        >
                          <Icon className="h-4 w-4" />
                        </span>
                        <span>{t(item.labelKey)}</span>
                      </NavLink>
                    )
                  })}
                </div>
              </section>
            ))}
          </nav>

          <div className="sticky bottom-0 shrink-0 border-t border-border/70 bg-background-elevated/95 pt-4 backdrop-blur">
            <div className="space-y-3 rounded-[22px] border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
              <div className="space-y-1">
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">
                  {t('navigation.shared.profile')}
                </p>
                <div className="flex items-center gap-3">
                  <UserAvatar
                    alt={t('profile.avatarFor', { name: profileLabel })}
                    fileId={profileQuery.data?.avatarFileKey}
                    displayName={profileQuery.data?.displayName}
                    email={profileQuery.data?.email}
                    size="md"
                    username={session?.user.username}
                  />
                  <div className="min-w-0">
                    <p className="truncate text-sm font-semibold text-text-primary">{profileLabel}</p>
                    <p className="truncate text-sm text-text-secondary">{session?.user.email}</p>
                  </div>
                </div>
              </div>
              <NavLink
                className="inline-flex min-h-11 w-full items-center justify-center rounded-[14px] border border-border bg-surface-muted px-3 text-sm font-medium text-text-primary transition hover:border-border-strong"
                to="/profile"
                onClick={() => {
                  if (open) {
                    onToggle()
                  }
                }}
              >
                {t('navigation.shared.profile')}
              </NavLink>
              <Button className="w-full lg:hidden" variant="secondary" onClick={onToggle}>
                {t('common.actions.close')}
              </Button>
            </div>
          </div>
        </div>
      </aside>
    </>
  )
}
