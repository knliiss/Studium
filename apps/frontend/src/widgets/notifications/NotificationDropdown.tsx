import { useQuery } from '@tanstack/react-query'
import { Bell } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { notificationService } from '@/shared/api/services'
import { formatDateTime } from '@/shared/lib/format'
import { Card } from '@/shared/ui/Card'

export function NotificationDropdown({ targetPath }: { targetPath: string }) {
  const { t } = useTranslation()
  const unreadQuery = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: () => notificationService.getUnreadCount(),
  })
  const notificationsQuery = useQuery({
    queryKey: ['notifications', 'dropdown'],
    queryFn: () => notificationService.getMyNotifications({ page: 0, size: 5 }),
  })

  return (
    <details className="relative">
      <summary className="list-none">
        <span className="inline-flex min-h-10 min-w-10 items-center justify-center rounded-[14px] border border-border bg-surface text-text-secondary">
          <Bell className="h-4 w-4" />
        </span>
        {unreadQuery.data?.unreadCount ? (
          <span className="absolute -right-1 -top-1 inline-flex min-h-5 min-w-5 items-center justify-center rounded-full bg-accent px-1 text-[11px] font-bold text-white">
            {unreadQuery.data.unreadCount}
          </span>
        ) : null}
      </summary>

      <Card className="absolute right-0 z-40 mt-3 w-[340px] space-y-3">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-semibold text-text-primary">{t('notifications.title')}</h3>
          <Link className="text-sm font-medium text-accent" to={targetPath}>
            {t('common.actions.viewDetails')}
          </Link>
        </div>
        <div className="space-y-3">
          {notificationsQuery.data?.items.map((notification) => (
            <div key={notification.id} className="rounded-[16px] border border-border p-3">
              <p className="text-sm font-semibold text-text-primary">{notification.title}</p>
              <p className="mt-1 text-sm leading-6 text-text-secondary">{notification.body}</p>
              <p className="mt-2 text-xs text-text-muted">{formatDateTime(notification.createdAt)}</p>
            </div>
          ))}
        </div>
      </Card>
    </details>
  )
}
