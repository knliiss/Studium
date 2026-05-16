import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { notificationService } from '@/shared/api/services'
import { getNotificationTypeLabel } from '@/shared/lib/enum-labels'
import { formatDateTime } from '@/shared/lib/format'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { PageHeader } from '@/shared/ui/PageHeader'
import { Select } from '@/shared/ui/Select'
import { ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { StatusBadge } from '@/widgets/common/StatusBadge'

type NotificationFilter = 'all' | 'unread' | 'assignments' | 'tests' | 'grades' | 'schedule' | 'materials' | 'system'

export function NotificationsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [filter, setFilter] = useState<NotificationFilter>('all')
  const notificationsQuery = useQuery({
    queryKey: ['notifications', 'page', filter],
    queryFn: () => notificationService.getMyNotifications({ status: filter === 'unread' ? 'UNREAD' : undefined }),
  })
  const unreadQuery = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: () => notificationService.getUnreadCount(),
  })

  const markAllMutation = useMutation({
    mutationFn: () => notificationService.markAllRead(),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
  })

  const markReadMutation = useMutation({
    mutationFn: (notificationId: string) => notificationService.markRead(notificationId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (notificationId: string) => notificationService.deleteNotification(notificationId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
  })

  const visibleNotifications = useMemo(
    () => notificationsQuery.data?.items.filter((item) => matchesNotificationFilter(item.type, filter)) ?? [],
    [filter, notificationsQuery.data?.items],
  )

  if (notificationsQuery.isLoading) {
    return <LoadingState />
  }

  if (notificationsQuery.isError || !notificationsQuery.data) {
    return <ErrorState title={t('notifications.title')} description={t('common.states.error')} />
  }

  return (
    <div className="space-y-6">
      <PageHeader
        actions={(
          <Button disabled={markAllMutation.isPending} onClick={() => markAllMutation.mutate()}>
            {t('common.actions.markAllRead')}
          </Button>
        )}
        description={`${t('notifications.unreadCount')}: ${unreadQuery.data?.unreadCount ?? 0}`}
        title={t('notifications.title')}
      />

      <Card className="space-y-4">
        <div className="grid gap-4 md:grid-cols-[240px_1fr]">
          <Select value={filter} onChange={(event) => setFilter(event.target.value as NotificationFilter)}>
            <option value="all">{t('notifications.filters.all')}</option>
            <option value="unread">{t('notifications.filters.unread')}</option>
            <option value="assignments">{t('notifications.filters.assignments')}</option>
            <option value="tests">{t('notifications.filters.tests')}</option>
            <option value="grades">{t('notifications.filters.grades')}</option>
            <option value="schedule">{t('notifications.filters.schedule')}</option>
            <option value="materials">{t('notifications.filters.materials')}</option>
            <option value="system">{t('notifications.filters.system')}</option>
          </Select>
        </div>
      </Card>

      <div className="space-y-4">
        {visibleNotifications.length === 0 ? (
          <Card className="rounded-[16px] border border-border bg-surface-muted p-4 text-sm text-text-secondary">
            {filter === 'unread' ? t('notifications.emptyUnread') : t('notifications.empty')}
          </Card>
        ) : null}
        {visibleNotifications.map((notification) => (
          <Card key={notification.id} className="space-y-4">
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div className="space-y-2">
                <div className="flex items-center gap-3">
                  <StatusBadge value={notification.status} />
                  <p className="text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">
                    {getNotificationTypeLabel(notification.type)}
                  </p>
                </div>
                <div className="space-y-1">
                  <h2 className="text-xl font-semibold text-text-primary">{notification.title}</h2>
                  <p className="max-w-3xl text-sm leading-6 text-text-secondary">{notification.body}</p>
                </div>
              </div>
              <p className="text-sm text-text-muted">{formatDateTime(notification.createdAt)}</p>
            </div>
            <div className="flex flex-wrap gap-3">
              {!notification.read ? (
                <Button variant="secondary" onClick={() => markReadMutation.mutate(notification.id)}>
                  {t('common.actions.markRead')}
                </Button>
              ) : null}
              {resolveActionUrl(notification.payloadJson) ? (
                <Link to={resolveActionUrl(notification.payloadJson)!}>
                  <Button variant="secondary">{t('common.actions.open')}</Button>
                </Link>
              ) : null}
              <Button variant="ghost" onClick={() => deleteMutation.mutate(notification.id)}>
                {t('common.actions.delete')}
              </Button>
            </div>
          </Card>
        ))}
      </div>
    </div>
  )
}

function matchesNotificationFilter(type: string, filter: NotificationFilter) {
  if (filter === 'all' || filter === 'unread') {
    return true
  }
  if (filter === 'assignments') {
    return type.startsWith('ASSIGNMENT_')
  }
  if (filter === 'tests') {
    return type.startsWith('TEST_')
  }
  if (filter === 'grades') {
    return type === 'GRADE_ASSIGNED'
  }
  if (filter === 'schedule') {
    return type.startsWith('SCHEDULE_')
  }
  if (filter === 'materials') {
    return type.includes('MATERIAL')
  }
  return !type.startsWith('ASSIGNMENT_')
    && !type.startsWith('TEST_')
    && !type.startsWith('SCHEDULE_')
    && type !== 'GRADE_ASSIGNED'
    && !type.includes('MATERIAL')
}

function resolveActionUrl(payloadJson: string | null) {
  if (!payloadJson) {
    return null
  }
  try {
    const payload = JSON.parse(payloadJson) as Record<string, unknown>
    const url = payload.actionUrl
    return typeof url === 'string' && url.startsWith('/') ? url : null
  } catch {
    return null
  }
}
