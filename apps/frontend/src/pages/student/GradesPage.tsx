import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'

import { useAuth } from '@/features/auth/useAuth'
import { analyticsService, dashboardService } from '@/shared/api/services'
import { formatDateTime } from '@/shared/lib/format'
import { DataTable } from '@/shared/ui/DataTable'
import { PageHeader } from '@/shared/ui/PageHeader'
import { ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { MetricCard } from '@/widgets/common/MetricCard'

export function GradesPage() {
  const { t } = useTranslation()
  const { session } = useAuth()
  const userId = session?.user.id ?? ''
  const dashboardQuery = useQuery({
    queryKey: ['dashboard', 'student'],
    queryFn: () => dashboardService.getStudentDashboard(),
  })
  const analyticsQuery = useQuery({
    queryKey: ['analytics', 'student', userId],
    queryFn: () => analyticsService.getStudent(userId),
    enabled: Boolean(userId),
  })

  if (dashboardQuery.isLoading || analyticsQuery.isLoading) {
    return <LoadingState />
  }

  if (dashboardQuery.isError || analyticsQuery.isError || !dashboardQuery.data || !analyticsQuery.data) {
    return <ErrorState title={t('navigation.student.grades')} description={t('common.states.error')} />
  }

  return (
    <div className="space-y-6">
      <PageHeader
        description={t('grades.description')}
        title={t('navigation.student.grades')}
      />
      <div className="grid gap-4 md:grid-cols-3">
        <MetricCard title={t('dashboard.metrics.averageScore')} value={analyticsQuery.data.averageScore ?? '-'} />
        <MetricCard title={t('analytics.riskLevel.label')} value={analyticsQuery.data.riskLevel} />
        <MetricCard title={t('assignments.gradedSubmissions')} value={dashboardQuery.data.recentGrades.length} />
      </div>
      <DataTable
        columns={[
          { key: 'assignmentTitle', header: t('dashboard.assignment'), render: (item) => item.assignmentTitle },
          { key: 'score', header: t('common.labels.score'), render: (item) => item.score },
          { key: 'feedback', header: t('assignments.feedback'), render: (item) => item.feedback ?? '-' },
          { key: 'gradedAt', header: t('dashboard.gradedAt'), render: (item) => formatDateTime(item.gradedAt) },
        ]}
        rows={dashboardQuery.data.recentGrades}
      />
    </div>
  )
}
