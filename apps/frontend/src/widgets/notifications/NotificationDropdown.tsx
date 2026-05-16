import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bell, Trash2 } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { notificationService } from '@/shared/api/services'
import { getNotificationTypeLabel } from '@/shared/lib/enum-labels'
import { formatDateTime } from '@/shared/lib/format'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'

export function NotificationDropdown({ targetPath }: { targetPath: string }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const containerRef = useRef<HTMLDivElement | null>(null)
  const autoMarkedIdsRef = useRef<Set<string>>(new Set())
  const [open, setOpen] = useState(false)

  const unreadQuery = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: () => notificationService.getUnreadCount(),
  })
  const notificationsQuery = useQuery({
    queryKey: ['notifications', 'dropdown'],
    queryFn: () => notificationService.getMyNotifications({ page: 0, size: 10 }),
    enabled: open,
  })

  const markAllReadMutation = useMutation({
    mutationFn: () => notificationService.markAllRead(),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
  })
  const deleteOneMutation = useMutation({
    mutationFn: (notificationId: string) => notificationService.deleteNotification(notificationId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
  })
  const deleteAllMutation = useMutation({
    mutationFn: () => notificationService.deleteAllNotifications(),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
  })

  const notifications = useMemo(
    () => notificationsQuery.data?.items ?? [],
    [notificationsQuery.data?.items],
  )
  const hasNotifications = notifications.length > 0
  const hasUnreadInLoaded = useMemo(
    () => notifications.some((notification) => !notification.read),
    [notifications],
  )

  useEffect(() => {
    if (!open) {
      return
    }
    const handleOutsideClick = (event: MouseEvent) => {
      if (!containerRef.current) {
        return
      }
      if (!containerRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleOutsideClick)
    return () => {
      document.removeEventListener('mousedown', handleOutsideClick)
    }
  }, [open])

  useEffect(() => {
    if (!open || notifications.length === 0) {
      return
    }

    const unreadIds = notifications
      .filter((notification) => !notification.read)
      .map((notification) => notification.id)
      .filter((notificationId) => !autoMarkedIdsRef.current.has(notificationId))

    if (unreadIds.length === 0) {
      return
    }

    unreadIds.forEach((notificationId) => autoMarkedIdsRef.current.add(notificationId))
    void Promise.allSettled(unreadIds.map((notificationId) => notificationService.markRead(notificationId)))
      .then(async () => {
        await queryClient.invalidateQueries({ queryKey: ['notifications'] })
      })
  }, [notifications, open, queryClient])

  return (
    <div ref={containerRef} className="relative">
      <button
        className="relative inline-flex min-h-10 min-w-10 items-center justify-center rounded-[14px] border border-border bg-surface text-text-secondary"
        type="button"
        onClick={() => setOpen((current) => !current)}
      >
        <Bell className="h-4 w-4" />
        {unreadQuery.data?.unreadCount ? (
          <span className="absolute -right-1 -top-1 inline-flex min-h-5 min-w-5 items-center justify-center rounded-full bg-accent px-1 text-[11px] font-bold text-white">
            {unreadQuery.data.unreadCount}
          </span>
        ) : null}
      </button>

      {open ? (
        <Card className="absolute right-0 z-40 mt-3 w-[320px] space-y-3 p-3 shadow-[var(--shadow-soft)]">
          <div className="flex items-center justify-between gap-2">
            <h3 className="text-sm font-semibold text-text-primary">{t('notifications.title')}</h3>
            <Link className="text-xs font-medium text-accent" to={targetPath} onClick={() => setOpen(false)}>
              {t('notifications.dropdown.openCenter')}
            </Link>
          </div>

          <div className="flex flex-wrap gap-2">
            <Button
              disabled={markAllReadMutation.isPending || !hasUnreadInLoaded}
              variant="secondary"
              className="min-h-9 rounded-[10px] px-3 text-xs"
              onClick={() => markAllReadMutation.mutate()}
            >
              {t('notifications.dropdown.markAllRead')}
            </Button>
            <Button
              disabled={deleteAllMutation.isPending || !hasNotifications}
              variant="ghost"
              className="min-h-9 rounded-[10px] px-3 text-xs"
              onClick={() => deleteAllMutation.mutate()}
            >
              {t('notifications.dropdown.deleteAll')}
            </Button>
          </div>

          <div className="max-h-[320px] space-y-2 overflow-y-auto pr-1">
            {notificationsQuery.isLoading ? (
              <p className="rounded-[12px] border border-border bg-surface-muted px-3 py-2 text-sm text-text-secondary">
                {t('common.states.loading')}
              </p>
            ) : null}
            {!notificationsQuery.isLoading && notificationsQuery.isError ? (
              <p className="rounded-[12px] border border-danger/30 bg-danger/5 px-3 py-2 text-sm text-danger">
                {t('common.states.error')}
              </p>
            ) : null}
            {!notificationsQuery.isLoading && !notificationsQuery.isError && notifications.length === 0 ? (
              <p className="rounded-[12px] border border-border bg-surface-muted px-3 py-2 text-sm text-text-secondary">
                {t('notifications.dropdown.empty')}
              </p>
            ) : null}

            {notifications.map((notification) => (
              <div key={notification.id} className="rounded-[12px] border border-border bg-surface-muted p-3">
                <div className="flex items-start justify-between gap-2">
                  <p className="text-xs font-semibold uppercase tracking-[0.14em] text-text-muted">
                    {getNotificationTypeLabel(notification.type)}
                  </p>
                  <button
                    aria-label={t('notifications.dropdown.deleteOne')}
                    className="inline-flex min-h-7 min-w-7 items-center justify-center rounded-[8px] text-text-muted transition hover:bg-surface hover:text-text-primary"
                    type="button"
                    onClick={() => deleteOneMutation.mutate(notification.id)}
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </button>
                </div>
                <p className="mt-1 text-sm font-semibold text-text-primary">{notification.title}</p>
                <p className="mt-1 text-sm leading-6 text-text-secondary">{notification.body}</p>
                <p className="mt-2 text-xs text-text-muted">{formatDateTime(notification.createdAt)}</p>
              </div>
            ))}
          </div>
        </Card>
      ) : null}
    </div>
  )
}
