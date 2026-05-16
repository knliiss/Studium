import { useQuery } from '@tanstack/react-query'
import { ChevronDown, ChevronRight, Menu, PanelLeftClose, PanelLeftOpen, Shield, X } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, NavLink, useLocation } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import {
  adminUserService,
  dashboardService,
  educationService,
  notificationService,
  scheduleService,
} from '@/shared/api/services'
import { navigationGroups, type NavigationItem, type NavigationNestedSource } from '@/shared/config/navigation'
import { cn } from '@/shared/lib/cn'
import { hasAnyRole } from '@/shared/lib/roles'
import type { StudentDashboardResponse, TeacherDashboardResponse } from '@/shared/types/api'
import { Button } from '@/shared/ui/Button'

interface SidebarProps {
  open: boolean
  collapsed: boolean
  onToggle: () => void
  onToggleCollapsed: () => void
}

interface NestedLinkItem {
  id: string
  label: string
  to: string
}

export function Sidebar({ onToggle, open, collapsed, onToggleCollapsed }: SidebarProps) {
  const { primaryRole, roles, session } = useAuth()
  const { t } = useTranslation()
  const { pathname } = useLocation()
  const [expandedItems, setExpandedItems] = useState<Record<string, boolean>>({})

  const groups = navigationGroups
    .filter((group) => hasAnyRole(roles, group.roles))
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => hasAnyRole(roles, item.roles)),
    }))
    .filter((group) => group.items.length > 0)

  const expandedSources = useMemo(() => {
    const sources = new Set<NavigationNestedSource>()
    for (const group of groups) {
      for (const item of group.items) {
        if (item.nestedSource && expandedItems[item.id]) {
          sources.add(item.nestedSource)
        }
      }
    }
    return sources
  }, [expandedItems, groups])

  const studentDashboardQuery = useQuery({
    queryKey: ['dashboard', 'sidebar', 'student'],
    queryFn: () => dashboardService.getStudentDashboard(),
    enabled: primaryRole === 'STUDENT',
  })

  const teacherDashboardQuery = useQuery({
    queryKey: ['dashboard', 'sidebar', 'teacher'],
    queryFn: () => dashboardService.getTeacherDashboard(),
    enabled: primaryRole === 'TEACHER',
  })

  const studentDashboard: StudentDashboardResponse | null = studentDashboardQuery.data ?? null
  const teacherDashboard: TeacherDashboardResponse | null = teacherDashboardQuery.data ?? null

  const unreadQuery = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: () => notificationService.getUnreadCount(),
  })

  const subjectsQuery = useQuery({
    queryKey: ['sidebar', 'nested', 'subjects', primaryRole, session?.user.id],
    queryFn: async () => {
      if (primaryRole === 'ADMIN' || primaryRole === 'OWNER') {
        const page = await educationService.listSubjects({ page: 0, size: 5 })
        return page.items.map((subject) => ({ id: subject.id, label: subject.name, to: `/subjects/${subject.id}` }))
      }

      const subjectsPage = await educationService.listSubjects({ page: 0, size: 100 })
      if (primaryRole === 'TEACHER') {
        return subjectsPage.items
          .filter((subject) => subject.teacherIds.includes(session!.user.id))
          .slice(0, 5)
          .map((subject) => ({ id: subject.id, label: subject.name, to: `/subjects/${subject.id}` }))
      }

      const memberships = await educationService.getGroupsByUser(session!.user.id)
      const memberGroupIds = new Set(memberships.map((membership) => membership.groupId))
      return subjectsPage.items
        .filter((subject) => subject.groupIds.some((groupId) => memberGroupIds.has(groupId)))
        .slice(0, 5)
        .map((subject) => ({ id: subject.id, label: subject.name, to: `/subjects/${subject.id}` }))
    },
    enabled: !collapsed && expandedSources.has('subjects') && Boolean(session?.user.id),
  })

  const groupsQuery = useQuery({
    queryKey: ['sidebar', 'nested', 'groups'],
    queryFn: async () => {
      const page = await educationService.listGroups({ page: 0, size: 5 })
      return page.items.map((group) => ({ id: group.id, label: group.name, to: `/groups/${group.id}` }))
    },
    enabled: !collapsed && (primaryRole === 'ADMIN' || primaryRole === 'OWNER') && expandedSources.has('groups'),
  })

  const teachersQuery = useQuery({
    queryKey: ['sidebar', 'nested', 'teachers'],
    queryFn: async () => {
      const page = await adminUserService.list({ page: 0, size: 5, role: 'TEACHER' })
      return page.content.map((user) => ({ id: user.id, label: user.username, to: `/teachers/${user.id}` }))
    },
    enabled: !collapsed && (primaryRole === 'ADMIN' || primaryRole === 'OWNER') && expandedSources.has('teachers'),
  })

  const roomsQuery = useQuery({
    queryKey: ['sidebar', 'nested', 'rooms'],
    queryFn: async () => {
      const rooms = await scheduleService.listRooms()
      return rooms
        .filter((room) => room.active)
        .slice(0, 5)
        .map((room) => ({ id: room.id, label: room.code, to: `/schedule/rooms/${room.id}` }))
    },
    enabled: !collapsed && (primaryRole === 'ADMIN' || primaryRole === 'OWNER') && expandedSources.has('rooms'),
  })

  const nestedBySource: Record<NavigationNestedSource, { items: NestedLinkItem[]; loading: boolean; error: boolean }> = {
    subjects: {
      items: subjectsQuery.data ?? [],
      loading: subjectsQuery.isLoading,
      error: subjectsQuery.isError,
    },
    assignments: {
      items: primaryRole === 'STUDENT'
        ? (studentDashboard?.pendingAssignments ?? []).slice(0, 5).map((assignment) => ({
          id: assignment.assignmentId,
          label: assignment.title,
          to: `/assignments/${assignment.assignmentId}`,
        }))
        : (teacherDashboard?.activeAssignments ?? []).slice(0, 5).map((assignment) => ({
          id: assignment.assignmentId,
          label: assignment.title,
          to: `/assignments/${assignment.assignmentId}`,
        })),
      loading: primaryRole === 'STUDENT' ? studentDashboardQuery.isLoading : teacherDashboardQuery.isLoading,
      error: primaryRole === 'STUDENT' ? studentDashboardQuery.isError : teacherDashboardQuery.isError,
    },
    tests: {
      items: primaryRole === 'STUDENT'
        ? (studentDashboard?.availableTests ?? []).slice(0, 5).map((test) => ({
          id: test.testId,
          label: test.title,
          to: `/tests/${test.testId}`,
        }))
        : (teacherDashboard?.activeTests ?? []).slice(0, 5).map((test) => ({
          id: test.testId,
          label: test.title,
          to: `/tests/${test.testId}`,
        })),
      loading: primaryRole === 'STUDENT' ? studentDashboardQuery.isLoading : teacherDashboardQuery.isLoading,
      error: primaryRole === 'STUDENT' ? studentDashboardQuery.isError : teacherDashboardQuery.isError,
    },
    groups: {
      items: groupsQuery.data ?? [],
      loading: groupsQuery.isLoading,
      error: groupsQuery.isError,
    },
    teachers: {
      items: teachersQuery.data ?? [],
      loading: teachersQuery.isLoading,
      error: teachersQuery.isError,
    },
    rooms: {
      items: roomsQuery.data ?? [],
      loading: roomsQuery.isLoading,
      error: roomsQuery.isError,
    },
  }

  const reviewQueueCount = teacherDashboard?.pendingSubmissionsToReview?.length ?? 0
  const notificationCount = unreadQuery.data?.unreadCount ?? 0

  function getBadge(item: NavigationItem) {
    if (item.id === 'notifications' && notificationCount > 0) {
      return notificationCount
    }
    if (item.id === 'review' && reviewQueueCount > 0) {
      return reviewQueueCount
    }
    return null
  }

  function toggleExpanded(itemId: string) {
    setExpandedItems((current) => ({ ...current, [itemId]: !current[itemId] }))
  }

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
          'fixed inset-y-0 left-0 z-40 h-[100dvh] overflow-hidden border-r border-border bg-background-elevated px-3 py-3 transition-[width,transform] duration-200 lg:sticky lg:top-0 lg:translate-x-0 lg:self-stretch',
          collapsed ? 'w-[72px]' : 'w-[264px]',
          open ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div className="grid h-full min-h-0 grid-rows-[auto_minmax(0,1fr)_auto] gap-3">
          <div className="flex items-center justify-between border-b border-border pb-3">
            <div className="flex items-center gap-2">
              <span className="inline-flex h-7 w-7 items-center justify-center rounded-[10px] bg-accent-muted text-accent">
                <Shield className="h-4 w-4" />
              </span>
              {!collapsed ? (
                <div>
                  <p className="text-xs font-semibold uppercase tracking-[0.2em] text-accent">Studium</p>
                  <p className="text-xs text-text-muted">{t(`common.roles.${primaryRole}`)}</p>
                </div>
              ) : null}
            </div>
            <button
              aria-label={collapsed ? t('navigation.sidebar.expand') : t('navigation.sidebar.collapse')}
              className="hidden min-h-8 min-w-8 items-center justify-center rounded-[10px] border border-border bg-surface text-text-secondary transition hover:text-text-primary lg:inline-flex"
              type="button"
              onClick={onToggleCollapsed}
            >
              {collapsed ? <PanelLeftOpen className="h-4 w-4" /> : <PanelLeftClose className="h-4 w-4" />}
            </button>
          </div>

          <nav className="min-h-0 space-y-4 overflow-y-auto pr-1">
            {groups.map((group) => (
              <section key={group.id} className="space-y-1.5">
                {!collapsed ? (
                  <p className="px-2 text-[10px] font-semibold uppercase tracking-[0.22em] text-text-muted">{t(group.labelKey)}</p>
                ) : null}
                <div className="space-y-1">
                  {group.items.map((item) => {
                    const isActive = (item.matchPrefixes ?? [item.to]).some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`))
                    const Icon = item.icon
                    const badge = getBadge(item)
                    const isExpanded = Boolean(expandedItems[item.id]) && !collapsed
                    const nestedState = item.nestedSource ? nestedBySource[item.nestedSource] : null

                    return (
                      <div key={item.id}>
                        <div
                          className={cn(
                            'flex min-h-10 items-center gap-2 rounded-[12px] border px-2 py-1.5 text-sm font-medium transition',
                            isActive
                              ? 'border-accent/40 bg-accent-muted/50 text-text-primary'
                              : 'border-transparent text-text-secondary hover:border-border hover:bg-surface-muted hover:text-text-primary',
                          )}
                        >
                          <NavLink
                            className="flex min-w-0 flex-1 items-center gap-2"
                            title={collapsed ? t(item.labelKey) : undefined}
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
                            {!collapsed ? <span className="truncate">{t(item.labelKey)}</span> : null}
                            {!collapsed && badge ? (
                              <span className="ml-auto inline-flex min-h-5 min-w-5 items-center justify-center rounded-full bg-accent px-1 text-[11px] font-semibold text-accent-foreground">
                                {badge}
                              </span>
                            ) : null}
                            {collapsed && badge ? <span className="h-2 w-2 rounded-full bg-accent" /> : null}
                          </NavLink>
                          {!collapsed && item.nestedSource ? (
                            <button
                              aria-label={isExpanded ? t('common.actions.collapse') : t('common.actions.expand')}
                              className="inline-flex min-h-7 min-w-7 items-center justify-center rounded-[8px] text-text-muted transition hover:bg-surface hover:text-text-primary"
                              type="button"
                              onClick={() => toggleExpanded(item.id)}
                            >
                              {isExpanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
                            </button>
                          ) : null}
                        </div>

                        {item.nestedSource && isExpanded ? (
                          <div className="ml-10 mt-1 space-y-1 border-l border-border pl-3">
                            {nestedState?.loading ? (
                              <p className="py-1 text-xs text-text-muted">{t('navigation.sidebar.loadingNested')}</p>
                            ) : null}
                            {nestedState?.error ? (
                              <p className="py-1 text-xs text-danger">{t('navigation.sidebar.errorNested')}</p>
                            ) : null}
                            {!nestedState?.loading && !nestedState?.error && nestedState?.items.length === 0 ? (
                              <p className="py-1 text-xs text-text-muted">{t('navigation.sidebar.emptyNested')}</p>
                            ) : null}
                            {!nestedState?.loading && !nestedState?.error
                              ? nestedState?.items.map((nestedItem) => (
                                <Link
                                  key={nestedItem.id}
                                  className="block rounded-[10px] px-2 py-1.5 text-xs text-text-secondary transition hover:bg-surface hover:text-text-primary"
                                  to={nestedItem.to}
                                >
                                  <span className="truncate">{nestedItem.label}</span>
                                </Link>
                              ))
                              : null}
                            <Link
                              className="block rounded-[10px] px-2 py-1.5 text-xs font-semibold text-accent transition hover:bg-surface"
                              to={item.to}
                            >
                              {t('navigation.sidebar.showAll')}
                            </Link>
                          </div>
                        ) : null}
                      </div>
                    )
                  })}
                </div>
              </section>
            ))}
          </nav>

          <div className="border-t border-border pt-3 lg:hidden">
            <Button className="w-full" variant="secondary" onClick={onToggle}>
              {t('common.actions.close')}
            </Button>
          </div>
        </div>
      </aside>
    </>
  )
}
