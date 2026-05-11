import { useQuery } from '@tanstack/react-query'
import { ChevronRight, Menu, Shield, X } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { NavLink, useLocation } from 'react-router-dom'

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
        className="fixed left-4 top-4 z-50 inline-flex min-h-10 min-w-10 items-center justify-center rounded-[12px] border border-border bg-surface lg:hidden"
        type="button"
        onClick={onToggle}
      >
        {open ? <X className="h-4 w-4" /> : <Menu className="h-4 w-4" />}
      </button>
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-40 h-[100dvh] w-[260px] overflow-hidden border-r border-border bg-background-elevated px-3 py-3 transition-transform lg:sticky lg:top-0 lg:translate-x-0 lg:self-stretch',
          open ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div className="grid h-full min-h-0 grid-rows-[auto_minmax(0,1fr)_auto] gap-3">
          <div className="border-b border-border pb-3">
            <div className="flex items-center gap-2">
              <span className="inline-flex h-7 w-7 items-center justify-center rounded-[10px] bg-accent-muted text-accent">
                <Shield className="h-4 w-4" />
              </span>
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.2em] text-accent">Studium</p>
                <p className="text-xs text-text-muted">{t(`common.roles.${primaryRole}`)}</p>
              </div>
            </div>
          </div>

          <nav className="min-h-0 space-y-4 overflow-y-auto pr-1">
            {groups.map((group) => (
              <section key={group.id} className="space-y-1.5">
                <p className="px-2 text-[10px] font-semibold uppercase tracking-[0.22em] text-text-muted">{t(group.labelKey)}</p>
                <div className="space-y-1">
                  {group.items.map((item) => {
                    const isActive = (item.matchPrefixes ?? [item.to]).some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`))
                    const Icon = item.icon

                    return (
                      <NavLink
                        key={item.to}
                        className={cn(
                          'flex min-h-10 items-center gap-2.5 rounded-[12px] border px-2.5 py-2 text-sm font-medium transition',
                          isActive
                            ? 'border-accent/40 bg-accent-muted/50 text-text-primary'
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
                            'inline-flex min-h-7 min-w-7 items-center justify-center rounded-[8px]',
                            isActive ? 'bg-accent text-accent-foreground' : 'bg-surface-muted text-text-secondary',
                          )}
                        >
                          <Icon className="h-4 w-4" />
                        </span>
                        <span className="truncate">{t(item.labelKey)}</span>
                      </NavLink>
                    )
                  })}
                </div>
              </section>
            ))}
          </nav>

          <div className="border-t border-border pt-3">
            <NavLink
              className="flex items-center gap-2 rounded-[12px] border border-border bg-surface px-2.5 py-2 transition hover:border-border-strong"
              to="/profile"
              onClick={() => {
                if (open) {
                  onToggle()
                }
              }}
            >
              <UserAvatar
                alt={t('profile.avatarFor', { name: profileLabel })}
                fileId={profileQuery.data?.avatarFileKey}
                displayName={profileQuery.data?.displayName}
                email={profileQuery.data?.email}
                size="sm"
                username={session?.user.username}
              />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-semibold text-text-primary">{profileLabel}</p>
                <p className="truncate text-xs text-text-secondary">{session?.user.email}</p>
              </div>
              <ChevronRight className="h-4 w-4 text-text-muted" />
            </NavLink>
            <Button className="mt-2 w-full lg:hidden" variant="secondary" onClick={onToggle}>
              {t('common.actions.close')}
            </Button>
          </div>
        </div>
      </aside>
    </>
  )
}
