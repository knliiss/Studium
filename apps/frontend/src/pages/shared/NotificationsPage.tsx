import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { notificationService } from '@/shared/api/services'
import { getNotificationTypeLabel } from '@/shared/lib/enum-labels'
import { formatDateTime } from '@/shared/lib/format'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { PageHeader } from '@/shared/ui/PageHeader'
import { Select } from '@/shared/ui/Select'
import { ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { StatusBadge } from '@/widgets/common/StatusBadge'

export function NotificationsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<string>('')
  const notificationsQuery = useQuery({
    queryKey: ['notifications', 'page', status],
    queryFn: () => notificationService.getMyNotifications({ status: status || undefined }),
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
          <Select value={status} onChange={(event) => setStatus(event.target.value)}>
            <option value="">{t('notifications.filters.all')}</option>
            <option value="UNREAD">{t('common.status.UNREAD')}</option>
            <option value="READ">{t('common.status.READ')}</option>
            <option value="ARCHIVED">{t('common.status.ARCHIVED')}</option>
          </Select>
        </div>
      </Card>

      <div className="space-y-4">
        {notificationsQuery.data.items.map((notification) => (
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
